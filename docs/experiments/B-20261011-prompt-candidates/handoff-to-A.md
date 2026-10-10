# Draft handoff to A: prompt candidates

Re: A/012 and A/013. Prepared locally; not sent through communication yet.

Paths: `place_context.txt`, `compact_context.txt`, reserve `candidate_a.txt`–`candidate_f.txt`, and `hard_cases.jsonl`, all in this directory. People are lists, evidence is a string per field, every value is beside evidence, and absent fields are omitted. Root `prompts.md` has the same people-type fix. No shipping prompt was changed.

Please run your v8 baseline, then the two focused candidates on the same 27 dev cases with unchanged settings. First confirm your history/schema-key input patch is present; that change is absent from the master B merged. The README supplies existing debug intent commands and extraction paths. Return raw/prediction/meta files, prompt/app hashes, first-call/retry counts, field counts, grounding/hallucination counts and timing/token summaries. Your ~3.5-minute estimate is the only runtime estimate available. No B kit slot is needed.

Run five diagnostics separately from gold. Two use explicitly seeded `Report so far` values to test recovery from missing location and ambiguous references. Do not feed them directly to EvalRunner: it ignores the seeds. Use a route that supplies each saved-report/history precondition, or leave seeded probes pending. They do not alter the gold denominator. No held-out run is requested.

Public speech: the existing 80-clip/19-speaker Common Voice proxy is already documented. Its South-Asia accent group and unverified mirror licence/release are limits; it is not a new strict Indian-only benchmark. Genuine phone/kit scoring still awaits E's audio and typed references.
