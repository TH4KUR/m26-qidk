"""Build SFT files for the Qwen3-1.7B fine-tune from B's synthetic gold, with the SHIPPING system prompt.

Only the system message is replaced (by `extract_v8.txt`, trimmed exactly as `PromptBuilder.default()` does), so the
model is trained on byte-identical inputs to the app. B's user and assistant messages are kept, with two deliberate edits:

* `--strip-location-prefix`: a bare leading "in " / "at " is removed from `location` values (target and saved
  "Report so far"), so the model writes the place, not the preposition (E's finding: 546 of 565 targets began with one).
  `near`, `outside`, `opposite`, `between` etc. are kept, as the dataset's own policy says. Evidence quotes are untouched.
* `--drop-ids FILE`: ids whose label the real parser + validator would not store as REPORTED (SftLabelAuditHarness).

    python tools/finetune/build_sft.py --gold-dir <.../data/synthetic_gold/v1> \
        --prompt core/src/main/resources/prompts/extract_v8.txt --out <out dir> \
        [--dev-gold <gold_v1.jsonl>] [--drop-ids audit_issues.json] [--strip-location-prefix]

Writes sft_train.jsonl, sft_val.jsonl, sft_test.jsonl (evaluation only), gold_norm.jsonl (the edited rows, for re-auditing)
and sft_stats.json. It never reads E's held-out cases: with --dev-gold only rows whose split is "dev" are compared.
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

SPLIT_FILES = {"train": "sft_train", "validation": "sft_val", "test": "sft_test"}
FIELD_ORDER = ["incident_type", "severity", "location", "date_time_text", "persons_involved", "equipment_asset",
               "immediate_actions", "witnesses"]
PREFIX = re.compile(r"^(?:in|at)\s+", re.IGNORECASE)


def load(p):
    return [json.loads(l) for l in Path(p).read_text(encoding="utf-8").splitlines() if l.strip()]


def toks(s):
    return re.sub(r"[^\w\s]", " ", s.lower()).split()


def grams(s, n=6):
    t = toks(s)
    return {" ".join(t[i:i + n]) for i in range(max(0, len(t) - n + 1))}


def compact(obj):
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))


def strip_location(row):
    """Returns (edited row, number of values changed)."""
    changed = 0
    m = row["messages"]
    answer = json.loads(m[2]["content"])
    loc = answer.get("arguments", {}).get("location")
    if isinstance(loc, str) and PREFIX.match(loc) and len(PREFIX.sub("", loc).split()) >= 1:
        answer["arguments"]["location"] = PREFIX.sub("", loc)
        m[2]["content"] = compact(answer)
        changed += 1
    user = m[1]["content"]
    mm = re.search(r"^Report so far: (\{.*\})$", user, re.MULTILINE)
    if mm:
        saved = json.loads(mm.group(1))
        sl = saved.get("location")
        if isinstance(sl, str) and PREFIX.match(sl):
            saved["location"] = PREFIX.sub("", sl)
            ordered = {k: saved[k] for k in FIELD_ORDER if k in saved}
            m[1]["content"] = user[:mm.start(1)] + compact(ordered) + user[mm.end(1):]
            changed += 1
    return row, changed


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gold-dir", required=True)
    ap.add_argument("--prompt", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--dev-gold", help="E's gold_v1.jsonl; only split=dev rows are read, for an n-gram overlap check")
    ap.add_argument("--drop-ids", help="JSON from SftLabelAuditHarness ({'issues': {id: [...]}})")
    ap.add_argument("--strip-location-prefix", action="store_true")
    a = ap.parse_args()

    system = Path(a.prompt).read_text(encoding="utf-8").strip()
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)

    dev_grams = set()
    if a.dev_gold:
        for r in load(a.dev_gold):
            if r.get("split") == "dev":
                for t in r.get("turns", []):
                    dev_grams |= grams(t)
    drop = set(json.loads(Path(a.drop_ids).read_text(encoding="utf-8"))["issues"]) if a.drop_ids else set()

    stats = {"system_prompt_sha256": hashlib.sha256(system.encode("utf-8")).hexdigest()[:16], "files": {},
             "dropped_by_audit": {}, "dropped_for_dev_overlap": 0, "location_values_stripped": 0,
             "gold_sha256": hashlib.sha256((Path(a.gold_dir) / "gold.jsonl").read_bytes()).hexdigest()}
    handles = {s: (out / f"{n}.jsonl").open("w", encoding="utf-8", newline="\n") for s, n in SPLIT_FILES.items()}
    norm = (out / "gold_norm.jsonl").open("w", encoding="utf-8", newline="\n")
    counts = {n: 0 for n in SPLIT_FILES.values()}
    for r in load(Path(a.gold_dir) / "gold.jsonl"):
        name = SPLIT_FILES[r["split"]]
        if r["id"] in drop and r["split"] != "test":
            stats["dropped_by_audit"][name] = stats["dropped_by_audit"].get(name, 0) + 1
            continue
        m = r["messages"]
        assert [x["role"] for x in m] == ["system", "user", "assistant"], r["id"]
        assert json.loads(m[2]["content"]).get("tool") == "update_report"
        if a.strip_location_prefix:
            r, n = strip_location(r)
            stats["location_values_stripped"] += n
            m = r["messages"]
        if r["split"] != "test" and dev_grams and len(grams(m[1]["content"]) & dev_grams) >= 3:
            stats["dropped_for_dev_overlap"] += 1
            continue
        handles[r["split"]].write(json.dumps(
            {"messages": [{"role": "system", "content": system}, m[1], m[2]]}, ensure_ascii=False) + "\n")
        norm.write(json.dumps({"id": r["id"], "raw_text": r["raw_text"], "messages": m, "split": r["split"]}, ensure_ascii=False) + "\n")
        counts[name] += 1
    for h in handles.values():
        h.close()
    norm.close()
    stats["files"] = counts
    (out / "sft_stats.json").write_text(json.dumps(stats, indent=2), encoding="utf-8")
    print(json.dumps(stats, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
