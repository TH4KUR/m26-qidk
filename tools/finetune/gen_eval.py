"""Greedy generation on E's dev cases, base vs fine-tuned, in the app's prompt format. The output is a `raw.jsonl` that
core/src/test/kotlin/.../session/ReplayScoringHarness.kt replays through the real parser, validator and merge, and that
E's score.py then scores (`captured.jsonl` carries one fixed capture instant per item, the same for every model).

    python gen_eval.py --model <HF id or merged dir> [--adapter <dir>] --gold <gold_v1.jsonl> --prompt <extract_v8.txt> \
        --out <dir> [--split dev]

Only split=dev rows are read (the held-out cases are never opened here). The "report so far" is rebuilt from the model's
own earlier answers by applying each patch, which approximates the app's validator-and-merge state; ranking two models
on the same procedure is what it is for, not a replacement for the kit run.
"""
import argparse
import json
import time
from pathlib import Path

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

FIELD_ORDER = ["incident_type", "severity", "location", "date_time_text", "persons_involved", "equipment_asset",
               "immediate_actions", "witnesses"]
CAPTURED_AT = "2026-10-11T10:00:00+05:30"


def first_json(text):
    start = text.find("{")
    depth = 0
    for i in range(start, len(text)) if start >= 0 else []:
        depth += {"{": 1, "}": -1}.get(text[i], 0)
        if depth == 0:
            try:
                return json.loads(text[start:i + 1])
            except Exception:
                return None
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--adapter")
    ap.add_argument("--gold", required=True)
    ap.add_argument("--prompt", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--split", default="dev")
    ap.add_argument("--max-new", type=int, default=320)
    a = ap.parse_args()

    system = Path(a.prompt).read_text(encoding="utf-8").strip()
    tok = AutoTokenizer.from_pretrained(a.model)
    model = AutoModelForCausalLM.from_pretrained(a.model, torch_dtype=torch.bfloat16).to("cuda")
    if a.adapter:
        from peft import PeftModel
        model = PeftModel.from_pretrained(model, a.adapter).merge_and_unload()
    model.eval()

    items = [json.loads(l) for l in Path(a.gold).read_text(encoding="utf-8").splitlines() if l.strip()]
    items = [r for r in items if r.get("split") == a.split]
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    raw, captured = [], []
    for r in items:
        report, said = {}, []
        captured.append({"gold_id": r["id"], "captured_at": CAPTURED_AT})
        for t, utt in enumerate(r["turns"]):
            earlier = "".join(f'Earlier the worker said: "{u}"\n' for u in said[-2:])
            shown = {k: report[k] for k in FIELD_ORDER if k in report}
            user = f'{earlier}Report so far: {json.dumps(shown, ensure_ascii=False, separators=(",", ":"))}\nWorker: "{utt.strip()}"'
            prompt = tok.apply_chat_template(
                [{"role": "system", "content": system}, {"role": "user", "content": user}],
                tokenize=False, add_generation_prompt=True, enable_thinking=False,
            )
            enc = tok(prompt, return_tensors="pt", add_special_tokens=False).to("cuda")
            t0 = time.time()
            with torch.no_grad():
                gen = model.generate(**enc, max_new_tokens=a.max_new, do_sample=False, pad_token_id=tok.eos_token_id)
            text = tok.decode(gen[0, enc["input_ids"].shape[1]:], skip_special_tokens=True).strip()
            raw.append({"gold_id": r["id"], "turn": t, "utterance": utt, "output": text, "parse_code": None,
                        "prompt_tokens": int(enc["input_ids"].shape[1]),
                        "completion_tokens": int(gen.shape[1] - enc["input_ids"].shape[1]),
                        "ms": round((time.time() - t0) * 1000)})
            obj = first_json(text) or {}
            for k, v in (obj.get("arguments") or {}).items():
                if k in FIELD_ORDER and v not in (None, "", []):
                    report[k] = ", ".join(v) if isinstance(v, list) else str(v)
            said.append(utt)
    (out / "raw.jsonl").write_text("\n".join(json.dumps(x, ensure_ascii=False) for x in raw) + "\n", encoding="utf-8")
    (out / "captured.jsonl").write_text("\n".join(json.dumps(x) for x in captured) + "\n", encoding="utf-8")
    print(f"{len(items)} items, {len(raw)} turns -> {out}")


if __name__ == "__main__":
    main()
