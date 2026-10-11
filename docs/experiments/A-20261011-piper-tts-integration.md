# Experiment log: Piper TTS as a second speech-output engine

Date: 2026-10-11. Owner: A (app track). Status: **built and compiled; not run on the kit, not heard, nothing measured.** The Android system voice stays the default and the fallback.

## What was built

| Piece | Where |
|---|---|
| `TtsKind { ANDROID, PIPER }`, default ANDROID; `EngineSelection.tts`; `EngineFactory.tts(kind)` | `core/.../tts/TtsKind.kt`, `core/.../engine/EngineFactory.kt` |
| Text normaliser for TTS (112 to "one one two", "Block B-2", times, ordinals, decimals, symbols) | `core/.../tts/TtsTextNormalizer.kt` |
| Sentence chunker (so a stop request is noticed between sentences) | `core/.../tts/TtsChunker.kt` |
| Voice catalogue by language (en, hi; none for te) with expected size and SHA-256 | `core/.../tts/PiperVoices.kt` |
| Model-file locator and verifier | `core/.../tts/PiperModelLocator.kt` |
| Float to PCM16 and duration helpers | `core/.../tts/TtsPcm.kt` |
| Fallback decision and "which engine really spoke" label (`RoutingSpeaker`, `SpokenWith`, `TtsBackend`) | `core/.../tts/RoutingSpeaker.kt` |
| `PiperSpeaker : TtsBackend, Speaker` (OfflineTts + AudioTrack, lazy, cancellable) | `app/.../speech/PiperSpeaker.kt` |
| Android voice adapter (unchanged behaviour) | `app/.../speech/AndroidTtsBackend.kt` |
| sherpa-onnx Kotlin API (one file) | `app/src/main/kotlin/com/k2fsa/sherpa/onnx/Tts.kt` |
| Staging and push script | `app/scripts/tts_bundle.sh` |

Behaviour:

- **Selection.** `--es tts PIPER` (case-insensitive) picks Piper. Anything else, including the existing `--es tts check` probe, means ANDROID. The extra is read in `Engines.selection`, like `speech` and `llm`.
- **Release builds** ask the speaker to verify the model's size and SHA-256 against `PiperVoices` before using it; a debug build only needs the files present. A release build with no verified files speaks with the Android voice.
- **Lazy.** The native library is loaded and a voice is built on the first utterance after PIPER was chosen, never at app start. `UnsatisfiedLinkError` (and any other `LinkageError`) becomes a permanent Piper failure; the Android voice speaks and the reason is logged (tag `Incident`, `tts fallback: ...`) and shown on the proof sheet.
- **Language.** The voice follows the session language (`en` default, `hi`). Telugu has no Piper voice, so it falls back to Android with a reason. In this branch the app's language is fixed to English (`AppViewModel.speechLanguage()` returns `"en"`; the EN/HI/TE toggle is not in this branch). Wire the toggle's value there.
- **Label.** The "Speak" stage on the proof sheet is taken from the engine that actually produced the audio: `Piper TTS (sherpa-onnx, en_US lessac medium)` with `CPU`, or `Android TTS (system voice)` with `CPU`. Piper is always labelled CPU (provider `cpu`; no QNN is configured).
- **Playback.** `speak` returns only when the AudioTrack has played the audio (or `stop()` was called). A new `speak` while one is playing stops the old one first. Text is normalised only on the Piper path; the Android voice receives the exact phrases it always did.
- **Debug extras.** `--es say "text"` (debug builds) speaks the text with the selected engine and logs `tts say engine=... compute=... ms=...`; Piper also logs `piper voice=... loadMs=... firstAudioMs=... synthMs=... audioMs=... rtf=...` per utterance.

## Native library and Kotlin API

- Library: `~/sherpa-bundle/bundle/libsherpa-onnx-jni.so` (5,239,728 bytes), exports `Java_com_k2fsa_sherpa_onnx_OfflineTts_{newFromFile,newFromAsset,generateImpl,generateWithCallbackImpl,generateWithConfigImpl,getSampleRate,getNumSpeakers,delete}` and `GeneratedAudio_saveImpl` (checked with `nm -D`).
- Kotlin source: `sherpa-onnx/kotlin-api/Tts.kt` from the local sherpa-onnx tree (version 1.13.8, file dated 2026-10-09; the library was staged 2026-10-10). The JNI class names in the library (`OfflineTtsModelConfig`, `...Vits...`, `...Pocket...`, `...Supertonic...`, `...ZipVoice...`) and every config field name found in its strings match this file. A field the library expects but this file lacks would fail at runtime with `NoSuchFieldError`; I found none by string comparison, but this was not run.
- Copied unchanged except for a notice at the top. Licence: Apache-2.0 (k2-fsa/sherpa-onnx, copyright Xiaomi Corporation). It must stay in package `com.k2fsa.sherpa.onnx`.
- `NEEDED` entries of the library: `libandroid`, `liblog`, `libonnxruntime`, `libm`, `libdl`, `libc`. No QNN library is linked at load time; the QNN libraries are only opened when the QNN provider is configured, which Piper does not do.
- No keep rules are needed: the release build has no minification configured. If R8 is ever enabled, keep `com.k2fsa.sherpa.onnx.**`.
- No `.so` and no model file is in git. `app/src/speech/` is git-ignored.

## Voice files (downloaded 2026-10-11 from the k2-fsa sherpa-onnx `tts-models` release, only these two)

Archives (SHA-256 matches the digest GitHub publishes for the release asset):

| File | Size (bytes) | SHA-256 |
|---|---|---|
| `vits-piper-en_US-lessac-medium.tar.bz2` | 67,230,653 | `9e3febfacf0abf4270172d2958bcec246032b7e88efc2720840cc80c93de334e` |
| `vits-piper-hi_IN-rohan-medium-int8.tar.bz2` | 21,064,499 | `b306822fe16516d3c882e070a7205d781524bd9e8f2d121c3da3e504afe20e05` |

Total download 88,295,152 bytes. Extracted into `~/tts-models/` (outside the repo; archives kept in `~/tts-models/_dl`).

Files the app uses (these are what `PiperVoices.kt` and `tts_bundle.sh` check):

| Voice | Model file | Size (bytes) | SHA-256 | Sample rate |
|---|---|---|---|---|
| en_US lessac medium (fp32) | `en_US-lessac-medium.onnx` | 63,149,198 | `4ba07d8549906668ee855fd9abf9faf66c5db74742712ff026a159f7277fca9f` | 22,050 Hz |
| hi_IN rohan medium (int8) | `hi_IN-rohan-medium.onnx` | 18,301,724 | `4f2dc5324c762fc676eb65be30b79b65c91848f47c3697ff86de69f3315c5da6` | 22,050 Hz |

`tokens.txt` SHA-256: en `87c8ef66eae5473ed0cc0366b3964c736ca6c5f676c979522ea31234e47430b9`, hi `e8a50ae0c75612d18cfcf3f90800dca79f961e3ed97f9574f3535194469187fd`. Each folder also holds `espeak-ng-data/` (about 355 files, ~15 MB) and a `MODEL_CARD`. On disk: 79 MB (en) and 36 MB (hi).

Not done: **Telugu.** There is no Telugu Piper voice in the sherpa release and none was converted. Telugu output uses the Android voice.

### Licences (from the model cards; I did not download or read the licence texts)

| Voice | Dataset and licence reference | Note |
|---|---|---|
| en_US lessac medium | Blizzard 2013 (Lessac Technologies) data, https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html | Terms not read here. Treat as restricted (research / non-commercial) until a person has read them. |
| hi_IN rohan medium | IITM IndicTTS "Hindi Mono Male", licence https://www.iitm.ac.in/donlab/indictts/downloads/license.pdf; model fine-tuned from the lessac voice | Terms not read here. The model is derived from the lessac voice, so treat the stricter terms as applying. |
| Piper / VITS code | MIT (Piper), Apache-2.0 (sherpa-onnx) | |
| espeak-ng data and code (phonemiser inside sherpa-onnx) | GPL-3.0 | Relevant if the app is distributed. |

For a hackathon demo this is likely fine. For anything beyond a demo, have someone read the two dataset licences first.

## How to run it on the kit (operator)

Voices are already on this laptop in `~/tts-models`. From the repo root:

```bash
# 0. local check, no adb: both voice folders present, hashes match
app/scripts/tts_bundle.sh check

# 1. stage the native libraries into the git-ignored jniLibs folder, then build and install
app/scripts/tts_bundle.sh stage
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=$HOME/Android/Sdk ./gradlew -PincludeApp=true :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2. push the voices into the app's private folder (adb root, chown, restorecon; prints the device-side sha256)
app/scripts/tts_bundle.sh push

# 3. speak with Piper (debug build), and watch the log
adb shell am start -n com.megathon.incident/.MainActivity --es tts PIPER --es say "Is anyone hurt? Call one one two. The leak is in Block B-2 near room 204 at 10:30."
adb logcat -s Incident:I | grep -E "piper|tts"
```

What to look for in the log:

- `piper voice=vits-piper-en_US-lessac-medium ... rtf=...` and `tts say engine=Piper TTS (sherpa-onnx, en_US lessac medium) compute=CPU ... fallback=none` means Piper spoke.
- `tts say engine=Android TTS (system voice) compute=CPU ... fallback=<reason>` means it fell back; the reason says why (files missing, library not in the APK, library failed to load).
- For the normal flow, launch with `--es tts PIPER` and open the proof sheet: "Spoken with" should say Piper and CPU. Say the same text with no `tts` extra to compare against the Android voice.
- To test the fallback: launch with `--es tts PIPER` before step 2 (no voices), or with Telugu once the toggle is wired.

## Risk to test: the sherpa JNI library in the same process as GenieX

The sherpa-onnx library was built with QNN support and brings its own `libonnxruntime.so`. Loading it in the same process as GenieX (which brings its own QNN libraries) is **untested**. ASR deliberately runs as a child process for that reason (`SherpaCliSpeechEngine`). Points in favour: the library does not link QNN at load time, Piper uses provider `cpu`, and it is loaded only when PIPER is selected. Points against: it shares the process with GenieX's runtime and with a 22 MB ONNX Runtime that nothing else in the app loaded before; a symbol or version clash would show up at `System.loadLibrary` or at the first `generate`. If it crashes the process (a native abort, not an exception), the fallback cannot catch it. If that happens, the alternative is a child process like ASR, which needs a `sherpa-onnx-offline-tts` CLI that is not available locally.

Also to check: the APK now packs `libsherpa-onnx-jni.so` next to `libonnxruntime.so`; if GenieX ships a different `libonnxruntime.so`, the packaging step will complain about a duplicate.

## Not done and not measured

- Nothing was run on the kit, and no audio was heard. Voice quality and "more natural than the system voice" are not assessed.
- Synthesis speed (real-time factor), first-audio latency, voice load time, and memory (about 80 MB model files; RSS not measured) are not measured. `THREADS = 2` is the sherpa default, not tuned.
- Whether sentence-by-sentence streaming plays without gaps is unknown; it depends on the real-time factor. The AudioTrack buffer holds two seconds.
- Hindi pronunciation of digits, English words inside Hindi, and Indian place names is unchecked. The normaliser only splits emergency numbers for non-English text.
- English normaliser rules are heuristics with unit tests; real incident phrases may expose gaps (units, abbreviations such as "Dr.", dates).
- Telugu has no Piper voice. The language toggle is not in this branch, so Piper always uses the English voice for now.
- The mic mute in the hands-free loop relies on `speak` returning after playback; the AudioTrack wait loop (position polling, with a timeout) is unproven on the device.
- Release-build verification (SHA-256 of a 63 MB file on first use) was not timed.
- The licence texts were not read.
- Unit tests cover only the pure parts (`:core`). `PiperSpeaker` and the Android wiring are compile-checked only.

## Attribution

`app/src/main/kotlin/com/k2fsa/sherpa/onnx/Tts.kt` is from https://github.com/k2-fsa/sherpa-onnx (`sherpa-onnx/kotlin-api/Tts.kt`), Apache License 2.0, copyright 2023 Xiaomi Corporation. Piper voices are from the Piper project (rhasspy) as repackaged by k2-fsa for sherpa-onnx.

## Voice licences and sources (read 2026-10-11; for the submission README)
| Voice | Sources | Terms |
|---|---|---|
| `en_US-lessac-medium` | model card https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/lessac/medium/MODEL_CARD ; release asset `vits-piper-en_US-lessac-medium.tar.bz2` in https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models ; dataset https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/ | **Read.** The model card lists the model as MIT and says "Trained from scratch". The dataset licence (https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html) is for **Research Purposes only**: clause 1.1 defines them as "only those purposes associated with research and exploration" and excludes commercial purposes, including developing, marketing, selling or licensing voice synthesis or speech recognition products or services; clause 3.2 is a "non-exclusive and non-transferable licence, without the right to sub-licence"; clause 7 bars distributing the Materials without the licensors' written consent. No attribution clause. The text does not say whether a model trained on the data may be redistributed: a legal question nobody here has answered. |
| `hi_IN-rohan-medium` (int8 build in sherpa's release) | model card https://huggingface.co/rhasspy/piper-voices/blob/main/hi/hi_IN/rohan/medium/MODEL_CARD ; dataset "Hindi Mono Male", https://www.iitm.ac.in/donlab/indictts ; licence https://www.iitm.ac.in/donlab/indictts/downloads/license.pdf ; asset `vits-piper-hi_IN-rohan-medium-int8.tar.bz2` in the same sherpa release | **NOT read.** The card lists the model as MIT and the dataset licence as the IITM DonLab PDF above; the fetch of that PDF timed out and a second attempt was stopped by the user. Its terms are unknown. The card says it was "Finetuned from U.S. English lessac voice (medium quality)", so the Blizzard terms above also sit upstream of it. |

Consequences for the submission, as far as they are known: this is a research and demonstration use; the voices are **not** in git (they are staged from `~/tts-models` by `app/scripts/tts_bundle.sh`) and must not be added to the repository or the submitted zip; the README should list the two sources and licences above and say the voices are for research use only; nothing here is cleared for commercial use. **Status of the IITM DonLab terms (2026-10-11, user-reported, not verified by us):** the user has spoken to IITM DonLab and reports that they are making an exception for use in this hackathon, with the written licence to follow. We have NOT seen that licence or any written confirmation, so the terms themselves are still unread; the Hindi voice is used on that basis and the README should say "permission for hackathon use pending written confirmation from IITM DonLab". When the document arrives, record its exact terms here and replace this note. The upstream Blizzard 2013 (lessac) research-only terms above are unchanged and still apply to the English voice and, as its parent, to the Hindi one.
