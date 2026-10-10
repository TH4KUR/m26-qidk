# D to lead 2: your 00:39 update applied, plus two things for A

Branch `d/core-suggestions`. `./gradlew :core:test` (JDK 17): **493 tests, 0 failures, 2 skipped** (C's skip and my env-gated measurement). No merge or rebase on my side.

## 1. Real names (your 00:39 update / user decision 00:35)
- **The real list is no longer committed.** `dev/site/campus.json` is untracked (`git rm --cached` in `71457cf`) and kept on D's laptop only. Caveat, so you can decide: it still exists in this branch's **history** (commits `c34020d`..`80bd605`), so merging the branch carries those old commits into `master`'s history, but not into its files. The mirror exports `HEAD` only (`git archive`), so the submission is not affected. If you want it gone from history too, say so before merging and I'll prepare a cleaned branch.
- **Your new `blindcheck.words` (with the campus names) over `core/ app/ gradle/ tools/` on my branch: clean.** Three code comments that quoted real names from my measurement were changed to fictional examples.
- The fictional default site already reads like a real campus (North/South/Research blocks, Conference Rooms 1-3, Lab C4, Maple/Cedar/Pine hostels, Central-store-style plant rooms).

## 2. For A: load the demo's site list from the kit
```kotlin
val load = SiteDirectory.loadPreferring(File(context.filesDir, "site.json"))
// load.site: the pushed list if present and valid, else the bundled fictional one
// load.fromFile: show it in the proof strip / debug screen
// load.problem: non-null when a pushed file was rejected (bad JSON, two places sharing an alias); show it
```
It never throws, so a bad file cannot stop the demo. Push the real list for the demo with `adb push campus.json /sdcard/...` and copy it into `filesDir` (your call how).

## 3. Near-miss chips: built, no plumbing needed
C asked me to build the chip from `FieldProposal.nearMiss`. That isn't necessary: C's validator already stores a near-miss in the field itself (value = meant, status `SUGGESTED`, evidence = heard words), so the engine detects it from the report and offers "Heard "weak", close to "last week"; confirm the time." Accepting it confirms the meant value. **Ask of the merge (your other session):** keep C's `FieldProposal.value` as is when it lands in the report (text, status and evidence), which is presumably what it does already.

## 4. Unchanged from D-to-lead-1
The 5 `Compute` import lines to repoint at `com.megathon.incident.testing.Compute` when you merge (listed in `D-to-lead-1.md` section 2), and the warning about real names in E's gold files under `app/`.
