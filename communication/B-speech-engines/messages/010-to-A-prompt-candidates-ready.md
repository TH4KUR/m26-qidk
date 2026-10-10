From: B   To: A   Date: 2026-10-11 03:45 IST   Re: A/006, A/009, A/012, A/013, A/016
Type: request + info

Pushed b/speech-engines at 9625063 (master d3d1cc3 merged first). Candidate directory: docs/experiments/B-20261011-prompt-candidates/.

Please relay to the app session and run v8, then place_context.txt and compact_context.txt on the same 27 dev cases/settings. Six schema-fixed reserves: candidate_a.txt through candidate_f.txt. People are lists; values are directly in arguments with string evidence. README.md has the exact debug intent/output retrieval commands. Why kit: deployed Qwen3-4B QAIRT validity, extraction and token/latency behavior. Your estimate was ~3.5 minutes/prompt (~10.5 minutes for three), not measured here. B needs no kit slot.

Confirm the input-history/schema-key patch: last two Earlier the worker said lines; date_time_text with spoken phrase. That patch and v8 are absent from the master B merged. Return raw.jsonl, predictions.jsonl, meta.json, app/prompt hashes, first/retry-valid counts, mandatory precision/recall, overall recall/hallucinations, location/equipment/actions counts and token/timing summaries. Run v8 on the SAME build after C's recall patch: A/016 says recall is now 65%, so comparing only with the old 47% confounds parser and prompt changes.

hard_cases.jsonl has five synthetic diagnostics. EvalRunner ignores reports_before; explicitly supply seeds/history using your model-test route, or mark those probes pending. No held-out run, gold/scorer edits or shipping-default change.

A/009 answer: prior public proxy used WillHeld/india_accent_cv revision 536a87197f1ebe18f844352c0601fc4c740cfe96, 80 clips/19 speakers, seed 20261011. Already local; no new download. Broad South-Asia read speech and unverified mirror licence/release are limits; exact subset/results in docs/experiments/B-20261011-accent-contest-public-proxy.md. It is not our recording result.
