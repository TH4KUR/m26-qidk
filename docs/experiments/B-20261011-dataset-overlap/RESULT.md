# Decision: the 800-row synthetic set is abandoned for fine-tuning (2026-10-11)

**Do not fine-tune or requantize against `data/synthetic_gold/v1/`.** The user decided this after the token overlap was found to exceed 80%; the measurement below confirms the overlap and shows that a minimal fix does not remove it.

Reproduce: `python3 docs/experiments/B-20261011-dataset-overlap/overlap.py` (read-only, no model; output copied to `overlap_output.txt`).

## Measured (raw_text, lower-cased token sets, Jaccard)
- **Inside train:** 317 of 600 rows have another train row at Jaccard >= 0.8 (median nearest-neighbour 0.81). 600 rows come from 168 families.
- **Validation / test against train:** 25/100 and 28/100 rows have a train row at Jaccard >= 0.8; on average 65% and 67% of their 4-grams already occur in train; 36 and 38 rows share >= 80% of their 4-grams. A's earlier count (16/100, 18/100) used a different cut and agrees in direction.
- So validation and test are not independent of train, and a model trained on the set can look good by memorising stock phrasing.

## Minimal mitigation tried: greedy pruning of near-duplicate train rows
| train after pruning at Jaccard < | rows kept | val rows with near-copy >= 0.8 | test rows with near-copy >= 0.8 | mean 4-gram share, val / test |
|---|---|---|---|---|
| (none) | 600 | 25 | 28 | 0.65 / 0.67 |
| 0.8 | 401 | 24 | 26 | 0.64 / 0.65 |
| 0.7 | 296 | 17 | 24 | 0.60 / 0.61 |
| 0.6 | 223 | 13 | 13 | 0.58 / 0.59 |
| 0.5 | 152 | 11 | 13 | 0.55 / 0.55 |

Pruning enough to matter leaves 150 to 300 rows, and more than half of validation and test 4-grams still occur in train, because stock phrases are shared across families. This is not a minimal fix: it needs different sentence skeletons at generation time, not a filter afterwards.

## Why not "fix it and train"
- No result shows improvement: C's fine-tunes on this set (4B v1 and v2, C/016, C/019) did not clear the adoption gate on E's dev 27; v2 recall rose but hallucination stayed above the 4% gate.
- Requantization is not available (no QAIRT, A/030) and no fine-tune has been exported.
- Training needs the user's explicit approval and rented compute; neither exists for a re-pruned set, and a smaller set would not be measured against an independent benchmark.

## What remains usable
`SyntheticGoldContractTest` and the replay audit use the files as a **parser/validator/merge regression fixture** (do the update, none and correction rules behave). That use does not depend on independence from a model and stays; it must not be quoted as a training or accuracy result.

## If a training set is wanted later (not started)
Generate from many more distinct skeletons, split by skeleton family and by stock-phrase cluster, include partial utterances (absent fields absent from the label), enforce a near-duplicate gate at generation time (Jaccard and 4-gram share against every other split), and get the user's approval first.
