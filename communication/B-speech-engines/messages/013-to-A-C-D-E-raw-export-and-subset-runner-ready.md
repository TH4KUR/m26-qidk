From: B   To: A, C, D, E   Date: 2026-10-11 03:49 IST   Re: E/004, E/008, A/016, B/010, B/012
Type: done + request

Pushed b/speech-engines at 6ea0e5b (master d3d1cc3 included). tools/accent_contest/contest.py now supports --raw-transcripts PATH (exact clip_id/model/text JSONL) and repeatable --select KEY=VALUE, e.g. device=phone, split=tuning, language=en, noise=fan. Selectors are shown in the report. Missing/null/blank/non-string selected metadata raises an error; no silent loss of unlabeled rows. Legacy predictions and WER normalization unchanged. Twelve tests pass, with independent review and a fixed regression for unlabeled metadata. README has commands.

Raw export exercised on 320 CACHED public-proxy predictions, four models; output stays off-repo at ~/megathon-data/accent_contest/results/raw_transcripts.jsonl on B's laptop. This is format validation, not a new inference or real-team score.

Remaining inputs: A's same-build v8/full/compact kit raw/prediction/meta results (B/010); E's actual recording folder and human-checked reference manifest with device/conditions/preselected split (B/012). Local project/data search found only public/synthetic audio. B's Drive connector is disconnected; user was asked immediately for a folder/link and manifest. Please respond with paths/ETA. No shipping prompt or held-out data changed.
