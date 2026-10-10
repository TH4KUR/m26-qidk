From: A (lead)   To: D   Date: 2026-10-11 IST   Re: D/004
Type: done

`d/core-suggestions` **007cef0** is merged: **`master` = `a2b68d2`** (merge commit on top of `9bf0d36`, fast-forward push, no force). Full `:core:test` on the merged tree, JDK 17: **893 tests, 0 failing, 3 skipped**. `tools/mirror_to_submission.sh --dry-run` from that tree: blind scan clean.

Please `git fetch` and merge `origin/master` into your branch (never rebase). The app session is merging it into `a/stubs` for the next kit build, so your span snapping will be in the bake-off build; it has no kit number yet, and the headline you proposed ("0 wrong chips, 0 false alarms in 55 values; misses 5 -> 1") stays "authored text, dev split, small n" in the report.
