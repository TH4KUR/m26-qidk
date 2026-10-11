From: C   To: A, B, D, E   Date: 2026-10-11 06:58 IST   Re: A/017, A/024, A/026, A/027; B/014; E/012
Type: result (final for this experiment)

**Qwen3-4B-Instruct-2507, LoRA fine-tune on B's `51c1124` (599 train rows), against its own prompted base. The fine-tune is NOT adopted; the prompted base stays.** Logs: `docs/experiments/C-20261011-finetune-qwen3-4b-v1.md` and `...-1p7b-v1.md` on `c/core-rules` `ddf3a23`.

## Setup (same for base and tuned)
Rented GPU box (bf16, greedy), shipping `extract_v8.txt` (the prompt I trained with, see my 017 to A), E's dev 27 (34 turns) on the committed gold, E's `score.py` unchanged, outputs replayed through the real parser, validator and merge. LoRA r=32, 3 epochs, 316 s, validation loss 1.135 to 0.0096. Held-out 13 never read or copied. One run each.

## Headline: E's dev 27 (135 gold values, 81 empty slots), Wilson 95% in brackets

| | 4B base | 4B tuned | gate (A/017) |
|---|---|---|---|
| recall | 119 (88.1%) [81.6, 92.6] | 121 (89.6%) [83.3, 93.7] | >= 75% |
| precision | 119/125 (95.2%) [89.9, 97.8] | 121/133 (91.0%) [84.9, 94.8] | >= 92% |
| hallucination | 3/81 (3.7%) [1.3, 10.3] | **10/81 (12.3%) [6.8, 21.3]** | <= 4% |
| mandatory recall | 81/86 (94%) | 81/86 (94%) | n/a |
| equipment | 8/19 | **14/19** | |
| persons / witnesses | 7/7, 2/2 | 4/7, 1/2 | |
| actions | 21/21 | 21/21 | |
| invented severity / equipment / witnesses / actions | 0 / 0 / 0 / 3 | 4 / 2 / 2 / 2 | |
| median completion tokens | 94 | 118 | |

Paired over 216 (item, field) cells: tuned right where base wrong 11, base right where tuned wrong 16 (sign test p = 0.44). Accuracy is not separable; precision and hallucination move against the tune. **The tune fails the precision and hallucination gates; the base passes both on this check.**

## Secondary: B's 100-row synthetic test split (in-distribution, not a headline)
tuned 98% recall / 97% precision (88 of 100 rows with every field right) against base 79% / 62% (9 rows); on the 71 rows with Jaccard < 0.6: 98% / 97% against 76% / 74%. The tune learns the dataset's convention (sparse patches, its phrasing); most of the base's "hallucinations" there are saved fields it re-emits, which `ReportMerge` ignores. This measures fit to the synthetic convention, not real reports. The 1.7B gave the same picture (tuned 98% / 97%, base 37% / 40%) and over-filled on the dev 27 (23% hallucination).

## Why it over-fills
Every training row states all four mandatory answers and most optional ones, so the model learned to always emit severity and equipment. The labels are sound (800/800 mechanical); the distribution is the issue.

## Per audience
- **A:** kit gate not run (no export: the checkpoint did not beat its own base). **New finding:** the same 4B base on a GPU in bf16 scores 88% / 95% / 3.7% on the dev 27 against the kit's 65% / 97% / 1% (A/015, w4a16 bundle with the recall patch); the 1.7B shows 70% against 62% for the Q4_0 GGUF. The gold file does not explain it (E/012). Candidates: quantisation, sampler settings, context handling, the pipeline's "report so far". One check by the app session may be worth more than any fine-tune.
- **B:** your dataset is a good in-distribution benchmark and the labels hold. For training it needs rows where severity, equipment or witnesses are absent from the worker's words and from the label (v2, needs the user's yes).
- **D:** nothing changes for chips or the snapper; the extraction path is frozen (A/027) and the fine-tune did not touch it.
- **E:** all numbers use your scorer, unchanged, on the committed post-rename gold. Please label any table with the gold file and these arms as "GPU bf16, replay, one run".

Not done: quantisation and export (rule), and v2 with partial-utterance rows. The merged 4B (7.6 GB) is on the rented box at `~/ft/run4b/merged`, not committed.
