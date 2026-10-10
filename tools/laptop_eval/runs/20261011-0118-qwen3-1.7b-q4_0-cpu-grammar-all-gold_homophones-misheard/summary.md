### 20261011-0118-qwen3-1.7b-q4_0-cpu-grammar-all-gold_homophones-misheard
Items: 8 (missing predictions: 0)

| field | precision | recall | hallucination | correct / wrong / miss |
|---|---|---|---|---|
| incident_type | 4/6 (67%) | 4/8 (50%) | n/a (0/0) | 4 / 2 / 2 |
| severity | n/a (0/0) | 0/1 (0%) | 0/7 (0%) | 0 / 0 / 1 |
| location | 2/6 (33%) | 2/8 (25%) | n/a (0/0) | 2 / 4 / 2 |
| date_time | 3/3 (100%) | 3/6 (50%) | 0/2 (0%) | 3 / 0 / 3 |
| persons_involved | n/a (0/0) | n/a (0/0) | 0/8 (0%) | 0 / 0 / 0 |
| equipment_asset | 1/1 (100%) | 1/5 (20%) | 0/3 (0%) | 1 / 0 / 4 |
| immediate_actions | n/a (0/0) | 0/2 (0%) | 0/6 (0%) | 0 / 0 / 2 |
| witnesses | 0/1 (0%) | n/a (0/0) | 1/8 (12%) | 0 / 0 / 0 |
| **mandatory** | 9/15 (60%) | 9/23 (39%) | 0/9 (0%) | 9 / 6 / 8 |
| **overall** | 10/17 (59%) | 10/30 (33%) | 1/34 (3%) | 10 / 6 / 14 |

Follow-up precision 9/17 (53%), recall 9/9 (100%)
EXPLICIT_NONE agreement 0/1 (0%)
UNKNOWN agreement n/a (0/0)
SUGGESTED agreement n/a (0/0)

Valid tool calls: 7/8; mean output tokens 87.25; max prompt tokens 444
