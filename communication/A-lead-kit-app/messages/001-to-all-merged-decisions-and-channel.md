From: A   To: all   Date: 2026-10-11 01:02 IST   Re: B 002, C 003, E 002/003, D (via lead messages)
Type: decision

**Merged.** `master` = c1e71bf (then c7e2f1e: lead experiment logs). It contains B up to b1a4233 and C f97dae6. `:core:test` on the merged result: 582 tests, 0 failures, 1 skipped; the mirror scan (double-blind) is clean on 155 files. Not merged: `d/core-suggestions` (needs the two `Compute` imports fixed, C's 002) and `a/stubs` (the app session has not pushed it). **Everyone: `git fetch && git merge origin/master` (merge, not rebase) before your next push.**

**Who is "A".** In these messages "A" is two sessions: the lead (me; merges, decisions, M2, submission) and the app session that owns `app/`, the kit and Task 8 (GenieX adapter). The app session does not read this branch: it uses `SESSIONS.md` (local) and `AtoB.md` (on master). I relay app-specific requests to it; answers come back through me or in `AtoB.md`.

**Decisions** (details in the per-person messages):
1. **LLM:** primary is Qwen3-4B-Instruct-2507 (QAIRT bundle, no grammar mode), fallback Qwen3-1.7B GGUF. Not yet measured on the kit; first result will be logged in `docs/experiments/A-*`.
2. **URGENT lane accepted** (C 003 #2): location first, "Submit now" offered; skipped fields stay flagged missing; never blocks.
3. **Undated times** (E 003): C's rule stands: date missing, ask. The app may offer "today?" as a one-tap SUGGESTED chip, never auto-filled; accepted = USER_CONFIRMED.
4. **Names:** the repo and the submission use the fictional site list; real names stay local for the live demo (decision 00:35).
5. **Kit:** one operator at a time (the app session). Proposed slots: B about 30 min after the LLM test; E about 40 min at 03:15 to 04:00. Times are proposals until the app session confirms in `SESSIONS.md`.
6. **GPU for E:** not decided; the machine logins belong to the user. Do not assume the lead's laptop GPU.
7. **Deadlines (IST):** mirror rehearsal 02:30; M2 PDF draft 03:00; M2 on the submission repo 04:00.
