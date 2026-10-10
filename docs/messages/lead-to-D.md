# Lead to D: where you are, what to do next (00:15 IST)

Read this whole file, then work down the list. M2 is due **04:00 IST**; the mirror rehearsal is **02:30**; code you want in the M2 snapshot must be on `master` before then.

## 1. What I verified on `d/core-suggestions` (head `fa9b93b`)
- I ran `./gradlew :core:test` (JDK 17) in a clean copy: **413 tests, 0 failures, 1 skipped.** That count includes C's 380 tests, which your branch already contains.
- **Your own work (6 commits):** `SiteDirectory` (loads the gazetteer; **exact alias lookup only**), the `SuggestionEngine` interface, `ChipDecisions`, `FakeSuggestionEngine`, `site.json`, four fixture files (`campus_snaps`, `deck_example`, `hedge`, `witness_role`) and an experiment log. Good, clean, test-first.
- **What is not there yet:** the part that actually fixes a mistake. "heard *conference room to*" will not match "Conference Room 2" with exact lookup. There is also no role rule (witness vs involved), no hedge chips, and no real engine behind your interface. That is the feature judges will see, so it is the priority.

## 2. Next steps, in order (times are targets)
1. **00:30 to 01:30: the real snapper** (`SuggestionEngine`, spec D16: report first, suggest after).
   - A homophone and number table first ("to/too/two" after "room", "for/four", "ten am" vs "10 AM").
   - Then fuzzy matching against `site.json` (Jaro-Winkler; Double Metaphone if you can add the dependency, but ask the lead first: a hand-written Jaro-Winkler plus the table is enough). Start the threshold at 0.88; it is a guess until you measure it.
   - It runs **after** the LLM and works on the raw transcript, so the evidence check still sees what was said.
   - Output a `Suggestion(field, original, proposed, reason, source = GAZETTEER)`. Accepting keeps the original in the journal; **Submit is never blocked by a pending chip.**
   - Test first with your fixtures, and add negatives: a place not in the list gives **no** chip; "Room 20" must not snap to "Room 2".
2. **01:30 to 02:15: role and hedge chips.** Witness vs involved (someone who only *saw* or *reported* is a witness; "nobody hurt" blocks the injury type), and "I think / maybe" chips using C's `HedgeDetector`.
3. **Measure it.** Run the snapper over E's gold dev split (27 cases; `git show origin/e/data-eval:app/src/main/assets/eval/gold_v1.jsonl`) and record: correct snaps, wrong snaps, and missed snaps, **with the counts**. Write it in `docs/experiments/D-<date>-snapper.md`, failures included. M2 needs numbers, not claims.
4. **By 02:20: merge and push.** `git fetch && git merge origin/c/core-rules && git merge origin/master` (merge, not rebase), run the tests, push your branch, and tell the lead so it can be merged into `master` before the rehearsal.

## 3. Two things to fix now
- **Real campus names are in your data** (`site.json` has dozens of hits, plus fixtures and tests). The private dev repo is fine, but the submission must not reveal the college. Proposal: keep two files, a fictional `site.json` as the default and a dev-only campus file selected by a config or test flag; make every test and fixture use the fictional names. The lead will add these words to the scan list, and the mirror will **refuse** to copy until the names are gone, so do this before 02:15.
- **User decision (00:35): real names are kept for the live demo only.** The submission, the demo video and the code zip use the fictional list. So: a fictional `site.json` is the default and is the one that gets mirrored; the real list stays in a separate dev-only file that is never committed to `master`, and the app loads a `site.json` from the kit's files directory over the bundled one if it exists (pushed over USB at the demo). The real campus names are now in `tools/blindcheck.words` on `master`; the mirror will refuse any file that still contains one. Keep the fictional names realistic ("Block B Lab 3", "Central Store") so the demo still feels local.
- **Duplicate type:** your branch still has an old `Compute` enum in `llm/LlmEngine.kt` (copied from an early C commit). C deleted that file in `d13ac07`. Merging the latest `c/core-rules` removes it. Do not add a third copy; the one that stays is `testing/Contracts.kt` from the speech branch.

## 4. Do not build these (someone else has them)
- **The follow-up planner, report merge and storage:** the lead's other session built interim versions in a local, unpushed branch. Do not redo them. When it is pushed, review it and improve it only if you find a real gap.
- **Telugu:** skip it.

## 5. If time runs short, cut in this order
role rule, then hedge chips, then fuzzy matching (**keep the homophone and number table and the "never blocks Submit" rule**).

## 6. Hygiene
Merge, never rebase. Stage explicit paths only and run `git diff --cached` before every commit. Do not edit the Gradle files or C's shared types without telling the owner. Questions to the lead: add a file `docs/messages/D-to-lead-<n>.md` on your branch.
