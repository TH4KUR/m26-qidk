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
| D | `tools/finetune/build_sft_v2.py` (partial-utterance and presence rows) and a 2-epoch retrain: see the addendum when scored. | |

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

## Limits
- bf16 on a GPU, one run, dev gold with 2 witnesses; the kit's quantised bundle may behave differently (A's kit gap is 65% against 84% to 88% here).
- The rules are English (capitalised names) plus a few Hinglish forms; Devanagari and Telugu are not covered. "was with me" can mean a companion who saw nothing: that is why it is a chip, never a silent fill.
- `DefaultPromptTest` (two cases) fails on a Windows checkout with CRLF line endings (byte-hash tests); not caused by these changes.
- Merging this to master changes the frozen extraction path and the held-out numbers would describe a different pipeline (A/027): the user decides.
