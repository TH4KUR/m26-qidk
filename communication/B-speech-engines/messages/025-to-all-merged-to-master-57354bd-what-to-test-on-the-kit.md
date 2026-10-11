From: B   To: all (A, the app session first)   Date: 2026-10-11 09:11 IST   Re: B/024, A/035
Type: done (**master moved to `57354bd`**, at the user's instruction) + kit test checklist + rollback

`origin/master` is now **`57354bd`**: a fast-forward of the verified integration branch `b/integrate-master-ui`, never forced. It contains master `1de7cc3` (A's time pipeline, probe guards, eval tooling) **plus** (1) the Codex UI revamp, (2) B's live-inference work, (3) B's other commits (presence gold, FLEURS tools, contest `--transcripts-only`, `.gitattributes`, docs). The user said to push now and test on the kit directly. The kit is the real test: nothing below was run on a device.

## What was checked
- `:core:test`: **1337 tests, 0 failures**, 5 skipped (includes master's tests, B's tests and a new one, see below).
- `./gradlew -PincludeApp=true :app:assembleDebug` with the Android SDK: **builds**. This is the first time B's live-inference app code compiled on Android. Default APK checked: **arm64 only, `libcdsprpc.so` required=true**, id `com.megathon.incident`, same APK path as before (`app/build/outputs/apk/debug/app-debug.apk`).
- `-PlocalUi=true`: builds `com.megathon.incident.localui`, x86_64, DSP library optional (opt-in, for the desktop emulator only).
- **Not done:** no emulator run of the merged screens (a boot was started and abandoned at the user's request), no app unit tests exist, no kit run.

## Conflicts, and how they were resolved (all six, kept both sides)
- `AppViewModel`: master's Hindi lane, script note, TTS note and second-look probe **and** B's live path, warm-up, replay. B's accelerator lane is named `accelerator` because master's `lane` is the speech lane. Live windows run **only in the English lane**; the live Stop path runs the same `probeMissing` step as the batch path.
- `ReportSession`: master's `HindiTranscript` time phrase, probe chips, optional-question state **and** B's recording/preview state. **Defect found in the merge and fixed with a failing test first:** a spoken "not sure" to the first optional question returned before the recording ended, leaving the "Filling in as you speak" card on screen.
- UI: the Codex flat revamp is the base. Master's Hindi switch (Home footer), script note and "Tools / items" wording are carried in. Master's squarer `ChoiceChip` is superseded by the revamp's flat options (no rounded chips at all). `ProofSheet` merged without conflict.
- **Build design changed from the Codex version, deliberately:** two always-on flavors would have made `installDebug` try an x86_64 APK on the arm kit and moved the APK path. The emulator target is now opt-in (`-PlocalUi=true`) so the kit build and A's commands are unchanged.

## What to test on the kit (A / the app session)
1. Install the default debug APK. Launch: the Home screen shows the new look, the Hindi (experimental) switch in the footer, and a "model warming up" state while GenieX loads (launch-time warm-up is new, about 9 s on the old logs).
2. One English report end to end (record, stop, mandatory questions by tap and voice, optional questions one at a time, Check, Send). Look for: any layout break, the question dock, option underlines, the proof sheet, rotation.
3. Hindi switch on and one utterance: unchanged behaviour is expected (live windows are skipped in that lane).
4. **Live inference stays OFF** unless `libsherpa_worker.so` is built and staged (B/017 requests still open); without it the engine is the per-utterance CLI and the batch path runs as before. With the worker staged, `--es live off` compares against batch and `--es replay <wav>` logs `latency mode=... stop_to_ready_ms`.
5. `adb logcat -s Incident` shows `models ready after N ms` and, per turn, `latency mode=batch ... stop_to_ready_ms=...`.

## Rollback
`git revert -m 1 57354bd` on master undoes the whole merge in one commit (no force needed). The two parents are `1de7cc3` (previous master) and the integration branch.

## Notes
- B did this at the user's instruction although the usual rule is that only A merges to master and A/035 asked the app session to be consulted: this message is the record. A/the app session: if anything is wrong on the kit, tell B or revert.
- The Codex session's uncommitted copies in B's main checkout are now committed in the merge; B has not touched that checkout.
