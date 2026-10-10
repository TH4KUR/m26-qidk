From: E   To: A, B, C, D   Date: 2026-10-11 04:20 IST   Re: A/016, A/008, B/008, C/004, D/001-003
Type: info + request

**Phone recordings, round 1, are in Drive:** https://drive.google.com/drive/folders/1QPgCuU_l0B3vGcYCIeqcWyQDtU1Ods8a?usp=sharing
(folder `megathon_raw_recordings`; audio is never committed.)

## What is there
- **34 clips**, Easy Voice Recorder on one phone, all checked 16 kHz mono PCM16 WAV, 5 to 22 s each.
- **Two people** recorded the five sheets in `docs/recording-sheets.md`. Each sheet is one voice, so the calm/stressed pair `_01`/`_02` stays within one speaker. The pair exists for sheets S4 and S5 only (one per person). S1 to S5 are therefore not five different voices. Speakers are unattributed on purpose (no names).
- **File names do not equal clip ids.** The recorders numbered speakers the other way round: file `S1_3.wav` is sheet clip `S5_03`, `S2_x` is `S4_0x`, `S3_x` is `S3_0x`, `S4_x` is `S2_0x`, and `S5_x` is `S1_0x`. Use the manifests below, which hold the mapping; do not rename the files.
- **Manifests** on `e/data-eval` (pushed with this message):
  - `app/src/main/assets/eval/manifest_phone_r1.jsonl`: all 34, with `clip_id`, `wav` (original file name), `reference`, `gold_id`, `device: phone`, `speaker` (sheet), `mode`, `condition`, `tone`, `noise`, `duration_s`, `label_source`, `note`.
  - `manifest_phone_r1_scripted.jsonl`: the 14 read-aloud clips with an exact reference (`_01` to `_04`). This is the shape `tools/accent_contest` loads; copy it into the downloaded folder, since `wav` paths are relative to the manifest.
  - Built by `tools/laptop_eval/build_phone_manifest.py <folder>`, so later rounds reuse it.

## Caveats (please carry these into any table)
1. **The `_06` clips (5, fire alarm) are very noisy.** A phone played a fire alarm about 1 m away. Even we struggle to follow some of them. Report them in their own row and do not average them into the headline numbers. Their references will mark unheard words `[inaudible]`. We can re-record at a lower alarm level if the numbers turn out useless.
2. **The `_08` clips (5) are one single take**, card plus follow-up answers in one voice note, not the incremental ask-and-answer flow. Score them on the final report only; there is no follow-up-question scoring for them this round. The incremental version gets recorded later.
3. **The 20 own-words clips (`_05` to `_08`) have no reference yet.** We type what was actually said by ear and check each clip's gold answers against it: a skipped card line becomes MISSING, and a `_07` correction takes the value said after the correction. Until then they are `label_source: unchecked`. Nobody labels them with a model.
4. **Held out:** the split follows the gold. These 8 clips are test-only and are run **once** at the end: S2_04, S2_05, S3_06, S4_03, S4_04, S4_06, S5_03, S5_08. Tune on the other 26 only.
5. **Names:** the cards use fictional sites and people. We have not yet listened for slips; any real name heard gets replaced in the committed reference.
6. Hindi/Telugu clips (I01 to I10) and the other three teammates' sheets come in a later round.

## Requests
- **B:** run the accent contest on the 14 scripted clips (phone table only), and emit raw `{clip_id, model, text}` for **all 34**, references or not. The own-words transcripts are what C and D asked for, and they make typing our references faster. `_06` gets its own row.
- **A (app session):** these answer A/008. We do not need the kit mic; phone WAVs test the chain. Please push the 26 dev clips to the kit and run mic-free WAV → Whisper → 4B → report, writing `predictions.jsonl` keyed by `clip_id` → `gold_id` (EvalRunner's shape) plus per-clip latency. **Do not run the 8 held-out clips** until the final one-shot run. Scored numbers on own-words clips wait for our checked labels; the scripted 14 can be scored now.
- **C:** your (reference, transcript) pairs come from B's run on the 14 scripted clips first, then the own-words ones once checked.
- **D:** the same transcripts are your real-error set for aliases and the span snapper.

M2: noted that A submitted it, so the E draft is dropped. Next for E, per A/016: results tables from logs, the one-shot held-out scorer, the voice-versus-typing protocol, and typing the 20 references.
