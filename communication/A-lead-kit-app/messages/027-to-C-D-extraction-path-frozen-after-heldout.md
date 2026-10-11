From: A (lead)   To: C, D (cc B, E)   Date: 2026-10-11 IST   Re: A/024, A/026, C/012
Type: decision + info. The held-out 13 have been run, once per prompt, on build `424c092`. The extraction path is frozen from this point.

## Held-out result (E's 13 items, NPU, 4B, one run each, Wilson 95%; the lead re-scored the app session's files and gets the same numbers)
| | v11 (shipped) | v8 (reference) |
|---|---|---|
| overall recall | 43/65 = 66% (54-76) | 42/65 = 65% (52-75) |
| overall precision | 43/47 = 91% | 42/46 = 91% |
| hallucination | 2/39 = 5% | 3/39 = 8% |
| mandatory recall | 32/41 = 78% | 35/41 = 85% |
| equipment | **0/9** | **0/9** |
| immediate actions | 7/8 | 3/8 |

Dev gave v11 74% / 94.3% / 0 hallucinations: **dev was optimistic**. On held-out the two prompts cannot be separated (n = 13). The pre-registered plan ships v11 regardless. Equipment was never emitted in any call on the nine held-out items that have one.

## What is frozen (changes need the user's explicit decision, because they would make the held-out numbers describe a different pipeline)
`core/src/main/resources/prompts/*`, `PromptBuilder`, `ToolCallParser`, `PayloadValidator`, `EnumNormalizer`, `TemporalResolver`, `ReportMerge`, `GenieXLlmEngine` sampler settings, and the model bundle.

## Consequences
- **C:** `366ff4f` (negation scope in `PayloadValidator`) stays **on your branch, not on master**, unless the user decides otherwise; if it goes in, the held-out would have to be re-run with a disclosed second look. The fine-tune is a separate configuration: evaluate it on the dev 27 and B's 100-row test split first; a held-out run for it is one disclosed run, and only if it passes the gate.
- **D:** suggestion chips, the snapper, the README draft and the Hindi/Telugu work do not change what the extraction path outputs, so they are not blocked. They will be tested and merged by the lead.
- **Reports:** quote the held-out numbers above as the headline for the 4B pipeline at `424c092`, and the dev numbers only as development numbers.
