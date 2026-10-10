"""LLM candidate contest (plan spike S3): run gold utterances through a llama-server model, keep every
raw output, build "raw" reports (parsed payload written straight in, no grounding), and score them.

    llama-server -m Qwen3-1.7B-Q4_0.gguf --port 8080 --ctx-size 4096
    python3 run_eval.py --model qwen3-1.7b-q4_0 --split dev --limit 20 [--grammar] [--no-think]

Writes runs/<run_id>/{raw.jsonl, predictions.jsonl, summary.json, summary.md}. The server result
shortlists models; the pick needs a run on the kit (server latency says nothing about the NPU, so
rank ties on output tokens instead).
"""

from __future__ import annotations

import argparse
import json
import time
import urllib.request
from datetime import datetime, timezone, timedelta
from pathlib import Path

from parse import parse
from score import FIELDS, render_markdown, score

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
IST = timezone(timedelta(hours=5, minutes=30))
LIST_FIELDS = {"persons_involved", "witnesses"}
PAYLOAD_TO_FIELD = {f: f.upper() for f in FIELDS} | {"date_time_text": "DATE_TIME"}


def apply_raw(fields: dict, payload: dict) -> dict:
    """The 'raw' Judge Bench config: every non-empty payload value becomes REPORTED, later turns overwrite."""
    out = dict(fields)
    for key, field in PAYLOAD_TO_FIELD.items():
        if key == "date_time":
            continue
        value = payload.get(key)
        if key in LIST_FIELDS:
            if value:
                out[field] = {"text": ", ".join(value), "status": "REPORTED"}
        elif value:
            out[field] = {"text": value, "status": "REPORTED"}
            if key == "date_time_text":
                out[field]["raw"] = True
    return out


def report_so_far(fields: dict) -> dict:
    keys = {v: k for k, v in PAYLOAD_TO_FIELD.items() if k != "date_time"}
    return {keys[f]: v["text"] for f, v in fields.items() if v.get("status") == "REPORTED"}


def build_messages(system: str, fields: dict, utterance: str, no_think: bool = False) -> list[dict]:
    user = f"Report so far: {json.dumps(report_so_far(fields), ensure_ascii=False)}\nWorker: \"{utterance}\""
    if no_think:
        user += " /no_think"
    return [{"role": "system", "content": system}, {"role": "user", "content": user}]


def run_items(items: list[dict], complete, system: str, no_think: bool = False):
    raw_log, predictions = [], []
    for item in items:
        fields: dict = {}
        captured_at = datetime.now(IST).isoformat(timespec="seconds")
        for turn, utterance in enumerate(item["turns"]):
            res = complete(build_messages(system, fields, utterance, no_think))
            parsed = parse(res["text"])
            if parsed.ok:
                fields = apply_raw(fields, parsed.payload)
            raw_log.append({"gold_id": item["id"], "turn": turn, "utterance": utterance, "output": res["text"],
                            "parse_code": parsed.code, "prompt_tokens": res.get("prompt_tokens"),
                            "completion_tokens": res.get("completion_tokens"), "ms": res.get("ms")})
        predictions.append({"gold_id": item["id"], "captured_at": captured_at, "compute": "SERVER",
                            "report": {"id": item["id"], "createdAtEpochMs": 0, "fields": fields}})
    return raw_log, predictions


def llama_server(url: str, max_tokens: int, schema: dict | None, enable_thinking: bool):
    def complete(messages):
        body = {"messages": messages, "temperature": 0, "seed": 42, "max_tokens": max_tokens,
                "chat_template_kwargs": {"enable_thinking": enable_thinking}}
        if schema is not None:
            body["response_format"] = {"type": "json_schema", "json_schema": {"name": "update_report", "schema": schema}}
        req = urllib.request.Request(f"{url}/v1/chat/completions", data=json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json"})
        t0 = time.perf_counter()
        with urllib.request.urlopen(req, timeout=600) as r:
            data = json.load(r)
        usage = data.get("usage", {})
        return {"text": data["choices"][0]["message"].get("content") or "", "ms": (time.perf_counter() - t0) * 1000,
                "prompt_tokens": usage.get("prompt_tokens"), "completion_tokens": usage.get("completion_tokens")}
    return complete


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True, help="label for the run, e.g. qwen3-1.7b-q4_0")
    ap.add_argument("--server", default="http://127.0.0.1:8080")
    ap.add_argument("--gold", default=str(REPO / "app/src/main/assets/eval/gold_v1.jsonl"))
    ap.add_argument("--prompt", default=str(HERE / "prompts/chat_json_system.txt"))
    ap.add_argument("--split", choices=["dev", "holdout", "all"], default="dev")
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--grammar", action="store_true", help="constrain decoding with prompts/update_report.schema.json")
    ap.add_argument("--no-think", action="store_true", help="Qwen3: append /no_think and disable thinking")
    ap.add_argument("--max-tokens", type=int, default=320)
    args = ap.parse_args()

    items = [json.loads(l) for l in Path(args.gold).read_text(encoding="utf-8").splitlines() if l.strip()]
    if args.split != "all":
        items = [i for i in items if i["split"] == args.split]
    if args.limit:
        items = items[:args.limit]
    schema = json.loads((HERE / "prompts/update_report.schema.json").read_text()) if args.grammar else None
    complete = llama_server(args.server, args.max_tokens, schema, enable_thinking=not args.no_think)
    system = Path(args.prompt).read_text(encoding="utf-8")

    run_id = f"{datetime.now(IST):%Y%m%d-%H%M}-{args.model}-{'grammar' if args.grammar else 'free'}-{args.split}"
    out = HERE / "runs" / run_id
    out.mkdir(parents=True, exist_ok=True)
    raw_log, preds = run_items(items, complete, system, args.no_think)
    summary = score(items, preds)
    parse_ok = sum(r["parse_code"] is None for r in raw_log)
    toks = [r["completion_tokens"] for r in raw_log if r["completion_tokens"] is not None]
    summary["run"] = {"model": args.model, "grammar": args.grammar, "split": args.split, "n_calls": len(raw_log),
                      "parse_ok": parse_ok, "mean_completion_tokens": (sum(toks) / len(toks)) if toks else None,
                      "max_prompt_tokens": max((r["prompt_tokens"] or 0) for r in raw_log) if raw_log else None}
    for name, rows in (("raw.jsonl", raw_log), ("predictions.jsonl", preds)):
        (out / name).write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")
    (out / "summary.json").write_text(json.dumps(summary, indent=1), encoding="utf-8")
    run = summary["run"]
    md = render_markdown(summary, title=run_id) + (
        f"\nValid tool calls: {run['parse_ok']}/{run['n_calls']}; mean output tokens {run['mean_completion_tokens']}; "
        f"max prompt tokens {run['max_prompt_tokens']}\n")
    (out / "summary.md").write_text(md, encoding="utf-8")
    print(md)
    print(f"saved to {out}")


if __name__ == "__main__":
    main()
