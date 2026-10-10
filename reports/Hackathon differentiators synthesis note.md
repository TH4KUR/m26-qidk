# Synthesis note: Hackathon differentiators

**Base:** candidate 1 (Fable). The cross-judge (Opus) scored it 28/30 against 25 for candidate 2 (Opus) and 21 for candidate 3 (Sonnet). My independent read agreed, so there was no disagreement to resolve.

**Why:** candidate 1 had the most honest compute map and the best grounding discipline (tri-state fields, snap after the LLM, post-ASR correction because WhisperKit takes no prompt). It also surfaced the most build-time traps: NPU cold start, the wrong kit clock, no word timestamps.

**Convergence (all three, so shipped as consensus):** site gazetteer with phonetic snapping, compute-proof panel, hands-free voice loop, measured on-kit scorecard. All three rejected Hindi/Telugu as a core feature and LLM-written follow-ups.
**Two of three:** incident-adaptive mandatory fields, accepting "none" or "I don't know", a judge speaking a live incident.

## Grafts
| From | What | Into |
|---|---|---|
| Candidate 2 | Role-aware people mapping (witness vs involved, negation) | #2, promoted into the top 3 |
| Candidate 2 | Confirm-back on low-confidence snaps | #1, as the fallback for uncertain snaps |
| Candidate 2 | `TrafficStats` "0 bytes sent" counter, raw-vs-improved ablation pair | #4 and #3 |
| Candidate 3 | Echo cancellation and templated acknowledgments | #5 |
| Candidate 3 | Colour-matched evidence spans and "why Severe?" tap, as a staggered reveal | #6 |
| Candidate 3 | Spoken safety advice from the rule table | #7 |
| Me | `tiny.en` vs `base.en` accent A/B | #3, from the speech report's finding that base.en is the cheap English gain |

## Rejected, and why
- **Candidate 1's fields-fill-while-speaking:** WhisperKit doesn't stream, and spec §5 puts streaming out of scope.
- **Candidate 2's live NPU-vs-CPU race:** a second LLM instance may not fit in memory, and the 4.8 s cold start would make the NPU look slower.
- **Candidate 2's initial-prompt biasing:** WhisperKit has no language or task API, and a prompt hook is unverified. The gazetteer works without it.
- **Candidate 3's repair-before-LLM placement:** it would break the D7 evidence check, so the snap runs after the LLM.
- **Candidate 3's "41 tok/s" and "380 ms" figures:** I grepped the repo and they appear in no grounding file. They are left out until measured on the kit.
- **Candidate 3's Hindi/Telugu stretch:** kept only as a post-hour-18 note, with the limits stated.

**Dropouts:** none. All three candidates and the judge completed.

## Hours corrected
The judge flagged these as too low, and the artifact uses the realistic figures: hands-free loop 4 h (candidate 2 said 1.5), ablation plus WER 5 h (candidate 2 said 2.5), Judge Bench live time 40–60 s for 10 clips (candidate 1 said about 20 s), and site directory 3.5 h (candidate 2 said 2.5).

## Verification
I checked the judge's fact-check against the repo files rather than trusting it:
- **Confirmed:** the "41 tok/s / 380 ms" figures are in no grounding file; the spec's §5 lists streaming ASR as out of scope; D7 requires evidence from the cumulative transcript; the kit clock is wrong (2026-01-12).
- **Judge was wrong:** it called WhisperKit defaulting to `CPU_AND_NPU` unsupported. The NPU notes verify it from `WhisperKit.kt`, so the "hybrid" label stays.
- **Still [I]:** `TrafficStats` as a network counter, the grammar sampler running on the CPU, the 0.88 snap threshold, and every hour estimate until someone builds it.
