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

For E/C/D's raw transcript format, add `--raw-transcripts transcripts.jsonl`. Each row is exactly `clip_id`, `model`, `text`; text retains the inference result's punctuation/case and is not WER-normalized. The existing predictions schema remains unchanged. Keep actual spoken-name references and raw outputs outside the repo; replacing a name in a scoring reference without replacing it in the audio changes WER.

For the team's recordings, use repeatable exact metadata filters to keep device, language and tuning/test subsets separate:

```sh
python tools/accent_contest/contest.py --manifest clips.jsonl \
  --models-dir "$HOME/.cache/megathon-asr-models" \
  --select device=phone --select split=tuning --select language=en \
  --dataset-label "Team recordings: phone, tuning, English" \
  --output results/phone-tuning-wer.md --predictions results/phone-tuning-predictions.jsonl \
  --raw-transcripts results/phone-tuning-transcripts.jsonl
```

Repeat with `device=kit` and distinct output paths. Selectors are recorded in the Markdown report and must match metadata exactly. They apply before WAV validation, so unselected WAVs need not be downloaded. Missing, blank or non-string selection metadata and empty selected subsets raise errors. Use the manifest's actual split names (`tuning/test` or `dev/holdout`), not a guessed mapping. Choose/freeze the split before inspecting inference, and reserve the test set until evaluation settings are fixed. Default English checkpoints should not be scored on Hindi/Telugu clips.

To get the raw transcripts of **every** clip, including ones whose reference is not typed yet (E's own-words clips), add `--transcripts-only`: it needs `--raw-transcripts`, accepts empty references, and writes no WER table or predictions file (so it never produces a score for unchecked labels):

```sh
python tools/accent_contest/contest.py --manifest manifest_phone_r1.jsonl --models-dir "$HOME/.cache/megathon-asr-models" \
  --transcripts-only --raw-transcripts results/phone-r1-all-transcripts.jsonl
```

Additional `--select noise=fan`, `--select tone=stressed`, or `--select condition=fan` runs provide separate condition tables using the manifest's actual keys. Select `label_source=human` only if that is the exact label in the manifest. Model-generated labels remain silver; mark that limitation in the dataset heading. A filtered run does not require files for unselected clips. Output paths must be distinct and cannot overwrite the input manifest. These options change neither WER normalization nor the legacy unfiltered invocation.

Use `--dataset-label "Synthetic smoke test — not an accuracy estimate"` for generated dummy audio.

The default comparison is stock `tiny.en`, stock `base.en`, fine tuned Indian English Tiny, and fine tuned Indian English Small. WER is the total word edit errors divided by the total reference words, after case folding and punctuation removal. The table includes both counts. The checkpoints differ in size and training; their ranking is an empirical result on these clips, not proof that fine tuning caused the difference. The model download and inference run on the development machine only. No cloud inference is used by the Android app.

## Proxy run on a public Indian-accented set

Until the team's own recordings exist, `prepare_public_set.py` builds a labelled proxy manifest from a public Hugging Face dataset (default `WillHeld/india_accent_cv`, Mozilla Common Voice, India and South Asia accent group, read speech). It reads only the parquet footers plus one ~100-clip row group per chosen speaker, never a whole corpus, and is deterministic for a fixed seed and dataset revision (the revision is recorded in `meta.json`). Output goes outside the repo; never commit audio.

```sh
~/.venvs/megathon-asr/bin/python -I tools/accent_contest/prepare_public_set.py --out-dir ~/megathon-data/accent_contest/india_accent_cv --where "up_votes>=2" --where "down_votes==0"
~/.venvs/megathon-asr/bin/python tools/accent_contest/contest.py --manifest ~/megathon-data/accent_contest/india_accent_cv/clips.jsonl --models-dir $HOME/.cache/megathon-asr-models --dataset-label "WillHeld/india_accent_cv, n=80 clips, public Indian-accented English proxy; NOT our recordings" --output ~/megathon-data/accent_contest/results/wer.md --predictions ~/megathon-data/accent_contest/results/predictions.jsonl
```

Always keep the proxy label in the heading. The result and its limits are in `docs/experiments/B-20261011-accent-contest-public-proxy.md`. Run the unit tests from this folder with `python -m unittest` (not `python -I`, which drops the current folder from the import path).
