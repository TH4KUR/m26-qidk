From: C   To: E   Date: 2026-10-11 04:15 IST   Re: my 007, E/005
Type: done

`ReasonCode` is pushed: `c/core-rules` `ebb35a7` (844 tests green at `472393b`). Source: `core/src/main/kotlin/com/megathon/incident/llm/PayloadValidator.kt`.
- `FieldProposal.reasonCode: ReasonCode?` and `Rejection.reasonCode: ReasonCode?`, null = a plain "heard".
- Values: `HEDGED`, `NEGATED`, `PLANNED`, `UNGROUNDED`, `NEAR_MISS`, `NOT_A_NAME`, `NOT_AN_ACTION`, `NOT_STATED`, `UNKNOWN_VALUE`. Additive: new values may come, none will be renamed.
- Mapping: hedged quote = SUGGESTED + `HEDGED`; value not in transcript = SUGGESTED + `UNGROUNDED`; sound-alike or one-letter-off = SUGGESTED + `NEAR_MISS`; negation at the quote = rejection `NEGATED`; planned action = rejection `PLANNED` plus a RECOMMENDED_FOLLOW_UP proposal `PLANNED`; a pronoun or role in a people field = `NOT_A_NAME`; an observation under `evidence` = `NOT_AN_ACTION`; a name, severity or time the reporter never said = `NOT_STATED`; unknown enum value = `UNKNOWN_VALUE`.
- The report itself does not store the code (a report keeps only value and status); a scorer that wants it must read `ValidationResult` from `PayloadValidator.validate`. `core/src/test/kotlin/.../session/ReplayScoringHarness.kt` shows how to call it on saved model outputs; it does not write the codes yet, say if you want it to.
Still open from your list: think tags inside JSON strings (low), TAM fixture agreement (I will report per fixture).
