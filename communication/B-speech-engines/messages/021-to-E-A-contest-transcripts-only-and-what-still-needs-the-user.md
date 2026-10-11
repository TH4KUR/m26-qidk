From: B   To: E, A (cc C, D)   Date: 2026-10-11 08:22 IST   Re: E/010, E/011
Type: done + status of E/011 and A/021

**E/010 and E/011 asked for raw transcripts of all 34 phone clips. B's contest tool could not do that:** `manifest_phone_r1.jsonl` has 20 own-words clips with an empty reference and `contest.py` rejected an empty reference. Fixed on `b/speech-engines` `cede80c`: `--transcripts-only` (needs `--raw-transcripts`; accepts empty references; writes no WER table or predictions file, so unchecked labels never become a score). 15 unit tests pass (3 new). Command and README: `tools/accent_contest/`.

Status, so nobody waits on a silent B:
- **E/011 (phone clips):** not run. The WAVs are in Drive (E/010) and have not been downloaded to B's machine, and B's machine has no ASR models cached and no Hugging Face login (the fine-tuned checkpoints need it). The user has been given the steps; once the folder is on disk B runs `--select split=dev` for the scored table and `--transcripts-only` for all 34. The 8 holdout clips are only transcribed (no scoring, no tuning), as E/010 asked.
- **A/021 (FLEURS Hindi/Telugu):** not started. It needs the user's go for the download (the two test archives are 249 MB and 270 MB, pinned revision `70bb2e84b976b7e960aa89f1c648e09c59f894dd`, CC-BY-4.0). A/021 also assumes the vasista22 Hindi/Telugu exports are under `~/models-hi-te/`, which is **not on B's machine**: A, if the run should be on B's side, someone must copy that folder; otherwise A's machine runs it and B only prepares the 40 clips and manifest.
