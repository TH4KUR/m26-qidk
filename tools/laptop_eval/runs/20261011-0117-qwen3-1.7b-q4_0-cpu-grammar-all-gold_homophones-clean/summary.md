### 20261011-0117-qwen3-1.7b-q4_0-cpu-grammar-all-gold_homophones-clean
Items: 8 (missing predictions: 0)

| field | precision | recall | hallucination | correct / wrong / miss |
|---|---|---|---|---|
| incident_type | 4/5 (80%) | 4/8 (50%) | n/a (0/0) | 4 / 1 / 3 |
| severity | 1/1 (100%) | 1/1 (100%) | 0/7 (0%) | 1 / 0 / 0 |
| location | 7/7 (100%) | 7/8 (88%) | n/a (0/0) | 7 / 0 / 1 |
| date_time | 3/3 (100%) | 3/6 (50%) | 0/2 (0%) | 3 / 0 / 3 |
| persons_involved | n/a (0/0) | n/a (0/0) | 0/8 (0%) | 0 / 0 / 0 |
| equipment_asset | 1/1 (100%) | 1/5 (20%) | 0/3 (0%) | 1 / 0 / 4 |
| immediate_actions | 1/1 (100%) | 1/2 (50%) | 0/6 (0%) | 1 / 0 / 1 |
| witnesses | n/a (0/0) | n/a (0/0) | 0/8 (0%) | 0 / 0 / 0 |
| **mandatory** | 15/16 (94%) | 15/23 (65%) | 0/9 (0%) | 15 / 1 / 7 |
| **overall** | 17/18 (94%) | 17/30 (57%) | 0/34 (0%) | 17 / 1 / 12 |

Follow-up precision 9/16 (56%), recall 9/9 (100%)
EXPLICIT_NONE agreement 0/1 (0%)
UNKNOWN agreement n/a (0/0)
SUGGESTED agreement n/a (0/0)

Valid tool calls: 7/8; mean output tokens 88.875; max prompt tokens 445
