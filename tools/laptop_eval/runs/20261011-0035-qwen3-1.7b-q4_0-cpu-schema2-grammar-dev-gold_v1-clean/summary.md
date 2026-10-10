### 20261011-0035-qwen3-1.7b-q4_0-cpu-schema2-grammar-dev-gold_v1-clean
Items: 20 (missing predictions: 0)

| field | precision | recall | hallucination | correct / wrong / miss |
|---|---|---|---|---|
| incident_type | 17/19 (89%) | 17/20 (85%) | n/a (0/0) | 17 / 2 / 1 |
| severity | 4/10 (40%) | 4/4 (100%) | 6/16 (38%) | 4 / 0 / 0 |
| location | 17/19 (89%) | 17/19 (89%) | 0/1 (0%) | 17 / 2 / 0 |
| date_time | 17/18 (94%) | 17/18 (94%) | 1/2 (50%) | 17 / 0 / 1 |
| persons_involved | 3/8 (38%) | 3/5 (60%) | 4/15 (27%) | 3 / 1 / 1 |
| equipment_asset | 5/8 (62%) | 5/14 (36%) | 2/6 (33%) | 5 / 1 / 8 |
| immediate_actions | 11/13 (85%) | 11/17 (65%) | 0/3 (0%) | 11 / 2 / 4 |
| witnesses | 0/4 (0%) | 0/1 (0%) | 3/19 (16%) | 0 / 1 / 0 |
| **mandatory** | 55/66 (83%) | 55/61 (90%) | 7/19 (37%) | 55 / 4 / 2 |
| **overall** | 74/99 (75%) | 74/98 (76%) | 16/62 (26%) | 74 / 9 / 15 |

Follow-up precision 12/14 (86%), recall 12/19 (63%)
EXPLICIT_NONE agreement 0/2 (0%)
UNKNOWN agreement n/a (0/0)
SUGGESTED agreement 0/1 (0%)

Valid tool calls: 19/20; mean output tokens 105.6; max prompt tokens 479
