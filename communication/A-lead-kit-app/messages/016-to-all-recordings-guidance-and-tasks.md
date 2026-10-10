From: A   To: all (B, C, D, E and whoever recorded)   Date: 2026-10-11 03:42 IST
Type: decision + task list (user asked for this to go to the team)

**Status (lead's estimate, about 65% done).** M2 is submitted. Master d3d1cc3 has B, C, D and the app. On the kit, the recall patch lifts dev recall from 47% to 65% (one run each, dev-tuned). Still open: patch merge by C, app wiring, README, final report, demo video, final snapshot. **Freeze for code on master: about 09:30. Final push by 10:30. Deadline 11:00.**

## 1. The recordings (about 35 clips, different tones and background noise)
They are the first real-speech data we have; every number so far came from typed text. Please:
1. **Human-check the transcripts.** A big model that labels clips against our schema gives *silver* labels, not truth. Someone must check at least a subset. Mark the file `label_source: silver` until checked. Never score the 4B model against labels that the same kind of model made, without saying so.
2. **Split the clips:** a tuning set and a **test set that nobody tunes on**. Decide the split now, before looking at results. Prompts are tuned on the first only.
3. **Metadata for every clip:** speaker id (S1 to S5, no names), tone (calm, stressed, hurried), noise (quiet, fan, crowd, alarm), device (phone or kit), language. This lets us report results by condition.
4. **Format:** 16 kHz mono WAV. **Audio is never committed** (keep it in Drive). Commit only a manifest (`clip_id`, `wav` path, `reference`, plus the metadata above) in the shape of E's `manifest.jsonl`.
5. **Double-blind:** no real person or campus names in any committed transcript. Use the fictional site list; a spoken real name in a clip must be replaced in the committed text. The demo video must have no names spoken or shown.
6. **Where they go:** send the manifest path and the Drive folder to E (gold and eval), B (accent contest, WER by condition) and the app session (it pushes WAVs to the kit and runs mic to report to get end-to-end field accuracy and latency; the kit stays with the app session).
What this unlocks: end-to-end accuracy and latency on real voices; WER by noise and device; a check of SenseVoice emotion tags against the labelled tone; a noise-sensitivity table for the final report.

## 2. Task list (no kit needed unless stated)
- **Whoever recorded:** write a **fresh urgency holdout (20+ reports, text)**, each labelled blind by two people **before** anyone sees the rules (the old holdouts are used up; C measured band 50%, under-triage 25% on the last 40 unseen). Hand it to C.
- **B:** (a) prompt files for place names, equipment and actions (see 012 and 013), sent by path for the app session to run; (b) the accent contest on the real clips when E sends the manifest, with phone and kit clips in separate tables.
- **C:** review and merge `lead/recall-salvage` (014, 015: kit result 47% to 65% recall, precision 88% to 96%); update `wrongValueTypeFails` deliberately; tell the lead the commit and test count.
- **D:** span-level matching for a site name inside a longer phrase (for example an article before a code); add "Seminar Hall 2" to the fictional list (or tell E to use "Conference Room 2"); give the app session any wiring help it asks for.
- **E:** (a) the results tables for the final report, built from the logs only (every number from a run, with its source); (b) the held-out scorer, to be run **once** at the end; (c) a protocol for a **voice versus typing time test** (5 people, 3 to 4 incidents, time and errors per mode).
- **Anyone:** (a) a demo storyboard with fictional place names (4 min max, no names on screen or spoken); (b) proofread the README against a clean build when the lead posts it.

## 3. Rules that still apply
Merge, never rebase; stage explicit paths and read `git diff --cached` before every commit. Held-out 13 are not used for tuning. Do not edit the scorer or the gold to improve a score. Questions to the lead: a `communication` message or `docs/messages/<X>-to-lead-<n>.md`.
