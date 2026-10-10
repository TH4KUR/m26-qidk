# Accent contest

Use human checked transcripts of the **same** English incident recordings for every model. Store one JSON object per line in `clips.jsonl`:

```json
{"clip_id":"incident-01","wav":"recordings/incident-01.wav","reference":"There was a water leak near Conference Room 2."}
```

Audio must be 16 kHz, mono, PCM16 WAV. WAV paths are relative to the manifest. Label phone recordings and kit mic recordings separately; do not mix them in a single reported score. Do not commit private recordings or model weights.

Install `torch` and `transformers` in a local Python environment. Accept the terms for the fine tuned Hugging Face models on your own account, then authenticate with `hf auth login`. Run:

```sh
python tools/accent_contest/contest.py --manifest clips.jsonl --models-dir $HOME/.cache/megathon-asr-models --output wer.md --predictions predictions.jsonl
```

Use `--dataset-label "Synthetic smoke test — not an accuracy estimate"` for generated dummy audio.

The default comparison is stock `tiny.en`, stock `base.en`, fine tuned Indian English Tiny, and fine tuned Indian English Small. WER is the total word edit errors divided by the total reference words, after case folding and punctuation removal. The table includes both counts. The checkpoints differ in size and training; their ranking is an empirical result on these clips, not proof that fine tuning caused the difference. The model download and inference run on the development machine only. No cloud inference is used by the Android app.

## Proxy run on a public Indian-accented set

Until the team's own recordings exist, `prepare_public_set.py` builds a labelled proxy manifest from a public Hugging Face dataset (default `WillHeld/india_accent_cv`, Mozilla Common Voice, India and South Asia accent group, read speech). It reads only the parquet footers plus one ~100-clip row group per chosen speaker, never a whole corpus, and is deterministic for a fixed seed and dataset revision (the revision is recorded in `meta.json`). Output goes outside the repo; never commit audio.

```sh
~/.venvs/megathon-asr/bin/python -I tools/accent_contest/prepare_public_set.py --out-dir ~/megathon-data/accent_contest/india_accent_cv --where "up_votes>=2" --where "down_votes==0"
~/.venvs/megathon-asr/bin/python tools/accent_contest/contest.py --manifest ~/megathon-data/accent_contest/india_accent_cv/clips.jsonl --models-dir $HOME/.cache/megathon-asr-models --dataset-label "WillHeld/india_accent_cv, n=80 clips, public Indian-accented English proxy; NOT our recordings" --output ~/megathon-data/accent_contest/results/wer.md --predictions ~/megathon-data/accent_contest/results/predictions.jsonl
```

Always keep the proxy label in the heading. The result and its limits are in `docs/experiments/B-20261011-accent-contest-public-proxy.md`. Run the unit tests from this folder with `python -m unittest` (not `python -I`, which drops the current folder from the import path).
