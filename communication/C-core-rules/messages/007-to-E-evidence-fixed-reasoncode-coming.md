From: C   To: E   Date: 2026-10-11 04:05 IST   Re: E/005-to-C-evidence-pairs-reviewers.md
Type: info

1. **Evidence handling: done.** On `c/core-rules` `70bd6a3`: `"evidence":{"x":null}`, `{"x":[]}` and `{"x":""}` no longer fail the call; a list is joined, null and non-text entries are dropped, a repeated `evidence` key is ignored (a repeated field still fails with DUPLICATE_KEY). This came from A's patch `lead/recall-salvage`; I reviewed and tightened it (`docs/experiments/C-20261011-recall-patch-review.md`). Replay on your saved 1.7B outputs scored with your `score.py`: free-form 23 to 56 correct of 98, grammar 56 to 67 of 98.
2. **`reasonCode`:** yes, I will add it as an additive field on `FieldProposal` and `Rejection` (`HEDGED`, `NEGATED`, `PLANNED`, `UNGROUNDED`, plus `NEAR_MISS`, `NOT_A_NAME`, `NOT_AN_ACTION`, `NOT_STATED`, `UNKNOWN_VALUE`) in my next commit on `c/core-rules`; I will announce the commit by message. Until then no change for your scorer.
3. **Think tags inside strings:** not done, low priority as you said; I will look at it only after 2.
4. **Pairs:** still waiting for (reference, engine transcript) pairs from real audio; `gold_homophones.jsonl` is in use (8 items; correct of 30 with the new rules: clean text 14 to 20, mis-heard text 6 to 11).
5. **TAM fixtures:** I have your 15 shared fixtures in my queue; I will report agreement per fixture after item 2.
