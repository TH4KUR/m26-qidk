From: D   To: A   Date: 2026-10-11 05:55 IST   Re: A/023, A/019
Type: done (T1, T2)

`d/core-suggestions` **e0079cb** is pushed and ready to merge. It contains master `a2b68d2` (fast-forward, merged, no rebase) plus two commits:
- **6918d15** T1: `docs/submission-readme-build-run.md`
- **e0079cb** T2: `MissedFieldRules` + tests + dev measurement + log

**Tests** (JDK 17.0.20 Temurin, Windows/Git Bash, `./gradlew :core:test --no-daemon`; `JAVA_HOME` was unset because the Linux path does not exist on this laptop, and `java` on the PATH is 17):
- Master before my work: 893 tests, 0 failures, 2 skipped. You saw 3 skipped on the same tree, so one skip depends on the environment.
- At e0079cb: **920 tests, 0 failures, 3 skipped**. The new skip is my env-gated measurement.
- English tests untouched.

**Blind scan:** `tools/mirror_to_submission.sh` cannot run here because this laptop has no `rsync`. Instead I ran its steps 5-6 by hand on HEAD e0079cb: the same `git archive` paths, the same strip list, and the same `blindcheck.words` scan of contents and file names. Result: **clean, 302 files**. My two docs (not mirrored) were scanned separately: clean. **Please run the real dry run when you merge.**

## T1: README Build/Run draft (`docs/submission-readme-build-run.md`)
- The replacement text for both sections, then "verified by running" and "not verified" lists.
- Every command is copied from `settings.gradle.kts`, `app/build.gradle.kts`, `app/scripts/*.sh` or section 4 of the app handoff on `a/stubs`.
- **Verified by me:** only `./gradlew :core:test`.
- **Not verified:** `assembleDebug` and the APK path, `adb install`, `am start`, and both bundle scripts. I have no SDK and no kit.
- **One conflict with A/023:** you wrote "the LLM preloads at launch". I found **no launch-time preload** on `a/stubs` `a48e746` or any other branch. `GenieXLlmEngine.warmUp()` is only called by the eval and coexistence runners; otherwise the model loads lazily on the first call (your handoff: "the first turn adds about 9 s"). So the draft says "loads on the first use, several seconds". If a preload lands, change step 5.
- **I did not read the submission README** (I don't touch that repo). The draft refers to its weights table as "Model weights": check that heading.

## T2: chips for equipment and actions the model did not emit
- **Where:** `suggest/MissedFieldRules.kt`, called by `RuleSuggestionEngine`. It gets a new **optional** constructor parameter `tam: TamDetector`, defaulting to the bundled cue lists, so the app's existing constructor call compiles unchanged and the chips appear with no app change.
- **What it does not touch:** the `SuggestionEngine` interface and C's files.
- **When it fires:** only when the field is MISSING. It offers **one chip per field**, `original = ""`, `Source.RULE`, and never fills anything. A decided chip is not offered again (journal behaviour, tested).
- **Equipment rule:** nouns from a general lexicon I wrote; how it was made is in the file header. Skipped when C's `TamDetector` marks the clause negated, hedged or planned, when the noun is inside the location value, when it comes before a place word ("printer room"), or when it is inside the speaker's own action phrase (a tool used to respond).
- **Actions rule:** I/we + perfect, progressive or simple past of a response verb, asserted only, trimmed from the verb.
- **Tests:** `MissedFieldRulesTest`, 26 tests, written first and watched fail.
- **App note (your file, not changed):** `SuggestionChips.kt` will show these as "Change to “ladder”?". "Add “ladder”?" would read better when `original` is empty.

**Numbers.** Dev split, n = 27 items, authored text, rules written after reading A/023's examples (which come from dev items). Run 2 came after one rule change made by looking at run 1. **These are not results on unseen data.**
| field | right chip | wrong chip | missed | false alarm | right no-chip |
|---|---|---|---|---|---|
| equipment_asset (run 1) | 11 | 4 | 4 | 2 | 6 |
| equipment_asset (**run 2, shipped**) | 11 | 3 | 5 | 1 | 7 |
| immediate_actions (runs 1 and 2) | 13 | 0 | 8 | 0 | 6 |
- I looked at dev results 2 times and changed 1 rule ("a tool the speaker used to respond is not the equipment involved"; it turned 1 false alarm into a right no-chip and 1 wrong chip into a miss).
- Setup: state = gold location as REPORTED, equipment and actions MISSING. Matching = E's `score.py` rule (content tokens, dice >= 0.8, value or alts), ported into the harness.
- **Holdout was never read:** the harness drops those rows first.
- Log: `docs/experiments/D-20261011-missed-field-chips.md`.

**Honest limits** (also in the log):
- **These chips do not raise the model's measured recall.** The scorer counts only REPORTED, USER_CONFIRMED and USER_EDITED, so an unaccepted chip is invisible to it. They help the person using the app.
- The input was authored text, not Whisper output. English only.
- The held-out run is E's, once.

**For C (information only):** 3 of the 5 equipment misses come from `TamDetector`'s clause scope, which does not split on commas or "and": "no power ... and the UPS ..." reads as negated, and the self-correction "no wait" reads as negation. I did not change C's files.

**Next:** the Hindi/Telugu items of A/020 (script-aware aliases, spoken numbers, the 40-item draft set, the gap list for C), target 08:00-08:45. T3 only if those are pushed.
