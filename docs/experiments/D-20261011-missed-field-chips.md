# D · 2026-10-11 · Chips for equipment and actions the model did not emit (T2, A/023)

- **Who:** D · **When:** 05:45-06:00 IST
- **Goal:** A/023 T2. When the model leaves `equipment_asset` or `immediate_actions` empty, offer a chip with the speaker's own words (never a fill). Measure right chip / wrong chip / missed / false alarm per field.
- **Code:** `core/src/main/kotlin/com/megathon/incident/suggest/MissedFieldRules.kt`, called by `RuleSuggestionEngine` (new optional constructor parameter `tam: TamDetector`, default reads the bundled cue lists, so the app's existing constructor call is unchanged). `SuggestionEngine` interface unchanged. Tests: `MissedFieldRulesTest` (26). CPU, rules, no model.
- **Rules:** equipment = nouns from a lexicon D wrote from general knowledge (campus, office, kitchen, lab, workshop, warehouse, vehicles, furniture, goods), skipped in clauses C's `TamDetector` marks negated, hedged or planned, inside the location value, before a place word ("printer room"), or inside the speaker's own action phrase. Actions = I/we + perfect, progressive or simple past of a verb from a list of response verbs, asserted only, trimmed from the verb to the end of that clause part. One chip per field (only one chip can be accepted into an empty field).
- **What D had seen before writing the lists:** gold item `g01` (while checking the file format) and the examples A quoted in A/023 from the kit run (bicycle, bulk bags, parked car, bikes, vending machine; "I've told the security guard", "I'm calling the ambulance now", "I've unplugged it"). Those A/023 examples come from dev items, so the lists are **not independent of the dev split**.
- **Setup:** gold `app/src/main/assets/eval/gold_v1.jsonl` at master `a2b68d2`, **`split == "dev"` only (27 items)**; holdout rows are dropped by the harness before anything else is read and were never printed. Transcript = the item's `turns` joined with spaces (authored text, not recognition output). State = gold location as REPORTED, equipment and actions MISSING (the case where the model emitted neither field). Matching = E's `score.py` rule (content tokens, same stop and number words, dice >= 0.8 against value or any alt), ported into the harness. Harness: `core/src/test/kotlin/.../suggest/MissedFieldEvaluation.kt`, run with
  `MISSED_EVAL_GOLD=<abs path to gold_v1.jsonl> MISSED_EVAL_OUT=<report> ./gradlew --no-daemon :core:test --tests "*MissedFieldEvaluation"` (JDK 17.0.20, Gradle 9.1.0, Windows/Git Bash).
- **Looks at dev results: 2. Rule changes after looking: 1.**

## Run 1 (lists as first written)
| field | right chip | wrong chip | missed | false alarm | right no-chip |
|---|---|---|---|---|---|
| equipment_asset | 11 | 4 | 4 | 2 | 6 |
| immediate_actions | 13 | 0 | 8 | 0 | 6 |

One pattern behind a false alarm (g26 "first aid kit") and a wrong chip (g35 "extinguisher", gold is another object): the noun was the tool the speaker used to respond. **Change (test-first, `a tool the speaker used to respond is an action, not the equipment involved`):** equipment nouns inside the speaker's own action phrase are skipped. No other change.

## Run 2 (final, shipped)
| field | right chip | wrong chip | missed | false alarm | right no-chip |
|---|---|---|---|---|---|
| equipment_asset | 11 | 3 | 5 | 1 | 7 |
| immediate_actions | 13 | 0 | 8 | 0 | 6 |

Label for every number above: **dev split, n = 27 items, authored text, rules written after reading the A/023 examples; run 2 after one change made by looking at run 1.** Not a result on unseen data. The holdout run is E's, once, at the end.

**Residual errors (run 2), by kind:**
- Equipment wrong (3): the chip names the head noun without its modifier or names a different object in the sentence: "printer" vs "3D printer", "pipe" vs "water pipe", "rack" vs "main network switch".
- Equipment missed (5): 2 nouns not in the lexicon ("pan", "power lines"); 3 nouns in a clause `TamDetector` marks negated or hedged, because its clauses do not split on commas or "and" (checked by reading the three dev sentences after run 2): "no power ... and the UPS ..." (negated), "I think the fire alarm went off" (hedged; skipping is the intended behaviour), "The projector ... caught fire at ten, no wait, half past ten" (the self-correction "no wait" reads as negation). Not tuned further, to avoid fitting the dev set; the clause scope is C's, noted for C.
- Equipment false alarm (1): g05, lab objects named in a spill where the gold has no equipment.
- Actions missed (8): 3 are third person ("Maintenance has already isolated the area"; by design, A/023 asks for first person only), the rest use verbs not on the list (cut, noted down, calmed, filed) or phrasing the pattern does not catch. No false alarm and no wrong chip.

## Honest limits
- **These chips do not raise the model's measured recall.** The scorer counts only REPORTED, USER_CONFIRMED and USER_EDITED; an unaccepted chip is invisible to it. They help the person using the app; a tapped chip becomes USER_CONFIRMED.
- Input here is the authored sentence, not Whisper output, and the state assumes the model emitted neither field. On the kit the model sometimes fills one of them, and then no chip is made for it.
- English only. Hindi/Telugu sentences get no chip (English lexicon and verb list).

## Run 2 per item (dev)
| id | field | gold | chip | outcome |
|---|---|---|---|---|
| g01 | equipment_asset |  |  | RIGHT_NO_CHIP |
| g01 | immediate_actions | isolated the area |  | MISSED |
| g02 | equipment_asset | microwave | microwave | RIGHT_CHIP |
| g02 | immediate_actions | switched off the power at the mains | switched off the power at the mains | RIGHT_CHIP |
| g04 | equipment_asset | UPS |  | MISSED |
| g04 | immediate_actions | informed the electrical maintenance team | informed the electrical maintenance team | RIGHT_CHIP |
| g05 | equipment_asset |  | bottle, fume hood | FALSE_ALARM |
| g05 | immediate_actions | opened the windows and put the spill kit on it | opened the windows and put the spill kit on it | RIGHT_CHIP |
| g07 | equipment_asset | computers | computers | RIGHT_CHIP |
| g07 | immediate_actions | covered the desktops with a plastic sheet | covered the desktops with a plastic sheet | RIGHT_CHIP |
| g08 | equipment_asset | bicycle | bicycle | RIGHT_CHIP |
| g08 | immediate_actions | told the security guard at the main gate | told the security guard at the main gate | RIGHT_CHIP |
| g10 | equipment_asset | parked car | car | RIGHT_CHIP |
| g10 | immediate_actions | put a cone next to it and informed the estate office | put a cone next to it and informed the estate office | RIGHT_CHIP |
| g11 | equipment_asset | main network switch | rack | WRONG_CHIP |
| g11 | immediate_actions | cut the power to the rack |  | MISSED |
| g13 | equipment_asset | delivery van | van | RIGHT_CHIP |
| g13 | immediate_actions | noted down the van number |  | MISSED |
| g14 | equipment_asset | pan |  | MISSED |
| g14 | immediate_actions | put it out with the fire blanket |  | MISSED |
| g16 | equipment_asset | vending machine | vending machine | RIGHT_CHIP |
| g16 | immediate_actions |  |  | RIGHT_NO_CHIP |
| g17 | equipment_asset | 3D printer | printer | WRONG_CHIP |
| g17 | immediate_actions | unplugged it | unplugged it | RIGHT_CHIP |
| g19 | equipment_asset | fire alarm |  | MISSED |
| g19 | immediate_actions |  |  | RIGHT_NO_CHIP |
| g20 | equipment_asset |  |  | RIGHT_NO_CHIP |
| g20 | immediate_actions | gave her an ice pack | gave her an ice pack | RIGHT_CHIP |
| g22 | equipment_asset | forklift | forklift | RIGHT_CHIP |
| g22 | immediate_actions | stopped all forklift movement in that aisle | stopped all forklift movement in that aisle | RIGHT_CHIP |
| g23 | equipment_asset | hot water heater | water heater | RIGHT_CHIP |
| g23 | immediate_actions |  |  | RIGHT_NO_CHIP |
| g25 | equipment_asset | lamp | lamp | RIGHT_CHIP |
| g25 | immediate_actions | applying pressure with a towel | applying pressure with a towel | RIGHT_CHIP |
| g26 | equipment_asset |  |  | RIGHT_NO_CHIP |
| g26 | immediate_actions | cleaned the wound with the first aid kit | cleaned the wound with the first aid kit | RIGHT_CHIP |
| g28 | equipment_asset |  |  | RIGHT_NO_CHIP |
| g28 | immediate_actions | calmed the patient down |  | MISSED |
| g29 | equipment_asset |  |  | RIGHT_NO_CHIP |
| g29 | immediate_actions | filed a police complaint |  | MISSED |
| g31 | equipment_asset | bulk bags | bulk bags | RIGHT_CHIP |
| g31 | immediate_actions | calling the ambulance | calling the ambulance now | RIGHT_CHIP |
| g32 | equipment_asset | power lines |  | MISSED |
| g32 | immediate_actions | cut the supply |  | MISSED |
| g34 | equipment_asset |  |  | RIGHT_NO_CHIP |
| g34 | immediate_actions | isolated the area |  | MISSED |
| g35 | equipment_asset | projector |  | MISSED |
| g35 | immediate_actions | used the extinguisher | used the extinguisher | RIGHT_CHIP |
| g37 | equipment_asset |  |  | RIGHT_NO_CHIP |
| g37 | immediate_actions |  |  | RIGHT_NO_CHIP |
| g38 | equipment_asset | water pipe | pipe | WRONG_CHIP |
| g38 | immediate_actions |  |  | RIGHT_NO_CHIP |
| g40 | equipment_asset | bikes | bikes | RIGHT_CHIP |
| g40 | immediate_actions |  |  | RIGHT_NO_CHIP |
