# Person C: core rules, what was built, what was measured, open questions

Branch `c/core-rules` · 2026-10-10 · Owner: C · Status: **implemented and tested** (246 tests, 0 failures; `./gradlew :core:test`).

Tags: **[DECIDED]** follows the spec or handoff · **[PROPOSED]** my default until you answer · **[MEASURED]** a number from a run on 2026-10-10 · **[Q#]** needs your call.

## 1. What shipped (all pure Kotlin in `:core`, no Android imports)

| Package | Class | What it does | Tests |
|---|---|---|---|
| `domain` | `Model.kt`, `Tam.kt`, `Urgency.kt` | Shared types (spec §4b) incl. `ValueStatus` with distinct MISSING / UNKNOWN / EXPLICIT_NONE | 7 |
| `llm` | `ToolCallParser` | Lenient syntax (think blocks, fences, prose, trailing commas), strict content: one `update_report` envelope, JSON or Qwen `<tool_call>`; rejects bare objects, two envelopes, truncation | 15 |
| `domain` | `EnumNormalizer` | English + Hinglish + Devanagari synonyms; unknown → null (rejected, never `other`) | 26 |
| `domain` | `TemporalResolver` | Resolves against the utterance capture instant in `Asia/Kolkata`; "around 10 AM" keeps the date missing; "kal" ambiguous | 27 |
| `domain` | `GroundingValidator` | Whole-token evidence match; negation and digits can't be dropped; "Room 2" ≠ "Room 20" | 18 |
| `tam` | `TamDetector` + `cues_en.json` / `cues_hi.json` | Clause-level tense, aspect, modality; cue lists are editable data | 40 |
| `llm` | `PayloadValidator` | Applies grounding + TAM + time rules to an LLM payload; returns proposals and rejections | 19 |
| `priority` | `UrgencyFeatureExtractor` + `hazards.json` | Transcript → impact 1–5, immediacy 1–4, reasons | 44 |
| `priority` | `UrgencyStrategies` (4) + `priority_rules.json` | 1–20 score; four formulas on the same features | 12 |
| `priority` | `UrgencyEvaluator` + 3 scenario sets | Band accuracy, MAE, under-/over-triage | 8 |
| `priority` | `PriorityAdvisor` + `TriageLanes` | Per-chunk ratchet, journaled override, "call 112", lane per level | 18 |
| `priority` | `SeveritySuggester` | Suggests a severity from the worst non-negated hazard; never fills | 10 |
| root | `SlideExampleEndToEndTest` | The deck example through parser → validator → suggester → advisor | 2 |

## 2. Your 1–20 idea as built

**Two things are kept apart (spec D18):** *severity* is a report field the reporter states or confirms; *urgency* (your 1–20) is advice with visible reasons. Urgency never writes the severity field.

### 2.1 Score = impact × immediacy, bands LOW 1–4 · MEDIUM 5–9 · HIGH 10–14 · URGENT 15–20

| Impact | Meaning | Examples in `hazards.json` |
|---|---|---|
| 5 | life threat | fire, explosion, unconscious / not breathing, weapon, structural collapse |
| 4 | serious | gas leak, smoke, bleeding, robbery / burglary, live wire, chemical, fainted |
| 3 | hazard to people | trapped, sparks, fight, flood, spill, minor injury, suspicious person |
| 2 | fault | leak, power cut, equipment not working, blockage, bad smell |
| 1 | nuisance | pothole, flicker, dirty, paint |

| Immediacy | Meaning | How it is decided |
|---|---|---|
| 4 | happening now / escalating | "still", "since morning", "spreading", "abhi bhi"; fire/gas/crime in present tense; danger sound |
| 3 | present or unresolved | present state of a hazard; past hazard with no resolution |
| 2 | today | fault in present tense; plain "is leaking"; hazard reported as isolated / fixed / out |
| 1 | can wait | reporter says "not urgent" (**only** for impact ≤ 2; a real hazard is never talked down) |

Worked examples **[MEASURED]**: pothole = 1×2 = 2 · isolated leak (the deck) = 2×2 = 4 · "still leaking" = 2×4 = 8 · "my house is on fire" = 5×4 = 20 · "robber with a gun right now" = 5×4 = 20.

### 2.2 Live per chunk, never lowered [DECIDED rule]
After every chunk the *whole* transcript is re-scored and the session keeps the **maximum**. A calm follow-up, "nobody hurt", or "it is out now" cannot lower it (property-tested over 200 random chunk sequences). Only the user can lower the **effective** level; that is journaled, and a later higher automatic level retires a stale override.

### 2.3 Different treatment by urgency (`TriageLanes`) [PROPOSED]
| Level | Questions per turn | Must ask | Confirm everything? | "Submit now"? | "Call 112" line |
|---|---|---|---|---|---|
| URGENT | 1 | location only | no | yes | yes |
| HIGH | 1 | location, incident type | no | yes | no |
| MEDIUM | 2 | type, location, date/time, severity (spec D6) | no | no | no |
| LOW | 2 | the above + equipment, immediate actions, witnesses | **yes** | no | no |

Strictness never increases with urgency (property-tested). D's `FollowUpPlanner` should obey `PriorityAdvisor.lane()`.

### 2.4 Brute force: four strategies, same features [MEASURED]
`risk-matrix` (I×M) · `additive` (points) · `protocol-tree` (ordered rules) · `authored-rules` (max of rules in `priority_rules.json`). Cost = 3 × under-triage + over-triage.

| Set | n | Strategy | Band accuracy | Under-triage | Over-triage |
|---|---|---|---|---|---|
| dev (tuned on) | 53 | risk-matrix | 0.96 | 0.00 | 0.04 |
| dev | 53 | additive | 0.92 | 0.00 | 0.08 |
| dev | 53 | protocol-tree / authored-rules | 0.96 | 0.00 | 0.04 |
| holdout 1, **first run** (before fixes) | 20 | all four | **0.20** | **0.60** | 0.20 |
| holdout 1, after fixes (now contaminated) | 20 | risk-matrix | 0.40 | 0.35 | 0.25 |
| holdout 2, **unseen** at first run | 20 | all four | **0.55** | **0.25** | 0.20 |

**What this says, plainly:**
1. **The four formulas tie** (within one scenario). The formula is not the bottleneck; **recognising the hazard is**. Brute-forcing formulas buys nothing; effort belongs in recognition.
2. The dev result (0.96) is optimistic: I tuned the lexicon against it. The honest generalisation estimate is **holdout 2: 55 % band accuracy, 25 % under-triage** on 20 hand-written unseen reports. Treat urgency as *advice*, which is how the spec frames it.
3. The first holdout run (60 % under-triage) exposed a design flaw (unrecognised text defaulted to a trivial impact); the fixes are in §3. Holdout 1 is spent; holdout 2 was written before the fixes. Do not tune on either; write holdout 3 for the next round.
4. Labels are **my judgement**, not ground truth. Please review (Q1).

## 3. Defects the measurements exposed (all fixed test-first)
- Unrecognised text defaulted to impact 2 (trivial). Now **3**: unknown must not look trivial, because under-triage costs 3× more.
- "ho gaya" ("it happened") was read as "it is over". A person who "behosh ho gaya" is not resolved.
- "is leaking" escalated like "still leaking". Plain progressive is now a present state for faults; only explicit continuation ("still", "since morning", "subah se") escalates.
- A fight or an injury got an extra "people at risk" boost although people are inherent to it.
- "since morning" and "a burst pipe" weren't treated as continuing.
- Kotlin nested comments: a KDoc containing `/*` broke the build.

## 4. Pipeline as the app should call it
```kotlin
val tam       = TamDetector(TamCueLists.loadDefault())
val extractor = UrgencyFeatureExtractor(HazardLexicon.loadDefault(), tam)
val strategy  = UrgencyStrategies.all(PriorityRules.loadDefault()).single { it.id == UrgencyStrategies.DEFAULT_ID }
val advisor   = PriorityAdvisor(extractor, strategy)
val validator = PayloadValidator(tam, TemporalResolver(ZoneId.of("Asia/Kolkata")))

// per utterance:
val parsed = ToolCallParser.parse(llmOutput)              // ParseResult.Ok | Failure(code, detail, raw)
if (parsed is ParseResult.Ok) {
    val v = validator.validate(parsed.payload, cumulativeTranscript, captureInstant)
    // v.proposals -> D's merge; v.rejections -> shown in the proof strip
}
val out = advisor.onChunk(chunkText, signalsFromBAndYamNet)   // out.advice, out.score, out.spoken (112 line, once)
val lane = advisor.lane()                                      // obey in the follow-up planner
```
Resources to bundle: `core/src/main/resources/{tam,priority,policies}/*.json` ship inside the `:core` jar; no copy is needed unless A wants them under `app/assets`.

## 5. Questions for you (defaults apply until you answer)

1. **[Q1] Review the labels.** Three files in `core/src/test/resources/priority/` (dev 53, holdout 20, holdout 2 20), each line `{"text", "target" 1–20}`. Are my targets right for your sense of urgency? Please change any and add the ones you had in mind (pothole, house fire, robber). Better still: a second person labels them independently, so we can measure agreement.
2. **[Q2] Which strategy is the default?** They tie. I set `risk-matrix` because it is the easiest to explain to judges ("impact × immediacy"). `authored-rules` is the one a non-programmer can edit (`priority_rules.json`). Either OK?
3. **[Q3] Lowering urgency.** Today only the user can lower the effective level (journaled). Should a spoken explicit correction ("sorry, not a fire, it was a drill") offer a one-tap "lower to LOW?" chip? [PROPOSED: yes as a chip, never automatically.]
4. **[Q4] URGENT lane skips questions.** URGENT asks only for the location and offers "Submit now", skipping type, date/time and severity. That **changes spec D6** (they are mandatory) for urgent cases. It needs the lead's OK, and I'd want D's planner to read `lane().mandatoryFields`. OK?
5. **[Q5] Unknown reports are MEDIUM.** A report with no recognised hazard scores impact 3 × immediacy 2 = 6 (MEDIUM), so it gets the standard question set, not the thorough LOW lane. Cost: a trivial unrecognised report asks 2–3 extra questions. Accept?
6. **[Q6] LLM hint (the real fix for recognition).** Rules alone under-triage about 1 in 4 unseen reports. I propose adding two optional fields to `update_report`: `"impact_hint": 1-5` and `"ongoing": true|false`, and using them as a **floor raised by rules, never lowered** (`final = max(rules, llm)`). It costs about 15 output tokens and one grammar field, and it needs spec §4 changed (A/B own the grammar and prompt). The LLM is unreliable at 1–2B, which is why it may only raise. Try it?
7. **[Q7] "not fixed yet".** "the leak has not stopped" currently has aspect NONE (a negated completion is not completion). Safety says it should count as ongoing and raise urgency. Do it? (small change in `TamDetector` + 2 tests.)
8. **[Q8] Distress words raise immediacy +1** ("help", "hurry", "jaldi", "!!"). People also say "please hurry up and fix the AC". Over-triage risk is low (+1 only, never a score jump to URGENT), but is it acceptable, or should distress only count together with a hazard?
9. **[Q9] Hindi.** Who reviews `cues_hi.json` and the Hindi/Devanagari entries in `hazards.json`? Cue lists are data, so a native speaker can edit them without touching code. Should I stub Telugu cues now (lane is experimental, D19)?
10. **[Q10] Shared types.** I've defined `Tam/Tense/Aspect/Modality`, `UrgencyAssessment/UrgencyReason`, `TriageLane`, `FieldProposal/Rejection/ValidationResult`, `ExtractionPayload`, and `FieldValue.explicitNone/unknown`. The plan's Task 1 `FieldStatus` is replaced by spec §4b's `ValueStatus`. These need announcing to **A** and **D** before anyone merges. May I send a short message?
11. **[Q11] Gradle.** I added a minimal root `settings.gradle.kts` + `:core` + wrapper (8.14) so tests run now. A's skeleton adds `:app`; expect a trivial merge in `settings.gradle.kts`/`.gitignore`. A should keep `:core`'s Kotlin 2.1.21 or tell me the version in use.
12. **[Q12] Push.** Commits are local on `c/core-rules` only. Push this branch to origin so the others can pull it? (Nothing goes to `master`.)

## 6. Known limits (honest)
- Hazard recognition is a keyword lexicon: anything unlisted becomes "unknown, impact 3". Real coverage needs E's recordings and Q6.
- Commas do not split TAM clauses (lists of names would break negation scope), so "we will call maintenance, it is still leaking" is one PLANNED clause that is still ONGOING.
- Hindi cue lists are my best effort; **not reviewed by a native speaker**.
- `TemporalResolver` handles the common English/Hinglish forms, not the full language ("day before yesterday", "last Tuesday", "noon" are unhandled).
- SenseVoice and YamNet signals are accepted as input and tested with fakes only; B's producers don't exist yet.

## References (ideas, not code)
ITIL priority matrix (impact × urgency) · risk matrices · Medical Priority Dispatch determinant levels · early-warning point scores · ClarifyMT-Bench / structured-output papers noted in `.claude/skills/incident-report-prior-art`.
