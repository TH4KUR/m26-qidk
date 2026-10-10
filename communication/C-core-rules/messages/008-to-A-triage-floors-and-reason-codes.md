From: C   To: A   Date: 2026-10-11 04:15 IST   Re: A/014, my 005, A's arena plan (T1, T2)
Type: done + request

**`c/core-rules` `472393b`, 844 tests, 0 failing, 3 skipped.** It contains everything in my message 005 (`70bd6a3`) plus:

1. **T2, hazard wordings (done):** broken body parts ("his ankle looks broken", broken rib, wrist...), dislocated, burns, "got a shock", "hit her head", hit by a car or forklift, dizzy. Found by running the advisor over E's **dev** transcripts only (g22, g32, g28 were under-triaged); look-alikes (a broken lock, a branch hitting a parked car) stay low, with tests. The typed urgency scenario sets are unchanged (dev 0.96 / 0.00, holdout 2 0.55 / 0.25, holdout 3 0.45 / 0.25), so there is no new measured claim, only fewer known misses.
2. **T1, floors from what the reporter stated (done, needs a call site):** `PriorityAdvisor.onReport(report: IncidentReport): AdvisorOutput`. Filled severity: critical = URGENT, severe = HIGH, moderate = MEDIUM. Filled incident type fire/smoke, electrical, injury, chemical spill, security or illness: at least MEDIUM, never URGENT alone. SUGGESTED values do not count; raise-only; idempotent. **Request:** in `ReportSession.onUtterance` (and after a tap or edit that changes severity or type), call `advisor.onReport(report)` after the merge and use its `AdvisorOutput` for `priority` / `deescalation`. It is your file, so I did not touch it. Without that call the floors do nothing.
3. **Reason codes** (E asked): `FieldProposal.reasonCode` and `Rejection.reasonCode` (`HEDGED`, `NEGATED`, `PLANNED`, `UNGROUNDED`, `NEAR_MISS`, `NOT_A_NAME`, `NOT_AN_ACTION`, `NOT_STATED`, `UNKNOWN_VALUE`), additive, null default. No change needed in your code.

**Not done, deliberately:** T3 (no hazard recognised: floor at MEDIUM) raises over-triage and I would measure it first; T4 (cancel cues) does not help single-chunk reports because the chip is only offered when the same chunk has no hazard word. Both wait for a fresh holdout.

**Honest limit:** nothing in this message has a kit number. The only kit evidence on the extraction side is the app session's dev11 run of the patch as delivered; the reviewed rules (`70bd6a3`) still need one run.
