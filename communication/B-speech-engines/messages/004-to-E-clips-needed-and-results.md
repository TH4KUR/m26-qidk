From: B   To: E   Date: 2026-10-11 01:04 IST   Re: -
Type: request

**What I need from your recordings** (the contest and probes are ready; each is one command): about 30 incident clips as 16 kHz mono WAV with your manifest (`id, path, device, speaker, scenario, language, condition, transcript_gold`), **kit and phone rows separate**, plus (a) 5 to 10 clips with a real alarm playing (smoke detector or fire-alarm beep), (b) calm and stressed takes of the same report, (c) one take with the fan on.

**What I will run on them:** `~/.venvs/megathon-asr/bin/python tools/accent_contest/contest.py --manifest <your manifest as clip_id,wav,reference> --models-dir $HOME/.cache/megathon-asr-models --model openai/whisper-tiny.en --model openai/whisper-base.en --model openai/whisper-small.en --dataset-label "<what these clips are>" --output wer.md --predictions predictions.jsonl` (needs `clip_id`, `wav`, `reference` per line; I can convert your manifest). Table with errors/words per model, then the probes in `tools/signals_probe/`.

**Results you can cite now (all proxy data, labelled as such in the logs):** English checkpoints on 80 public Indian-accent clips (829 words): `small.en` 9.65%, `base.en` 12.30%, `tiny.en` 16.65%; the fine-tuned Indian-English Small 13.51% and Tiny 24.73%. A simulated noise floor 13 dB under the speech costs 9 to 14 points; level alone costs nothing measurable. Logs: `docs/experiments/B-20261011-accent-contest-public-proxy.md`, `B-20261011-english-checkpoints-and-mic-level.md`, `B-20261011-signals-probe.md`. Limits are in each log; none of it is the kit mic.

**Scorer note:** `contest.py` has no number normalisation ("4 years" against "four years" counts as an error); decide whether your scorer should.
