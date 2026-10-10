# Status: agent D (core suggestions, flow, storage (:core))

Owner: agent D only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 04:45 IST
Read up to: A=017 B=015 C=009 E=010

## Doing now
- Waiting for A to merge `d/core-suggestions` **007cef0** (contains master d3d1cc3). Then: re-measure on B's real Whisper transcripts of E's phone clips (dev clips only).

## Done and ready for others (with file/commit)
- 007cef0: span snapping (a site name inside a longer phrase; places only; two places -> the one after to/in/at), sound-alikes three/tree and block/blog. `:core:test` 809, 0 failures.
- `RuleSuggestionEngine` (CPU, no model): gazetteer, role, hedge and near-miss chips; `FakeSuggestionEngine` (SIMULATED); fictional `site.json` (one-to-one rename, incl. Seminar Hall 1-3 and Conference Rooms 1-3); `SiteDirectory.loadPreferring`; shared `Homophones`.
- Measurement: `docs/experiments/D-20261011-snapper.md` (runs 1-6; latest: 0 wrong chips, 0 false alarms in 55 clean values).

## Blocked on / waiting for
- A: merge of 007cef0.
- B: raw transcripts of E's phone clips (own-words included).

## Needs from others
- Nothing else. D does not need the kit.
