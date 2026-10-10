### 20261011-0113-qwen3-1.7b-q4_0-cpu-free-dev-gold_v1-clean
Items: 20 (missing predictions: 0)

| field | precision | recall | hallucination | correct / wrong / miss |
|---|---|---|---|---|
| incident_type | 5/6 (83%) | 5/20 (25%) | n/a (0/0) | 5 / 1 / 14 |
| severity | 3/4 (75%) | 3/4 (75%) | 1/16 (6%) | 3 / 0 / 1 |
| location | 5/6 (83%) | 5/19 (26%) | 0/1 (0%) | 5 / 1 / 13 |
| date_time | 6/6 (100%) | 6/18 (33%) | 0/2 (0%) | 6 / 0 / 12 |
| persons_involved | 1/2 (50%) | 1/5 (20%) | 0/15 (0%) | 1 / 1 / 3 |
| equipment_asset | 4/5 (80%) | 4/14 (29%) | 1/6 (17%) | 4 / 0 / 10 |
| immediate_actions | 4/6 (67%) | 4/17 (24%) | 0/3 (0%) | 4 / 2 / 11 |
| witnesses | 0/1 (0%) | 0/1 (0%) | 1/19 (5%) | 0 / 0 / 1 |
| **mandatory** | 19/22 (86%) | 19/61 (31%) | 1/19 (5%) | 19 / 2 / 40 |
| **overall** | 28/36 (78%) | 28/98 (29%) | 3/62 (5%) | 28 / 5 / 65 |

Follow-up precision 18/58 (31%), recall 18/19 (95%)
EXPLICIT_NONE agreement 0/2 (0%)
UNKNOWN agreement n/a (0/0)
SUGGESTED agreement 0/1 (0%)

Valid tool calls: 6/20; mean output tokens 104.0; max prompt tokens 478
