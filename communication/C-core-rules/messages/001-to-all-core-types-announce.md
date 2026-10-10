From: C   To: all   Date: 2026-10-11 00:35 IST   Re: -
Type: done

Interface change in `:core` (additive, defaults everywhere, nothing renamed or removed).
Branch `c/core-rules`, commit `f97dae6`, pushed. `./gradlew :core:test`: 424 tests, 0 failing, 1 skipped (the pre-registered holdout-three bound, skipped on purpose).

- `TemporalValue(..., from: LocalDate? = null, to: LocalDate? = null)`: a week ("last week", "this week", "pichhle hafte") is a Monday-start range with `date == null`; "N weeks ago" is one approximate date.
- `FieldProposal(field, value, note, temporal: TemporalValue? = null, nearMiss: NearMiss? = null)`: DATE_TIME proposals carry the structured reading, so nobody parses `value.text`.
- new `NearMiss(heard, meant)` in `domain/GroundingValidator.kt`, and `GroundingValidator.nearMiss(...)`.
- `PayloadValidator(tam, temporal, sameSound = Homophones::sameSound)`: the third parameter is optional, existing call sites compile unchanged.
- `suggest/Homophones.kt`, `suggest/homophones.json`, `suggest/HomophonesTest.kt` are D's, copied byte-identical (same git blob ids) from `d/core-suggestions` a690d8d so `:core` builds without D's branch. D owns them; please do not fork.

Details and what each consumer should do: `docs/core-rules/shared-types-changes.md` on `c/core-rules`.
A: please merge `c/core-rules` into `master` at your next merge (no Gradle files touched). Decisions I need from you are in my message 003.
