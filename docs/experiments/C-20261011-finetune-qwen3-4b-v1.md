# C-20261011: LoRA fine-tune of Qwen3-4B-Instruct-2507 on B's synthetic gold (v1), compared with the 1.7B run

- **Date/time:** 2026-10-11, about 06:50 to 07:15 IST (laptop clock).
- **Who:** C, on the user's instruction ("do the same you did for the 1.7B, benchmark and compare").
- **Goal:** the same recipe as `C-20261011-finetune-qwen3-1p7b-v1.md`, on the model the app actually ships as primary.
- **Result in one line:** the 4B base already passes A's gate on this check (recall 88%, precision 95%, hallucination 3.7%); the fine-tune reaches 90% recall but drops to 91% precision and 12% hallucination, so it fails the gate and is not adopted.

## Setup (identical to the 1.7B run except the base)
- Base `Qwen/Qwen3-4B-Instruct-2507`, bf16, same machine and library versions (torch 2.10.0+cu128, transformers 5.7.0, peft 0.21.2).
- Data: B's `51c1124` gold (SHA-256 `9e00c787e1c6da572f4f95d28c96fa7091db4a60fbcf9dca366547a3c543f389`), 599 train / 100 validation after my label audit, shipping `extract_v8.txt` prompt. E's held-out 13 never read or copied.
- LoRA r=32, alpha 64, dropout 0.05, all linear layers, lr 1.5e-4 cosine, 3 epochs, effective batch 16, bf16, max length 2048, seed 42, loss on the answer only. 114 steps, **316 s**. Validation loss 1.135 before, 0.0155 / 0.0097 / 0.0096 after epochs 1 to 3. Merged checkpoint 7.6 GB.
- Evaluation: E's dev 27 (34 turns), committed gold, greedy decoding, same prompt, raw outputs replayed through the real parser, validator and merge (`ReplayScoringHarness`), E's `score.py` unchanged. One run each.

## Results (correct of 135 gold values; hallucination = filled where the gold is empty, of 81)

| run | recall | precision | wrong | hallucination | mandatory recall | valid JSON | median tokens |
|---|---|---|---|---|---|---|---|
| 1.7B base | 94 (70%) | 94/98 (96%) | 4 | 0 | 75/86 (87%) | 34/34 | 67 |
| 1.7B tuned | 99 (73%) | 99/126 (79%) | 8 | 19 (23%) | 67/86 (78%) | 33/34 | 123 |
| **4B base** | **119 (88%)** | **119/125 (95%)** | 3 | **3 (3.7%)** | **81/86 (94%)** | 34/34 | 94 |
| 4B tuned | 121 (90%) | 121/133 (91%) | 2 | 10 (12%) | 81/86 (94%) | 34/34 | 118 |

Per field, correct of gold values (type, severity, location, date, persons, equipment, actions, witnesses):
1.7B base 21/27, 9/9, 23/26, 22/24, 2/7, 3/19, 13/21, 1/2; 1.7B tuned 21/27, 8/9, 18/26, 20/24, 4/7, 12/19, 14/21, 2/2;
4B base 24/27, 9/9, 24/26, 24/24, 7/7, 8/19, 21/21, 2/2; 4B tuned 23/27, 9/9, 25/26, 24/24, 4/7, 14/19, 21/21, 1/2.

## Gate (A/017, fixed beforehand): recall >= 75%, precision >= 92%, hallucination <= 4%, valid-after-retry >= 95%, median turn <= 6 s
- 4B base: passes recall, precision, hallucination and validity on this check (turn time not measured here).
- 4B tuned: **fails precision (91%) and hallucination (12%)**; recall passes.
- Both 1.7B runs fail the recall gate; the tuned one also fails precision and hallucination.

## What the tune did to the 4B
- **Gained:** equipment 8 to 14 of 19 (6 more, e.g. microwave, pan, electric pole, bulk bags, water pipe, bikes); incident type g14.
- **Lost:** persons 7 to 4 (g05, g14, g22 became misses), witnesses 2 to 1, incident type g04 and g26.
- **Invented (hallucinated, not in the base):** severity 4 (g13 "none", g22, g25, g32 "severe"), equipment 2 (g05 "bottle of acid", g29 "robber"), witnesses 2 (g05, g22), actions 1 (g16 "saw it"). The base invented only 3 actions.
- **Cause (by inspection, as for the 1.7B):** every training row states all four mandatory answers and most optional ones, so the model learned to always emit severity and equipment.
- Net +2 correct cells against +7 hallucinations and +24 completion tokens (about +1 s per turn at the kit's 22 tok/s).

## Caveats
- One run, n = 27. A 2-cell recall difference is noise; 3 to 10 hallucinated fields and 95% to 91% precision are the differences that matter, and they are in the direction seen on the 1.7B.
- **These are bf16 results on a GPU, not the kit.** A's kit run of the same model (quantised w4a16 bundle on the NPU, recall patch) scored 65% recall, 97% precision, 1% hallucination on the same 27 cases (A/015). The 23-point recall gap between 88% here and 65% there is not explained by the gold file (E/012: only location changed) and is worth a check by the app session: quantisation, sampling settings, the "report so far" context, or the engine. The same comparison for the 1.7B is 70% here against 62% for the Q4_0 GGUF.
- `report so far` is rebuilt from the model's own earlier answers by applying patches, an approximation of the app's validator-and-merge state (same for both arms).
- No quantisation or export was attempted (A/017 rule: only a checkpoint that beats its own base goes to export).

## Addendum: uncertainty, paired comparison, and B's synthetic test split

### Dev 27, 4B base against 4B tuned, with intervals (Wilson 95%)

| measure | 4B base | 4B tuned |
|---|---|---|
| recall | 119/135 = 88.1% [81.6, 92.6] | 121/135 = 89.6% [83.3, 93.7] |
| precision | 119/125 = 95.2% [89.9, 97.8] | 121/133 = 91.0% [84.9, 94.8] |
| hallucination | 3/81 = 3.7% [1.3, 10.3] | 10/81 = 12.3% [6.8, 21.3] |
| mandatory recall | 81/86 = 94.2% [87.1, 97.5] | 81/86 = 94.2% [87.1, 97.5] |
| median completion tokens | 94 | 118 |

Paired over the 216 (item, field) cells: the tuned model is right where the base is wrong in **11** cells and wrong where the base is right in **16** (two-sided sign test p = 0.44). On dev 27 the two models cannot be separated on accuracy; the direction on precision and hallucination is against the tune, and it is the same direction as the 1.7B run.

### B's synthetic test split (100 rows, `data/synthetic_gold/v1/test.jsonl`), in-distribution, not a headline
Field-level comparison with the row's target patch (`tools/finetune/score_test.py`; 71 of the 100 rows have worker words with 3-word-shingle Jaccard < 0.6 against every training row). Same `extract_v8.txt` prompt for every arm.

| model | all 100 rows: recall | precision | halluc. | rows all fields right | Jaccard < 0.6 (71 rows): recall / precision |
|---|---|---|---|---|---|
| 1.7B base | 181/486 (37%) | 40% | 225/314 | 1 | 37% / 54% |
| 1.7B tuned | 476/486 (98%) | 97% | 9/314 | 85 | 98% / 97% |
| 4B base | 382/486 (79%) | 62% | 210/314 | 9 | 76% / 74% |
| **4B tuned** | **478/486 (98%)** | **97%** | **5/314** | **88** | **98% / 97%** |

Reading: the tune learns B's labelling convention almost perfectly (sparse patches that omit unchanged fields, the dataset's phrasing of locations and actions), which the prompted base does not follow: most of the base's "hallucinations" here are fields it re-emits although they are already saved in the report, which the app's `ReportMerge` ignores. So this table measures how well a model reproduces the synthetic convention. It says little about real reports: on E's gold (dev 27), where mandatory fields are sometimes missing, the same tuned model invents severities and equipment. Both numbers are true; the dev 27 is the one that matters for the app.

Artefacts (not committed): raw outputs for both benchmarks and the replayed predictions are in the working copy's scratch folder; the merged 4B checkpoint (7.6 GB) is on the rented GPU box at `~/ft/run4b/merged` and not exported.
