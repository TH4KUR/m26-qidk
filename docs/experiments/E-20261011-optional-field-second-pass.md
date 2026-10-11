# E-20261011: a second-pass helper for the optional fields (equipment first)

**Owner:** E. **Date:** 2026-10-11, 06:40 to 07:45 IST. **Status:** laptop CPU experiment; not run on the kit.
**Run:** `tools/laptop_eval/runs/20261011-0738-optional-pass-qwen3-1.7b-q4_0-grammar/`. **Code:** `tools/laptop_eval/optional_pass.py` (tests: `test_optional_pass.py`).

## Why
The mandatory fields are extracted well. The optional ones are not, and equipment worst of all:

| Equipment, prompted 4B on the kit (NPU, w4a16) | v8 | v11 (shipped) | source |
|---|---|---|---|
| dev 27 | 3/19 | 3/19 | A/022, A/029 |
| held-out 13 (run once) | 0/9 | **0/9** (never emitted) | A/027 |

What did **not** explain it:
- **Position in the sentence** (E/013): equipment is said earliest (mean position 0.14) and missed most; severity is said last (0.79) and always found.
- **Permission to guess** (A/028): a "may guess" prompt produced zero new optional values on the kit.
- **Training data** (C/016): the fine-tuned 4B finds equipment 14/19 but invents values (hallucination 12%, fails the gate).

Other models show the information is there: equipment 10/19 with the 8B, 6/19 with the 1.7B GGUF, 8/19 with the 4B in bf16 on a GPU (A/029, C/016).

## The idea
Keep the 4B pass exactly as it is. In parallel, ask a small model **one narrow question per report**: only equipment, actions and named people. While the report's field is empty, show its answer as the existing **"Suggested / Use this"** popup. It never fills a field on its own.

## Setup
- **Model and runtime:** Qwen3-1.7B Q4_0 GGUF, llama.cpp b11541 server on the laptop CPU (10 threads); JSON-schema constrained decoding; temperature 0; one run.
- **Input:** one call per report over the whole transcript. The prompt is in `optional_pass.py`; its examples use objects that occur in no E item.
- **Filters** (all general, written after reading the first dev run):
  - each value must be grounded (its content words appear in the transcript);
  - equipment must not contain a person word and must not consist only of incident words ("water leak", "wet floor"); "water pipe" passes;
  - names must start with a capital and must not be a role or pronoun.
- **Scoring:** every value is scored **as if the worker accepted it**.
  - `exact` is the team scorer's headline rule (`score.outcome`, unchanged).
  - `contains` and `partial` come from `score.free_text_match`, the new **lenient free-text view** in `score.py`, reported next to the headline and never instead of it. A value counts as `contains` when it holds every content word of the gold (or an alt), with light stemming, spaces ignored ("icepack" = "ice pack"), and at most 4 extra content words, so a whole-sentence dump earns nothing. `partial` means a strict subset of the gold. `score()` now also returns `lenient_free_text` for equipment and actions; tests in `test_score.py`.
- **Sets:**
  - E dev 27 (held-out never read);
  - E homophones 8;
  - **B's synthetic validation+test initial reports, 134 rows from 56 scenario families** (`b/speech-engines` `51c1124`, written independently of this prompt);
  - **9 typed own-words phone transcripts** (dev clips; human transcription of round-1 audio, references not yet reviewed by a second person).

## Results (equipment)
| set | gold has equipment | found, exact + contains | wrong | gold empty | false alarm |
|---|---|---|---|---|---|
| E dev 27 | 19 | **17** (15 + 2) | 2 | 8 | 2 |
| E homophones 8 | 5 | **5** | 0 | 3 | 1 |
| **B synthetic 134** | 115 | **106** (93 + 13) = 92% | 9 | 19 | **19** |
| **E phone transcripts 9** | 5 | **5** (4 + 1) | 0 | 4 | 2 |

**Offered as popups:** 106 of 134 equipment suggestions on B are right (79%); 17 of 21 on dev; 5 of 7 on the phone transcripts. That is about one wrong suggestion per five reports.

## Compared with what we already have (equipment, dev 27, per item)
D's rule chips (`MissedFieldRules`, merged on master `c6eb46b`; per-item table in `D-20261011-missed-field-chips.md`, run 2) under the same setup (model emitted no equipment):

| | right | wrong | missed | false alarm (of 8) |
|---|---|---|---|---|
| prompted 4B v11, kit | 3 | n/a | 16 | 0 |
| D's rule chips | 11 | 3 | 5 | 1 |
| 1.7B helper | **17** | 2 | 0 | 2 (both gold gaps) |
| **either one right** | **19 of 19** | | | |

- **The helper is right on 8 items where D's chips missed or were wrong:** UPS, pan, fire alarm, power lines and projector (no chip), plus main network switch, 3D printer and water pipe (D named a part: "rack", "printer", "pipe").
- **D is right on the 2 items where the helper was wrong:** "parked car" (helper: "tree branch") and "bikes" (helper: "glass").
- Both were written after reading dev, so the dev comparison is fair between them but optimistic for both. D's chips were not run on B or the phone set.

**Full-pipeline runs re-scored with the lenient view** (our saved 1.7B laptop runs, dev): equipment unchanged (6/14 grammar, 4/14 free), actions +1 in each run. The scorer artefact matters most for the helper's longer values (dev +2, B +13 equipment).

**Other fields:**
- **Witnesses on B:** 74 of 94 found, 0 false alarms.
- **Actions:** found 105 of 107 on B, but in 21 of 27 reports **without** an action it writes a description ("caught fire", "stopped working", "I rate this as serious").
- **Persons:** confuses involved with witness ("Yusuf saw the incident" put under persons).
- **Speed:** median 1.6 to 2.2 s per report on the laptop CPU.

## Hand check of every equipment error
| set | error | what it was |
|---|---|---|
| B | wrong (9, 3 families) | the substance instead of the asset ("chlorine gas" for gold "dosing line"; "plastic waste" for "storm drain"); a place ("Seminar Hall 2" for "sound system") |
| B | false alarm (19, about 7 families) | a place (5: "Seminar Hall 3", "gym office"), a person's name (4: "Vikram"), a substance (4: "garbage"), nonsense (4: "scheduled class"), a body part (2: "wrist") |
| dev | false alarm (2) | "bottle of acid", "counter": **gaps in our gold** (see below), not model errors |
| dev | wrong (2) | "tree branch" for "parked car" (both objects are in the sentence), "glass" for "bikes" |
| phone | false alarm (2) | a place ("back gate"), a lowercase-typed name ("kavya") |
| phone | (fixed by the lenient view) | "icepack" vs "ice pack" in actions now counts as contained |

**Reading:** the model finds the equipment, but it **never answers "no equipment"**. Its errors fall into the same few kinds on three independent sets: places, people's names, substances, body parts. A "not the place, not a named person" filter is therefore justified by B and the phone set, not only by dev. It is **not applied in this run**, so these numbers do not include it.

## Confounds found in the data
1. **Our gold has labelling gaps in the optional fields.** 2 of the 8 dev items with "no equipment" arguably have one ("a bottle of acid spilled", "hit her head on the counter"), while g31 labels goods ("bulk bags") as equipment. With 8 negatives, false-alarm rates on our gold are not meaningful. Debatable labels: g19 "fire alarm" as equipment; "Everyone came out" not labelled as an action.
2. **B's data leans the other way:** 86% of its initial reports name equipment, and its 19 "no equipment" rows come from about 7 families written in four styles. That is few true negatives, and it favours a model that always fills.
3. **The 80% word-overlap rule under-counts correct free text.** On B, 13 correct equipment values ("stack of heavy bulk bags" for "bulk bags") score as wrong under the headline rule (11% of the found values); on dev 2; on the phone set "icepack" vs "ice pack". Fixed as a second, labelled view (`lenient_free_text` in `score.py`); the headline rule is unchanged because the held-out numbers were already reported with it (A/027) and the team rule forbids changing the scorer to raise a score.
4. **Card wording leaks into own-words clips:** speakers often said "the equipment involved was ...", because the card said "Equipment:". That makes equipment easier than in a natural report.
5. **Typed names are lowercase,** so the capital-letter name filter drops them; Whisper output usually capitalises names. Unverified on real recogniser output.

## Verdict
**Useful for equipment as a suggestion source, not as an automatic fill.** It finds equipment where the shipped 4B finds almost none (17/19 vs 3/19 on dev, 92% on 115 independent reports, 5/5 on real typed speech). It over-offers when there is none, so a wrong popup appears in about one report in five. Witnesses look usable too (0 false alarms on B). Actions and persons: not worth it.

## Caveats
- **Runs on the CPU on the kit** (GenieX `llama_cpp` runtime, GGUF Q4_0). The project rule is that every model runs on Snapdragon acceleration (A/030), so the NPU alternative is the same focused prompt sent to the shipped 4B once, at the review screen. That variant is untested; the 4B emitted equipment 0/9 on held-out with the full prompt, so it may not transfer.
- Laptop CPU, not the kit. CPU/RAM contention with the 4B and the recognisers on the kit is untested.
- Typed text, not recogniser output.
- One run.
- The prompt and filters were written after reading the first dev run, so the dev numbers are dev-informed. B and the phone transcripts are the fresher checks, but B is synthetic and templated, and the phone set is 9 items.
- Not a held-out result: the held-out set is spent (A/027).

## Reproduce
```bash
~/megathon-llm/bin/llama-b11541/llama-server -m ~/megathon-llm/models/Qwen3-1.7B-Q4_0.gguf --port 8080 --ctx-size 4096 --threads 10 --jinja
git show origin/b/speech-engines:data/synthetic_gold/v1/gold.jsonl > /tmp/b_gold.jsonl
cd tools/laptop_eval && python3 optional_pass.py --b-gold /tmp/b_gold.jsonl --transcripts <typed transcript sheet>
```
