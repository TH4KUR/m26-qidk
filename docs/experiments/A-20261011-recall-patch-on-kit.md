# Recall patch on the kit: before/after (A, 2026-10-11)

Measured by session A on the QIDK HDK8750 kit. Everything below was produced by the app's real path (Whisper is bypassed: the gold text goes in as the transcript) with **Qwen3-4B-Instruct-2507 on the NPU (GenieX 0.3.16, QAIRT)**, sampler temperature 0.1, top-k 1, seed 42, max 320 new tokens, context reset before every call, parser, validator, merge and planner as in the app. Scored with E's `score.py` on E's **27 dev cases (34 turns)**; the 13 held-out cases were never run. Raw outputs stay on the kit and in A's scratchpad: they contain the gold sentences, and the gold text on A's kit still carries real campus names (the blind-check word list hits it), so they are **not committed**.

## Before / after (same app, same prompt, same kit)

- **Baseline** = branch `a/stubs` at `eb1e013` (app default prompt v8 plus the last two sentences as context), run `dev10-earlier`, 41 model calls.
- **Patched** = the same tree plus `git merge origin/lead/recall-salvage` (`71ad07d`, arena candidate 2; touches ToolCallParser, PayloadValidator, EnumNormalizer, TemporalResolver), throwaway branch `a/try-salvage`, run `dev11-salvage`, 37 model calls. Core tests there: 804 run, 1 failed (`ToolCallParserTest.wrongValueTypeFails`, encodes the old strictness, left to C).

| field | baseline precision | baseline recall | patched precision | patched recall |
|---|---|---|---|---|
| incident_type | 20/22 | 20/27 | 22/23 | 22/27 |
| severity | 5/5 | 5/9 | 8/8 | 8/9 |
| location | 19/21 | 19/26 | 22/24 | 22/26 |
| date_time | 12/15 | 12/24 | 22/22 | 22/24 |
| persons_involved | 4/5 | 4/7 | 4/4 | 4/7 |
| equipment_asset | 1/1 | 1/19 | 1/1 | 1/19 |
| immediate_actions | 3/3 | 3/21 | 8/8 | 8/21 |
| witnesses | 0/1 | 0/2 | 1/2 | 1/2 |

Headline: mandatory-field precision 89% (56/63, CI 79-95) to 96% (74/77, CI 89-99), recall 65% (56/86, CI 55-74) to 86% (74/86, CI 77-92). `equipment_asset` stays near zero in both runs: the model rarely writes it, so that gap is the prompt's, not the parser's.

## All prompt runs on the dev split

Mandatory fields = incident type, location, date/time, severity. "Valid" = the parser and validator accepted the model's tool call (first call, then after the one repair retry). Median seconds per turn includes the retry.

| run | mandatory precision | mandatory recall | overall correct / gold | wrong | halluc. | valid, first call | valid, after retry | median s/turn |
|---|---|---|---|---|---|---|---|---|
| v3 compact (E's format) | 79% (38/48, CI 66-88) | 44% (38/86, CI 34-55) | 49/135 | 11 | 6 | 29/34 (85%) | 32/34 (94%) | 4.3 |
| v4 stricter evidence | 85% (45/53, CI 73-92) | 52% (45/86, CI 42-63) | 58/135 | 8 | 5 | 23/33 (69%) | 26/33 (78%) | 4.6 |
| v5 strings (my mistake: validator wants lists) | 88% (38/43, CI 76-95) | 44% (38/86, CI 34-55) | 43/135 | 5 | 0 | 21/34 (61%) | 24/34 (70%) | 4.9 |
| v6 evidence values are strings | 90% (52/58, CI 79-95) | 60% (52/86, CI 50-70) | 57/135 | 7 | 3 | 27/34 (79%) | 32/34 (94%) | 5.1 |
| v7 evidence key must also be a field | 86% (50/58, CI 75-93) | 58% (50/86, CI 48-68) | 54/135 | 8 | 2 | 30/34 (88%) | 32/34 (94%) | 4.5 |
| v8 = v7 + equipment/time definitions (app default) | 90% (56/62, CI 80-95) | 65% (56/86, CI 55-74) | 64/135 | 7 | 3 | 30/34 (88%) | 33/34 (97%) | 4.7 |
| v9 = v8 + 3 examples | 90% (38/42, CI 78-96) | 44% (38/86, CI 34-55) | 46/135 | 5 | 5 | 14/27 (51%) | 18/27 (66%) | 6.3 |
| **baseline**: v8 + Earlier turns, a/stubs eb1e013 | 89% (56/63, CI 79-95) | 65% (56/86, CI 55-74) | 64/135 | 7 | 2 | 27/34 (79%) | 30/34 (88%) | 5.0 |
| **patched**: same app + lead/recall-salvage 71ad07d | 96% (74/77, CI 89-99) | 86% (74/86, CI 77-92) | 88/135 | 3 | 1 | 31/34 (91%) | 32/34 (94%) | 4.8 |

(95% intervals are Wilson-style intervals from E's scorer; with 27 cases they are wide, so differences of a few points are noise.)

## Caveats (read before quoting)

- One run per row. The model is near-greedy (temperature 0.1) but each row was not repeated, so run-to-run variation is unmeasured.
- Prompts v3 to v9 were tuned on these same dev cases: they are optimistic. The held-out 13 are the fair final check and have not been run.
- The dev split is mostly single-turn, so it cannot show the benefit of the earlier-turn context added in `eb1e013`; that change was neutral here (same overall correct count as v8: 64/135, first-call validity 79% vs 88%, one run each).
- **Incomplete rows:** a full run is 34 turns. v4 has 33 and **v9 only 27**, so v9 is a partial run (the app was interrupted on the kit) and its numbers are not comparable; do not read "more examples is worse" from it as established.
- v5 was a mistake of mine (it asked for comma-separated strings where the validator wants lists), kept in the table so the history is honest. v10 and v11 were started and discarded (not completed); a first dev9 run was cut short when the app was swiped away and was thrown away.
- Not a speech result: the transcripts are the gold text, not Whisper output. Median turn time here is the model alone; a live turn adds Whisper (about 2.5-3.4 s) and voice tags (about 0.8 s), and the first turn adds about 9 s for model load.
