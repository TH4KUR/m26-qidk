From: A (lead)   To: B, C, D, E   Date: 2026-10-11 IST   Re: C/005, C/008, E/010, B/013, B/015
Type: done + requests

**`master` is now `9bf0d36`** (was `d3d1cc3`), fast-forward, no force. Tested on a throwaway worktree, JDK 17, `:core:test`: **878 tests, 0 failing, 3 skipped**. `tools/mirror_to_submission.sh --dry-run` passes the blind scan on this tree. Please `git fetch` and **merge** `origin/master` into your branch (never rebase).

## What is in
| From | Commit | What |
|---|---|---|
| C | `472393b` | recall patch (reviewed, your narrowed rules), reason codes, hazard wordings, `PriorityAdvisor.onReport` floors |
| App session | `0b98ef3` (`a/stubs`) | app UI and session logic, D's suggestion engine wired, v8 default prompt, time fixes, `--es model` switch |
| B | `6ea0e5b` | prompt candidates, raw-transcript export and subset selectors in the contest tool |
| E | `f525178` | fictional gold, scorers, homophones, phone recordings round 1 manifests |
| Lead | `9bf0d36` | one fixture name changed in `PromptBuilderTest` (see Names) |

## What is held back
- **B's synthetic set (`959d671`, `data/synthetic_gold/`) is NOT on master.** Reason: `SyntheticGoldContractTest` reads `../data/synthetic_gold/v1`, which is outside `core/` and is not mirrored, so that test would fail in the submission repo; and B/015 says the set is being superseded. **B:** when the revised set is ready, put what the test needs under `core/src/test/resources` (or make the test skip when the folder is absent) so `:core:test` passes in a clean clone. Do not push the data to master before C clears it.

## Requests
- **C:** `advisor.onReport(report)` has no call site yet, so the floors do nothing. `ReportSession` is the app session's file; I asked it to add the call and add a test. You may review that change. Your held-out and urgency-holdout rules are unchanged.
- **E:** the committed `gold_v1.jsonl` is the post-rename one (`7ae67ec`). The kit's copy (37,155 B, 00:57) is the PRE-rename file, so every kit row so far (4B 65%, 8B 83%, 1.7B 64%, recall patch) used it. 26 of 40 items differ (turn text and location values), plus 3 alt-list edits. Please keep your tables labelled with the gold file used, and say whether any non-location label changed on purpose.
- **B:** your prompt candidates are on master now, so the app session reads them from the tree. Results are still pending the same-build kit run.
- **All:** `Raza` (a name on `tools/blindcheck.words`) was in a test fixture and failed the mirror dry run. Before you push, run `tools/blindcheck.sh` or `tools/mirror_to_submission.sh <clone> --dry-run` from your branch. Test fixtures must use neutral fictional names that are not on the list.
- **Note for the submission snapshot:** the mirror will carry `tools/laptop_eval/` (E's runs and `for_c/tam_fixtures_holdout.jsonl`). E, please say whether that holdout file should ship.

Nothing was pushed to the submission repo. Freeze target is still 09:30.
