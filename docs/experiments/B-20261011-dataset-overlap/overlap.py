"""Token overlap inside and across the splits of data/synthetic_gold/v1/gold.jsonl, and what greedy near-duplicate pruning of
train would cost. Read-only, no model. Run from the repo root:  python3 docs/experiments/B-20261011-dataset-overlap/overlap.py
Text compared is raw_text (the worker's utterance) as a lower-cased set of [a-z0-9] tokens; Jaccard = |A and B| / |A or B|."""
import json
import re
import statistics

rows = [json.loads(line) for line in open("data/synthetic_gold/v1/gold.jsonl", encoding="utf-8")]
toks = lambda s: re.findall(r"[a-z0-9]+", s.lower())
jac = lambda a, b: len(a & b) / len(a | b) if a | b else 0.0
grams = lambda t, n=4: {tuple(t[i:i + n]) for i in range(len(t) - n + 1)}
S = [(r["split"], r["group_id"], set(toks(r["raw_text"])), toks(r["raw_text"])) for r in rows]
train = [s for s in S if s[0] == "train"]


def report(label, pool):
    shared = set().union(*[grams(k[3]) for k in pool])
    for sp in ("validation", "test"):
        v = [s for s in S if s[0] == sp]
        near = [max(jac(s[2], k[2]) for k in pool) for s in v]
        share = [len(grams(s[3]) & shared) / max(1, len(grams(s[3]))) for s in v]
        print(f"{label:>34} | {sp:<10} nearest-train Jaccard median {statistics.median(near):.2f}, >=0.8: {sum(x >= 0.8 for x in near)}/100, "
              f"mean share of 4-grams seen in train {statistics.mean(share):.2f}, rows with >=80% shared 4-grams: {sum(x >= 0.8 for x in share)}")


within = [max(jac(s[2], u[2]) for u in train if u is not s) for s in train]
print(f"rows {len(rows)}; train {len(train)} in {len({s[1] for s in train})} groups")
print(f"train rows with a near-copy (Jaccard >= 0.8) inside train: {sum(x >= 0.8 for x in within)}/{len(train)} (median nearest {statistics.median(within):.2f})")
report("full train (600)", train)
for thr in (0.8, 0.7, 0.6, 0.5):
    kept = []
    for s in train:
        if all(jac(s[2], k[2]) < thr for k in kept):
            kept.append(s)
    report(f"train pruned at Jaccard<{thr} ({len(kept)} rows)", kept)
