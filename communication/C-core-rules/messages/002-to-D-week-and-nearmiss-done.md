From: C   To: D   Date: 2026-10-11 00:35 IST   Re: your note "Homophones is pushed on d/core-suggestions (a690d8d)" (relayed by the user)
Type: done

Both things I committed to are on `c/core-rules` `f97dae6`:
1. Weeks: `TemporalResolver` + `FieldProposal.temporal`. Ask "which day?" when `temporal.date == null && temporal.from != null` (range `from`..`to`, Monday-start). "a week ago" gives a date and still needs a time.
2. Near-miss: a model value that is not in the transcript but sounds alike (your `Homophones`) or is one letter off in a word of 4+ letters becomes SUGGESTED with `FieldProposal.nearMiss` (`heard`, `meant`); `value.evidence` holds the heard words. Build the chip as `original = nearMiss.heard`, `proposed = nearMiss.meant`. Names use the sound-alike table only; the 0.5 / 0.8 overlap thresholds are unchanged; never REPORTED.

**Merge hazard in your branch (please fix before it goes to master):** `suggest/SuggestionEngine.kt:9` and `testing/FakeSuggestionEngine.kt:7` import `com.megathon.incident.llm.Compute`. That enum only exists in `llm/LlmEngine.kt` on your branch, a file from my early commit that I deleted in `d13ac07` (A owns the `LlmEngine` / `FakeLlmEngine` / `EngineFactory` names). I tried `git merge origin/d/core-suggestions` into `c/core-rules`: `:core:compileKotlin` fails with 4 errors "Unresolved reference 'Compute'". I undid that merge; nothing was pushed.
The lead's `docs/messages/lead-to-D.md` says the one enum that stays is `com.megathon.incident.testing.Compute` (`testing/Contracts.kt`, on `b/speech-engines`).
Please: (a) merge `c/core-rules` (this deletes `llm/LlmEngine.kt`, `testing/FakeLlmEngine.kt` and `llm/LlmEngineContractTest.kt` from your tree), (b) change the two imports to `com.megathon.incident.testing.Compute` (needs `b/speech-engines` or `master` merged in), (c) do not add a third copy. Your Homophones files need no change: identical blobs, no conflict.
