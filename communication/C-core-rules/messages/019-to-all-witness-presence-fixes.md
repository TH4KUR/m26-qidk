From: C   To: A, B, D, E   Date: 2026-10-11 07:55 IST   Re: user report "my friend Rahul was with me" leaves witnesses empty; A/030; E/013
Type: done (on `c/core-rules`, not merged; extraction path stays frozen on master, A/027)

**Code:** `c/core-rules` `82eb6f0`. Log: `docs/experiments/C-20261011-witness-presence-fixes.md`. Saved outputs of every fine-tune arm: `tools/finetune/runs/` (re-scorable, A/030 item 3).

## Diagnosis (measured, not guessed)
4B base bf16, app prompt format, then the real validator. Alone, "My friend Rahul was with me when the incident happened." gives `witnesses: [Rahul]` and the validator keeps it. Inside a full report the model files Rahul under `persons_involved` (or emits nothing), because the prompt says witnesses "saw it". The mandatory fields survive vague sentences because each has a recovery layer (cue-word grounding, `findTimePhrase`, promoted evidence); the optional fields have none, and the suggestion rules only knew saw/noticed/spotted/watched.

## What changed
| | Change | Needs | Test |
|---|---|---|---|
| A | `suggest/RoleRules.kt`: presence cues ("was with me", "was there", "standing next to me", "mere saath tha"...) make a listed person a witness chip and an unlisted capitalised name a witnesses chip when the field is empty; hurt, negated, roles, places and the speaker excluded | D's file, review please | `PresenceWitnessChipsTest` (11) |
| B | `prompts/extract_v12.txt` = v11 + one rule sentence + one worked example. Not the default | the kit run is the app session's | `PromptV12Test` (3) |
| C | `QuestionPlanner`: one optional "Did anyone see it happen?" (+ "No one" = explicit none) when all mandatory fields are answered, a person is listed, lane below HIGH, once | `ReportSession.answer` (2 lines) | `QuestionPlannerTest` +4, `ReportSessionTest` +2 |
| D | `tools/finetune/build_sft_v2.py` + 4B retrain on partial-utterance and presence rows, 2 epochs | the user approved missing-mandatory rows | scored below |

## Results (4B, E's dev 27 on the committed gold, E's scorer, replay through the real validator, bf16, one run)
| arm | recall | precision | hallucination | equipment | witnesses |
|---|---|---|---|---|---|
| base + v11 (shipped) | 114/135 (84%) | 94% | 3/81 | 5/19 | 2/2 |
| base + v12 | 114/135 (84%) | 97% | 2/81 | 4/19 | 2/2 |
| **v2 tuned + v12** | **125/135 (93%)** | 95% | **5/81 (6.2%)** | **15/19** | 2/2 |
| v1 tuned + v8 (016) | 121/135 (90%) | 91% | 10/81 (12%) | 14/19 | 1/2 |

- **v2 against v1:** hallucination halves, precision recovers, no invented severity: the partial rows did what they were for.
- **v2 fails the fixed gate on hallucination** (5/81 = 6.2% against 4%; Wilson 95% [2.7, 13.7]); not exported (no QAIRT, A/030).
- The witness effect is shown by a 24-sentence probe, not by the dev 27 (only 2 witness values): with v12 every presence wording is a witness, including "Priya slipped and Rahul was with me" (involved Priya, witnesses Rahul).
- **Caveats:** the dev 27 chose the prompt (not untouched); held-out 13 never run with any of this; bf16 GPU, one run; the suite on this Windows checkout: 1086 tests, 2 failing (`DefaultPromptTest` byte hashes, CRLF line endings, not these changes), 5 skipped.

## Per person
- **A:** message 020. **B:** 021. **D:** 022. **E:** 023.
