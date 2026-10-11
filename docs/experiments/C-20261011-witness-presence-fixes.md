# C-20261011: witnesses who were present but did not "see" it (fixes A, B, C; D pending)

- **Date/time:** 2026-10-11, about 07:00 to 07:50 IST (laptop clock).
- **Who:** C, on the user's report and request ("do all the A, B, C, D fixes").
- **Report:** "my friend Rahul was with me when the incident happened" leaves `witnesses` empty, while "witness is Rahul and me" works; the mandatory fields work with vague sentences.

## Diagnosis (probe of the real pieces, not a guess)
Greedy decoding, app prompt format, `Qwen3-4B-Instruct-2507` bf16 on a GPU, then the real `PayloadValidator`.

| Said | Model output (base, prompts v8 and v11) | After the validator |
|---|---|---|
| "My friend Rahul was with me when the incident happened." (alone) | `witnesses: [Rahul]` | kept, REPORTED |
| "Witness is Rahul and me." | `witnesses: [Rahul]` ("me" is dropped on purpose) | kept |
| "Rahul was there." / "...standing next to me..." | `witnesses: [Rahul]` | kept |
| "Water leak near Conference Room 2 around 10 AM. My friend Rahul was with me when it happened." | **`persons_involved: [Rahul]`**, no witnesses | witnesses empty, Rahul filed as involved |
| same, fine-tuned v1 with prompt v8 | `{}` | empty |
| "Rahul and I were together when the pipe burst." | `persons_involved` | involved |

So the validator is not the cause (it keeps `witnesses: [Rahul]`, `NOT_A_NAME` only drops "me"); the model files a present-but-not-seeing person as involved when the sentence sits inside a full report, because the prompt defines witnesses as people "who saw it". Why the mandatory fields feel robust: each has a recovery layer (cue-word grounding of the type, `findTimePhrase`, promotion of the evidence quote). Optional fields have none, and the suggestion rules only recognised *saw / noticed / spotted / watched / witnessed*.

## Changes (all on `c/core-rules` `81d6f8d`; the extraction path on master stays frozen, A/027)
| | Change | Test |
|---|---|---|
| A | `suggest/RoleRules.kt`: presence cues (was with me/us, was there, present, nearby, around, next to / beside me, standing, in the room/area/lab/office, at the spot/scene, and "mere saath tha", "wahan tha") make a **listed** person a witness chip (moving them out of involved) and an **unlisted capitalised name** with a presence cue a witnesses chip when the field is MISSING. Excluded: hurt/slipped/etc. in the clause, negation, roles and common words, the speaker, names inside the location. One chip per field (D's convention). | `PresenceWitnessChipsTest` (11) |
| B | `prompts/extract_v12.txt` = v11 plus one rule sentence and one worked example. **Not the default.** | `PromptV12Test` (3): only that line and two appended lines differ |
| C | `QuestionPlanner`: when all four mandatory fields are answered, a person is listed, witnesses are not answered, the lane is below HIGH and it was not asked before: "Did anyone see it happen?" with a "No one" quick reply, recorded as an explicit none (not the text "none"). | `QuestionPlannerTest` (+4), `ReportSessionTest` (+2) |
| D | `tools/finetune/build_sft_v2.py` builds partial-utterance rows (a sentence that carries severity, equipment, witnesses, actions or time is dropped from the words and the field from the label) and presence rows ("X saw it" becomes "My friend X was with me when it happened"); 1,175 train rows (599 original + 576 added), 196 validation rows; 4B base, prompt v12, 2 epochs, lr 1e-4, epoch 1 kept by validation loss. | scored below |

## Measured (4B base, bf16, E's dev 27 on the committed gold, E's scorer, outputs replayed through the real validator and merge; one run)
| prompt | recall | precision | hallucination | mandatory | witnesses |
|---|---|---|---|---|---|
| v11 (shipped) | 114/135 (84%) | 114/121 (94%) | 3/81 | 80/86 | 2/2 |
| v12 | 114/135 (84%) | 114/118 (97%) | 2/81 | 81/86 | 2/2 |

The dev 27 has only **2 witness values**, so it cannot show the effect; v12 is "no worse here". The effect is shown by the phrasing probe (24 sentences x 2 prompts, same model):

| Said | v11 | v12 |
|---|---|---|
| full report + "My friend Rahul was with me when it happened." | involved | **witnesses: Rahul** |
| "Priya slipped on the wet floor and Rahul was with me when it happened." | unparseable | **involved Priya, witnesses Rahul** |
| "My colleague Priya was beside me when the shelf fell." | involved | **witnesses** |
| "Rahul was in the room when the fire started, he called the guard." | involved and witness | **witness only** |
| "Rahul and I were together when the pipe burst, he got hurt." | involved | involved **and** witnesses (imperfect: the "unless hurt" rule is not always obeyed) |
| "The pump failed at 3 PM, nobody else was around." | `{}` | `witnesses: []` (dropped by the parser) |

## D: the v2 retrain (4B, prompt v12, same dev 27 and scorer, one run)

| arm | recall | precision | hallucination | mandatory | equipment | persons | actions | witnesses |
|---|---|---|---|---|---|---|---|---|
| base + v11 (shipped prompt) | 114/135 (84%) | 94% | 3/81 (3.7%) | 80/86 | 5/19 | 7/7 | 20/21 | 2/2 |
| base + v12 | 114/135 (84%) | 97% | 2/81 (2.5%) | 81/86 | 4/19 | 7/7 | 20/21 | 2/2 |
| **v2 tuned + v12** | **125/135 (93%)** | **125/132 (95%)** | **5/81 (6.2%)** | 81/86 | **15/19** | 6/7 | 21/21 | 2/2 |
| (v1 tuned + v8, earlier run) | 121/135 (90%) | 91% | 10/81 (12%) | 81/86 | 14/19 | 4/7 | 21/21 | 1/2 |

- **Against the v1 tune:** hallucination halves (10 to 5 of 81), precision recovers (91% to 95%), recall rises (90% to 93%); the
  invented values are 3 equipment, 1 action, 1 witness (v1 invented 4 severities, 2 equipment, 2 witnesses, 2 actions).
  Training on rows where the fields are absent worked as intended: no invented severity.
- **Against the prompted base:** +11 correct cells (all equipment, 4 to 15 of 19), at the price of +3 hallucinated fields.
- **Gate (A/017, fixed): recall >= 75% yes, precision >= 92% yes, hallucination <= 4% NO** (5 of 81 is 6.2%; 3 of 81 would pass;
  Wilson 95% [2.7%, 13.7%]). By the fixed rule it is not adopted and was not exported.
- **Witness probe (24 sentences, same harness):** every presence wording is a witness now, including the full report and "Priya
  slipped and Rahul was with me" (involved Priya, witnesses Rahul); "he got hurt" lists Rahul as both (the base v12 did the same).
  "nobody else was around" gives `witnesses: []` with an evidence entry (dropped by the parser).
- **Caveats:** the dev 27 chose the prompt (v8, v11, v12), so it is not untouched; the held-out 13 were never run with this model;
  one run, n = 27, bf16 GPU; the checkpoint was chosen by validation loss; the model must be used with prompt v12 (it was trained
  with it); the weights are on the rented box and not exported (A/030: no QAIRT toolchain, no time before the freeze).

## Limits
- bf16 on a GPU, one run, dev gold with 2 witnesses; the kit's quantised bundle may behave differently (A's kit gap is 65% against 84% to 88% here).
- The rules are English (capitalised names) plus a few Hinglish forms; Devanagari and Telugu are not covered. "was with me" can mean a companion who saw nothing: that is why it is a chip, never a silent fill.
- `DefaultPromptTest` (two cases) fails on a Windows checkout with CRLF line endings (byte-hash tests); not caused by these changes.
- Merging this to master changes the frozen extraction path and the held-out numbers would describe a different pipeline (A/027): the user decides.
