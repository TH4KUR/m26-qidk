### 20261011-0030-qwen3-1.7b-q4_0-cpu-grammar-all-gold_homophones-clean
Items: 8 (missing predictions: 0)

| field | precision | recall | hallucination | correct / wrong / miss |
|---|---|---|---|---|
| incident_type | 4/5 (80%) | 4/8 (50%) | n/a (0/0) | 4 / 1 / 3 |
| severity | 1/1 (100%) | 1/1 (100%) | 0/7 (0%) | 1 / 0 / 0 |
| location | 6/6 (100%) | 6/8 (75%) | n/a (0/0) | 6 / 0 / 2 |
| date_time | 2/2 (100%) | 2/6 (33%) | 0/2 (0%) | 2 / 0 / 4 |
| persons_involved | n/a (0/0) | n/a (0/0) | 0/8 (0%) | 0 / 0 / 0 |
| equipment_asset | n/a (0/0) | 0/5 (0%) | 0/3 (0%) | 0 / 0 / 5 |
| immediate_actions | 1/1 (100%) | 1/2 (50%) | 0/6 (0%) | 1 / 0 / 1 |
| witnesses | 0/1 (0%) | n/a (0/0) | 1/8 (12%) | 0 / 0 / 0 |
| **mandatory** | 13/14 (93%) | 13/23 (57%) | 0/9 (0%) | 13 / 1 / 9 |
| **overall** | 14/16 (88%) | 14/30 (47%) | 1/34 (3%) | 14 / 1 / 15 |

Follow-up precision 9/18 (50%), recall 9/9 (100%)
EXPLICIT_NONE agreement 0/1 (0%)
UNKNOWN agreement n/a (0/0)
SUGGESTED agreement n/a (0/0)

Valid tool calls: 6/8; mean output tokens 87.0; max prompt tokens 446
