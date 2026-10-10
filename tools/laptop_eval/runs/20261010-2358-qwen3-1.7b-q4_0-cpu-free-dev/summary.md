### 20261010-2358-qwen3-1.7b-q4_0-cpu-free-dev
Items: 20 (missing predictions: 0)

| field | precision | recall | hallucination | correct / wrong / miss |
|---|---|---|---|---|
| incident_type | 6/7 (86%) | 6/20 (30%) | n/a (0/0) | 6 / 1 / 13 |
| severity | 4/5 (80%) | 4/4 (100%) | 1/16 (6%) | 4 / 0 / 0 |
| location | 7/7 (100%) | 7/19 (37%) | 0/1 (0%) | 7 / 0 / 12 |
| date_time | 7/7 (100%) | 7/18 (39%) | 0/2 (0%) | 7 / 0 / 11 |
| persons_involved | 1/2 (50%) | 1/5 (20%) | 0/15 (0%) | 1 / 1 / 3 |
| equipment_asset | 4/6 (67%) | 4/14 (29%) | 1/6 (17%) | 4 / 1 / 9 |
| immediate_actions | 4/7 (57%) | 4/17 (24%) | 0/3 (0%) | 4 / 3 / 10 |
| witnesses | 0/1 (0%) | 0/1 (0%) | 1/19 (5%) | 0 / 0 / 1 |
| **mandatory** | 24/26 (92%) | 24/61 (39%) | 1/19 (5%) | 24 / 1 / 36 |
| **overall** | 33/42 (79%) | 33/98 (34%) | 3/62 (5%) | 33 / 6 / 59 |

Follow-up precision 18/54 (33%), recall 18/19 (95%)
EXPLICIT_NONE agreement 0/2 (0%)
UNKNOWN agreement n/a (0/0)
SUGGESTED agreement 0/1 (0%)

Valid tool calls: 7/20; mean output tokens 106.9; max prompt tokens 479
