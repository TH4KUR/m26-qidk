From: C   To: A, E (cc the app session)   Date: 2026-10-11 10:38 IST   Re: the user asked me to review docs/submission/README.md and LICENSE against the T&C, and to fix everything; A/041
Type: two branches, neither merged, nothing touched on master

## 1. `c/submission-docs` `aa4d07c` (on master `a2d93d6`): README and LICENSE for the submission repo
Started from E's `docs/submission/` on `e/data-eval` (`96d6ae1`; that folder is not on master). LICENSE is unchanged (standard MIT, "Team 087"). README changes:
- Licence column for every component; the Piper voices say research and demonstration use only, with the Blizzard 2013 and IITM DonLab terms and "permission for hackathon use pending written confirmation from IITM DonLab" (the wording A/Piper doc asked for). Supertonic: OpenRAIL-M with its use restrictions (Hugging Face model card). Nemotron: OpenMDW-1.1 (NVIDIA card). SenseVoice: FunASR Model Open Source License v1.1. Qwen3-4B: Apache-2.0.
- Hindi lane steps completed (step 1f): where the online CLI comes from (`install/bin/sherpa-onnx` of the same build), the Nemotron SM8750 1120 ms asset on release `asr-models-qnn-binary-3` (checked on the release page), `stage-online` / `push-hindi` placed in the build and install steps; `check-hindi` compares sizes recorded for our build, so skip it after a rebuild.
- sherpa-onnx pinned to `v1.13.8`: the repo's `Tts.kt` is byte-identical to the tag's `kotlin-api/Tts.kt` (I diffed it). The library we ran was built from a default-branch clone, commit not recorded; the README says so and that it was not rebuilt from the tag.
- Third-party files in the repo are listed: `Tts.kt` (Apache-2.0), the Whisper `suppress_tokens` file (Apache-2.0), the YAMNet class map CSVs (Apache-2.0). Gradle libraries are pointed to `gradle/libs.versions.toml`. New "Acknowledgments and credits" section. `--es tts SUPERTONIC` added to the run step.
- The final report path `deliverables/final_report.pdf` is removed from the README (the organisers' file-placement spec is not in the repo); it says "the final report (PDF) submitted with this repository".
- Blind check against master's current `tools/blindcheck.words`: clean for both files.

For A, to put them in the submission repo (the mirror script never touches README/LICENSE): copy `docs/submission/README.md` to the repo root as `README.md` and `docs/submission/LICENSE` as `LICENSE`, commit on `main`, push.

Needs a person, not me:
1. Someone on the team confirms the sentence "All other code and data in this repository were written by the team during the hackathon; anything that was not is listed above." I could not verify it for the whole repo.
2. The IITM DonLab written permission: when it arrives, replace "pending". Until then the Hindi Piper voice stays optional and off by default.
3. Licence names for GenieX and the AI Hub bundle are "Qualcomm's terms, as published"; I did not find the exact licence names.

## 2. `c/brand-ui-fontscale` `044aac8` (on master `a2d93d6`, which contains `c/brand-ui`): A/041
The top-bar wordmark was a fixed 20 dp; it is now 20 sp (grows with the text size) in a column with a 4 dp gap to the subtitle. One block in `Chrome.kt` plus a note in `docs/ui/brand.md`. Clean merge with master. **Not built or run (no Android SDK here), and I could not reproduce the overlap from the code: the layout was already a column.** If the app session still sees an overlap at 200% after this, please send a screenshot or the Compose layout inspector output; the cause is then something outside `AppTopBar`.

## 3. For A, not a file change: commit history on the submission repo
Section 10 of the T&C: "squashed or imported history, or a first commit containing a substantially complete solution, will be treated as evidence of a pre-existing codebase". The submission repo gets snapshot commits from the mirror script (M1 and M2 pushes exist, so it is not a single commit). The incremental history stays in the private repo and cannot be imported (author names). I have no safe fix beyond: keep the snapshots as frequent as the freeze allows, never rewrite history, and be ready to show the private repo's log live if the organisers ask.
