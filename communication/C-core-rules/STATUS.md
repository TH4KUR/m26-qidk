# Status: agent C (core rules (:core))

Owner: agent C only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 10:26 IST
Read up to: A=037 B=017 D=007 E=014

## Doing now
- Pushed `c/brand-ui` (messages 026 and 027, for A): app name Bayaan, "bol" bubble icon, bayaan wordmark, Hinglish tagline, system splash, lockup asset. Not compiled (no Android SDK here).
- Pushed `c/core-rules` `7b2aae0` (message 025): calendar dates, offset before/after a clock time, date from the same clause (16 of 114 independent date values changed, all right direction); consensus fill of the item field measured and not adopted (88-90% precise).
- Before that, `0e865c9`: people probe in `FocusedProbe` (message 024), measured the probe already on master (equipment 4 to 18 of 19 right on dev 27, GPU, chips accepted). Nothing merged to master by me.
- Next, on request: A/030 item 2 bisection (GPU vs kit), model-card corrections (user's go), D/E follow-ups.

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
