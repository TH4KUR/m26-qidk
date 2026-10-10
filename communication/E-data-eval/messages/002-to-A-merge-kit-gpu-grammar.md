From: E   To: A   Date: 2026-10-11 00:45 IST   Re: docs/messages/lead-to-E.md
Type: request

Read your lead-to-E. Status against it: grammar-mode runs done (dev recall 74/98, mandatory 55/61, valid calls 19/20, hallucination 16/62, mostly persons/witnesses); Kotlin scorer done; recordings and GPU runs not yet. Requests:
1. **Merge `b/speech-engines` and `c/core-rules` into `master`**, and push `a/stubs` when you can. My `core/.../eval/EvalScorer.kt` and `testing/FakePipelineRunner.kt` need C's types and B's `testing.Compute` (504/504 tests on an export of B's branch + my files). I am **not** writing my own "+grounding" replay: I want the Judge Bench to run contest outputs through your `Pipeline` + `ReplayLlmEngine`, so there is one merge implementation.
2. **Kit slot for recordings**, about 40 minutes, ideally 03:15–04:00 after the M2 push, or earlier if you prefer. The stock recorder saves 8 kHz AMR; I need 16 kHz mono WAV. Please confirm the `tinycap` card/device numbers (or a recorder mode in the app) and which mic the demo uses.
3. **GPU:** who has the GPU-machine login? If nobody, can I run llama-server on your RTX 4060 (Vulkan build in `~/dev-tools/llama.cpp`)? CPU is about 7 s per call, too slow for Qwen3-4B / Gemma / NuExtract on 27 items.
4. **App grammar must match mine:** `tools/laptop_eval/prompts/update_report.schema.json` (v2: evidence values `minLength 1`, strings `maxLength 120`). C's parser rejects `""`, `null` or `[]` evidence (verified on C's Kotlin), and the old schema let `""` through. Without the same limits in the app's GBNF, my numbers overstate validity.
5. **Decisions I need:** (a) the demo sentence (D proposes "Seminar Hall 2", which is fictional-safe); (b) the undated-time rule (message 003).
6. **M2:** draft will follow your 03:00 target; it uses only measured numbers (your section 3 table plus our runs).
