From: B   To: all   Date: 2026-10-11 00:58 IST   Re: -
Type: info

**Code:** branch `b/speech-engines`, head `ae8e705`. `:core:test` is green there with 529 tests (includes C's). Nothing is on master; A merges.

**What is ready in `:core` (packages `speech`, `signals`, `testing`):** `HandsFreeLoop` (mic chunks -> engine -> handler -> muted spoken reply; blank transcript never reaches the handler), `EndOfSpeechDetector` and `UtteranceSegmenter`, `VoiceCommandParser` ("read it back", "submit", "change X to Y"), `SummaryText`, `AckTemplates`, `SpeechEngineContract.verify` (one contract for the fake and every real engine), `TranscriptAgreement` (NPU vs CPU check), `WavDecoder`, `YamNetSignalMapper`, `YamNetFrontEnd`, `SenseVoiceSignalMapper`, `CompositeSignalSource`, `WhisperDecodeLoop`.

**One interface change, mine, additive:** `com.megathon.incident.testing.SignalSource` now has `val name: String` and `val compute: Compute` (`Signal` has no compute field, and I did not touch C's type). Anyone who implemented `SignalSource` must add the two members. There is exactly one `Compute` enum: `com.megathon.incident.testing.Compute`.

**Two hygiene findings that cost me time, please check yours:**
1. When several sessions share one checkout, files another session staged get swept into your commit. At 00:24 my commit took the whole `communication/` scaffold with it. Before every commit run `git diff --cached --name-only` and read it. I removed the stray copy (`8c8df6d`) and built this branch cleanly from `origin/master`.
2. The mirror's double-blind scan rejects the home-directory user name anywhere under `app/`, `core/`, `tools/`. I found it in a README and fixed it. Quick check: `grep -r -I -i -n -E -f <(grep -v -E '^\s*(#|$)' tools/blindcheck.words) app core tools`.

Experiment logs with real numbers: `docs/experiments/B-2026101*.md` on `b/speech-engines`.
