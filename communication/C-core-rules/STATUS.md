# Status: agent C (core rules (:core))

Owner: agent C only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 04:10 IST
Read up to: A=016 B=013 D=003 E=009

## Doing now
- Reason codes on `FieldProposal` / `Rejection` for E's scorer (additive; announced by message when pushed).
- Next, unless A objects (message 005): missing hazard classes (broken bone, burns, shock, hit by vehicle) and urgency floors from a stated severity and a validated incident type, both in my files (`priority/`).

## Done and ready for others (with file/commit)
- `c/core-rules` `70bd6a3`: A's recall patch (`lead/recall-salvage`) reviewed and merged, rules narrowed where replay on E's saved outputs showed wrong fills; `wrongValueTypeFails` replaced deliberately; generic `alarm` signal. 820 tests, 0 failing, 3 skipped. Log: `docs/experiments/C-20261011-recall-patch-review.md`. Please merge to master (A, message 005).
- Earlier: `f97dae6` (weeks, near-miss grounding), already on master.

## Blocked on / waiting for
- A: merge of `70bd6a3` and one kit dev run of the reviewed rules by the app session (the only kit evidence for them).
- Whoever recorded: a fresh urgency holdout (20+ text reports, two blind labels); without it no new urgency claim.
- E / user: (reference, engine transcript) pairs from real audio; a human second labeller; a native Hindi reviewer.

## Needs from others
- Nothing blocking my code. The held-out 13 gold cases stay unopened until E's single final scorer run.

## Honest limits (keep in reports)
- Replay numbers are on dev items, with a different model than the patch was tuned on, no retries simulated; reviewed rules were written after reading the wrong dev cells.
- Urgency band accuracy on unseen reports is about 0.50 (pre-registered bound missed); LLM hint and key question are unmeasured; Hindi lists are model-reviewed only.
