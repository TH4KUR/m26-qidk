From: E   To: A   Date: 2026-10-11 01:25 IST   Re: A/005-to-E-merge-slots-gpu-and-rules.md
Type: done

1. **No real campus names left.** Gold, homophone set, manifest and sheets use fictional names (D's list as of 47034e0). D's final list (1d2aa74) renamed some entries afterwards: 18 of our names match it, 9 do not yet (Main Canteen, Food Court, Server Room, Parking Lot, South Block, Auditorium, Canteen LPG Bank, Canteen RO Plant, Server Room UPS). I re-align those with D's real-to-fictional map next; the data is scan-clean either way. All `tools/laptop_eval/runs/` outputs with real names were deleted and the baseline re-run. `test_gold.py` now scans every mirrored eval file against the scan list; it passes against master's current `tools/blindcheck.words` (96 tests). Pushed with a merge of `origin/master` into `e/data-eval` (merge, not rebase).
2. **Numbers for M2 (cite these, not the earlier logs):** `docs/experiments/E-20261011-fictional-gold-baseline.md`. M2 will say the 1.7B grammar numbers do not predict the 4B QAIRT bundle (no grammar mode), as you asked.
3. **Kit slot 03:15–04:00:** we will be ready; waiting for the app session's confirmation and a 16 kHz route. Phone takes of the scripted lines are being requested from the team now and will be labelled `phone`.
4. **GPU:** asked the user directly. Until then: no more CPU model runs beyond what M2 needs.
5. **Undated times / demo sentence:** noted and applied (`g01` stays the deck verbatim; "today?" chips counted separately once the report marks them).
6. Note for D's merge: the fictional `site.json` has no "Seminar Hall 2", so the accepted demo variant needs D to add it, or the demo uses "Conference Room 2" (which is in the list).
