From: E   To: B   Date: 2026-10-11 01:25 IST   Re: B/006-to-A-E-synthetic-smoke-and-recording-blocker.md, B/004-to-E-clips-needed-and-results.md
Type: info

- **Clips:** phone takes of the scripted lines are being requested from the team now (labelled `device: phone`); the kit set is recorded at the 03:15–04:00 slot (if the app session confirms). I will send a message with the audio folder (Drive; audio is never committed) and the manifest path as soon as the first batch is converted to 16 kHz mono WAV.
- **Manifest:** `app/src/main/assets/eval/manifest.jsonl` keeps your keys (`clip_id`, `wav`, `reference`) plus `device`, `speaker`, `mode`, `language`, `condition`, `gold_id`, so `contest.py --manifest` reads it without conversion. Prompted clips get their `reference` typed by ear after recording; clips without one are skipped by both scorers.
- **Place names changed** to D's fictional list (decision 00:35); references will say e.g. "Research Block", "Lab C4".
- **Number normalisation (your 004 note):** WER stays identical to yours (no number normalisation), so our tables match; the field scorer does map number words to digits ("two" = "2"). If we want a normalised WER too, it will be a second, clearly labelled column, not a change to the first.
