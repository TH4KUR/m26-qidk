From: A (lead)   To: A (app/kit session)   cc: user   Date: 2026-10-11 08:08 IST
Type: kit request, PRE-REGISTERED (gate fixed before any run; the user asked for this test directly: "test out my hypothesis")

## The hypothesis (the user's)
The model does not understand the word "equipment"; people say tools or items. If the prompt describes `equipment_asset` in everyday words, the model emits more correct equipment values. Equipment is the one unsolved field: 3/19 on dev on every v8/v11 run so far (0/9 held-out).
Only the WORDING can be tested: the JSON key must stay `equipment_asset` (the parser and validator have no alias; renaming the key would be a change to frozen code).

## Arms (dev 27 only, same installed build, same session, one run each; nothing else changes)
| arm | file (absolute path) | sha256 prefix |
|---|---|---|
| ctl | /tmp/claude-1000/-home-th4kur-Desktop-m26-qidk/7bd57c53-1720-4bc9-8f24-712c36b32980/scratchpad/hyp/extract_v11.txt | 1ae9711fdb3f0bdb (= the shipped v11) |
| v12a | .../scratchpad/hyp/extract_v12a_tools_items.txt | 632b2407c88de870 |
| v12b | .../scratchpad/hyp/extract_v12b_tools_items_gloss.txt | c4e66249bb903919 |
v12a = v11 with the equipment rule reworded: "the tool, machine, item or thing ... in everyday words ... even when it is just an ordinary object" (examples unchanged, so no new dev nouns). v12b = v12a plus a gloss where the model first meets the key: "equipment_asset (the tool or item involved)". `diff` against v11 shows exactly these two lines, nothing else.
Command (your script): `kit_runs.sh hyp1 ctl:<ctl file> v12a:<v12a file> v12b:<v12b file>`. Run ctl first. **Do not run the held-out 13.** Installed build today is whatever is on the kit now (a/stubs with the Hindi lane and Supertonic); the control must be run on it, do not reuse the 06:39 control.

## Gate (fixed now, not to be moved after seeing results)
Primary: equipment values correct out of 19 (strict scorer: REPORTED, USER_CONFIRMED, USER_EDITED only; E's score.py). Control expectation 3/19.
**Hypothesis supported only if a variant has equipment >= 7/19 (control + 4)** AND, against the control of the same session: overall recall not lower, precision >= 90%, hallucination <= 4% (<= 3 of 81), valid after retry >= 90%, median turn <= 6 s. +1 to +3 is reported as "not supported at this gate". Control equipment has been 3/19 on every run (no run-to-run spread seen), so +4 is outside the noise we know.

## Disclosure (goes into every report that mentions it)
This is the sixth prompt variant tried on the same dev 27 (v9, v10, place-context, compact-context, may-guess before it). A pass is dev-informed, label it "post-held-out, dev only". Claiming it on unseen data would need a second held-out look, which is the user's call. Shipping any v12 changes the frozen extraction path: the user's call too. n = 19 equipment cells.

## What I need back
Per-arm: overall recall/precision/hallucination/valid/median, per-field table, and the list of the 19 equipment items with correct/miss per arm (so we can see which objects changed). Raw outputs to your usual folder. Claim and release the kit in SESSIONS.md; if the kit is busy with the Hindi work, queue this after it (no force-stop of your own run). If you are not the kit session, ignore this message and tell the user.
