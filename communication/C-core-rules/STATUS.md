# Status: agent C (core rules (:core))

Owner: agent C only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 07:58 IST
Read up to: A=031 B=017 D=007 E=013

## Doing now
- Witness-presence fixes A, B, C, D done and pushed (`c/core-rules` `82eb6f0`, messages 019 to 023): chips for presence, prompt v12, one optional witness question, v2 retrain (dev 27: recall 93%, precision 95%, hallucination 6.2%, fails the 4% gate; not exported).
- Next: A/030 item 2, the GPU-vs-kit gap bisection on the 4B base (arms a to e), table by about 08:30; model card corrections wait for the user's go.
- Then: TAM fixtures from E (15 shared).

## Done and ready for others (with file/commit)
- `c/core-rules` `08565d5`: review verdict on B's synthetic gold (message 010: mechanical PASS 800/800, cleared for the pilot only); validator fixes `35a96dc` (severity none) and `366ff4f` (names, stated severities, failure states); `tools/finetune/` (SFT builder with the shipping prompt, LoRA trainer, dev-27 generator, VM setup). SFT files built locally: 599 train, 100 val, 100 test.
- `c/core-rules` `472393b` (844 tests, 0 failing, 3 skipped): A's recall patch reviewed and merged with narrowed rules (`70bd6a3`, log `docs/experiments/C-20261011-recall-patch-review.md`); reason codes on proposals/rejections (`ebb35a7`); injury/shock/head-hit hazard wordings; `PriorityAdvisor.onReport` floors from a stated severity or hazard type (`472393b`). Please merge to master (A, messages 005 and 008).
- Earlier: `f97dae6` (weeks, near-miss grounding), already on master.

## Blocked on / waiting for
- A: merge of `472393b`; the call `advisor.onReport(report)` in `ReportSession` (message 008, otherwise the floors do nothing); one kit dev run of the reviewed rules by the app session (the only kit evidence for them).
- Whoever recorded: a fresh urgency holdout (20+ text reports, two blind labels); without it no new urgency claim.
- E / user: (reference, engine transcript) pairs from real audio; a human second labeller; a native Hindi reviewer.

## Needs from others
- Nothing blocking my code. The held-out 13 gold cases stay unopened until E's single final scorer run.

## Honest limits (keep in reports)
- Replay numbers are on dev items, with a different model than the patch was tuned on, no retries simulated; reviewed rules were written after reading the wrong dev cells.
- Urgency band accuracy on unseen reports is about 0.50 (pre-registered bound missed); LLM hint and key question are unmeasured; Hindi lists are model-reviewed only.
