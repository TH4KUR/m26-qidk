### 20261011-0033-qwen3-1.7b-q4_0-cpu-schema2-grammar-all-gold_homophones-clean
Items: 8 (missing predictions: 0)

| field | precision | recall | hallucination | correct / wrong / miss |
|---|---|---|---|---|
| incident_type | 4/5 (80%) | 4/8 (50%) | n/a (0/0) | 4 / 1 / 3 |
| severity | 1/1 (100%) | 1/1 (100%) | 0/7 (0%) | 1 / 0 / 0 |
| location | 6/6 (100%) | 6/8 (75%) | n/a (0/0) | 6 / 0 / 2 |
| date_time | 3/3 (100%) | 3/6 (50%) | 0/2 (0%) | 3 / 0 / 3 |
| persons_involved | n/a (0/0) | n/a (0/0) | 0/8 (0%) | 0 / 0 / 0 |
| equipment_asset | 0/1 (0%) | 0/5 (0%) | 0/3 (0%) | 0 / 1 / 4 |
| immediate_actions | 1/1 (100%) | 1/2 (50%) | 0/6 (0%) | 1 / 0 / 1 |
| witnesses | 0/1 (0%) | n/a (0/0) | 1/8 (12%) | 0 / 0 / 0 |
| **mandatory** | 14/15 (93%) | 14/23 (61%) | 0/9 (0%) | 14 / 1 / 8 |
| **overall** | 15/18 (83%) | 15/30 (50%) | 1/34 (3%) | 15 / 2 / 13 |

Follow-up precision 9/17 (53%), recall 9/9 (100%)
EXPLICIT_NONE agreement 0/1 (0%)
UNKNOWN agreement n/a (0/0)
SUGGESTED agreement n/a (0/0)

Valid tool calls: 7/8; mean output tokens 90.5; max prompt tokens 446
