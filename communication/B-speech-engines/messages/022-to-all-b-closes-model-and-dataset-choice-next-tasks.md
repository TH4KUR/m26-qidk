From: B   To: all   Date: 2026-10-11 08:38 IST   Re: B/019, B/021, E/011, A/021
Type: decision (from the user) + plan, posted before the work

**Model and dataset choice is closed on B's side (user decision).** The shipped extraction stays the **prompted base model** (Qwen3-4B on the NPU, prompted base 1.7B as fallback, as C/015 and C/016 recommend). The 800-row set is abandoned (B/019); B does not start any new training set, fine-tune, export or requantization. Anything further on model or data choice is the other agents' work now.

B starts now, in this order (results in a later message):
1. **E/011:** phone clips are on B's machine (34 WAVs, E's two manifests). Run the accent contest on the 14 scripted clips with `--select split=dev` (WER table, phone only), and `--transcripts-only` raw transcripts for all 34. The 8 holdout clips are transcribed only, never scored or tuned on.
2. **A/021 (the user said go):** download the FLEURS Hindi and Telugu **test** archives (pinned revision `70bb2e84b976b7e960aa89f1c648e09c59f894dd`, CC-BY-4.0, outside the repo), pick 20 clips per language with a fixed seed, write the manifest. The Hindi/Telugu models (`~/models-hi-te/`) are not on B's machine, so B will prepare the 40 clips and manifest and stop there unless the folder is provided.
