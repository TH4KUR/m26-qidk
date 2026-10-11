From: B   To: all   Date: 2026-10-11 08:16 IST   Re: B/014, B/016, C/010, C/015, C/016, C/019, C/021, A/030
Type: decision (from the user) + evidence. Please read before anyone trains, exports, requantizes or quotes the set.

**The 800-row synthetic set (`data/synthetic_gold/v1/`, `51c1124`) is abandoned for fine-tuning.** The user decided this because its token overlap is too high and would only teach the model to memorise. They allowed one exception: keep it only if a minimal fix removes the overlap and the models then improve from fine-tuning and requantization. I tried the fix and it does not hold, so the answer is abandon.

## Evidence (read-only, no model; reproducible: `python3 docs/experiments/B-20261011-dataset-overlap/overlap.py`, `b/speech-engines` after the push of this block; full table in RESULT.md there)
- **Inside train:** 317 of 600 rows have another train row at Jaccard >= 0.8 (median nearest 0.81), from 168 families.
- **Validation/test against train:** 25/100 and 28/100 rows have a train near-copy (Jaccard >= 0.8); about **65% and 67% of their 4-grams** already occur in train; 36 and 38 rows share >= 80% of their 4-grams. (A's count of 16/100 and 18/100 used a different cut; same direction.)
- **Minimal fix tried (greedy pruning of near-duplicate train rows):** keeping only rows below Jaccard 0.8 / 0.7 / 0.6 / 0.5 leaves 401 / 296 / 223 / 152 rows, and the 4-gram share with validation and test stays 0.64 / 0.60 / 0.58 / 0.55, because stock phrasing is shared across families. Removing the overlap needs different sentence skeletons at generation time, which is a new dataset, not a fix.
- **No improvement to defend:** C's fine-tunes on this set did not clear the adoption gate (C/016, C/019: v1 hallucination 12%, v2 6.2% against the 4% gate on E's dev 27). Requantization is not possible here (no QAIRT, A/030). Training would also need the user's approval and rented compute.

## What changes for each of you
- **C:** do not train or export further on `51c1124`; C/015's v2 retrain and C/021 item 3 (`build_sft_v2.py` on B's gold) are superseded. Your prompted-base recommendation (C/015 item 3, C/016) stands. Your validator fixes are unaffected.
- **A:** nothing on master depends on it (A/018 kept it off). Do not take the data to the submission repo. `SyntheticGoldContractTest` may stay as a **parser/validator/merge regression fixture** (update, none, correction rules); it must not be quoted as accuracy or as a training result. If you would rather drop the test and the folder, say so and B will remove them on `b/speech-engines`.
- **D, E:** nothing changes for you. E's gold, dev 27 and the phone clips are unaffected.
- **B:** marker file `data/synthetic_gold/v1/ABANDONED_FOR_TRAINING.md` added; B's earlier STATUS lines about the dataset review and training gate are replaced.

## If a training set is wanted later (not started, needs the user)
Many more distinct skeletons, splits by skeleton family and stock-phrase cluster, partial utterances where absent fields are absent from the label, a near-duplicate gate at generation time (Jaccard and 4-gram share against every other split), and the user's approval first.
