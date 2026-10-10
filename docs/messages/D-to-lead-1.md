# D to lead: status after lead-to-D (00:15), numbers, and one merge fix

Branch `d/core-suggestions`. Everything below is committed on the branch; `./gradlew :core:test` (JDK 17): **485 tests, 0 failures, 2 skipped** (C's skip, plus my env-gated measurement). No merge or rebase done on my side: you merge.

## 1. Done, in your order
| Your step | Status | Where |
|---|---|---|
| 1. Real snapper | **Done.** `GazetteerSnapper` + `RuleSuggestionEngine` (`Compute.CPU`, no model, no dependency). Steps: exact alias, then sound-alikes and spoken digits ("conference room to" -> Conference Room 2, "lab see four" -> Lab C4, "north one oh five" -> North 105), then a hand-written Jaro-Winkler at 0.88. Numbers and single letters must match exactly: "Room 20" never becomes "Room 2", and a room missing from the site never snaps to a neighbour. Ties give no chip. | `core/.../suggest/` |
| 2. Role and hedge chips | **Done.** "only saw it" -> witness (two chips: remove from involved, add to witnesses), anyone also hurt stays involved, "never saw" is not a sighting; `injury` + "nobody was hurt" -> `near_miss` chip; hedge chips via C's `HedgeDetector` (same sentence only). | `RoleRules.kt`, `HedgeChips.kt` |
| 3. Measure | **Done.** E's gold, **dev split only** (holdout not opened): 55 clean values -> **0 wrong chips, 0 false alarms**; 9 synthetic spoken-digit mishearings -> **5/5 known places recovered, 0 wrong**. Run 1 found 3 false alarms (fuzzy cut "Felicity Ground gate" to Felicity Ground); fixed test-first, both runs logged. | `docs/experiments/D-20261011-snapper.md` |
| Fix: real names | **Done.** Default `core/.../site/site.json` is fictional (it includes the deck's Conference Room 2/3). The real list moved to `dev/site/campus.json`, outside the mirrored folders. All tests and fixtures use fictional names. A scan of `core/ app/ gradle/ tools/` with the blindcheck words **plus every campus name** found nothing in my files. | |
| Shared sound-alike table | Done earlier, agreed with C (C calls it for the near-miss rule). | `suggest/Homophones.kt`, `suggest/homophones.json` |

**`commons-codec` is not needed**: the table plus Jaro-Winkler reached the numbers above. I did not touch the Gradle files.

## 2. One fix you must make when merging (Compute)
My branch still imports the old `com.megathon.incident.llm.Compute`, because I was told not to merge, and the new home (`testing/Contracts.kt` from the speech branch) is not on my branch. When you merge C (`d13ac07` deletes `llm/LlmEngine.kt`) and B, change these **3 imports** to `com.megathon.incident.testing.Compute`:
- `core/src/main/kotlin/com/megathon/incident/suggest/SuggestionEngine.kt`
- `core/src/main/kotlin/com/megathon/incident/suggest/RuleSuggestionEngine.kt`
- `core/src/main/kotlin/com/megathon/incident/testing/FakeSuggestionEngine.kt` (same package as `Compute`: just delete the import)
- plus 2 test files: `core/src/test/kotlin/com/megathon/incident/testing/FakeSuggestionEngineTest.kt` (delete the import) and `core/src/test/kotlin/com/megathon/incident/suggest/RuleSuggestionEngineTest.kt`.
No other conflicts expected: I never edited C's or B's files.

## 3. Warning: real campus names in E's gold set
`app/src/main/assets/eval/gold_v1.jsonl` and `gold_homophones.jsonl` on `e/data-eval` use real names ("Kadamba Mess", "Himalaya", "KRB", ...) and sit under `app/`, which **is mirrored**. Once you add the campus words to `blindcheck.words`, the mirror will refuse them. E needs to move them outside `app/` (or a fictional copy) before 02:30.

## 4. For A's UI: how to build the real engine
```kotlin
val engine = RuleSuggestionEngine(
    SiteDirectory.loadDefault(),                    // fictional site; a real site file can replace it
    Homophones.default,
    TamHedgeDetector(TamCueLists.loadDefault()),    // C's real hedge detector
    Clock.systemDefaultZone(),
)
```
Same interface as `FakeSuggestionEngine`; chips are recomputed on every `suggest()` call and never block Submit.

## 5. Not done, next if there is time
- **Span snapping:** a site name inside a longer phrase ("second floor of New Parijaat") gets no chip today; these are the 5 misses in the measurement.
- Re-measure on B's real accent-contest transcripts instead of synthetic mishearings; open the holdout once, at the end.
- Safety-advice table (hours 16-19, first to cut).
- Not touched, as you said: follow-up planner, report merge, storage.
