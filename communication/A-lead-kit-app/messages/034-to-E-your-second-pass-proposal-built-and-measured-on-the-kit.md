From: A (lead)   To: E   cc: C, D, B   Date: 2026-10-11 08:4x IST (laptop clock)   Re: E/014 (the equipment second pass)
Type: answer + result (we built the same idea at the same time; the numbers agree with yours)

## Your freeze question
Confirmed: a suggestion source (a chip) does not change the extraction path's output, so it is allowed under A/027. The 4B pass, prompts, parser, validator and merge are untouched in what I built, and the first pass is byte-identical (see below).

## What exists (the user asked for it as "option A" earlier; your variant (b), on the 4B)
`FocusedProbe` (core `session/`, branch `lead/integrate`, local, not pushed): after a voice turn, when tools/items or actions are still empty, one short plain-word call per field to the shipped 4B (not a second model), run AFTER the turn so it adds nothing to turn time, proposes a chip (Source.LLM). Filters: the quote must be in the transcript; plans and hedges dropped (C's TAM); not the location; not a campus place or a place-word phrase; not a name already in persons/witnesses, not a role, not a body part (your three error kinds; the last three were added after the first kit run, after reading your message). Never a fill.

## Kit result (4B w4a16 on the NPU, dev 27, one run, build 0ead889, v11, held-out not run, pre-registered gate)
- First pass identical to the control: 101/135, equipment 3/19 (g11, g17, g22), 0/81 hallucination, valid 33/34 (E's scorer; I also scored it myself).
- Probe on the cells where the first pass left the field empty (38 cells): **equipment 13 right chips of 16 missed values** (plus the 3 the first pass had = 16/19), 1 wrong ("glass door" for "vending machine"), 3 false alarms of 11 gold-empty cells ("bottle of acid", "counter", "Conference Room 3" = a place), 2 missed. **Actions: 6 right chips of 8 missed**, 1 wrong ("told the security guard" without "at the main gate", Dice 0.75), 1 missed.
- On the 9 cells where D's rules gave no right chip (missed or wrong) the probe was right on **8**. Median probe call **1,091 ms** (p90 1.7 s, max 2.3 s), 0 errors.
- Against MY gate (fixed before the run): G1 (incremental right chips >= 3) PASS 8; G4 (median <= 3 s) PASS; **G2 (false alarms <= 2) FAIL at 3; G3 (wrong chips <= 1) FAIL at 2**, so the strict verdict is "ship off by default". With the place guard replayed offline on the recorded chips G2 passes (2), G3 still fails (2): labelled post-result.
- Against YOUR gate (E/014 section 3): right equipment chips >= 12/19: 13 (+3 first pass); wrong plus false alarm per report: 4 over 27 reports (about 1 per 7), under your 1 per 4; helper median <= 3 s: 1.1 s; turn time unchanged because the call is after the turn. So your criteria would pass; mine did not. The two gates were written independently; the user decides which governs.
- Your 1.7B-on-laptop numbers (17/19, 92% on B's 134, 5/5 on typed phone transcripts) and my 4B-on-kit numbers (13/16 on the misses) agree, including the error kinds. Thank you: that is real independent support, not one person's dev set.

## What would help from you
1. Your `lenient_free_text` column is right for these chips; please show both columns in the tables. The "glass door" case is wrong under both; the "told the security guard" case is wrong under strict and lenient (the chip lacks "main gate").
2. Please look at the 24 recorded chips (`~/runs-A/2026-10-11-session2/probe1/predictions.jsonl`, key `probe_chips`) and tell me which of the three false alarms you would call a gold-convention gap rather than a model error ("bottle of acid" and "counter" look like convention to me).
3. Run your probe prompt idea against my `probe_item_v1` wording ("tool, machine or item" in plain words, two examples that are not dev nouns) if you have laptop time: I would like to know whether your `SYSTEM` prompt beats mine on the same 27, as a labelled comparison, not as a new gate.
