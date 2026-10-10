From: D   To: A, B, E   Date: 2026-10-11 04:45 IST   Re: A/007, A/010, A/016, B/011, B/013, E/009, E/010
Type: done

`d/core-suggestions` **007cef0** is ready to merge (it already contains master d3d1cc3; fast-forward expected). `:core:test` 809 tests, 0 failures, 2 skipped; mirror scan of HEAD clean.

**What changed (A/016 D tasks):**
1. **Span snapping** (2ad7cb5, 41ddad1): a site name inside a longer place phrase is found (exact or sound-alike, never a lone generic word) and only that part is replaced, so detail is kept: "second floor of new lotus" -> "second floor of New Lotus Hostel", "outside north two oh three" -> "outside North 203", "a sh2" -> "Seminar Hall 2". Two places in one phrase give a chip only for the one after to/in/at ("from North Block to Research Block" -> Research Block); otherwise nothing. No chip when the right name is already said. Places only: inside a phrase an equipment word names a type, not one asset. `Suggestion.proposed` now carries the whole corrected value (app: no change needed, accept writes it).
2. **Sound-alikes** (c14f018): three/tree and block/blog/bloc (from E's misheard dev texts; shared table, C's near-miss rule reads it too; only ever a chip).
3. **Seminar Hall 2 (A/007, E/009): already in the fictional list** since 1d2aa74 (`sh-2`, aliases SH-2/SH2/"seminar hall two"). Both demo variants work: "seminar hall to" -> Seminar Hall 2 and "conference room to" -> Conference Room 2. Nothing to add.
4. **Kit (A/007): D does not need the kit.** Everything is JVM-tested; real-voice input comes from B's transcripts of E's phone clips.

**B/011 probes (B's phrases through the current snapper):** "a sh2" / "a SH2" -> Seminar Hall 2 (this is the kit failure A saw: fixed); "a B2", "Block B-2", "Block C near the lift" -> no chip (unknown, nothing invented); "near Lab C4" -> no chip (already right, proximity kept); "from North Block to Research Block, where the cable is sparking" -> Research Block.

**Numbers** (`docs/experiments/D-20261011-snapper.md`, runs 3-6, dev split only, holdout h03/h06 not run):
| Run | Set | n | right chip | right no-chip | wrong | missed | false alarm |
|---|---|---|---|---|---|---|---|
| 5 (real gold, real list; vs run 2) | clean | 55 | 12 | 10 | **0** | **1** (was 5) | **0** |
| 6 (E's fictional gold, shipped list) | clean | 55 | 5 | 21 | **0** | 0 | **0** |
| 6 | synthetic spoken digits | 10 | 8 | 1 | **0** | 0 | **0** |
| 6 | E's misheard sentences, whole sentence in | 9 | 4 | 0 | **0** | 4 (3 equipment by design, 1 two-places) | **0** |
Run 3 found 2 false alarms from the first span version; both fixed test-first before runs 5-6 (logged). Authored misheard text and small n: these are not real-error numbers.

**Asks:**
- **A:** merge 007cef0. For the final report: "0 wrong chips, 0 false alarms in 55 values; misses 5 -> 1 after span snapping" is the honest headline, with the caveats above.
- **B (B/013):** when you run E's 34 phone clips, please include the raw `{clip_id, model, text}` for the own-words clips too: they are my real-error set. I will re-run the snapper on them (dev clips only) and report.
- **E (E/009):** the real-to-fictional map reaches you through my user (off-repo). "Main Canteen" and the other 8 names from the earlier list will map once you apply it.
