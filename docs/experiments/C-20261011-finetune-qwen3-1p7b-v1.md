# C-20261011: LoRA fine-tune of Qwen3-1.7B on B's synthetic gold (v1): failed the pre-export check

- **Date/time:** 2026-10-11, about 05:40 to 06:30 IST (laptop clock).
- **Who:** C, for the user's go; plan from A (`communication/A-lead-kit-app/messages/017`, `024`), data from B (`b/speech-engines` `51c1124`).
- **Goal:** teach the 1.7B the incident-extraction task to raise recall on the weak fields (equipment, actions, persons), then export to the NPU only if it beats its own base.
- **Result in one line:** recall 70% to 73%, but precision 96% to 79% and hallucination 0/81 to 19/81; not exported.

## Setup
- **Machine:** rented GPU box (RTX PRO 6000 Blackwell, 96 GB, Ubuntu 24.04), torch 2.10.0+cu128, transformers 5.7.0, peft 0.21.2 (versions pinned by the Qualcomm Qwen3-1.7B recipe).
- **Data:** B's `data/synthetic_gold/v1`, gold SHA-256 `9e00c787e1c6da572f4f95d28c96fa7091db4a60fbcf9dca366547a3c543f389`. Every label went through the real `ToolCallParser` and `PayloadValidator` (1 of 800 dropped); bare leading "in "/"at " removed from 481 location values. **599 train, 100 validation**, 100 test not used. System prompt: shipping `extract_v8.txt`, chat template with thinking off. No overlap of 3 or more 6-grams with E's dev 27; E's held-out 13 never read or copied (only the dev rows were uploaded).
- **Training:** LoRA r=32, alpha 64, dropout 0.05, all linear layers, lr 1.5e-4 cosine, 3 epochs, effective batch 16, bf16, max length 2048, seed 42, loss on the answer tokens only. 114 steps, 150 s. Validation loss 1.46 before, 0.0191 / 0.0121 / 0.0117 after epochs 1 to 3 (templated data: memorisation is likely).
- **Evaluation:** E's dev 27 (34 turns) on the post-rename committed gold, greedy decoding, same prompt, `report so far` rebuilt from the model's own earlier answers. Raw outputs replayed through the real parser, validator and merge (`ReplayScoringHarness`) and scored with E's `score.py`, unchanged. One run each.

## Results (correct of 135 gold values; hallucination = filled where the gold is empty, of 81)

| | recall | precision | wrong | hallucination | mandatory recall | equipment | persons | witnesses | valid JSON |
|---|---|---|---|---|---|---|---|---|---|
| base Qwen3-1.7B bf16 | 94/135 (70%) | 94/98 (96%) | 4 | 0/81 | 75/86 (87%) | 3/19 | 2/7 | 1/2 | 34/34 |
| fine-tuned, epoch 3 | 99/135 (73%) | 99/126 (79%) | 8 | **19/81 (23%)** | 67/86 (78%) | 12/19 | 4/7 | 2/2 | 33/34 |

Fixed gate (A/017): recall >= 75%, precision >= 92%, hallucination <= 4%, valid-after-retry >= 95%, median turn <= 6 s. **The fine-tune fails precision and hallucination by a wide margin**, and loses mandatory recall (location 23 to 18, date 22 to 20). Median completion 123 tokens against 67 for the base (1.2 s against 0.6 s on the GPU).

## What went wrong
Invented values: 6 severities that nobody stated, 6 equipment values, 3 witnesses, 3 actions, 1 date. Cause, by inspection of the wrong cells and the data: every training row answers all four mandatory questions and most optional ones, so the model learned to always fill them. This was the risk named at review time (B/014 reply, "limitations").

## Not done, and why
- **No quantisation or export:** the checkpoint did not beat its own base (A/017 rule), so the 1 to 3 hour AI Hub step was not started.
- **No v2:** it needs partial-utterance rows (drop the severity, equipment or witness sentence and the label field), which B's note says the user ruled out; waiting for the user.

## Caveats
One run, n = 27, dev tuned by others; the gaps above are far larger than that noise. The base number is unquantised bf16 on a GPU; the kit's Q4_0 GGUF of the same model scored 62% / 91% in A's run, so the base is not what runs on the kit. Epoch 1 and 2 adapters were saved but not evaluated.
