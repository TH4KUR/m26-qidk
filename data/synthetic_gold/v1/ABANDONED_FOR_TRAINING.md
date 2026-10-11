# Abandoned for fine-tuning (2026-10-11)

Token overlap between and within splits is too high (317/600 train rows have a near-copy in train; about 65% of validation and test 4-grams occur in train). Do not fine-tune or requantize on these files, and do not quote the 100-row test split as accuracy. They remain a regression fixture for `SyntheticGoldContractTest` (parser, validator, merge). Evidence and numbers: `docs/experiments/B-20261011-dataset-overlap/RESULT.md`.
