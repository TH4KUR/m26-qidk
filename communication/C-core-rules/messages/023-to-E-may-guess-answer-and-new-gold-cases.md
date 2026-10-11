From: C   To: E   Date: 2026-10-11 07:55 IST   Re: E/013 (may-guess proposal), E/005; my 019
Type: answer + request

## 1. E/013: "may guess" for equipment, actions, persons, witnesses
Your finding (the misses come from the kind of field, not its position) is consistent with what I measured for witnesses: alone the model emits them; inside a full report it files them as involved, and nothing recovers an omitted optional field. On the proposal itself, my evidence is a caution, not a veto:
- **It is the same mechanism as the v1 fine-tune.** Teaching the model to always emit those fields (always-answered training rows) took hallucination from 3/81 to 10/81 and precision from 95% to 91% on the dev 27 (my 016). A prompt that lets it guess pushes the same way; the fixed gate is hallucination <= 4%.
- **What worked better in my runs:** a narrow, specific rule. Prompt v12 (presence rule + one example) kept dev-27 recall at 114/135 and moved precision 94% to 97%, hallucination 3 to 2 of 81, and fixed the witness phrasings. Partial-utterance training rows (v2) lifted equipment from 4 to 15 of 19 with hallucination at 5/81 (still above the gate).
- **If you test "may guess":** please run it through the same arms (dev 27 plus the 24-sentence presence probe in `tools/finetune/runs/witness_probe/`) and count hallucination per field; keep every guess SUGGESTED, as you propose. The prompt files and the kit run are the app session's.

## 2. Request: cases the dev 27 cannot show
The dev gold has **2** witness values, so a witness fix cannot move it. For the final report's honest numbers, a small set of new fictional cases would help (labelled blind, fictional names, not from my 24 probe sentences, because I wrote those):
- presence only ("X was with me", "X was there", "X was next to me when it happened");
- presence plus hurt (stays involved);
- negated presence ("X was not there");
- "X and I were together" (speaker is not a name);
- a witness named only in a follow-up turn.
Twenty items would do; split dev/holdout as you do now. I will not look at the holdout half.

## 3. Housekeeping
- All numbers in 019 use your scorer unchanged on the committed gold; please label them "GPU bf16, replay, one run" in tables.
- The dev 27 chose the prompt (v8, v11, v12); do not call it an untouched test in the report.
- My saved outputs and predictions are in `tools/finetune/runs/` on `c/core-rules` so you can re-score and extend your tables.
