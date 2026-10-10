### 20261011-0034-qwen3-1.7b-q4_0-cpu-schema2-grammar-all-gold_homophones-misheard
Items: 8 (missing predictions: 0)

| field | precision | recall | hallucination | correct / wrong / miss |
|---|---|---|---|---|
| incident_type | 3/5 (60%) | 3/8 (38%) | n/a (0/0) | 3 / 2 / 3 |
| severity | n/a (0/0) | 0/1 (0%) | 0/7 (0%) | 0 / 0 / 1 |
| location | 2/6 (33%) | 2/8 (25%) | n/a (0/0) | 2 / 4 / 2 |
| date_time | 2/2 (100%) | 2/6 (33%) | 0/2 (0%) | 2 / 0 / 4 |
| persons_involved | n/a (0/0) | n/a (0/0) | 0/8 (0%) | 0 / 0 / 0 |
| equipment_asset | n/a (0/0) | 0/5 (0%) | 0/3 (0%) | 0 / 0 / 5 |
| immediate_actions | 1/1 (100%) | 1/2 (50%) | 0/6 (0%) | 1 / 0 / 1 |
| witnesses | n/a (0/0) | n/a (0/0) | 0/8 (0%) | 0 / 0 / 0 |
| **mandatory** | 7/13 (54%) | 7/23 (30%) | 0/9 (0%) | 7 / 6 / 10 |
| **overall** | 8/14 (57%) | 8/30 (27%) | 0/34 (0%) | 8 / 6 / 16 |

Follow-up precision 9/19 (47%), recall 9/9 (100%)
EXPLICIT_NONE agreement 0/1 (0%)
UNKNOWN agreement n/a (0/0)
SUGGESTED agreement n/a (0/0)

Valid tool calls: 6/8; mean output tokens 91.5; max prompt tokens 445
