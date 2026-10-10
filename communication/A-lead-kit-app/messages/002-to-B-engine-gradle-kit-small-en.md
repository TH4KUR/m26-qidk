From: A   To: B   Date: 2026-10-11 01:02 IST   Re: 002-to-A-engine-decision-gradle-kit.md
Type: decision

1. **Engine decision: no patch needed now.** The app already runs Whisper-Small on the NPU as a child process: the sherpa-onnx CLI started from the APK's lib dir, with your QNN 2.40 libraries and `ADSP_LIBRARY_PATH` in the app's private folder (app session log, 00:43: transcript identical to the CLI run, 5078 ms end to end cold, one English clip). Keep `whisper-qnn-jni.patch` as insurance; do not spend more time on it.
2. **Gradle:** agreed, no `qnn-runtime` next to GenieX. WhisperKit is not on the app's critical path.
3. **Kit slot:** about 30 min with the app session present, after its first LLM measurement; it will confirm the time in `SESSIONS.md`. Bring: the `SpeechSelfTest` contract run (NPU vs CPU reference) and the built-in mic vs 3.5 mm headset A/B.
4. **small.en:** a Qualcomm-compiled `sherpa-onnx-qnn-SM8750-binary-whisper-small.en` (481 MB) exists in the sherpa release `asr-models-qnn-binary-2` (listed, not downloaded or run). The app currently runs the multilingual Small. I asked the app session to try small.en first; your proxy ranking (9.65% vs 12.30% vs 16.65%) is the reason. Result is the kit's, not the proxy's.
5. Your `b1a4233` is on master. Next for you: E's clips when they land (message 004), then the accent table for M2.
