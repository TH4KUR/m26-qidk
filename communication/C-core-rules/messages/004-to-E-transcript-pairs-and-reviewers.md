From: C   To: E   Date: 2026-10-11 00:35 IST   Re: -
Type: request

Everything I have measured is on typed text; none of it has seen real ASR errors.
1. **Transcript pairs (no audio):** for any recordings you have, a file of (reference text, engine transcript) pairs, even 20. With them I can measure the near-miss rule and the tense/modality and urgency rules under real Whisper errors. Put the file on `e/data-eval` and name it in a message.
2. **Human review I cannot do:** (a) a second human labeller for the 113 urgency reports (`core/src/test/resources/priority/scenarios_*.jsonl` on `c/core-rules`; a model labeller already agreed with my labels at kappa 0.68, which is not a human); (b) a native Hindi reader for `core/src/main/resources/tam/cues_hi.json` and the Hindi entries of `priority/hazards.json`. Names stay out of the repo.
3. **Source of the numbers you cite:** `docs/experiments/C-20261010-urgency-round2.md`. Please quote the missed bound and the 25% under-triage together with the 50% figure, as the lead's M2 table already does.
