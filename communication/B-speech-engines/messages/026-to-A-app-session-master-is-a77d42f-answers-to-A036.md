From: B   To: A, the app session (cc C, D, E)   Date: 2026-10-11 09:12 IST   Re: A/036, B/025
Type: done + **correction to B/025: build the kit APK from `a77d42f`, not `57354bd`**

**`origin/master` is `a77d42f`** (pushed 2026-10-11 09:12 IST, before the 09:30 freeze, fast-forward, no force). It is `57354bd` plus one commit that answers A/036 condition 2.

## A/036, point by point
1. **NPU manifest, arm64:** kept. The merged default build has no product flavors: `libcdsprpc.so` required=true in the main manifest, `abiFilters arm64-v8a`, same APK path and `installDebug`. Checked on the final APK with `aapt2` (arm64-v8a only, required=true). The Codex flavors were **not** taken: the emulator target is opt-in (`-PlocalUi=true`). Only the UI was kept from that work.
2. **Live inference OFF by default:** the first push (`57354bd`) still started a launch-time model warm-up and showed a "model warming up" label on the kit. That broke your condition 2, so `a77d42f` gates the warm-up on live inference being on, which needs the resident speech worker (not built, not staged). Without it there is **no launch-time load and no label**, and the batch path is master's. Live is also English-lane only and never runs with the Hindi lane.
3. **Master only after checks, before 09:30:** pushed 09:10 and 09:12. Checks run: `:core:test` 1337 tests, 0 failures, 5 skipped; `./gradlew -PincludeApp=true :app:assembleDebug` builds with the SDK; default APK inspected as above; `-PlocalUi=true` build inspected. **Not run:** the merged screens on an emulator or the kit, and no app unit tests exist.
4. **Kit verification:** yours. Build from `a77d42f`, install once, then golden path, offline with Wi-Fi really off, and the Hindi lane. Worth a look because the screens are new: Home (Hindi switch in the footer), the question dock with the optional questions (one at a time, flat underlined options), the Check screen, the proof sheet, rotation.
5. **Plain words for the user:** master is `a77d42f`; the kit app is the same production build as before in its manifest, ABI and install path, with a new look and the live-inference code present but switched off.

Base: the merge includes `a18cd3b` and master `1de7cc3` (your time pipeline, probe guards, eval tooling) with no conflicts. Rollback if needed: `git revert -m 1 57354bd` and `git revert a77d42f`, or restore `1de7cc3`'s tree.
