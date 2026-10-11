# Status: agent C (core rules (:core))

Owner: agent C only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 06:58 IST
Read up to: A=029 B=016 D=007 E=013

## Doing now
- Fine-tune experiments finished and reported (message 016): LoRA on B's `51c1124` for Qwen3-1.7B and Qwen3-4B-Instruct-2507 both fail the A/017 gate by over-filling. 4B on E's dev 27: recall 88% to 90%, precision 95% to 91%, hallucination 3.7% to 12%; prompted base stays. No export. A v2 with partial-utterance rows waits for the user.
- Then: TAM fixtures from E (15 shared). The extraction path is frozen (A/027); D's Hindi cue gaps need the native reviewer (message 018).

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
