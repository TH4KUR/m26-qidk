### 20261011-0115-qwen3-1.7b-q4_0-cpu-grammar-dev-gold_v1-clean
Items: 20 (missing predictions: 0)

| field | precision | recall | hallucination | correct / wrong / miss |
|---|---|---|---|---|
| incident_type | 18/20 (90%) | 18/20 (90%) | n/a (0/0) | 18 / 2 / 0 |
| severity | 4/10 (40%) | 4/4 (100%) | 6/16 (38%) | 4 / 0 / 0 |
| location | 16/20 (80%) | 16/19 (84%) | 1/1 (100%) | 16 / 3 / 0 |
| date_time | 18/19 (95%) | 18/18 (100%) | 1/2 (50%) | 18 / 0 / 0 |
| persons_involved | 3/8 (38%) | 3/5 (60%) | 4/15 (27%) | 3 / 1 / 1 |
| equipment_asset | 6/8 (75%) | 6/14 (43%) | 2/6 (33%) | 6 / 0 / 8 |
| immediate_actions | 11/13 (85%) | 11/17 (65%) | 0/3 (0%) | 11 / 2 / 4 |
| witnesses | 0/4 (0%) | 0/1 (0%) | 3/19 (16%) | 0 / 1 / 0 |
| **mandatory** | 56/69 (81%) | 56/61 (92%) | 8/19 (42%) | 56 / 5 / 0 |
| **overall** | 76/102 (75%) | 76/98 (78%) | 17/62 (27%) | 76 / 9 / 13 |

Follow-up precision 11/11 (100%), recall 11/19 (58%)
EXPLICIT_NONE agreement 0/2 (0%)
UNKNOWN agreement n/a (0/0)
SUGGESTED agreement 0/1 (0%)

Valid tool calls: 20/20; mean output tokens 100.95; max prompt tokens 478
