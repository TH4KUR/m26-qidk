### 20261011-0031-qwen3-1.7b-q4_0-cpu-grammar-all-gold_homophones-misheard
Items: 8 (missing predictions: 0)

| field | precision | recall | hallucination | correct / wrong / miss |
|---|---|---|---|---|
| incident_type | 2/4 (50%) | 2/8 (25%) | n/a (0/0) | 2 / 2 / 4 |
| severity | n/a (0/0) | 0/1 (0%) | 0/7 (0%) | 0 / 0 / 1 |
| location | 1/5 (20%) | 1/8 (12%) | n/a (0/0) | 1 / 4 / 3 |
| date_time | 2/2 (100%) | 2/6 (33%) | 0/2 (0%) | 2 / 0 / 4 |
| persons_involved | n/a (0/0) | n/a (0/0) | 0/8 (0%) | 0 / 0 / 0 |
| equipment_asset | n/a (0/0) | 0/5 (0%) | 0/3 (0%) | 0 / 0 / 5 |
| immediate_actions | 1/1 (100%) | 1/2 (50%) | 0/6 (0%) | 1 / 0 / 1 |
| witnesses | n/a (0/0) | n/a (0/0) | 0/8 (0%) | 0 / 0 / 0 |
| **mandatory** | 5/11 (45%) | 5/23 (22%) | 0/9 (0%) | 5 / 6 / 12 |
| **overall** | 6/12 (50%) | 6/30 (20%) | 0/34 (0%) | 6 / 6 / 18 |

Follow-up precision 9/21 (43%), recall 9/9 (100%)
EXPLICIT_NONE agreement 0/1 (0%)
UNKNOWN agreement n/a (0/0)
SUGGESTED agreement n/a (0/0)

Valid tool calls: 5/8; mean output tokens 144.25; max prompt tokens 445
