# E-20261011: sound-alike set (clean vs mis-heard) and a tighter grammar schema

- **When:** 2026-10-11, 00:30 to 00:50 IST. **Who:** E (AI assistant under E's direction).
- **Goal:** (1) D's question: does the LLM recover sound-alike words ("miner leak", "fair alarm", "seminar hall to") from context? (2) Remove the parse failures left in grammar mode.

## Setup
- **Model / runtime:** Qwen3-1.7B Q4_0, llama.cpp b11541 CPU on a laptop (WSL, 3 GB RAM), **not the kit**. Temperature 0, seed 42, thinking off, grammar on.
- **Data:** `gold_homophones.jsonl`, 8 items (h01–h08). Each is run twice: `--variant clean` (correct text) and `--variant misheard` (the same words as Whisper might write them, lower-case, no punctuation). Also the first 20 dev items of `gold_v1.jsonl`.
- **Schema v1:** `prompts/update_report.schema.json` as in round 1. **Schema v2:** evidence values `minLength 1`; strings `maxLength 120`; lists `maxItems 6`.
- **Config:** raw (parsed payload written straight in; no grounding, no gazetteer).

## Results
| Run | Valid calls | Overall recall | Mandatory recall | Location recall | Hallucination |
|---|---|---|---|---|---|
| Homophones, clean, schema v1 | 6/8 | 14/30 | 13/23 | 6/8 | 1/34 |
| Homophones, mis-heard, schema v1 | 5/8 | 6/30 | 5/23 | 1/8 | 0/34 |
| Homophones, clean, schema v2 | 7/8 | 15/30 | 14/23 | — | 1/34 |
| Homophones, mis-heard, schema v2 | 6/8 | 8/30 | 7/23 | 2/8 | 0/34 |
| gold_v1 dev 20, schema v1 (round 1) | 19/20 | 70/98 | 53/61 | — | 16/62 |
| gold_v1 dev 20, schema v2 | 19/20 | **74/98** | **55/61** | 17/19 | 16/62 |

Per item, schema v1 (type / location as the model wrote them; "—" = call rejected):

| id | mis-heard input (excerpt) | gold | clean | mis-heard |
|---|---|---|---|---|
| h01 | it happened last weak the ceiling in seminar hall to… | water_leak_flood / Seminar Hall 2 | water_leak_flood / Seminar Hall 2 | water_leak_flood / ceiling in seminar hall |
| h02 | its a miner leak from the r o plant in yuktahar… | water_leak_flood / Yuktahar | water_leak_flood / Yuktahar | — |
| h03 | the fair alarm went off in k r b at around nine… | fire_smoke / KRB | fire_smoke / KRB | — (v2: security / k r b) |
| h04 | there was a water leek near seminar hall to around… | water_leak_flood / Seminar Hall 2 | water_leak_flood / Seminar Hall 2 | water_leak_flood / seminar hall |
| h05 | for people were in the room when the projector in himalaya one oh five… | fire_smoke / Himalaya 105 | — | other / himalaya one oh five |
| h06 | to chairs were broken in vindhya c for this morning… | property_damage / Vindhya C4 | — / Vindhya C4 | — / vindhya c |
| h07 | the chilla in k r b is making a loud grinding noise… | equipment_failure / KRB | — | — |
| h08 | someone fainted outside himalaya three oh five… | illness / outside Himalaya 305 | injury / Himalaya 305 | injury / Himalaya three oh five |

## Findings
- **The 1.7B model does not repair sound-alikes from context; it copies them.** Location recall drops from 6/8 to 1/8 (v1) when the text is mis-heard: "seminar hall to" becomes "seminar hall", "k r b" stays "k r b", "vindhya c for" becomes "vindhya c", "himalaya one oh five" is kept verbatim. "Fair alarm" was read as a **security** incident. Correction has to come from the gazetteer (D), which runs after the LLM on the transcript, not from the model.
- **Strings D can add as aliases** (model-produced, from mis-heard text): "seminar hall" (for Seminar Hall 2, ambiguous with 1 and 3), "k r b", "vindhya c", "himalaya one oh five", "himalaya three oh five", "chilla", "yuktahar" (lower case).
- **Empty evidence strings were the main remaining failure** in grammar mode (C's parser turns "" into null and rejects the call); `minLength 1` removed them.
- **Remaining failures (4 calls across all v2 runs):** the model writes `"}}}</think>[]</think>…<think>"` inside a string value. Both parsers strip `<think>` before scanning, even inside strings, so the call reads as truncated. Next: forbid `< > { }` in string values in the schema.
- Witnesses and persons remain the hallucination source (dev: witness precision 0/4, persons 3/8). The grounding layer is the fix; measure "+grounding" next.

## Limits
Laptop CPU, gold text not audio, 8 sound-alike items (wide intervals), one model. The mis-heard text is hand-written to look like Whisper output; real Whisper output from the kit clips replaces it once recorded.

## Next step
Schema v3 (no `< > { }` in strings); the same runs on NuExtract-2.0-2B and Gemma; send D the alias list above; "+grounding" through A's pipeline once it is pushed.
