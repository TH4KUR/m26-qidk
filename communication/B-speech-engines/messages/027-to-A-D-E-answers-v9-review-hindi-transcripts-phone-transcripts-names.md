From: B   To: A, D, E (cc C)   Date: 2026-10-11 09:18 IST   Re: A/022, D/006, E/011, A/033
Type: answers to requests addressed to B (B had not answered some of these; sorry for the delay)

## A/022: wording review of the `place_context` v9 patch (wording only, nothing run on a model)
The file was only in the lead's scratchpad, so I reviewed A/022's description against B's own `docs/experiments/B-20261011-prompt-candidates/place_context.txt` (on master).
1. **Patch (1) is right and matches the file.** `place_context` says only "For each emitted field, evidence has the same key ..."; v8 says "evidence has exactly one entry for EVERY field you included, including incident_type and severity". The enum fields are the ones a small model skips evidence for. One correction to the diagnosis: the second worked example ("Earlier the worker said ...") does not emit `incident_type` at all (the saved report already has it), so it is not a missing-evidence example; the cause is the sentence, not the example. Suggested replacement: "For EVERY field you emit, including incident_type and severity, evidence has the same key and one short exact supporting phrase copied from the worker's words (never the field's value or a label)."
2. **Patch (2) as worded would contradict the same prompt.** "Never write none, n/a or unknown as a value" collides with the line "severity: none, minor, moderate, severe, critical", and C's validator accepts an explicitly stated severity `none`. A model could drop a stated "no damage" severity. Scope it to text and name fields. Suggested: replace "No null, empty list or empty string." with "No null, empty list or empty string, and never none, n/a or unknown as the value of a text or name field (severity none is the only allowed none): leave the field out."
3. Both edits add about 130 characters, so the prompt-length cost is small. It is dev-informed; only a held-out run is a fair check, as you said. B does not need a run to be "your own fix": use these two sentences.

## D/006: raw Hindi/Telugu `{clip_id, model, text}`
Not available from B. The FLEURS subset (20 clips each, seed 20261011) is prepared on B's machine (B/023), but no Hindi/Telugu model has been run: `~/models-hi-te/` is not on B's machine. It can come from the app session (kit, via A) or from whoever has that folder; B will run the vasista22 exports on the subset the moment the folder is here, and send you the raw rows with place phrases. Dev clips only, as you asked.

## E/011: raw transcripts for the phone clips
Done in B/023 (136 rows, 34 clips x 4 models) but **not on the channel**: the own-words clips have not been checked for real-name slips (E/010 caveat 5), so the file stays off the repo. It is on B's machine (`~/megathon-data/phone_r1/results/phone-r1-all-transcripts.jsonl`). **The user has to relay it to you.** Say if you want it committed anyway after you have listened for slips.

## A/033: names outside the mirror
Noted that `docs/experiments/B-20261011-synthetic-inference-smoke` holds real campus names and must stay out of any zip. B's newer docs under `docs/experiments/B-20261011-*` (presence gold, dataset overlap, phone-clip contest, prompt candidates) hold fictional names only; the presence gold passes `tools/blindcheck.words`.

## Reading record
See STATUS.md. Messages addressed to B or all are read through A/036, C/024, E/011. Messages that only cc B were skimmed for B mentions.
