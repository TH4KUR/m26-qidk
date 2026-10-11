# Status: agent D (core suggestions, flow, storage (:core))

Owner: agent D only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 06:15 IST
Read up to: A=026 B=015 C=012 E=010

## Doing now
- Waiting for A: merge of 799f59d, and a go or no-go for T3 (graph view) before 08:45.

## Done and ready for others (with file/commit)
- **799f59d** (pushed, ready to merge; contains master 12d0ae2): `:core:test` 1034 tests, 0 failures, 4 skipped, with LF prompt files (see the hazard below). Blind scan of the HEAD snapshot: clean, 330 files (mirror steps 5-6 run by hand; no rsync here).
  - T1 `docs/submission-readme-build-run.md` (6918d15, 799f59d): Build/Run draft incl. the Piper steps and the preload sentence (A/025). Only `:core:test` verified by D.
  - T2 `MissedFieldRules` (e0079cb): dev n=27, equipment 11 right / 3 wrong / 5 missed / 1 false alarm; actions 13 / 0 / 8 / 0. Dev only; does not raise measured recall.
  - A/020 items 1-2 (65a8273): Hindi/Telugu script words and number words for the snapper. English unchanged (tests, and the dev re-run equals run 6).
  - A/020 item 3 (6f4606c): `data/indic_draft_v1/`, 40 items, model-authored and unreviewed, split fixed before results. Dev snapper: hi 8/0/1/1/0, te 8/0/1/1/0, hi-en 3/0/3/0/0 (right / wrong / right no-chip / missed / false alarm).
  - A/020 item 4: gap list sent to C (D/007).
- **Hazard for A:** `DefaultPromptTest` fails on Windows checkouts (`core.autocrlf` turns the prompt files into CRLF); the repo blobs are correct. Fix suggested in D/006.

## Blocked on / waiting for
- A: merge; a real mirror dry run; the T3 decision.
- E: reviewers for the draft set.
- B / app session: raw Hindi/Telugu transcripts that contain place phrases (dev clips only).

## Needs from others
- Nothing else. D does not need the kit.
