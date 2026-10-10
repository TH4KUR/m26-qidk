# Shared types: what C added or changed (for A and D)

Owner: C · Branch `c/core-rules` · 2026-10-10. Pure Kotlin in `:core`; no Android imports. Everything here is additive except where marked.

## Decisions D asked for

| Question | Decision |
|---|---|
| Which status enum? | **`ValueStatus`** (spec §4b). `FieldStatus` from plan Task 1 is superseded and does not exist in code. |
| What is UNCONFIRMED? | **`SUGGESTED`**: not filled (`isFilled` is false). |
| What is "no witnesses"? | **`EXPLICIT_NONE`**: answered (`isAnswered` is true), not filled. "I don't know" is `UNKNOWN`: also answered, not filled. |
| `ReportState`? | `ReportState(report: IncidentReport, journal: List<JournalEntry>)`. No pending-chip list. |
| Who owns the journal type? | **C owns the types**; D owns the logic that appends entries and the storage that persists the state. Append-only. |
| How is a rejected chip kept from reappearing? | `ReportState.hasDecided(suggestion)` is true after a `SUGGESTION_ACCEPTED/REJECTED` entry for the same `(field, original, proposed)`; `suggest()` filters on it. |
| Clock? | Inject `java.time.Clock` (`Clock.fixed(...)` in tests). No custom interface. |

Status rules: a field holds `SUGGESTED` only when it was `MISSING`; a change to an existing heard value is a `Suggestion` chip and the field stays untouched until the user accepts (then `USER_CONFIRMED`, original kept in the journal). `USER_EDITED` is never overwritten by speech or the model. `DESCRIPTION` is `REPORTED`; `RECOMMENDED_FOLLOW_UP` is `SUGGESTED`.

## New or changed types

| Type | File | Change |
|---|---|---|
| `ValueStatus`, `Source`, `Field`, `FieldValue`, `IncidentReport` | `domain/Model.kt` | as spec §4b; `FieldValue.explicitNone/unknown`; `IncidentReport.isFilled/isAnswered` |
| `Source` | `domain/Model.kt` | **added `LLM`, `USER`** (additive; check any exhaustive `when`) |
| `JournalKind`, `JournalEntry`, `ReportState` | `domain/Model.kt` | new |
| `ExtractionPayload` | `domain/Model.kt` | **added optional `impact_hint: Int?`, `ongoing: Boolean?`** |
| `LlmHint` | `domain/Model.kt` | new; `LlmHint.from(payload)` drops out-of-range values |
| `Tam`, `Tense`, `Aspect`, `Modality` | `domain/Tam.kt` | new |
| `UrgencyAssessment`, `UrgencyReason`, `TriageLane`, `priorityForScore` | `domain/Urgency.kt` | new |
| `ParseResult`, `FailureCode`, `Envelope` | `llm/ToolCallParser.kt` | new; `FailureCode.DUPLICATE_KEY` |
| `FieldProposal`, `Rejection`, `ValidationResult` | `llm/PayloadValidator.kt` | new; `ValidationResult.hint`; `FieldProposal.temporal` and `.nearMiss` are **additive, default null** |
| `TemporalValue` | `domain/TemporalResolver.kt` | **added `from`, `to`** (default null; a week is a range with `date == null`) |
| `NearMiss(heard, meant)` | `domain/GroundingValidator.kt` | new |
| `HomophoneTable`, `Homophones` | `suggest/Homophones.kt` | **D owns**; copied here verbatim (same blob) so `:core` builds without D's branch |
| `LlmEngine`, `LlmRequest`, `LlmResult`, `FakeLlmEngine`, `EngineFactory` | **A owns these** (not C) | removed from C's branch to avoid a name collision |
| `HedgeDetector` | `tam/HedgeDetector.kt` | new (D needs it); `TamHedgeDetector` is the real one |
| `FakeHedgeDetector` | `testing/` | new fake for D |
| `PriorityAdvisor` | `priority/PriorityAdvisor.kt` | `onChunk(text, signals, hint)`; outputs `deescalation`, `keyQuestion`; `acceptDeescalation()`, `answerKeyQuestion()` |

## Weeks and near-misses (agreed with D)

| Input | What `PayloadValidator` returns |
|---|---|
| "last week" / "pichhle hafte" / "पिछले हफ्ते" | `DATE_TIME` REPORTED, `temporal.date == null`, `temporal.from`..`to` = previous Monday..Sunday (ISO weeks), `approximate`, note "needs clarification: which day" |
| "this week" / "is hafte" | same, `from` = this Monday, `to` = today |
| "a week ago" / "2 weeks ago" / "ek hafte pehle" | `temporal.date` = capture - 7 (or 14) days, approximate, time missing (still needs clarification) |
| the model's value is not in the transcript but a word sounds alike ("last weak" for "last week", "room to" for "room 2") or is one letter off in a word of 4+ letters ("leaks" for "leak") | **SUGGESTED**, `value.evidence` = the words actually heard, `nearMiss.heard` / `.meant`, note `heard "...", proposed "..."`. Never REPORTED; the 0.5 / 0.8 overlap thresholds are unchanged |

Rules: names (`persons_involved`, `witnesses`) use the sound-alike table only, never the one-letter rule (Rahul/Rahil are two people); Hindi words are never matched by the one-letter rule; a near-miss in a negated clause is still rejected; a DATE_TIME that was never said is still rejected.

D's planner: ask "which day?" when `temporal.date == null && temporal.from != null`; build the chip from `nearMiss` (`original = heard`, `proposed = meant`).

## Reason codes (additive, `c/core-rules`)

`FieldProposal.reasonCode` and `Rejection.reasonCode` are `ReasonCode?` (null = a plain "heard"): `HEDGED`, `NEGATED`, `PLANNED`, `UNGROUNDED`, `NEAR_MISS`, `NOT_A_NAME`, `NOT_AN_ACTION`, `NOT_STATED`, `UNKNOWN_VALUE`. `note` / `reason` stay the human text; the code is the stable key for per-field scoring. New values may be added later, none will be renamed.

## `PriorityAdvisor.onReport(report)` (additive, `c/core-rules`)

After each merge, `advisor.onReport(report)` lets what the reporter stated raise the advice, never lower it: a filled severity (critical = URGENT, severe = HIGH, moderate = MEDIUM) and a filled incident type that is a hazard in itself (fire/smoke, electrical, injury, chemical spill, security, illness: at least MEDIUM, never URGENT alone). SUGGESTED values do not count. It returns the same `AdvisorOutput` as `onChunk`; calling it again with an unchanged report changes nothing. Owner of the call site: `ReportSession` (A / app session).

## What each consumer should do
- **D:** build `ReportLogic` on `ValueStatus` and `ReportState`; use `HedgeDetector` (real or fake) for hedge chips; filter chips with `hasDecided`; obey `PriorityAdvisor.lane()` in the follow-up planner. A hedged or ungrounded model value arrives from `PayloadValidator` as a `FieldProposal` with status `SUGGESTED` and a `note`.
- **A:** keep your fake engine behind `EngineFactory` and refuse it in release builds; pass the model's `ValidationResult.hint` to `PriorityAdvisor.onChunk`; show `deescalation` as a "lower to LOW?" chip and `keyQuestion` as one spoken question before anything else.
- **B:** `Signal(kind, label, source, segmentId)`: events use `kind="event"` with the YamNet/AudioSet label (`fire alarm`, `screaming`, `gunshot`...), emotions `kind="emotion"` with `angry|sad|neutral|happy`.

## Open items that need the lead
- **URGENT lane asks only for the location** and offers "Submit now" (changes spec D6 for urgent cases).
- **Spec §4 contract** now has two optional fields (`impact_hint`, `ongoing`); A/B own the grammar and prompt. Safe to ignore until the model can be measured on-device.

## Note to A: what C changed that touches ToolCallParser / ExtractionPayload
- `ExtractionPayload` gained two **optional** fields, `impact_hint` and `ongoing` (a fake reply of `{"tool":"update_report","arguments":{...}}` still parses).
- `ToolCallParser` is stricter in one way: **two writes to the same key in one object now fail** with `FailureCode.DUPLICATE_KEY` (new enum value; check exhaustive `when`s). An unparseable hint is dropped, not fatal.
