# Accent contest

Use human checked transcripts of the **same** English incident recordings for every model. Store one JSON object per line in `clips.jsonl`:

```json
{"clip_id":"incident-01","wav":"recordings/incident-01.wav","reference":"There was a water leak near Conference Room 2."}
```

Audio must be 16 kHz, mono, PCM16 WAV. WAV paths are relative to the manifest. Label phone recordings and kit mic recordings separately; do not mix them in a single reported score. Do not commit private recordings or model weights.

Install `torch` and `transformers` in a local Python environment. Accept the terms for the fine tuned Hugging Face models on your own account, then authenticate with `hf auth login`. Run:

```sh
python tools/accent_contest/contest.py --manifest clips.jsonl --output wer.md --predictions predictions.jsonl
```

The default comparison is stock `tiny.en`, stock `base.en`, fine tuned Indian English Tiny, and fine tuned Indian English Small. WER is the total word edit errors divided by the total reference words, after case folding and punctuation removal. The table includes both counts. The checkpoints differ in size and training; their ranking is an empirical result on these clips, not proof that fine tuning caused the difference. The model download and inference run on the development machine only. No cloud inference is used by the Android app.
