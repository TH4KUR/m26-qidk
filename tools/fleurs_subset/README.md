# FLEURS Hindi/Telugu subset (experimental lane, A/021)

**Label for every table made from this: "experimental, small n, FLEURS read speech, not incident speech".** FLEURS is read-aloud sentences. The vasista22 Hindi/Telugu exports were trained on FLEURS train and dev, so FLEURS test is close to in-domain for them; stock Whisper-Small is expected to be much worse on Telugu. These are ranking numbers, not claims about incident speech.

## Data
`google/fleurs`, **test** split, `hi_in` and `te_in`, licence **CC-BY-4.0** (record it in any report), dataset revision **`70bb2e84b976b7e960aa89f1c648e09c59f894dd`**. Audio stays outside the repo (about 520 MB downloaded to `~/megathon-data/fleurs`, never committed).

```sh
hf download google/fleurs data/hi_in/test.tsv data/hi_in/audio/test.tar.gz data/te_in/test.tsv data/te_in/audio/test.tar.gz \
  --repo-type dataset --revision 70bb2e84b976b7e960aa89f1c648e09c59f894dd --local-dir ~/megathon-data/fleurs
python3 tools/fleurs_subset/prepare.py --fleurs-dir ~/megathon-data/fleurs --out-dir ~/megathon-data/fleurs_subset \
  --revision 70bb2e84b976b7e960aa89f1c648e09c59f894dd
```
(`hf download` takes file names positionally; do not pass them after `--include`, which then ignores them.)

`prepare.py` picks **20 clips per language by a fixed seed (20261011, one random stream per language, not hand-picked)**, extracts only those, converts them to 16 kHz mono PCM16, and writes `manifest_fleurs_r1.jsonl` (the shape `tools/accent_contest` loads) and `selection_log.json` (seed, revision, licence, every chosen file). FLEURS ships **32-bit float WAVs**, which Python's `wave` cannot read: the script reads the RIFF chunks itself, scales by 32767 and clips.

## Scoring: do not use `accent_contest`'s `normalize`
`contest.py` normalises with `[^\w\s]`, which deletes Indic vowel signs and the virama: it turns `स्कीइंग मार्ग` into single consonants, so its WER is meaningless for Hindi and Telugu. Use `indic_wer.py` instead (`normalize`, `wer`, `cer`): it keeps letters, combining marks and digits, treats punctuation (including the danda) as a space, drops only ZWJ/ZWNJ, and does not transliterate or merge spellings. Report WER and CER with their denominators (reference words / characters), per language.

## Tests
`cd tools/fleurs_subset && python3 -m unittest test_fleurs_subset.py -v` (11 tests: normalisation keeps marks, deterministic selection, extraction, float32 conversion, the manifest loads in `accent_contest`'s loader).

## Not done here
No Hindi/Telugu recogniser has been run: the vasista22 exports (`~/models-hi-te/`) are not on this machine, and the Whisper-Small QNN run needs the kit through the app session.
