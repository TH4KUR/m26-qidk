# Status: agent C (core rules (:core))

Owner: agent C only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 00:35 IST
Read up to: A=000 B=000 D=000 E=000 (every other `messages/` folder was empty on `communication` at b281dac; B's STATUS read). Legacy `docs/messages/` read: B-to-A-1, B-to-A-2, B-to-lead-1, B-to-lead-2, lead-to-D, lead-to-E; nothing addressed to C. B's "duplicate Compute" item is already answered by B (C has had no `llm.Compute` since `d13ac07`).

## Doing now
- No code in flight; waiting for A's answers (message 003). Next, unless someone objects: friendlier display names for `IncidentType` (B asked the lead whether "water leak" can replace "water leak flood" in SummaryText), and a measurement pass as soon as E sends transcript pairs.

## Done and ready for others (with file/commit)
- `c/core-rules` `f97dae6`: week ranges (`TemporalValue.from/to`), `FieldProposal.temporal` and `.nearMiss`, near-miss grounding on D's shared Homophones. 424 tests, 0 failing, 1 skipped on purpose.
- Earlier on the same branch: shared types (`ValueStatus`, `ReportState`, `JournalEntry`), `ToolCallParser`, `PayloadValidator`, TAM cues (English + Hindi), urgency 1 to 20 with `PriorityAdvisor`, `SeveritySuggester`. Docs: `docs/core-rules/C-design-and-questions.md`, `docs/core-rules/shared-types-changes.md`, `docs/experiments/C-*`.

## Blocked on / waiting for
- A: merge of `c/core-rules`, decision on the URGENT lane (spec D6).
- D: fix of the `llm.Compute` imports before their branch can be merged (message 002).

## Needs from others
- E: (reference, engine transcript) pairs; a human second labeller; a native Hindi reviewer (message 004).
- Open, not blocking: Hindi lists are model-reviewed only, not native-reviewed; unseen urgency band accuracy is about 0.50 and the pre-registered bound was not met.
