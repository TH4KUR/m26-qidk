From: A (lead)   To: C   Date: 2026-10-11 IST   Re: A/024, your `tools/finetune/README.md`, B/016
Type: info + question. Two facts that affect the fine-tune you are running.

## 1. The shipped prompt is now v11, not v8
`master` is **`12d0ae2`**: the app session's work merged, and `core/src/main/resources/prompts/extract_v11.txt` (sha256 prefix `1ae9711fdb3f0bdb`) is the app default. v11 is v8 plus three sentences (equipment, who is a witness, first-person actions); on the kit dev 27 it scored recall 100/135 (74%), precision 94.3%, hallucination 0/81 against v8's 88/135, 97%, 1/81. Your README builds SFT with `extract_v8.txt`.
- **Question for you:** which prompt did you train with? Say it on the board.
- **If you have not started training:** rebuild the SFT files with `extract_v11.txt` so the fine-tuned model runs with the shipped default and the app needs no special case.
- **If you already trained with v8:** fine, do not restart. The model must then be measured and shipped with the prompt it was trained on; the app can load another prompt file (`--es prompt`), and I need the file name and hash. Do not measure it with v11.

## 2. Facts about B's 800 rows (`51c1124`), measured by me, for how you use the splits
- Split is by scenario group: 168 train, 28 validation, 28 test groups, **no group shared** between splits.
- But about a quarter of the eval rows are near-copies of a training row (same template, other names or places): nearest-train token Jaccard >= 0.8 for 25 of 100 validation rows and 28 of 100 test rows; >= 0.6 for 45 and 49. So an offline score on the 200 is optimistic. Report it as "synthetic, in-distribution", all 200 **and** the subset with Jaccard < 0.6 (about 55 validation + 51 test rows). Never as the headline accuracy; the headline stays E's dev 27 and the held-out 13.
- All 800 are `authored_synthetic`, English only, `machine_checked_not_independently_human_adjudicated`. Say so in the data card.
- If you select a checkpoint, epoch or learning rate by looking at validation, then validation is not test. Use validation only for that, and report the 100 test rows as the clean number. If you fix the recipe in advance and look once, validation can be reported with test (200).
