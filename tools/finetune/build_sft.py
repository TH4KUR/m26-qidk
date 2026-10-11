"""Build SFT files for the Qwen3-1.7B fine-tune from B's synthetic gold, with the SHIPPING system prompt.

Only the system message is replaced (by `extract_v8.txt`, trimmed exactly as `PromptBuilder.default()` does), so the
model is trained on byte-identical inputs to the app. User and assistant messages are B's, unchanged.

    python tools/finetune/build_sft.py --gold-dir <.../data/synthetic_gold/v1> \
        --prompt core/src/main/resources/prompts/extract_v8.txt --out <out dir> [--dev-gold <gold_v1.jsonl>]

Writes sft_train.jsonl (600), sft_val.jsonl (100), sft_test.jsonl (100, evaluation only) and sft_stats.json.
It never reads E's held-out cases: with --dev-gold only rows whose split is "dev" are compared.
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


def load(p):
    return [json.loads(l) for l in Path(p).read_text(encoding="utf-8").splitlines() if l.strip()]


def toks(s):
    return re.sub(r"[^\w\s]", " ", s.lower()).split()


def grams(s, n=6):
    t = toks(s)
    return {" ".join(t[i:i + n]) for i in range(max(0, len(t) - n + 1))}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gold-dir", required=True)
    ap.add_argument("--prompt", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--dev-gold", help="E's gold_v1.jsonl; only split=dev rows are read, for an n-gram overlap check")
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

    stats = {"system_prompt_sha256": hashlib.sha256(system.encode("utf-8")).hexdigest()[:16], "files": {}}
    dropped = []
    for name, src in (("sft_train", "train.jsonl"), ("sft_val", "validation.jsonl"), ("sft_test", "test.jsonl")):
        kept = 0
        with (out / f"{name}.jsonl").open("w", encoding="utf-8", newline="\n") as f:
            for r in load(Path(a.gold_dir) / src):
                m = r["messages"]
                assert [x["role"] for x in m] == ["system", "user", "assistant"], r
                answer = json.loads(m[2]["content"])  # must be one JSON object
                assert answer.get("tool") == "update_report"
                overlap = len(grams(m[1]["content"]) & dev_grams) if dev_grams else 0
                if name != "sft_test" and overlap >= 3:  # three shared 6-grams with a dev narrative: drop
                    dropped.append((name, overlap))
                    continue
                f.write(json.dumps({"messages": [{"role": "system", "content": system}, m[1], m[2]]}, ensure_ascii=False) + "\n")
                kept += 1
        stats["files"][name] = kept
    stats["dropped_for_overlap_with_dev"] = len(dropped)
    (out / "sft_stats.json").write_text(json.dumps(stats, indent=2), encoding="utf-8")
    print(json.dumps(stats, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
