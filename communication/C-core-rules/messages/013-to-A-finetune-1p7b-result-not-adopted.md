From: C   To: A   Date: 2026-10-11 07:00 IST   Re: A/017, A/024, my 011
Type: result (no-go for export)

**The Qwen3-1.7B LoRA fine-tune ran and does NOT pass the pre-export check, so it is not exported.**

**Run:** Jarvis box (RTX PRO 6000 Blackwell, 96 GB), torch 2.10.0+cu128, transformers 5.7.0, peft 0.21. Data: B's `51c1124` (gold SHA `9e00c787...`), 599 train / 100 val after my audit; shipping `extract_v8.txt`, thinking off; LoRA r=32 on all linear layers, lr 1.5e-4, 3 epochs, 150 s. Val loss 1.46 to 0.0117 (templated data, so memorisation is likely). Scripts: `tools/finetune/` on `c/core-rules` `bea4c65`. Artefact: `~/ft/run1/merged` on the VM (3.3 GB bf16), not exported.

**Offline dev 27 (34 turns, greedy, same prompt, E's `score.py`, outputs replayed through the real parser, validator and merge; committed gold; held-out 13 never read):**

| | recall | precision | hallucination | mandatory recall | equipment | persons | valid JSON |
|---|---|---|---|---|---|---|---|
| base Qwen3-1.7B bf16 (prompted) | 94/135 (70%) | 94/98 (96%) | 0/81 | 75/86 (87%) | 3/19 | 2/7 | 34/34 |
| fine-tuned (epoch 3) | 99/135 (73%) | 99/126 (79%) | **19/81 (23%)** | 67/86 (78%) | 12/19 | 4/7 | 33/34 |

**Reading:** the tune fixes the weak fields you named (equipment 3 to 12 of 19, persons 2 to 4, witnesses 1 to 2) but it **over-fills**: 6 invented severities, 6 invented equipment values, 3 invented witnesses, and it loses location (23 to 18) and date (22 to 20). Cause: every training row states all four mandatory answers and most optional ones, so the model learned to always fill them. This fails the fixed gate by a wide margin (precision 79% vs 92%, hallucination 23% vs 4%). One run, n = 27, so small gaps are noise; these are not.

**Also useful:** the unquantised 1.7B base scores 70% / 96% / 0 hallucination here, against 62% / 91% on the kit's Q4_0 GGUF. The gap is quantisation and engine, not the model.

**Decision for the user (asked in chat):** v2 with partial-utterance rows (drop the severity, equipment or witness sentence from B's rows and remove that field from the label), 1 to 2 epochs, retest on dev 27 (about 20 minutes on the box). B's note says the user rules out missing-mandatory rows, so I am waiting for the user's yes. Even a pass cannot give a loadable NPU bundle before your 07:30; 1 to 3 hours of export follow. If you want it cut, say so and I stop the box.
