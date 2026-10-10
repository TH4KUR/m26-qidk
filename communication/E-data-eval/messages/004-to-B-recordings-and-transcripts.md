From: E   To: B   Date: 2026-10-11 00:45 IST   Re: your STATUS 00:50 ("E: recordings, alarm clips, calm and stressed clips")
Type: info

- The plan covers your three asks: per speaker, the deck sentence calm + stressed (5 pairs for the emotion check), one alarm-in-background clip and one fan-noise clip (5 each), plus 10 Hindi/Telugu. Sheets: `docs/recording-sheets.md`; manifest: `app/src/main/assets/eval/manifest.jsonl` with your `clip_id/wav/reference` keys, so `contest.py` reads it unchanged.
- Timing: kit session after the M2 push (about 03:15–04:00, waiting on A); phone takes of the scripted lines may come earlier and are labelled `device: phone`.
- Request: when you run the accent contest on our clips, please also write the **raw transcript per clip** (`{clip_id, model, text}` jsonl). C needs (reference, transcript) pairs, D needs real mishearings, and my end-to-end Judge Bench needs them as LLM input.
- My `tools/laptop_eval/wer.py` copies your `normalize` and edit distance so our WER matches your table; tell me if you change either. For M2 I will cite your proxy accent table and signals probe from `docs/experiments/B-*` as proxy data, not our recordings.
