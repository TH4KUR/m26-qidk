"""Second-pass helper for the optional fields (experiment E-20261011-optional-field-second-pass).

One extra call per report asks a small model ONLY for equipment_asset, immediate_actions, persons_involved and
witnesses, over the whole transcript. The idea: its answers become "Suggested / Use this" popups, never silent
fills. Every value is scored as if the worker accepted it, so wrong values and false alarms are counted against it.

    llama-server -m Qwen3-1.7B-Q4_0.gguf --port 8080 --ctx-size 4096 --jinja
    git show origin/b/speech-engines:data/synthetic_gold/v1/gold.jsonl > /tmp/b_gold.jsonl
    python3 optional_pass.py --b-gold /tmp/b_gold.jsonl [--transcripts <sheet.md>] [--free]

Sets: E dev 27 (held-out never read), E homophones 8, B synthetic validation+test initial reports (written
independently of this prompt), and optionally the typed own-words phone transcripts (dev clips only).
The team scorer (score.py) is unchanged; its verdict is "exact". "contains" and "partial" are an
experiment-only fair match for free text, reported next to it, never instead of it.
"""

from __future__ import annotations

import argparse
import json
import re
import time
import urllib.request
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path

import score

HERE = Path(__file__).resolve().parent
EVAL = HERE.parents[1] / "app/src/main/assets/eval"
IST = timezone(timedelta(hours=5, minutes=30))
OPT = ["equipment_asset", "immediate_actions", "persons_involved", "witnesses"]

# Examples use objects that do not occur in E's dev or held-out items.
SYSTEM = """You read a worker's spoken incident report and pick out four things. Reply with ONLY one JSON object.
Keys (leave a key out when the worker does not state it; never write null, [] or ""):
- equipment_asset: the object the incident happened to or with: a machine, device, appliance, vehicle, furniture, container or goods (for example trolley, gas cylinder, projector, shelf, sacks). Copy the worker's words. Not the place, not the incident.
- immediate_actions: what the worker or others already did or are doing now (switched off, called, moved, covered, informed). Copy the worker's words. Not plans, not a description of the problem.
- persons_involved: list of names of people the incident happened to or who caused it. Names only, never a role ("guard") or the speaker.
- witnesses: list of names of people who saw it or were there. Names only. "A lot of people" is not a witness.
Examples:
Report: "The projector in the seminar room started smoking, I pulled the plug and told the caretaker. Meera saw it."
{"equipment_asset":"projector","immediate_actions":"pulled the plug and told the caretaker","witnesses":["Meera"]}
Report: "Arjun tripped over a box of sacks near the store, he hurt his wrist. We gave him first aid."
{"equipment_asset":"box of sacks","immediate_actions":"gave him first aid","persons_involved":["Arjun"]}
Report: "There's a strong gas smell near the back gate, started ten minutes ago."
{}"""
SCHEMA = {"type": "object", "additionalProperties": False, "properties": {
    "equipment_asset": {"type": "string", "minLength": 1, "maxLength": 120},
    "immediate_actions": {"type": "string", "minLength": 1, "maxLength": 120},
    "persons_involved": {"type": "array", "items": {"type": "string", "minLength": 1}, "maxItems": 6},
    "witnesses": {"type": "array", "items": {"type": "string", "minLength": 1}, "maxItems": 6}}}

PERSON_WORDS = set("""person people someone somebody anyone everyone student students worker workers staff member members
patient boy girl man woman men women guard guards nurse doctor driver cook rider robber child kid kids visitor
colleague friend friends me i he she they him her them we us myself caretaker technician electrician cleaner""".split())
INCIDENT_WORDS = set("""water leak leaking leakage wet floor fire smoke smoking spill spilled flood flooding gas smell burning
sparks sparking shock injury accident damage""".split())


def keep_equipment(value: str) -> bool:
    """Not a person word, and not only incident words ("water leak", "wet floor"); "water pipe" stays."""
    words = [t for t in re.findall(r"\w+", value.lower()) if t not in score.STOP_WORDS]
    if not words or any(t in PERSON_WORDS for t in words):
        return False
    return not all(t in INCIDENT_WORDS for t in words)


def keep_name(name: str) -> bool:
    parts = name.strip().split()
    return bool(parts) and parts[0][0].isupper() and not any(p.lower() in PERSON_WORDS for p in parts)


def grounded(value: str, transcript: str) -> bool:
    words = set(re.findall(r"\w+", transcript.lower()))
    toks = [t for t in re.findall(r"\w+", value.lower()) if t not in score.STOP_WORDS]
    return bool(toks) and all(t in words for t in toks)


def fair(field: str, gold: dict, pred_text: str) -> str:
    """exact (team scorer) / contains / partial (score.free_text_match, the lenient view) / wrong."""
    if score.outcome(field, gold, {"text": pred_text, "status": "REPORTED"}, None) == "correct":
        return "exact"
    return score.free_text_match(gold, pred_text) or "wrong"


def read_transcripts(path) -> list[tuple[str, str]]:
    """(clip_id, said) for filled, non-held-out entries of the typed-transcript sheet."""
    out = []
    for block in re.split(r"\n## ", Path(path).read_text(encoding="utf-8"))[1:]:
        head = block.splitlines()[0]
        m = re.search(r"\(clip (S\d_\d\d)", head)
        said = re.search(r"^SAID:(.*)$", block, re.M)
        if m and said and said.group(1).strip() and "held out" not in head:
            out.append((m.group(1), said.group(1).strip()))
    return out


def ask(server: str, text: str, grammar: bool):
    body = {"messages": [{"role": "system", "content": SYSTEM}, {"role": "user", "content": f'Report: "{text}" /no_think'}],
            "temperature": 0, "seed": 42, "max_tokens": 200, "chat_template_kwargs": {"enable_thinking": False}}
    if grammar:
        body["response_format"] = {"type": "json_schema", "json_schema": {"name": "optional_fields", "schema": SCHEMA}}
    req = urllib.request.Request(f"{server}/v1/chat/completions", data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"})
    t0 = time.perf_counter()
    with urllib.request.urlopen(req, timeout=600) as r:
        data = json.load(r)
    return data["choices"][0]["message"].get("content") or "", time.perf_counter() - t0


def parse_output(raw: str) -> dict | None:
    raw = re.sub(r"<think>.*?</think>", "", raw, flags=re.S).strip()
    m = re.search(r"\{.*\}", raw, flags=re.S)
    try:
        return json.loads(m.group(0)) if m else None
    except json.JSONDecodeError:
        return None


def _jsonl(path) -> list[dict]:
    return [json.loads(l) for l in Path(path).read_text(encoding="utf-8").splitlines() if l.strip()]


def e_items(path, split=None) -> list[dict]:
    return [{"id": g["id"], "text": " ".join(g["turns"]), "gold": {f: g["gold"][f] for f in OPT}}
            for g in _jsonl(path) if split is None or g["split"] == split]


def b_items(path) -> list[dict]:
    out = []
    for r in _jsonl(path):
        if r["report_before"] or r["split"] not in ("validation", "test"):
            continue
        args = r["target"]["arguments"]
        out.append({"id": r["id"], "text": r["raw_text"],
                    "gold": {f: ({"status": "REPORTED", "value": args[f]} if args.get(f) else {"status": "MISSING"}) for f in OPT}})
    return out


def transcript_items(sheet) -> list[dict]:
    """Own-words clips: gold is the card's gold item (spoken detail corrections do not touch these four fields)."""
    clip_gold = {r["clip_id"]: r["gold_id"] for r in _jsonl(EVAL / "manifest_phone_r1.jsonl")}
    gold = {g["id"]: g for g in _jsonl(EVAL / "gold_v1.jsonl")}
    return [{"id": cid, "text": said, "gold": {f: gold[clip_gold[cid]]["gold"][f] for f in OPT}}
            for cid, said in read_transcripts(sheet) if gold[clip_gold[cid]]["split"] == "dev"]


def run_set(items, complete) -> tuple[dict, list, list]:
    tally = {f: Counter() for f in OPT}
    review, secs = [], []
    for it in items:
        raw, s = complete(it["text"])
        secs.append(s)
        p = parse_output(raw) or {}
        for f in OPT:
            v = p.get(f)
            if isinstance(v, list):
                v = ", ".join(x for x in v if isinstance(x, str) and x.strip() and grounded(x, it["text"]) and keep_name(x))
            elif isinstance(v, str):
                v = v if grounded(v, it["text"]) and (f != "equipment_asset" or keep_equipment(v)) else ""
            else:
                v = ""
            g = it["gold"][f]
            has_gold = g["status"] in ("REPORTED", "SUGGESTED")
            if not v:
                tally[f]["miss" if has_gold else "correct_empty"] += 1
            elif not has_gold:
                tally[f]["false_alarm"] += 1
                review.append({"id": it["id"], "field": f, "verdict": "false_alarm", "pred": v, "gold": g["status"], "text": it["text"]})
            else:
                k = fair(f, g, v)
                tally[f][k] += 1
                if k != "exact":
                    review.append({"id": it["id"], "field": f, "verdict": k, "pred": v, "gold": g.get("value"), "text": it["text"]})
    return {f: dict(c) for f, c in tally.items()}, review, secs


def table(name: str, tally: dict, n: int, secs: list) -> str:
    rows = [f"### {name} ({n} items, median {sorted(secs)[len(secs) // 2]:.1f} s)", "",
            "| field | gold has | exact | contains | partial | wrong | miss | gold empty | false alarm |",
            "|---|---|---|---|---|---|---|---|---|"]
    for f in OPT:
        c = tally[f]
        has = sum(c.get(k, 0) for k in ("exact", "contains", "partial", "wrong", "miss"))
        empty = c.get("correct_empty", 0) + c.get("false_alarm", 0)
        rows.append(f"| {f} | {has} | {c.get('exact', 0)} | {c.get('contains', 0)} | {c.get('partial', 0)} | "
                    f"{c.get('wrong', 0)} | {c.get('miss', 0)} | {empty} | {c.get('false_alarm', 0)} |")
    return "\n".join(rows) + "\n"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--server", default="http://127.0.0.1:8080")
    ap.add_argument("--model", default="qwen3-1.7b-q4_0")
    ap.add_argument("--b-gold", help="B's data/synthetic_gold/v1/gold.jsonl (b/speech-engines 51c1124)")
    ap.add_argument("--transcripts", help="typed own-words transcript sheet (kept outside the repo)")
    ap.add_argument("--free", action="store_true", help="no JSON-schema constraint")
    args = ap.parse_args()
    sets = [("E-dev27", e_items(EVAL / "gold_v1.jsonl", "dev")), ("E-homophones8", e_items(EVAL / "gold_homophones.jsonl"))]
    if args.b_gold:
        sets.append(("B-synthetic134", b_items(args.b_gold)))
    if args.transcripts:
        sets.append(("E-phone-transcripts", transcript_items(args.transcripts)))
    grammar = not args.free
    run_id = f"{datetime.now(IST):%Y%m%d-%H%M}-optional-pass-{args.model}-{'grammar' if grammar else 'free'}"
    out = HERE / "runs" / run_id
    out.mkdir(parents=True, exist_ok=True)
    md = [f"# {run_id}", ""]
    for name, items in sets:
        tally, review, secs = run_set(items, lambda t: ask(args.server, t, grammar))
        (out / f"{name}.tally.json").write_text(json.dumps(tally, indent=1), encoding="utf-8")
        (out / f"{name}.review.jsonl").write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in review), encoding="utf-8")
        md.append(table(name, tally, len(items), secs))
    (out / "summary.md").write_text("\n".join(md), encoding="utf-8")
    print("\n".join(md))
    print(f"saved to {out}")


if __name__ == "__main__":
    main()
