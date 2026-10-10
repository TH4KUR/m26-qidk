# B-20261011: Whisper accent contest on a public Indian-accented proxy set

- **Date/time:** 2026-10-10 (laptop clock, IST). Venv and tests about 23:40 to 23:48, data preparation 23:46 to 23:51, contest run 23:54 to 23:56. The file name says 20261011 because that is the id B asked for.
- **Who:** B (speech), **run by an AI assistant** on B's laptop. No human listened to any clip or checked any transcript.
- **Goal:** run the existing `tools/accent_contest/contest.py` end to end, for real, on public Indian-accented English with human transcripts, as a **labelled proxy** until the team's own recordings (E's gold clips) exist. This also proves the tool works on real audio.

## Setup
- **Machine:** Fedora laptop, NVIDIA GeForce RTX 3050 6 GB Laptop GPU, driver 580.159.03 (CUDA 13.0 capable). RAM was tight (about 3 GB available, swap in use); nothing ran out of memory.
- **Python env (outside the repo):** `~/.venvs/megathon-asr`, Python 3.13.13, torch 2.14.1+cu130 (default PyPI wheel, bundles CUDA 13.0; `torch.cuda.is_available()` was True), transformers 5.19.0, accelerate 1.15.0, soundfile 0.14.0, soxr 1.1.0, datasets 5.1.0, huggingface_hub 2.2.0, pyarrow 26.0.0.
- **Models (already local, not re-downloaded):** `$HOME/.cache/megathon-asr-models`, run one after another on the GPU in fp32 through the `transformers` ASR pipeline (`chunk_length_s=30`, one clip at a time, default decoding settings). `openai/whisper-tiny.en` (English-only), `openai/whisper-base.en` (English-only), `theshivam7/whisper-tiny-aesrc-indian-english` (fine-tuned from multilingual `openai/whisper-tiny`), `theshivam7/whisper-small-aesrc-indian-english` (fine-tuned from multilingual `openai/whisper-small`). The two fine-tuned checkpoints report their own in-domain AESRC eval WER (7.5 % tiny, 4.0 % small) in `eval_results.json`; that is not our test.
- **Unit tests:** `cd tools/accent_contest && python -m unittest -v test_contest` gave **7 tests, all OK**. (`python -I -m unittest` cannot import `contest` because `-I` drops the current folder from the import path; the tests are written for plain `python -m unittest` from that folder.)
- **Commands (exactly what ran):**
  ```sh
  # data (public, ungated, no token used)
  ~/.venvs/megathon-asr/bin/python -I tools/accent_contest/prepare_public_set.py \
    --out-dir ~/megathon-data/accent_contest/india_accent_cv --where "up_votes>=2" --where "down_votes==0"
  # contest (through a small timing wrapper outside the repo that calls contest.main() unchanged)
  ~/.venvs/megathon-asr/bin/python -I ~/megathon-tmp/scripts/timed_contest.py ~/megathon-data/accent_contest/results/timing.json \
    --manifest ~/megathon-data/accent_contest/india_accent_cv/clips.jsonl --models-dir $HOME/.cache/megathon-asr-models \
    --dataset-label "WillHeld/india_accent_cv (Common Voice, India and South Asia accent), n=80 clips, public Indian-accented English proxy; NOT our recordings" \
    --output ~/megathon-data/accent_contest/results/wer.md --predictions ~/megathon-data/accent_contest/results/predictions.jsonl
  ```
  The plain equivalent (no per-model timing) is the `contest.py` command in `tools/accent_contest/README.md`. `contest.py` was **not modified**.
- **Outputs (outside the repo):** `~/megathon-data/accent_contest/results/` holds `wer.md`, `predictions.jsonl`, `timing.json`, `bootstrap.json`, `gpu_samples.csv`. Audio and manifest are in `~/megathon-data/accent_contest/india_accent_cv/` (`wavs/`, `clips.jsonl`, `meta.json`). Nothing was committed.

## Data
- **Dataset:** `WillHeld/india_accent_cv` on the Hugging Face Hub, revision `536a87197f1ebe18f844352c0601fc4c740cfe96`: Mozilla Common Voice English clips, India-and-South-Asia accent group, with Common Voice's sentence transcripts. **Read speech** of Wikipedia-style sentences.
- **Licence:** the dataset card declares **no licence field**. The content is Mozilla Common Voice, which Mozilla publishes under CC0-1.0. I did not verify which Common Voice release this mirror was built from. The data stays on this laptop and is not redistributed.
- **Accent label caveat:** the Common Voice accent group is "India and South Asia (India, Pakistan, Sri Lanka)", i.e. broader than India. I read the accent column only for the one shard I inspected (all 2,692 rows had that label); I did not re-check it for the 80 selected clips. Age and gender were not recorded for the sample.
- **Gated alternatives skipped (needs the user to accept terms, not clicked):** `ai4bharat/Svarah` (gated, auto-approval), `ai4bharat/IndicVoices` (gated, auto, not English-only anyway), `ai4bharat/NPTEL` (gated, auto).
- **Sample:** **n = 80 clips, 19 speakers (1 to 5 clips each), 476.4 s of audio (3.46 to 10.15 s per clip), 829 reference words** under the contest's normaliser (816 by plain whitespace split; the difference is hyphens and apostrophes). 74 distinct sentences, so 6 clips share a sentence with another clip in the set.
- **Selection (deterministic, `random.Random(20261011)`, script `tools/accent_contest/prepare_public_set.py`):** read the parquet footers of all 34 shards (1,217 speakers), shuffle speakers with the seed, and for each speaker pick a seeded row group, then a seeded shuffle of its rows, keeping rows with `up_votes>=2`, `down_votes==0` (Common Voice listeners validated the transcript), at least 4 words, and a decoded duration of 3 to 15 s, up to 5 per speaker, until 80 clips. Because I fetched one row group (about 100 consecutive recordings) per speaker, each speaker's clips come from a single recording stretch, probably one session and one microphone. 20 speakers were visited (19 yielded clips), one row group of about 17 MB each, so roughly 350 MB was fetched (I did not measure it); no corpus was downloaded. MP3 (48 kHz) was decoded and resampled to 16 kHz mono PCM16 WAV. I did not re-run the script to confirm a byte-identical second selection.
- **Reference text:** the dataset transcript as-is; the contest lowercases and strips punctuation. There is no number normalisation (see Caveats).
- **This is NOT our recordings and not the kit microphone.** No phone, no kit mic, no incident vocabulary, no spontaneous speech.

## Results (copied from `wer.md`)

# WillHeld/india_accent_cv (Common Voice, India and South Asia accent), n=80 clips, public Indian-accented English proxy; NOT our recordings

| Rank | Model | WER | Errors / reference words | Clips |
|---:|---|---:|---:|---:|
| 1 | openai/whisper-base.en | 12.30% | 102/829 | 80 |
| 2 | theshivam7/whisper-small-aesrc-indian-english | 13.51% | 112/829 | 80 |
| 3 | openai/whisper-tiny.en | 16.65% | 138/829 | 80 |
| 4 | theshivam7/whisper-tiny-aesrc-indian-english | 24.73% | 205/829 | 80 |

**Uncertainty (my addition, not produced by `contest.py`; `bootstrap.json`).** Resampling the 19 speakers with replacement, 10,000 times, seed 1, 95 % percentile intervals:

| Model | WER 95 % interval | Difference vs base.en (points), 95 % interval |
|---|---|---|
| openai/whisper-base.en | 8.6 % to 16.5 % | reference |
| theshivam7/whisper-small-aesrc-indian-english | 10.3 % to 16.8 % | +1.2 (-3.2 to +5.3) |
| openai/whisper-tiny.en | 12.2 % to 21.4 % | +4.3 (+0.5 to +8.7) |
| theshivam7/whisper-tiny-aesrc-indian-english | 20.1 % to 29.4 % | +12.4 (+8.3 to +16.8) |

**Time and memory on the RTX 3050 (one run, one clip at a time, from `timing.json`):**

| Model | Load (s) | Inference, 80 clips (s) | Per clip (s) | Torch peak allocated (MiB) |
|---|---:|---:|---:|---:|
| openai/whisper-tiny.en | 0.59 | 15.99 | 0.20 | 276 |
| openai/whisper-base.en | 0.41 | 21.22 | 0.27 | 509 |
| theshivam7/whisper-tiny-aesrc-indian-english | 0.24 | 18.33 | 0.23 | 293 |
| theshivam7/whisper-small-aesrc-indian-english | 0.90 | 49.74 | 0.62 | 1530 |

Total wall clock for the whole contest process, 107.6 s (about 112 s including interpreter start). Whole-GPU memory sampled once a second with `nvidia-smi` (156 samples): peak 1,833 MiB, idle 10 MiB. The first model (tiny.en) includes one-off CUDA start-up and kernel warm-up, so its time is probably a little inflated. The weights were in the OS cache, so "load" excludes a cold disk read. These are fp32 PyTorch-on-GPU numbers from a laptop under memory pressure; they say nothing about speed on the QIDK.

## Example errors (verbatim from `predictions.jsonl`)
- **openai/whisper-tiny.en**
  - REF "Sherman flees the unit that night and Hawkeye is allowed to stay." HYP "Sherman, please use it that night and pop it throughout the state." (8 errors)
  - REF "The title of the song is a reference to the inhalant drug amyl nitrite." HYP "The title of the song is the reference to the inhale in drug and male night right." (7)
  - REF "She returned with her children to Italy with Germanicus' ashes." HYP "See we turn with a true length to Italy with Germanicus' asses." (7)
- **openai/whisper-base.en**
  - REF "Usually the shoals break up and the fish disappear into deeper water around Durban." HYP "Usually it shows breakup and fish disappear into deeper water around the other." (7)
  - REF "Here, however, Adam's will designates Fran as his heir at law." HYP "Here, however, Adamsville designates Fran as is a draw." (7)
  - REF "Both had died four years earlier." HYP "Both had died 4 years earlier." (1; a digit versus a spelled number, not a mishearing; see Caveats)
- **theshivam7/whisper-tiny-aesrc-indian-english**
  - REF "Lucy weakened and struck Japan as a minimal typhoon." HYP "you see we can't structure that as a minimum try for" (10)
  - REF "However, he was on the losing end of most of these fights." HYP "up river he was on the losing end of most of these fights" (2; both stock models got this clip exactly right)
  - REF "Traditionally, one of the purposes of the bagpipe was to provide music for dancing." HYP "creditially one of the purposes of the back pipe was to provide music a dancing"
- **theshivam7/whisper-small-aesrc-indian-english**
  - REF "The title of the song is a reference to the inhalant drug amyl nitrite." HYP "the title of the song is a reference to the nail in truck a male night ride" (7)
  - REF "He was killed by Alpha Flight but Vindicator escapes with Claire." HYP "he was killed by alpha flies but vindicated as keeps with clay" (5)
  - REF "Many Chinese political leaders paid visits to these locations." HYP "many chinese political leaders paid visas to these locations." (1)
- **Pattern:** the worst clips for all four models are rare proper nouns and technical words from Wikipedia-sourced sentences (Germanicus, Durban, amyl nitrite, Vindicator). That is a property of this text, not of incident reports. No model produced an empty output or a runaway repetition (no prediction was more than twice the reference length), and no prediction was in another language (the only non-ASCII output is "fiancé" from two models, which also counts as an error against the reference "fiancee").

## Verdict
- **What the ranking says (on these 80 clips only):** stock `base.en` had the lowest WER (12.30 %). The fine-tuned Small was 1.2 points behind with an interval that spans zero, so these two are **not distinguishable here**. Stock `tiny.en` was 4.3 points behind base.en (interval just above zero). The fine-tuned Tiny was clearly worst, 12.4 points behind, and 8.1 points worse than stock `tiny.en`.
- **What it does NOT prove:**
  - **It does not show that Indian-English fine-tuning helps or hurts.** The four checkpoints differ in size (tiny, base and small are different architectures; the fine-tuned Small has about 6 times the weights of `tiny.en`), in base model (multilingual versus English-only) and in training data. The one near-matched pair, `tiny.en` versus the fine-tuned Tiny, goes against the fine-tune, but they also differ in multilingual versus English-only base, so even that is not a clean test. A clean test needs stock `whisper-small` (multilingual) in the contest; it is not in the four.
  - **It is read speech of Wikipedia sentences with rare names.** Incident reports are spontaneous, noisy, full of everyday words and place names. A model's rank here may not carry over.
  - **It is a proxy set:** one dataset, 19 speakers, 80 clips, 829 words, mixed devices, a wide "South Asia" accent group, one speaker's clips from one session. Intervals are wide, and speakers are the unit that matters, not clips.
  - **It does not test our pipeline:** not the phone or kit microphone, not the on-device runtime, not quantisation. These are fp32 PyTorch GPU runs.
  - I did not check whether the training data of any checkpoint overlaps Common Voice.
- **Do not use this table to pick the shipping model.** It shows the tool works on real audio and gives a first reading of how four checkpoints behave on Indian-accented read speech.

## Caveats found while running
- `contest.py` has **no number normalisation**: base.en wrote "4 years" for "four years" (1 error), and any digit/word mismatch counts as an error. Worth deciding before scoring E's clips if incident reports contain numbers (room numbers, times).
- `contest.py` does not record per-model time or memory; I used an external wrapper that calls `contest.main()` unchanged. The wrapper is in `~/megathon-tmp/scripts/timed_contest.py` (not in the repo).
- transformers prints that `chunk_length_s` is "very experimental" for seq2seq models. Every clip here is under 30 s, so chunking was not triggered; for longer incident recordings the pipeline behaviour is untested.
- A first attempt at `prepare_public_set.py` was too slow (about 1 s per row group over the network); I changed it to read speaker ranges from the parquet footers, which cut the scan to well under a minute. A first timing-wrapper attempt crashed (patched the wrong `transformers` attribute); I fixed it and re-ran the whole contest, so the results above are from the second, complete run.

## Next step
- Re-run the same contest on E's gold clips, with phone and kit-mic recordings reported as separate rows, using the same command:
  `~/.venvs/megathon-asr/bin/python tools/accent_contest/contest.py --manifest <gold>/clips.jsonl --models-dir $HOME/.cache/megathon-asr-models --dataset-label "<what these clips are>" --output <results>/wer.md --predictions <results>/predictions.jsonl`
- Add stock multilingual `openai/whisper-tiny` and `openai/whisper-small` (with `--model`, after downloading) if the question is whether the Indian-English fine-tune itself helps.
- Decide whether to add number normalisation to the scorer.
- Gated sets that would be a better spontaneous-speech proxy, **needs the user to accept terms:** `ai4bharat/Svarah` (Indian-accented English, recorded for ASR evaluation).
