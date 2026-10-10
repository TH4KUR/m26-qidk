### 20261011-0000-qwen3-1.7b-q4_0-cpu-grammar-dev
Items: 20 (missing predictions: 0)

| field | precision | recall | hallucination | correct / wrong / miss |
|---|---|---|---|---|
| incident_type | 18/19 (95%) | 18/20 (90%) | n/a (0/0) | 18 / 1 / 1 |
| severity | 3/9 (33%) | 3/4 (75%) | 6/16 (38%) | 3 / 0 / 1 |
| location | 15/19 (79%) | 15/19 (79%) | 1/1 (100%) | 15 / 3 / 1 |
| date_time | 17/18 (94%) | 17/18 (94%) | 1/2 (50%) | 17 / 0 / 1 |
| persons_involved | 3/8 (38%) | 3/5 (60%) | 4/15 (27%) | 3 / 1 / 1 |
| equipment_asset | 4/6 (67%) | 4/14 (29%) | 1/6 (17%) | 4 / 1 / 9 |
| immediate_actions | 10/12 (83%) | 10/17 (59%) | 0/3 (0%) | 10 / 2 / 5 |
| witnesses | 0/4 (0%) | 0/1 (0%) | 3/19 (16%) | 0 / 1 / 0 |
| **mandatory** | 53/65 (82%) | 53/61 (87%) | 8/19 (42%) | 53 / 4 / 4 |
| **overall** | 70/95 (74%) | 70/98 (71%) | 16/62 (26%) | 70 / 9 / 19 |

Follow-up precision 11/15 (73%), recall 11/19 (58%)
EXPLICIT_NONE agreement 0/2 (0%)
UNKNOWN agreement n/a (0/0)
SUGGESTED agreement 0/1 (0%)

Valid tool calls: 19/20; mean output tokens 112.65; max prompt tokens 479
