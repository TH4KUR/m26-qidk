# Person C: core rules design and open questions

Branch `c/core-rules` · 2026-10-10 · Owner: C.

**Sources:** handoff `docs/handoff/C-core-rules.md`; spec D16–D21 and §4b; plan Addendum A (A5, A7); codex plan §5.2–5.5 (adopted as tests); dispatch and triage practice (references at the end).

Tags: **[DECIDED]** follows the spec or handoff, **[PROPOSED]** is my default until you answer, **[Q#]** needs your call.

## 1. What C delivers (pure Kotlin, `:core`, no Android imports)

| Package | Content | Status |
|---|---|---|
| `domain` | Shared types: `IncidentType`, `Severity`, `Field`, `ValueStatus`, `Source`, `FieldValue`, `IncidentReport`, `ExtractionPayload`, `Signal`, `Suggestion`, `Tam`, `Priority`, `PriorityAdvice`, `UrgencyAssessment`, `TriageLane` | [DECIDED] types from spec §4b; urgency types [PROPOSED] |
| `llm` | `ToolCallParser`: lenient extraction, strict validation | [DECIDED] |
| `domain` | `EnumNormalizer`, `SeverityRules` (suggests only), `TemporalResolver` (keeps precision, never invents a date) | [DECIDED] |
| `domain` | `GroundingValidator` | [DECIDED] |
| `tam` | `TamDetector`: clause-level tense, aspect and modality, with English + Hindi (Devanagari and romanised) cue lists in editable JSON | [DECIDED] |
| `priority` | `UrgencyScorer` (1–20) → `PriorityAdvice` (LOW/MEDIUM/HIGH/URGENT) → `TriageLane` | [PROPOSED] (your idea, below) |

## 2. Your 1–20 idea, made concrete

**Two things that must not be mixed (spec D18):**
- **Severity** is a report *field*: what the reporter states or confirms (`none|minor|moderate|severe|critical`). Rules may only *suggest* it.
- **Urgency/priority** is *advice*: how fast someone must act, with visible reasons. **Your 1–20 score is urgency.** It may drive how the app behaves, but it never writes the severity field.

### 2.1 The score: impact × immediacy = 1…20 [PROPOSED]
This is the standard risk-matrix / ITIL "impact × urgency" idea, sized to your 1–20 range.

| Impact (I) | Meaning | Examples |
|---|---|---|
| 1 | Nuisance or cosmetic | flickering light, small pothole, dirty floor |
| 2 | Property or service disruption, no one at risk | leak already isolated, broken AC, jammed printer |
| 3 | A hazard to people is possible, or a minor injury | spill on a walkway, exposed wire, minor cut, stuck lift (nobody inside) |
| 4 | Serious harm likely, or a crime against property | injury needing hospital, burglary discovered, gas smell, person stuck in lift |
| 5 | Life threat or many people | fire spreading, unconscious person, assault, armed robbery, explosion |

| Immediacy (M) | Meaning | Text cues |
|---|---|---|
| 1 | Can wait days | "whenever", "not urgent", cosmetic |
| 2 | Today | completed or contained: "already isolated", "fixed for now" |
| 3 | Within the hour | present state, not ongoing: "there is water on the floor" |
| 4 | Happening now or escalating | ongoing aspect: "still leaking", "spreading", "abhi bhi", "ho raha hai"; danger sounds (YamNet alarm, scream) |

`score = I × M`. Bands: **1–4 LOW, 5–9 MEDIUM, 10–14 HIGH, 15–20 URGENT**.
- Pothole I1×M1 = 1 (LOW)
- Isolated water leak I2×M2 = 4 (LOW)
- Leak spreading near electrics I3×M4 = 12 (HIGH)
- House fire I5×M4 = **20**
- Robbery in progress I5×M4 = 20; burglary discovered later I4×M3 = 12

Every score carries its reasons, e.g. `["incident_type=fire_smoke → impact 5 (RULE)", "‘still spreading’ → immediacy 4 (TEXT_CUE)"]`.

### 2.2 Live re-scoring per chunk, never lowered [DECIDED rule, PROPOSED mechanics]
- After every transcript chunk, recompute from the **whole transcript so far**.
- Session score = **max(previous, new)**, a ratchet. Signals can only raise (D18). Calm voice, "nobody hurt", or a missing signal never lowers it.
- **Only two things can lower it:** a user override (journaled), or an explicit correction the user confirms with a tap ("sorry, not a fire, it was a drill" → a suggestion chip "Lower urgency to LOW?"). [Q3]
- Negated hazards never raise in the first place: "there is **no** fire" doesn't count as fire (TAM negation scope).

### 2.3 Different treatment by urgency: the triage lane [PROPOSED; the planner is D's code]
This is your "fast for high, thorough for low" idea. It also mirrors how dispatch protocols work: life-threat calls get the fewest questions before help is sent.

| Lane | Score | Behaviour |
|---|---|---|
| **URGENT** | 15–20 | Speak "If anyone is in danger, call 112 now." first. Ask **only location** if missing (1 question). No confirmation loop. "Submit now" is shown immediately; everything else can be completed later. Short TTS. |
| **HIGH** | 10–14 | Ask location + incident type only. 1 question per turn. Quick read-back of key fields. |
| **MEDIUM** | 5–9 | Standard D6 mandatory set (type, location, date/time, severity). ≤ 2 questions per turn. |
| **LOW** | 1–4 | Thorough: mandatory + optional fields (equipment, witnesses, actions). Every suggestion chip must be confirmed. Full read-back before submit. Aim for a 100 % confirmed report. |

C produces `TriageLane(level, maxQuestionsPerTurn, mandatoryFields, requireConfirmation, speak112)`. D's `FollowUpPlanner` consumes it. [Q4]

### 2.4 "Brute-force" the scoring: four strategies, chosen by measurement [PROPOSED]
You asked to try every idea when the best isn't clear. All four implement one interface, run on the same labelled catalogue, and the winner is picked by numbers.

| Strategy | Idea | From |
|---|---|---|
| **S1 RiskMatrix** | `I × M` as above | Risk matrices / ITIL impact × urgency |
| **S2 Additive points** | Base points per incident type + points per cue (ongoing +4, people at risk +3, crime in progress +5, distress +2…), capped at 1–20 | Early-warning scores (NEWS-style points) |
| **S3 Protocol tree** | Ordered rules, first match wins ("life-threat cue → 20", "crime in progress → 18", …) | Medical Priority Dispatch "determinant" levels (Ω, A…E) |
| **S4 Max-of-rules** | Each rule in `priority_rules.json` gives a *minimum* score; the result is the max. "Only raise" holds by construction; easiest to explain. | Spec D18 |

**Scenario catalogue:** `core/src/test/resources/priority/scenarios.jsonl`, about 60 lines (English + Hindi), each with a target score and band. **You (and E) must review the target labels: they are my proposal, not ground truth.** [Q1]

**Metrics:**
- band accuracy
- mean absolute error on the score
- **under-triage rate**: predicting a lower band than the target. This is the safety metric and is weighted 3× (triage literature treats under-triage as the costly error).

The winner is wired as the default; the others stay as tests and data. [Q2]

## 3. Tense / aspect / modality (A5a) [DECIDED scope, PROPOSED mechanics]
- **Clauses:** split on `. ! ? ; ।` and on conjunctions *but, and then, however, lekin, par, magar, phir, aur phir*. TAM is decided **per clause**; negation scope runs from the cue to the end of its clause.
- **Labels (spec):**
  - tense `PAST|PRESENT|FUTURE|UNKNOWN`
  - aspect `ONGOING|COMPLETED|NONE`
  - modality `ASSERTED|HEDGED|NEGATED|PLANNED`
- **Cue lists:** JSON in `core/src/main/resources/tam/` (`cues_en.json`, `cues_hi.json`), so a Hindi speaker can edit them without touching code. **Hindi lists need a native speaker's review** (handoff).
  - Examples: ongoing "still, keeps, spreading, abhi bhi, ho raha hai, अभी भी"; completed "already, turned off, isolated, ho gaya, kar diya"; planned "will, going to, karenge, bulayenge"; hedged "I think, maybe, shayad, lagta hai, शायद"; negated "no, not, nobody, nahi, koi nahi, नहीं".
- **Effects (D17), applied by validator and merge, not by the detector:**
  - planned → never `immediate_actions` (goes to follow-up)
  - hedged → `UNCONFIRMED` / suggestion chip
  - negated → `EXPLICIT_NONE` ("nobody was hurt" → persons involved: none stated)
  - ongoing → immediacy 4 in the urgency scorer
- **Tonality layer (text):** distress cues ("help", "hurry", "please quickly", "emergency", "jaldi", "bachao", "!!", repeated words) → `Signal(kind="distress", source=TEXT_CUE)`. These raise priority only. SenseVoice/YamNet signals come from B with `Source.SENSEVOICE`/`YAMNET`.

## 4. Parser, normalisers, time, grounding [DECIDED unless marked]
- **ToolCallParser:**
  - Accepts exactly **one** known envelope: `{"tool":"update_report","arguments":{…}}` (GBNF/JSON mode) or Qwen-style `<tool_call>{"name":"update_report","arguments":{…}}</tool_call>` (native mode, D5).
  - Strips ```` ``` ```` fences and `<think>` blocks; tolerates trailing commas.
  - **Fails on:** no envelope, two envelopes, a truncated object, a wrong tool name. Never rescues partial values with regex. Bare argument objects: rejected [Q5].
- **EnumNormalizer:** exact wire value or a synonym table (English + Hindi: "aag" → fire_smoke, "chori" → security, "paani" → water_leak_flood). **Unknown → null, rejected, not coerced to `other`** (codex rule) [Q6].
- **SeverityRules:** returns a `SUGGESTED` severity with reasons, never a fill (D18). It is derived from the impact level: I1 → minor, I2 → minor, I3 → moderate, I4 → severe, I5 → critical. Only a suggestion; the user confirms [Q7].
- **TemporalResolver(captureInstant, zone = Asia/Kolkata):** keeps precision. "around 10 AM" → time 10:00, approximate, **date missing (ask)**. "yesterday evening" → date −1, partOfDay EVENING, no invented 19:00. "2 hours ago" → exact. **"kal" → ambiguous (ask).** "aaj subah" → today + MORNING. A later-today time is never moved to yesterday.
- **GroundingValidator:**
  - grounded = the normalised evidence (NFC, case-folded, whitespace-collapsed, digits and negation kept) is a substring of the cumulative transcript, **and**, for verbatim fields, the value's tokens overlap the evidence ≥ 0.5.
  - With no evidence: the value's token overlap with the transcript ≥ 0.8.
  - Enum fields skip the value-vs-evidence check.

## 5. Questions for you (answer in any order; the defaults apply until you do)
1. **[Q1] Scenario labels:** are the impact and immediacy anchors in §2.1 right for a campus/office setting? Are the 1–20 targets in `scenarios.jsonl` sensible? Add your own examples, especially the ones you had in mind (pothole, house fire, robber).
2. **[Q2] Metric:** is "lowest weighted error with under-triage ×3" the right way to pick the winning strategy, or do you prefer the one that is easiest to explain to judges (S4)?
3. **[Q3] Lowering urgency:** only by user override or a confirmed correction chip? Or should an explicit spoken correction ("not a fire, just a drill") lower it automatically?
4. **[Q4] Lanes:** OK for C to define `TriageLane` and for D's planner to obey it? In the URGENT lane, can we skip the date/time and severity questions (D6 says they are mandatory)? That changes D6 for urgent cases and needs the lead's OK.
5. **[Q5] Bare JSON** (arguments without the envelope): reject (strict, my default) or accept as an envelope-less fallback?
6. **[Q6] Unknown incident type:** reject (codex rule, default) or map to `other`?
7. **[Q7] Severity suggestion from impact:** is that mapping acceptable, or should severity suggestions come only from explicit words ("minor", "serious", "badly hurt")?
8. **[Q8] Hindi:** who on the team can review the Hindi cue lists? Should Telugu cues be stubbed now (lane is experimental, D19)?
9. **[Q9] Shared types:** I am adding `UrgencyAssessment`, `TriageLane`, `Impact`, `Immediacy` to the shared types. I'll announce this to A and D before merging; OK?
10. **[Q10] Gradle:** A owns the skeleton. I'm adding a minimal root `settings.gradle.kts` + `:core` so tests run now; A's skeleton will add `:app` (expect a trivial merge in `settings.gradle.kts` and `.gitignore`). OK?

## References (ideas, not code)
- ITIL incident priority matrix (impact × urgency); risk matrices (likelihood × consequence 5×5)
- Medical Priority Dispatch System determinant levels; Emergency Severity Index (ESI 1–5); NEWS early-warning points
- Codex plan §5.4–5.5 (negation, corrections, temporal precision), adopted as tests per the handoff
- TAM cue idea: spec D17; Hindi progressive "raha/rahi/rahe hai", perfective "gaya/diya", future "-ega/-enge/-egi"
