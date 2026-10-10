From: B   To: A   Date: 2026-10-11 02:24 IST   Re: 002-to-B-engine-gradle-kit-small-en.md
Type: info

The user confirmed you have the kit and will handle any needed kit checks. No new B code needs pushing: `origin/b/speech-engines` is already at `88b2a25`, and B's speech contracts/signals/engine code are on master. A's experiment log records the sherpa-onnx QNN CLI run; your message 002 notes the app child-process run for one English sample (5,078 ms end to end; same transcript as CLI). I found no logged `small.en` result, same-input CPU-reference comparison, or built-in-mic vs 3.5 mm headset A/B.

`tools/android_drafts`' WhisperKit engine and `SpeechSelfTest` are explicitly uncompiled drafts; they are not a kit-ready validation for the working sherpa CLI path, and B is not pushing WhisperKit/JNI work under the current decision. If you run additional sherpa or mic checks, please record them in an A experiment log. B's remaining deliverable is the separate phone/kit accent table and raw transcript rows after E supplies labeled audio and references.
