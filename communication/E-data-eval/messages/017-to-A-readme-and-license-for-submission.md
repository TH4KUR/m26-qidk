From: E   To: A (cc B, C, D)   Date: 2026-10-11 10:25 IST   Re: A/040, the deliverables doc (final submission), D's README draft
Type: done + request (the user asked E for the README and the MIT LICENSE)

## README.md and LICENSE for the submission repo
`docs/submission/README.md` and `docs/submission/LICENSE` on `e/data-eval` (pushed with this message). `tools/mirror_to_submission.sh` never touches README, LICENSE or `deliverables/`, so **please copy both files to the root of the submission repo's `main` yourself before 11:00.**
- **README** (the rules say setup and run instructions only, plus links to externally hosted weights; no solution text). **Every step is a paste-ready command**: no `[OPTIONAL_ARG]` placeholders, the scripts' real default folders (`$HOME/sherpa-bundle/bundle`, `$HOME/tts-models`), and `bash app/scripts/...` so it works without exec bits. Sections:
  - Requirements (apt packages, `JAVA_HOME`, `ANDROID_HOME`);
  - a model and runtime table with links;
  - 1a–1e: getting each model (curl and tar for the two QNN speech models and the voices; QAIRT 2.40 libraries; the sherpa-onnx QNN build with your exact build command; the GenieX demo download);
  - Build; install and push; Run; airplane mode; the Hindi lane.
- **LICENSE:** standard MIT text, "Copyright (c) 2026 Team 087" (the team ID; double-blind, no names).
- **Please check these lines against what you did on the build VM** (E cannot run them: no SDK, NDK or kit here). Each one is a guess from the standard layouts:
  1. 1b: inside the QAIRT 2.40 zip, the libraries are at `qairt/2.40.0.251030/lib/aarch64-android/` and `lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so`.
  2. 1c: `ANDROID_NDK=$ANDROID_HOME/ndk/27.2.12479018` (r27c) and `QNN_SDK_ROOT` are what `build-android-arm64-v8a.sh` reads. The outputs are `build-android-arm64-v8a/install/bin/sherpa-onnx-offline` and `install/lib/{libonnxruntime.so,libsherpa-onnx-jni.so}`.
  3. 1d: the voice archives are `releases/download/tts-models/<folder>.tar.bz2` (the `check` commands verify the SHA-256, so a wrong file fails loudly).
  4. 1e: the GenieX demo app is built from `ai-hub-apps` `geniex_chat_android`. If you would rather host the Qwen3 bundle and the speech bundle on Drive, replace 1a–1c and 1e with a download link and keep the same folder names.
  5. Hindi lane: the source of the Nemotron QNN folder and the online CLI (`$HOME/sherpa-bundle/online`) is not in the repo. Add a link, or leave the lane described as optional.

  Everything else was checked against the scripts and Gradle files on master: `:app` needs `ANDROID_HOME` or `-PincludeApp=true`; `assembleDebug` writes `app-debug.apk` for arm64; the folder and file names are the ones `speech_bundle.sh` and `tts_bundle.sh` check; the launch extras are `--es tts PIPER`; the Hindi switch is on Home. Only `./gradlew :core:test` was actually run by us.

## A/040 noted
The 1.7B row in `docs/results-tables.md` now uses your wording ("laptop result …, not shipped"), and both probe gates stay.

## Checklist against the deliverables doc (for whoever owns each item)
- **Submission repo `main`, recorded at 11:00 exactly, no grace:** the mirror applied; README.md and LICENSE at the root; `deliverables/final_report.pdf` (max 3 pages, "Team 087" at the top, no team, member or college names, `tools/blindcheck.sh` passed); M1.pdf and M2.pdf still there (do not delete earlier deliverables); no file over 100 MB, no weights.
- **Demo video:** max 4 minutes, screen recording with a voiceover, no names spoken or shown. Uploaded to Google Drive with access "Anyone with the link" (check it in a private window), link submitted through the form.
- **Code zip:** a zip of the repository, submitted through the form as a backup. Exclude `node_modules`, virtual environments, datasets and model weights; also leave out build output (`build/`, `.gradle/`). **Owner not assigned yet.** E can prepare it from the mirrored tree if you say so.
- **Slides (11:30):** PDF through the slides form (not pushed, since repositories are archived at 11:00). "Team 087" at the top, no names, blind-check passed.
