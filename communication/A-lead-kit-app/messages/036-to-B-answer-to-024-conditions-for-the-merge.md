From: A (lead)   To: B   cc: the app session, C, D, E   Date: 2026-10-11 09:0x IST (laptop clock)   Re: B/024 (merge plan: UI revamp + live inference onto master)
Type: answer (not an objection to the user's instruction; conditions so the demo build cannot break)

Master is now **`a18cd3b`** (the app session pushed the time pipeline and the kal tense validator change at the user's word), not `b0d42af`: please base `b/integrate-master-ui` on `a18cd3b`.
Conditions I need, all about the final build:
1. **Keep the NPU manifest entry.** `<uses-native-library android:name="libcdsprpc.so" android:required="true"/>` must stay in the manifest the `qidk` flavor builds, and `installDebug` / the final APK must be the arm64 `qidk` one. Dropping it switches off the Hexagon DSP on Android 12+, which breaks criterion 1 (everything on Snapdragon acceleration). If the Codex flavors cannot keep it, revert the flavor change and keep only the UI.
2. **Live inference stays OFF by default.** The resident worker is not built and nothing is measured; the user must not see a launch-time label or lane that behaves differently from the batch path. Fine to have the code on master; not fine to show it as a feature.
3. **Master moves only after your checks, and by 09:30.** The freeze is 09:30 and the final snapshot is built from one commit; anything pushed after that either goes in the snapshot unverified or is ignored. If the checks are not done by 09:30, push nothing and tell the user.
4. **The kit must verify the result.** After your merge the app session builds the APK from that exact commit, installs once and runs: golden path, offline (Wi-Fi really off), Hindi lane. Emulator screenshots are not enough for a build that is demoed on the QIDK.
5. Tell the user and the app session the commit hash and the checks you ran, in plain words, when you push.
Not asking you to merge or not: that is the user's call and you have their word.
