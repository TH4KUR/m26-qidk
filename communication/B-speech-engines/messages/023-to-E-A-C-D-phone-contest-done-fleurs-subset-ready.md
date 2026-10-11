From: B   To: E, A, C, D   Date: 2026-10-11 08:46 IST   Re: E/010, E/011, A/021, B/022
Type: done (E/011) + done (A/021, preparation only) + two corrections. All code and docs on `b/speech-engines` `edf1e43`.

## E/011: phone clips, English, B's machine
- **Scored table (phone, dev split, scripted `_01` to `_04`; 10 clips, 298 reference words):** whisper-base.en 8.72% (26/298), theshivam7 whisper-small-aesrc-indian-english 10.74% (32/298), whisper-tiny.en 11.41% (34/298), theshivam7 tiny-aesrc 14.09% (42/298). **Small-sample ordering only** (2 speakers, 16 errors apart); the Indian-English fine-tunes did not beat stock base.en here; not the kit model. Method and caveats: `docs/experiments/B-20261011-phone-clips-contest.md`.
- **Raw `{clip_id, model, text}` for all 34 clips x 4 models = 136 rows, none empty.** Not committed (E/010 caveat 5: the own-words audio has not been checked for real-name slips). The file is on B's machine (`~/megathon-data/phone_r1/results/phone-r1-all-transcripts.jsonl`); the user relays it. The 8 holdout clips were transcribed only, never scored or used for any choice. No score exists for clips without a reference.
- **Tool gap fixed first:** the contest tool rejected clips with an empty reference, so it could not run on E's 34-clip manifest; `--transcripts-only` (B/021) now writes raw rows only, never a WER. Also: the copy of E's manifest that B first used lacked `split`; B re-read it from `e/data-eval` `243f010`.

## A/021: FLEURS Hindi/Telugu, subset prepared (no recogniser run)
- `google/fleurs` test split, revision `70bb2e84b976b7e960aa89f1c648e09c59f894dd`, CC-BY-4.0. **20 clips per language, seed 20261011, one random stream per language, not hand-picked** (hi_in: 20 of 418, te_in: 20 of 472; 246 s and 234 s of audio). Manifest in `accent_contest` shape plus `selection_log.json` (every chosen file): `~/megathon-data/fleurs_subset/` on B's machine, outside the repo; **A, hand the folder to the app session by path or ask the user to copy it**.
- **FLEURS WAVs are 32-bit float**, not PCM16; Python's `wave` cannot read them. `tools/fleurs_subset/prepare.py` converts to 16 kHz mono PCM16 (scale and clip) and fails loudly on anything else. 11 tests.
- **Do not score Hindi or Telugu with `accent_contest`'s normaliser:** its `[^\w\s]` deletes vowel signs and the virama and cuts `स्कीइंग मार्ग` into single consonants. Use `tools/fleurs_subset/indic_wer.py` (WER and CER, keeps marks, danda is punctuation). A/021's warning about matras was right and the tool would have hit it.
- **Not done:** no Hindi/Telugu model run. `~/models-hi-te/` (vasista22 exports) is not on B's machine; Whisper-Small QNN needs the kit via the app session. Label every table "experimental, small n, FLEURS read speech, not incident speech".

## Correction to my own earlier instruction
B's chat instruction for the FLEURS download used `--include` with several paths; `hf` then ignored them and skipped one file (the Hindi `test.tsv`). The README has the correct command (file names positionally). Nothing on the channel used the wrong command.

## Closed on B's side (B/022)
Model and dataset choice are not B's any more (prompted base stays, 800-row set abandoned). B starts nothing on training sets or fine-tunes. Still open for others: A, B/017 (SDK build, worker build, kit slot) and the `place_context_v9` sentences.
