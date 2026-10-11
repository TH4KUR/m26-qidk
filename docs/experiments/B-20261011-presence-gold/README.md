# Presence-sentence gold (16 items) for a v11 versus v12-presence kit comparison

**Label, to be printed above any table made from this file: "authored after seeing failures (the 'my friend Rahul was with me' report and dev item g20), not dev, not held-out".** It can show whether a prompt or rule captures "was with me / nearby / there" as a witness without breaking the controls. It is not a headline accuracy number and not a generalisation test.

Requested by A/032 (10 to 20 presence sentences with gold in `gold_v1.jsonl` format). Same item schema as `app/src/main/assets/eval/gold_v1.jsonl`; `split` and `source` are `authored_after_failures`, so E's scorer needs `--split authored_after_failures` or a copy of the loader that accepts it (nothing here edits E's files).

## Contents
- `presence_v1.jsonl`: 16 items, English, fictional names and places only (no teammate or campus names; checked against `tools/blindcheck.words`). 15 single-turn, 1 two-turn (`p14`).
- **10 positives** (`positive`): a named person was with the speaker, next to them, nearby or there, with different wordings. `p09` is a "saw it" control that already works. Gold: `witnesses` reported.
- **6 controls** (`control`) where witnesses must stay empty or the person belongs elsewhere:
  - `p04` was there but did not see it (the convention of dev item g20)
  - `p07` a role, not a name
  - `p08` the speaker alone
  - `p15` a named person who was explicitly not there
  - `p16` a named person who was hurt: `persons_involved`, not a witness
  - `p03` a **judgement call**: a person was there and helped; gold says `persons_involved` (the prompt calls such a person "handling it"). Drop `p03` from any table if A/C disagree.
- `p05`, `p06`, `p16` also hold a hurt person who must stay in `persons_involved` while a different person is the witness.

## How to use
Score v11 against v12 (or v12-presence) on the 16 items with E's scorer, reporting witness recall on the 10 positives separately from false witnesses on the 6 controls, with the denominators. Sixteen items cannot support a significance claim; report counts.

## Checks
`python3 -m unittest docs/experiments/B-20261011-presence-gold/test_presence_gold.py -v` (schema as in `tools/laptop_eval/test_gold.py`, missing-mandatory consistency, every gold value actually said in the turns, the labelled convention, no blind-check words). No model was run on these items.
