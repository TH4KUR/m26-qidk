# Lead to E: where you are, what to do next (00:15 IST)

Read this whole file, then work down the list. M2 is due **04:00 IST**. Your **M2 draft is due 03:00** so the lead can check and push it by 03:45. **Every number in the M2 report must come from a real run.**

## 1. What I verified on `e/data-eval` (head `1de2a9d`, pushed 00:02 IST)
- **85 Python tests pass** (`pytest`, 0.22 s).
- **Gold set:** 40 cases, 27 dev and 13 held-out (10 handwritten including the deck example as `g01`, 10 broad, 12 workplace-incident based, 8 multi-turn). **All English.**
- **Tooling:** scorer, parser port, runner, prompts and schema, recording sheets for five speakers (S1 to S5, no names anywhere: good), eval guide. No audio is committed: good.
- **One real run:** Qwen3-1.7B Q4_0 on the laptop CPU, free-form (no grammar), 20 dev items. Of what it filled in, **79% was right (33/42)**, but it found only **34% of the details (33/98)**, and only **7 of 20** answers were valid tool calls (about 107 tokens each). Honest and useful: it says the small model needs forced-format output, few-shot examples, or a bigger model.
- **Not there yet:** recordings, any on-kit run, Hindi cases, and the Kotlin scorer.

## 2. Next steps, in order (times are targets)
1. **00:30: remove real campus names from the gold data.** Your gold set, manifest, recording sheets and `build_gold.py` contain real place names (the mess, hostel and hall names). The private repo is fine, but the submission snapshot copies `app/` and `tools/`, and the mirror's scan will refuse it. Change them at the source (`build_gold.py`), regenerate `gold_v1.jsonl`, `manifest.jsonl` and `recording-sheets.md`, rerun your tests, and re-run the scorer. Do this **before more model runs**, so results stay comparable. Delete or regenerate `tools/laptop_eval/runs/` too (it holds the names in its predictions).
   *User decision (00:35): real names are kept for the live demo only.* The repo, the video and the zip use fictional names, so keep a **local, uncommitted** real-name copy of the sheets if you need them for the demo day. The real campus names are now in `tools/blindcheck.words` on `master`, so the mirror refuses any file that still contains one.
2. **00:45 to 02:00: the next model runs** (dev split only; keep the held-out 13 untouched until the end):
   - the same 20 items **with forced JSON output** (your `update_report.schema.json` as a llama.cpp grammar or JSON schema) and 2 or 3 few-shot examples;
   - bigger models on the GPU machine: Qwen3-4B-Instruct, Gemma 4B, NuExtract-2.0-2B.
   - Record every run, failures included, in `docs/experiments/E-<date>-<topic>.md`, with counts. The final model pick must still be confirmed **on the kit**: hand your prompts, schema and gold set to the lead's other session (A) for that run. For context, the user measured Qwen3-4B-Instruct-2507 on the kit's NPU at about 22 to 25 tokens/s in the demo chat.
3. **Recordings (needs the user's kit slot).** Ask the user for about 30 to 40 minutes on the kit; the sheets are ready. Until then, phone recordings are fine as a smoke test, but label them `phone` in the manifest. Convert to 16 kHz mono WAV; do not commit audio, keep it in a Drive folder and commit only the manifest.
4. **02:00 to 03:00: write the M2 PDF** (`deliverables/M2.pdf`, **2 pages**, "Team 087" at the top, no names). Use `docs/templates/report-template.tex` (it is on your branch). Required content: changes since M1, experiments with results, testing progress, plan for the remaining time.
5. **Run `tools/blindcheck.sh` on it before handing it over.** First add every teammate's name, GitHub handle and email to `tools/blindcheck.words` (that file never goes to the submission). A "pass" means nothing until those names are in.

## 3. Results you can cite in M2 (all verified; give the source)
| Source | Result |
|---|---|
| Your run | Qwen3-1.7B, CPU, free-form: precision 33/42, recall 33/98, valid tool calls 7/20 (20 dev items) |
| C's urgency experiments (`docs/experiments/C-*`) | on unseen reports, urgency band about 50% right, about 25% too low (under-triage); C's own logs say the pre-registered target was **not met** |
| Lead, on the kit | sherpa-onnx builds with the Qualcomm backend; Whisper-Small on the NPU transcribed a 6.6 s English clip correctly in 3.18 s; SenseVoice ran 12.7 s of audio in 0.058 s (its English text is poor, so it is for emotion tags only) |
| User, on the kit | Qwen3-4B-Instruct-2507 (NPU) decodes about 22 tok/s Hindi and about 25 tok/s English; Hindi text came out corrupted because of a GenieX bug fixed in 0.3.16+ |
| Test counts | `:core` 454 tests passing on the speech branch (includes C), 413 on D's branch, 85 Python tests on yours |
The lead's experiment logs are not pushed yet; ask the lead if you need the full text. Report limits honestly (one clip, one run, CPU only, etc.).

## 4. If time runs short, cut in this order
Hindi cases, then the Kotlin `EvalScorer`/`FakePipelineRunner`, then the extra models. **Keep the recordings and the M2 draft.**

## 5. Hygiene
Merge, never rebase. Stage explicit paths only and run `git diff --cached` before every commit. Do not edit another person's files without telling them. Questions to the lead: add a file `docs/messages/E-to-lead-<n>.md` on your branch.
