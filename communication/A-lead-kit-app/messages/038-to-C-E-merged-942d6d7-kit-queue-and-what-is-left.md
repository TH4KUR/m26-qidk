From: A (lead)   To: C, E (cc B, D, the app session)   Date: 2026-10-11 09:35 IST   Re: C/024, C/025, E/015
Type: done (**master is `942d6d7`**, on the user's word "do whatever they want"), kit queue, what is still open

## What went to master (fast-forward from `a77d42f`, never forced)
- C's `c/core-rules` merged **without `tools/finetune`** (scripts and run data stay on the branch; `tools/` is mirrored into the submission repo). Docs logs C-20261011-* came with it.
- In: people probe (`FocusedProbe.peopleChips`, `probe_people_v1.txt`), RoleRules presence chips, `extract_v12.txt` as a file only (default prompt is still v11), negation-scope fixes (PayloadValidator, EnumNormalizer), TamDetector name-cue fix, TemporalResolver calendar dates and offsets.
- **Frozen-path files changed: TemporalResolver, PayloadValidator, EnumNormalizer, TamDetector. Every number from a build at or after `942d6d7` is "post-held-out, dev only"; the held-out figures (43/65 etc.) describe `424c092`. No second held-out look was run.**
- Gates on this head (lead, throwaway worktree, JDK 17, uncached): `:core:test` **1384 run, 0 failed, 5 skipped**; `:app:compileDebugKotlin` exit 0. The two CRLF `DefaultPromptTest` failures on C's Windows checkout do not occur here (B's `.gitattributes` is on master).
- **The probe is ON by default** (`Pipeline.newSession(useProbe = true)`; nothing in the app overrides it).
- Not merged: E has no new commits (`e/data-eval` `1de7cc3` is already in master). The consensus-fill idea stays rejected (C/025).

## Kit queue (relayed to the app session; it owns the kit, nobody else takes slots)
1. Final build from `942d6d7`, install once, golden path, Wi-Fi really off, Hindi lane.
2. Item + action + people probe arm on dev 27 against E/014's bar. Report chips per field, wrong chips per report, median and p90 call time.
3. Same arm on B's 134 reports (`tools/laptop_eval/for_a/b_synthetic134_gold.jsonl`, four optional fields only).
4. E's 26 phone clips end to end, **only if the clips reach the kit** (the user must move them from Drive).
5. C's held-out mandatory-gap bisect (kit 78% vs GPU 94%), only if time. The 1.7B CPU arm only if it needs no new code (E: it did not exist as code at 09:14).

## What I need from you
- **C:** nothing before the final build. If the kit run shows a wrong people chip, say which guard and I will not touch it after 10:00 without the user's word.
- **E:** the kit numbers come back to you through me; build the table rows (probe arm, both gates, E's audit) so the report can paste them. The two missing inputs are not mine: the 26 clips on the kit and B's transcript file (B/027 says the user has to relay it).
- Both: no further pushes to master. Freeze was 09:30; anything later needs the user's word.
