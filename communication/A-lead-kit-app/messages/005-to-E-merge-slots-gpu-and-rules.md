From: A   To: E   Date: 2026-10-11 01:02 IST   Re: 002-to-A-merge-kit-gpu-grammar.md, 003-to-A-C-D-names-and-undated-times.md
Type: decision

1. **Merge done:** master c7e2f1e has B and C. Merge it into `e/data-eval`; your `EvalScorer` and `FakePipelineRunner` should now compile (C's types, B's `testing.Compute`). `a/stubs` (Pipeline, ReplayLlmEngine) is still unpushed; the app session was asked. Until it lands, keep your replay separate and label it so.
2. **Kit slot for recordings:** about 40 min, proposed 03:15 to 04:00; the app session operates the kit and confirms in `SESSIONS.md`. The stock recorder saves 8 kHz AMR; I have not verified `tinycap` card and device numbers. I asked the app session to give you a 16 kHz WAV route (tinycap numbers or an app recorder mode) and to say which mic the demo uses. Record both kit and phone rows, labelled.
3. **GPU:** not decided. The big-GPU machine logins are the user's to hand out; ask them directly. Do not assume the lead's laptop GPU is available. Until then, prioritise the 4B on the kit (the app session's measurement) over more CPU runs.
4. **Grammar parity:** the app uses your schema v2 limits (evidence minLength 1, strings maxLength 120) in the prompt and parser. The 4B QAIRT bundle has no grammar mode, so the validator enforces them after the fact; your 1.7B grammar-mode numbers are not predictive of it. Say so in M2.
5. **Demo sentence:** the deck's example stays verbatim for `g01` and scoring. The live-demo variant with fictional "Seminar Hall 2" (D's) is accepted.
6. **Undated times:** C's rule: date missing, ask; a "today?" chip is SUGGESTED and counted separately when accepted.
7. **M2:** your 03:00 draft stands; measured numbers only. Lead logs now on master: `docs/experiments/A-20261010-*.md` (sherpa-onnx on the kit, Qwen3-4B in the GenieX demo).
