From: A (lead)   To: B, E (cc D, C)   Date: 2026-10-11 IST   Re: A/020, B/013, E/010
Type: decision + tasks. The user answered two questions: **(1) yes, measure Hindi/Telugu ASR on a small public subset; (2) yes, the team has Hindi and Telugu speakers who can verify.**

Everything stays an **experimental lane**: English is the headline, Hindi/Telugu numbers are labelled "experimental, small n, FLEURS read speech, not incident speech" in every table. Priority is below the prompt bake-off, the held-out run and the merge deadlines.

## E: reviewers
- Arrange a native-speaker review of D's draft set (A/020 item 3: 15 Hindi, 15 Telugu, 10 Hinglish, `label_source: model_draft_unreviewed`) when D announces it. Each item is checked for: does the sentence read naturally, are the expected fields supported by what is said, are negations and corrections right.
- Record the reviewer **as a role** (`hi-reviewer-1`, `te-reviewer-1`), never a name or college, in the file and in any message. Change `label_source` to `human_checked` only for items a reviewer actually passed; fixed items keep a note. A second reviewer for a subset is better than one for all.
- The same reviewers can type reference transcripts for the Hindi/Telugu clips of the later recording round (the `I01` to `I10` clips in E/010).

## B: public-data WER subset (the user approved this small download)
- **Data:** FLEURS `hi_in` and `te_in`, **test split only**, 20 clips each, chosen by a fixed seed written in the log (a random choice, not hand-picked). Licence CC-BY-4.0: record it and the dataset revision. About a few hundred MB at most; stay under 2 GB and keep it **outside the repo**. Never commit audio.
- **Scoring shape:** `manifest` + references in the shape `tools/accent_contest` already loads; Indic text normalisation stated in the log (do not strip matras; the repo research notes warn that the default Whisper normaliser roughly halves Hindi WER).
- **Models you can run now on the laptop CPU, no kit:** `~/models-hi-te/ippocode/models/whisper-small-hi` (Hindi) and `whisper-tiny-te` (Telugu), sherpa-onnx int8. Run both and write raw `{clip_id, model, text}`.
- **Caveat to print above every table:** the vasista22 models behind these exports were trained on FLEURS train and dev, so FLEURS test is close to in-domain; stock Whisper-Small QNN is expected to be far worse on Telugu. These are ranking numbers, not claims about incident speech.
- Hand the same 40 clips and manifest to the app session by path (below).

## App session (message sent to it directly)
Queued **after** the prompt bake-off, with the user's go for kit time: push the 40 FLEURS clips, run Whisper-Small QNN with `language=hi`/`te`, and the Nemotron-3.5 Hindi QNN bundle through the **online** recogniser (the offline CLI rejects it) if the online binary is present. Log WER-ready raw transcripts, real-time factor, memory, and whether it coexists with the 4B. English results and the bake-off come first.

## Open for the user (not for you)
The Nemotron online binary has to be copied from the cloud VM by the user (SSH is their call). The Hindi bundle itself is already on the laptop under `~/models-hi-te/`.
