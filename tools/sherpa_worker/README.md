# Resident sherpa-onnx speech worker

**Status: source only. Not compiled, not run on the kit.** It needs the NDK build that produced the current `libsherpa_offline.so`.

Why: `SherpaCliSpeechEngine` starts `sherpa-onnx-offline` for every utterance, so every call pays recognizer creation (model/context load). Merely keeping the Kotlin engine object alive keeps nothing: the executable exits after each file. This worker creates the recognizer once and decodes many WAV files, so live (incremental) transcription can decode a window every few seconds without reloading.

It stays a **separate process** on purpose: sherpa's QNN **2.40** libraries and GenieX's **2.45** libraries must never share an address space. Moving speech into the app process (the untested JNI route in `tools/sherpa_patch/README.md`) is not part of this work.

## Pinned build

k2-fsa/sherpa-onnx commit `9db1af1871ed78cee9357c23898e5a7d8702d21a` (2026-10-09), the revision the kit CLI was verified against. `worker.cc` uses `OfflineRecognizerConfig::Register/Validate`, `OfflineRecognizer::CreateStream/DecodeStream`, `ReadWave` and `OfflineStream::GetResult().text`, all present at that revision.

```sh
tools/sherpa_worker/build_worker.sh /path/to/sherpa-onnx      # copies worker.cc, adds the CMake target
cd /path/to/sherpa-onnx && SHERPA_ONNX_ENABLE_QNN=ON ./build-android-arm64-v8a.sh
app/scripts/speech_bundle.sh stage                            # packs it as libsherpa_worker.so (also stages the CLI, kept as the debug baseline)
```

## Protocol (v1, JSON lines)

stdout carries protocol only; stderr carries sherpa's logs and is drained continuously by the app, which also reads the Hexagon evidence from it.

| direction | line |
|---|---|
| worker → app, after the recognizer exists | `{"v":1,"ready":true,"init_ms":N}` |
| worker → app, config/creation failed (exit 1) | `{"v":1,"ready":false,"error":"..."}` |
| app → worker | `{"v":1,"id":N,"cmd":"transcribe","wav":"/abs/path.wav","language":"en"}` |
| worker → app | `{"v":1,"id":N,"text":"...","decode_ms":N}` or `{"v":1,"id":N,"error":"..."}` |
| app → worker | `{"v":1,"id":N,"cmd":"quit"}` (stdin closing also ends it) |

Flags are the CLI's, minus the wave file: `--provider=qnn --tokens=... --whisper.qnn-backend-lib=... --whisper.qnn-system-lib=... --whisper.qnn-context-binary=enc.bin,dec.bin --whisper-language=en --whisper-task=transcribe`. The language is fixed when the worker starts; a request for another language is an error.

Kotlin side: `core/.../speech/ResidentWorkerClient.kt` (protocol, ids, timeouts, restart; JVM-tested with a fake channel) and `app/.../speech/ResidentSherpaSpeechEngine.kt` (process launch, WAV hand-off). The compute label is `NPU` only if the worker's own log shows the Hexagon provider started (`HtpProvider`), as for the CLI.

## Kit checks still to do (not done)

- one `recognizer created` log line per worker lifetime across ≥20 requests; same transcripts as the CLI on the fixed clips
- worker PID, app + child memory, thermal state and latency drift over ≥20 requests
- clean exit when the app releases it; no orphan process after the app is killed (stdin EOF)
