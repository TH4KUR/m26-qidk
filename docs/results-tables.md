# Results tables for the final report and slides (E, 2026-10-11 09:00 IST)

Every number below comes from a logged run; the **source** column names the log or board message (`A/027` = `communication/A-lead-kit-app/messages/027-*`). Copy a row together with its label. Do not mix rows from different builds or gold files in one comparison.

**Labels to keep with every number**
- **Kit:** Qwen3-4B-Instruct-2507, w4a16 QAIRT bundle on the Hexagon NPU (GenieX 0.3.16), Whisper-Small on the NPU (sherpa-onnx QNN). One run per arm.
- **Gold:** `app/src/main/assets/eval/gold_v1.jsonl`, sha256 prefix `e1f1c009fd00a3d4` (fictional names): 27 dev items (135 gold values, 81 empty slots) and 13 held-out items (65 values, 39 empty slots). Rows marked *pre-rename* used the earlier gold copy (only location labels differ, A/018, E/012).
- **Dev 27 chose the prompt** (v8, v11). Dev numbers are development numbers, not an untouched test. **Held-out 13 is the only unbiased check**, run once per prompt and now spent.
- **Scorer:** `tools/laptop_eval/score.py` (Kotlin `EvalScorer` gives the same 216/216 slot outcomes on the kit run, A/033). Recall = correct / gold values; precision = correct / filled; hallucination = filled where gold is empty / empty slots. Only REPORTED, USER_CONFIRMED and USER_EDITED count as filled; a suggestion chip counts only when accepted.
- **Scorer caveats:**
  - the text rule (Dice ≥ 0.8) can pass a 5–6-word phrase that differs in one token: one dev slot (g08 location, passes at exactly 0.80) moves v11's 101 to 100 (A/033);
  - number words above twenty are not normalised;
  - free text is also reported with the labelled lenient view (`lenient_free_text`), never instead of the headline.

## 1. Headline: the shipped pipeline on unseen data (kit, held-out 13)
| | v11 (shipped) | v8 (reference) | source |
|---|---|---|---|
| overall recall (Wilson 95%) | **43/65 = 66%** (54–76) | 42/65 = 65% (52–75) | A/027 |
| overall precision | **43/47 = 91%** | 42/46 = 91% | A/027 |
| hallucination | **2/39 = 5%** | 3/39 = 8% | A/027 |
| mandatory-field recall | 32/41 = 78% | 35/41 = 85% | A/027 |
| equipment | 0/9 | 0/9 | A/027 |
| immediate actions | 7/8 | 3/8 | A/027 |

Build `424c092`. The two prompts cannot be separated at n = 13. Dev gave v11 74% recall, so dev was optimistic.

## 2. Development: what each change did (kit, dev 27)
| run | build | recall | precision | halluc. | valid 1st / after retry | median turn | source |
|---|---|---|---|---|---|---|---|
| v8, before recall patch *(pre-rename)* | dev10 | 64/135 (47%) | 88% | 2/81 | 27/34 / 30/34 | 5.0 s | A/015 |
| v8 + recall patch *(pre-rename)* | dev11 | 88/135 (65%) | 96% | 1/81 | 31/34 / 32/34 | 4.8 s | A/015 |
| v8 (bake-off control) | `60270bd` | 88/135 (65%) | 97% | 1/81 | 30/34 / 32/34 | 4.2 s | A/022 |
| place_context (B) | `60270bd` | 97/135 (72%) | 91% | 5/81 | 33/34 / 34/34 | 5.4 s | A/022 |
| compact_context (B) | `60270bd` | 100/135 (74%) | 90% | 5/81 | 33/34 / 33/34 | 6.5 s | A/022 |
| **v11 (shipped)** | bo-v11 | **100/135 (74%)** | **94.3%** | **0/81** | – / 97% | 4.9 s | commit `12d0ae2` |
| v11 control | `424c092` | 101/135 (75%) | 94% | 0/81 | 33/34 / 33/34 | 4.6 s | A/028 |
| "may guess" optional fields (E) | `424c092` | 97/135 (72%) | 93% | 2/81 | 34/34 / 34/34 | 5.3 s | A/028 |
| v12a "tools/items" wording | session 2 | 94/135 | – | – | – | – | A/033 |
| v12b wording + gloss | session 2 | 98/135 | – | – | – | – | A/033 |

**Gates fixed before each run:**
- B's two candidates failed hallucination (and compact_context also failed speed).
- "may guess" failed recall (−3); it produced zero new optional-field suggestions.
- The wording test was not supported (equipment 3 → 2/19).
- Recall patch per field (A/015): severity 5/9 → 8/9, location 73% → 85%, date/time 12/24 → 22/24, actions 14% → 38%.

## 3. Model choice (dev 27, v8 + recall patch unless stated)
| model / runtime | recall | precision | halluc. | equipment | actions | median turn | fits the gate? | source |
|---|---|---|---|---|---|---|---|---|
| **Qwen3-4B w4a16, NPU (shipped)** | 88/135 (65%) | 96% | 1% | 1/19 | 8/21 | 4.8 s | yes | A/017, model comparison log |
| Qwen3-8B w4a16, NPU | 112/135 (83%) | 90% | 9% | 10/19 | 20/21 | 7.9 s | no (speed, halluc.) | A/017 |
| Qwen3-1.7B Q4_0 GGUF, llama.cpp | 62–64% | 91% | – | 6/19 | – | 2.4 s | not NPU-proven | A/029, A/030 |
| 4B bf16 base, GPU replay *(lab only)* | 119/135 (88%) | 95% | 3/81 | 8/19 | 21/21 | – | cannot run on the kit | C/016 |
| 4B LoRA fine-tune, GPU replay | 121/135 (90%) | 91% | **10/81** | 14/19 | 21/21 | – | no (halluc.), not exported | C/016 |
| 1.7B bf16 base, GPU replay | 94/135 (70%) | 96% | 0/81 | 3/19 | – | – | lab only | C/013 |
| 1.7B LoRA fine-tune, GPU replay | 99/135 (73%) | 79% | **19/81** | 12/19 | – | – | no | C/013 |

**Finding:** the same 4B scores 88% unquantised on a GPU and 65–75% on the kit. The bisection (quantisation vs sampler vs context) is open (A/030). The fine-tunes over-fill because every training row stated every field.

## 4. The weak field: equipment, and what recovers it (dev 27, 19 gold values, 8 or 11 empty)
| approach | right | wrong | missed | false alarms | runs on | source |
|---|---|---|---|---|---|---|
| 4B v11 extraction (shipped) | 3 | – | 16 | 0 | NPU | A/029 |
| D's rule chips (`MissedFieldRules`, merged) | 11 | 3 | 5 | 1 of 8 | CPU rules | D/005 |
| E's focused 1.7B pass (laptop, every value as if accepted) | **17** (15 + 2 lenient) | 2 | 0 | 2 of 8 | laptop CPU | E log `E-20261011-optional-field-second-pass` |
| **A's FocusedProbe: same 4B, one narrow question after the turn** | **16** (3 first pass + 13 chips) | 1 | 2 | 3 of 11 | **NPU, 1.1 s median after the turn** | A/034 |
| D's chips or E's pass | 19 of 19 | | | | | E log |

- **Independent sets for the focused pass (E, laptop):** B's 134 synthetic reports 106/115 found (92%), 19 of 19 false alarms; 9 typed phone transcripts 5/5, 2 of 4 false alarms.
- **Error audit (E log):** 2 of the dev false alarms are gaps in our gold ("bottle of acid", "counter"); the rest are a place, a name, a substance, a body part.
- **Probe actions (A/034):** 6 right chips of 8 missed, 1 wrong.
- **Gates:**
  - **probe:** passes E/014's pre-registered gate; fails A's stricter gate (3 false alarms > 2, 2 wrong > 1), so A's verdict is "off by default", pending the user.
  - **D's action chips:** 13 right, 0 wrong, 8 missed, 0 false alarms (D/005).
- **Decision (user, 10:00, A/040):** keep the 4B probe (item, action, people), ON by default. The 1.7B is **not shipped**: report it as "laptop result (17/19 dev equipment, 106/115 on B's 134) and kit measurement of the plain 1.7B Q4_0 (62–64% recall, 2.4 s median, CPU via llama.cpp, NPU use unproven)".
- **The probe is ON by default on master** (`b0d42af`: `Pipeline.newSession(useProbe = true)`), run after each turn.
- Chips do not raise the headline recall unless accepted.

## 5. Place names (gazetteer snapper, D; dev text)
| run | n | right chip | right no-chip | wrong | missed | false alarm | source |
|---|---|---|---|---|---|---|---|
| fictional gold, shipped list | 55 | 5 | 21 | **0** | 0 | **0** | D/004 |
| synthetic spoken digits | 10 | 8 | 1 | 0 | 0 | 0 | D/004 |
| E's mis-heard sentences | 9 | 4 | 0 | 0 | 4 (3 equipment by design) | 0 | D/004 |
| before vs after span snapping (real list) | 55 | | | 0 | 5 → **1** | 0 | D/004 |

The LLM copies sound-alikes rather than repairing them: place recall 7/8 on clean text, 2/8 on mis-heard text (E baseline). The snapper is what repairs them.

## 6. Speech recognition
| measurement | value | source |
|---|---|---|
| Whisper-Small on the kit NPU (sherpa-onnx QNN), one clip | decode 3.18 s, real-time factor 0.48; recognizer creation 0.71 s | `A-20261010-sherpa-onnx-qnn-speech-on-kit` |
| App child process, one English sample, end to end | 5,078 ms, same transcript as the CLI | B/009 |
| Indian-accent public proxy (80 clips, 19 speakers, 829 words; GPU, fp32) | base.en **12.30%** WER (8.6–16.5), fine-tuned Small 13.51%, tiny.en 16.65%, fine-tuned Tiny 24.73% | `B-20261011-accent-contest-public-proxy` |
| **Our phone recordings**, read-aloud dev clips (10 clips, 298 words, 2 speakers, phone mic, 16 kHz; B's laptop, not the kit) | **base.en 8.72%** (26/298), fine-tuned Small 10.74%, tiny.en 11.41%, fine-tuned Tiny 14.09% | B/023, `B-20261011-phone-clips-contest` (`b/speech-engines`) |

Both are small samples: the phone ordering rests on 16 errors between the best and the worst model; the Indian-English fine-tunes did not beat stock base.en on either set. Neither is the kit's Whisper-Small QNN; a kit run of the 26 dev phone clips is the missing end-to-end number. The proxy is read speech from a public set, not incident speech on the kit microphone. Hindi/Telugu speech numbers: none yet (A/021 not started). Do not quote hi/te WER from `wer.py` until its Indic fix lands (A/033).

## 7. Speed on the kit
| stage | value | source |
|---|---|---|
| LLM call | median 4.2–5.3 s; first token about 0.47 s for about 810 prompt tokens; decode 18–22 tok/s; about 80 tokens per call | A/030 |
| Warm turn breakdown (earlier build) | Whisper 2.5–3.4 s, SenseVoice 0.7–0.9 s, LLM 2–3 s | A/012 |
| Model load | 13.4 s, now preloaded at launch | A/030, commit `8caaa5a` |
| Equipment/actions probe | median 1.09 s, p90 1.7 s, after the turn | A/034 |

## 8. Urgency advisor (C, typed scenarios, 1–20 score in four bands)
| set | n | band accuracy | under-triage | source |
|---|---|---|---|---|
| dev (tuned on it) | 53 | 0.96 | 0.00 | `C-20261010-urgency-scoring` |
| held-out 2 (unseen) | 20 | **0.55** | **0.25** | same |
| held-out 3 (unseen) | 20 | 0.45 | 0.25 | C/008 |

The pre-registered bound was missed. The advisor can only raise priority, and the reporter can override it. Floors from a stated severity or hazard type were added after these runs (C/008), with no new measurement.

## 9. Laptop baseline used to pick the setup (Qwen3-1.7B Q4_0, laptop CPU, gold text, raw LLM config)
| run | valid calls | recall | mandatory recall | halluc. | source |
|---|---|---|---|---|---|
| dev 20, free decoding | 6/20 | 28/98 | 19/61 | 3/62 | `E-20261011-fictional-gold-baseline` |
| dev 20, JSON grammar | 20/20 | 76/98 | 56/61 | 17/62 | same |
| homophones clean / mis-heard | 7/8, 7/8 | 17/30, 10/30 | 15/23, 9/23 | 0/34, 1/34 | same |
| C's recall patch replayed on these outputs (free / grammar) | | 23 → 56, 56 → 67 of 98 | | | C/005 |

## 10. Evaluation integrity (one line each, for the report's method paragraph)
- Gold rebuilt byte-identically from `build_gold.py`; Python and Kotlin scorers agree 216/216 on the kit run (A/033).
- Held-out run once per prompt, then frozen; the extraction path is frozen since (A/027).
- No text-level train/test leakage between B's synthetic set and our dev or held-out (A/030; C/012).
- Fictional names in every committed file; blind-check clean.
- Our gold has known gaps in optional labels (2 of 8 "no equipment" dev items arguably have equipment; E log).
