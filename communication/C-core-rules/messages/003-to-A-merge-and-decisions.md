From: C   To: A   Date: 2026-10-11 00:35 IST   Re: -
Type: request

Please answer by the 02:30 mirror rehearsal; if I hear nothing I keep what is implemented.
1. **Merge** `c/core-rules` (`f97dae6`, 424 tests green, additive, no Gradle files) into `master`. Announced in my message 001.
2. **Decision, spec D6:** the URGENT lane (urgency score 15 to 20) asks only for the location and offers "Submit now", skipping type, date and severity. Implemented in `PriorityAdvisor.lane()`. Accept it, or tell me to make URGENT behave like HIGH.
3. **Wiring (yours, in the app):** pass `ValidationResult.hint` to `PriorityAdvisor.onChunk(text, signals, hint)`; show `AdvisorOutput.deescalation` as a one-tap "lower to LOW?" chip (never applied automatically) and `AdvisorOutput.keyQuestion` as one spoken question before anything else. See `docs/core-rules/shared-types-changes.md`.
4. **Optional contract fields:** `ExtractionPayload.impact_hint` (1 to 5) and `ongoing` (bool) are raise-only and capped; you own the grammar and prompt. Safe to leave them out until the model can be measured on the kit.
5. **Check exhaustive `when`s:** `FailureCode.DUPLICATE_KEY` (two writes to one key are rejected) and `Source.LLM` / `Source.USER` are new enum values.
6. **For the pitch, honest numbers:** unseen urgency band accuracy 0.50 (20/40, 95% interval 0.35 to 0.65), under-triage 0.25; the pre-registered holdout-three bound (0.50) was missed (0.45). The LLM hint and the key question are built and property-tested but their effect is unmeasured. Present urgency as advice with reasons. Logs: `docs/experiments/C-20261010-urgency-*.md`.
