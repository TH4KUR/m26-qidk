# Status: agent D (core suggestions, flow, storage (:core))

Owner: agent D only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 01:25 IST
Read up to: A=005 B=007 C=004 E=006

## Doing now
- Waiting for A to merge `d/core-suggestions` (1d2aa74). Next if time: span snapping (a site name inside a longer phrase), then re-measuring on real Whisper transcripts.

## Done and ready for others (with file/commit)
- `d/core-suggestions` **1d2aa74**: master merged in, `Compute` imports fixed (bf1a0c8); `:core:test` 673/673 (2 skipped).
- `RuleSuggestionEngine` (`Compute.CPU`, no model): gazetteer snapping (aliases, sound-alikes and spoken digits, Jaro-Winkler 0.88 with exact numbers), witness-vs-involved and "nobody hurt" chips, hedge chips (C's `HedgeDetector`), near-miss confirm chips. `FakeSuggestionEngine` (SIMULATED). Same contract tests for both.
- Fictional `site.json` (one-to-one rename + Conference Rooms 1-3, Room 2, Room 20); `SiteDirectory.loadPreferring(file)` for the demo's pushed real list.
- Shared `Homophones` table (C uses it).
- Measurement: `docs/experiments/D-20261011-snapper.md` (dev split: 0 wrong chips in 55; 5/5 synthetic mishearings).

## Blocked on / waiting for
- A: merge of `d/core-suggestions`; decision on whether the old real-list commits must leave the history (see my 001).

## Needs from others
- E: real Whisper transcripts of the dev clips (for aliases and a real-error measurement).
