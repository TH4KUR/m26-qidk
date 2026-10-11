# Hindi lane (Nemotron-3.5 ASR) and Supertonic TTS on the kit, 2026-10-11

Status: EXPERIMENTAL. One real Hindi clip, one speaker. These are working checks, not accuracy measurements.
Build: a/stubs (local), APK `0f90dcc3c56a1810` for the ASR runs, `1aded89bc7b357dc` for the TTS runs. Kit: the HDK8750 (SM8750P).
Raw logs are in the session scratchpad, outside git (they contain the user's voice transcript only, no names).

## 1. Hindi speech recognition, in the app, on the NPU

Model: `sherpa-onnx-qnn-SM8750-binary-nemotron-3.5-asr-streaming-0.6b-1120ms` (context binaries, QNN 2.40), run as a child
process of the ONLINE `sherpa-onnx` CLI (`--provider=qnn --model-type=nemo_transducer --language=hi`). Files checked by SHA-256
on the device after the push.

Input: the user's recording `hi_01` (7.19 s after conversion to 16 kHz mono; mean level -24.7 dB; the source AAC had one damaged
packet that ffmpeg skipped). Not committed.

| Check | Result |
|---|---|
| Transcript, 4 runs + 19 coexistence loops | identical every time: `आज सुबह दस बजे कमरे में पानी लीक हो गया।  हमने वाल बंद कर दिया` |
| Against the sentence the user meant | one word differs (वाल for वाल्व, "valve"): 1 of 15 words, n=1, NOT a WER measurement |
| Compute | NPU (QNN provider), child exit 0 every time |
| ASR time per call (includes process start and model load) | 4.6 to 5.2 s on a fresh launch, 2.9 to 3.3 s in the coexistence loops (median 3.1 s) |
| Coexistence, Nemotron child + Qwen3-4B in one app, 19 loops | 19 of 19 ok, 0 errors; turn 7.1 to 8.4 s (median 7.3 s); app memory 222 to 341 MB (median 271 MB); the loop 20 was cut by the user using the kit |
| English clip sent through the Hindi lane (n=1, 3.8 s) | empty transcript (no text, no crash). English words spoken inside Hindi may be dropped. |
| SenseVoice tags on the Hindi audio | `neutral` / `Speech`. SenseVoice is not trained on Hindi: treat as meaningless. |

## 2. What the rest of the app did with the Hindi sentence (4B model, prompt v11)

Model reply: `incident_type: water_leak_flood`, `location: कमरे`, and `immediate_actions: वाल बंद कर दिया` written only inside
`evidence` (the known evidence-only quirk), no time. On screen: "What happened: Water leak or flood", "Where: कमरे", and
"When: Needed" with a "Just now" chip. So the time in "आज सुबह दस बजे" (this morning at ten) is not understood: the model did
not emit it and the temporal resolver is English-only. The action was dropped (the unmerged recall patch addresses that).

## 3. Supertonic 3 TTS (Hindi and English), CPU

Model: `sherpa-onnx-supertonic-3-tts-int8-2026-05-11`, 145,295,768 bytes in seven files, SHA-256 verified on the device. Licence:
model weights OpenRAIL-M, sample code MIT (Supertone). Run in-process through sherpa-onnx, 4 threads, 5 steps, speed 1.0, 44.1 kHz.

| Check | Result |
|---|---|
| Model load (first utterance) | 0.67 to 0.97 s |
| Real-time factor (synthesis time / audio time) | 0.26 to 0.39 across 10 voice styles and several sentences |
| Longest case, saved-report summary in Hindi | 3.06 s of synthesis for 11.6 s of audio (4 chunks) |
| Voice styles `--es voice 0` to `9` | all ten run; which are male or female is not known |
| Latin-script place in a Hindi sentence | spoken as two segments: the place by the English voice, the rest by the Hindi voice |
| Full loop (Hindi speech, 4B extraction, follow-up spoken in Hindi) | works; the follow-up was spoken with `lang=hi` |

Note: the logged `firstAudioMs` includes the time the audio buffer blocks while a long chunk plays, so it overstates the time to
first sound for long chunks; use load time plus the first chunk's synthesis time instead.

NOT measured: how natural the voice sounds (nobody has judged it except for "slow sounded better", which is why the speed stays
at 1.0), Telugu (the model does not cover it), the Hindi wording by a native reader.

## 4. Hindi wording

`HindiPhrases` (core) translates the app's spoken English sentences: the planner questions, the advisor's key question and the 112
line, severity words, and the saved-report summary (times such as ढाई बजे, dates with Hindi day numbers). The reporter's own words
are never translated; a sentence it does not know stays English. The wording was written by the assistant and has NOT been read by a
Hindi speaker.
