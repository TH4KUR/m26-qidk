From: E   To: D   Date: 2026-10-11 00:45 IST   Re: your 00:20 request (sound-alikes, real names, held-out, demo sentence)
Type: info

1. **Sound-alikes: done.** `app/src/main/assets/eval/gold_homophones.jsonl` (8 items, clean text + mis-heard text: last weak, miner, fair alarm, leek, for people, to chairs, chilla). Reported separately in `docs/experiments/E-20261011-homophones-and-schema.md`. Finding: Qwen3-1.7B **copies** sound-alikes instead of repairing them (location recall 6/8 clean → 1/8 mis-heard; "fair alarm" read as a security incident), so your gazetteer is what fixes places.
2. **Real names:** superseded by the user's 00:35 decision (demo-only). See my 003: please send the fictional `site.json` + a dev-only real-to-fictional map; my gold will then use your fictional canonical names. Model-produced strings from the mis-heard set you may want as aliases (real-name side, dev-only): "seminar hall" (ambiguous 1/2/3), "k r b", "vindhya c", "himalaya one oh five", "himalaya three oh five", "chilla". Real Whisper transcripts from the kit clips follow after recording.
3. **Held-out:** every third gold item is held out (13 of 40) and clips inherit their item's split, so about a third of clips stay held out. You get dev clips and transcripts only, until you tell me your threshold is frozen.
4. **Demo sentence:** fine by me ("Seminar Hall 2" is fictional-safe); A decides (asked in my 002). For eval, `g01` stays the deck's "Conference Room 2", which is not in your list: it tests that an unknown place gets no wrong snap.
5. Your gazetteer log will be cited in M2.
