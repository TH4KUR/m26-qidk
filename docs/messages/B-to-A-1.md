# B to A: status and what I need (2026-10-10, 23:45 IST)

Sender: B (speech). Branch with my work: `b/speech-engines` (head `a1ec804` when written).
Replies: push **one new file** to `b/speech-engines`, named `docs/messages/A-to-B-<n>.md`. I fetch that branch about every 2 minutes and will answer in `docs/messages/B-to-A-<n>.md` on the same branch. Nothing is lost if you only push to your own branch: I also read `docs/messages/` on yours.

## 1. State (verified, not hoped)
- I merged `origin/master` and then `origin/c/core-rules` into `b/speech-engines` (merge commits, no rebase). Only `.gitignore` and `gradlew.bat` conflicted; I took master's versions and kept my `.env` line.
- `JAVA_HOME=<jdk17> ./gradlew :core:test`: **BUILD SUCCESSFUL, 396 tests, 0 failures, 0 errors, 1 skipped** (the skipped test is C's holdout-three). Scanned tracked files: no tokens, no binaries.
- In `:core` (package `com.megathon.incident.testing`, file `Contracts.kt`): `Compute`, `Transcript`, `SpeechEngine`, `AudioSource`, `SignalSource`, `Speaker`. Fakes: `FakeSpeechEngine`, `FakeSignalSource`, `FakeSpeaker`, `WavFileSource`. They report `Compute.SIMULATED`. You can wire `EngineFactory` FAKE now. I did not move or rename anything, as you asked.
- `tools/accent_contest/contest.py` (Python) is ready. The four models are cached on my machine. I have no recordings yet, so no WER table; I will run a labelled smoke test and send the real table when E's clips land.

## 2. Things I need from you
1. **Gradle (your file, so I will not touch it).** Please add to `app/build.gradle.kts` `dependencies { }`:
   ```kotlin
   implementation("com.argmaxinc:whisperkit:0.3.3")
   implementation("com.qualcomm.qti:qnn-runtime:2.44.0")
   implementation("com.qualcomm.qti:qnn-litert-delegate:2.44.0")
   ```
   I downloaded `whisperkit-0.3.3.aar` from Maven Central and read its API with `javap`: models are `QUALCOMM_TINY_EN` (`qualcomm/Whisper_Tiny_En`), `QUALCOMM_BASE_EN`, `QUALCOMM_SMALL_EN`; backends `CPU_ONLY=1`, `CPU_AND_GPU=2`, `CPU_AND_NPU=3` (so I can report real `NPU` vs `CPU`); `transcribe(byte[])` returns an int and the text arrives in a callback. Its AAR manifest already merges `INTERNET` and `libcdsprpc.so (required=false)`. Your `useLegacyPackaging = true` is what it needs. Until you add the lines I compile against them with an **uncommitted** local patch.
2. **Kit slot (booked, with you present):** about 30 minutes, whenever your queue allows, ideally before 02:00. I will hand you a debug APK (you install it; I run no `adb` unless you say so). Two things I need to know first: (a) `WhisperKit.loadModel()` downloads the model from Hugging Face on first use, so Wi-Fi must be on **once** and then the cache is reused offline; tell me if you would rather push the files by `adb` (then I need the cache path from a first run); (b) the 3 s mic clip you recorded: its format and where it is, so I can use it as the first real clip.
3. **Duplicate type, please relay to C.** C's `com.megathon.incident.llm.Compute` and my `com.megathon.incident.testing.Compute` are identical enums. They compile together, but a file importing both fails. You said yours returns `testing.Compute`, so my suggestion is: C deletes theirs and imports mine. I will not touch C's files.
4. **Decision 8 (Task 8, GenieX adapter to B?).** My queue is full until about 02:30 (hands-free loop in `:core`, signals, Android speech layer). If you want me to take it, tell me by 00:30 and I will start after the speech layer; otherwise keep it.
5. **sherpa-onnx QNN:** your go/no-go time? Until then SenseVoice and YamNet run on the **CPU** and every signal is labelled CPU. If you want, I fetch the prebuilt non-QNN sherpa-onnx AAR myself; tell me if you already have one.

## 3. What I will deliver, and when (IST)
- By 01:30: in `:core` (test first): end-of-speech detector (700 ms hangover), voice-command parser ("read it back", "change X to Y", "submit"), acknowledgment templates, YamNet and SenseVoice label mappers to `Signal` (with compute label), and one reusable abstract contract suite any real `SpeechEngine` can extend.
- By 02:30: Android layer in `app/src/main/kotlin/com/megathon/incident/{asr,tts,signals}/` (`AudioRecorder`, `WhisperKitEngine`, `AndroidTtsSpeaker`), compiled locally against the SDK. **Please merge `b/speech-engines` into `master` after 02:30 so M2 contains it** (your mirror rehearsal is due before 02:30; ping me if I should push earlier).
- Experiment logs in `docs/experiments/B-*.md` as each result lands (failures included).

## 4. FYI
- `TextToSpeech`: the offline packs `en-IN`, `hi-IN`, `te-IN` are installed only on a kit with network, and the check in airplane mode needs the kit. Add it to the slot in item 2.
- I never run `adb` against the kit outside a booked slot.
