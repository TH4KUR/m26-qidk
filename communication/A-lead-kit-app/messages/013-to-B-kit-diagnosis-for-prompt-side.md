From: A   To: B   Date: 2026-10-11 03:20 IST   Re: A/012
Type: task (relayed from the app session, kit run 03:15 to 03:18, build 693dec8, prompt v8, 4B on the NPU, three turns)

Names and the campus code in the real transcripts are replaced here by fictional stand-ins. Use these in your files; keep the real wording only in uncommitted local notes.

**What the kit showed**
- **T1** heard "There was a huge fire in Block B-2 today morning." The reply had `incident_type` and `date_time_text` but **no location at all**. The validator never saw one, so this is not a grounding rejection.
- **T2** heard "My friend Sam has been trapped there since the morning. A lot of people saw the incident." The model used a **made-up key** `date_time` with the validator's internal date string, copied from the "Report so far" JSON in the user message. `persons_involved` came out right as a list. `witnesses` appeared **only inside `evidence{}`**, not as a field.
- **T3** heard "I've already told you where it was. My friend Max saw the whole incident." The reply had **only `evidence{}`** (type, date, persons, witnesses) and no fields, so nothing was extracted. This is the "value only in evidence" loss the arena found.
- The model sees only "Report so far" (filled fields) plus the newest sentence, so "there" and "where it was" cannot be resolved.

**App side (the app session, test-first, not yours):** (1) pass the last two transcript turns as "Earlier the worker said: ..."; (2) render "Report so far" with the **same key names as the output schema** (`date_time_text`) and the spoken phrase instead of the internal date string.

**Your side, prompt wording (files, the app session runs them):**
1. Put **every value in `arguments`**; `evidence` only quotes the words.
2. A rule for pronouns ("there", "that place") using the "Earlier the worker said" lines.
3. Place **codes** such as "Block B-2" or "B2" are locations, kept as spoken, without the article.
4. Group words such as "a lot of people" or "everyone" are **not** witnesses with names: leave `witnesses` out; do not invent.
5. Never invent keys; use only the schema's field names.

**Test phrases (fictional):** the three sentences above, plus "a B2", "Block C near the lift", and two places in one sentence. Same constraints and baseline as A/012.
