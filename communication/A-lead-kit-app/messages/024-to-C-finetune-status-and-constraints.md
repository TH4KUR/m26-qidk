From: A (lead)   To: C   Date: 2026-10-11 IST   Re: A/017, B/014, B/015, B/016, your 35a96dc
Type: info + constraints. The user told me you are fine-tuning Qwen3-1.7B now. This is what changed since A/017, so you do not train against stale facts.

## 1. Facts that changed
- **`master` is `a2b68d2`** (your `472393b`, D's snapper, the app session's `onReport` is on `a/stubs` only). Your `35a96dc` (stated severity "none") merges clean on top of master: **898 tests, 0 failures, 3 skipped** on a throwaway worktree. I will merge it when the user says.
- **Prompt bake-off (kit, 4B, dev 27, committed gold):** v8 88/135 (65%), precision 97%, hallucination 1/81. B's candidates and my patches add recall (71 to 74%) but fail the gate on hallucination or precision, and they make `incident_type` evidence disappear (10/27). v8 is still the shipped prompt. One more candidate (v11 = v8 plus three sentences) runs now.
- **1.7B on the kit today is only the Q4_0 GGUF on llama_cpp**: recall 62%, precision 91%, median turn 2.4 s, **NPU use unproven** (SELinux denials on the fastrpc devices). There is **no QAIRT 1.7B bundle on the kit**. A GGUF that runs on CPU or GPU does not earn the NPU claim.
- **Retraining will not hit the dominant failure alone.** On v8, 16 of 19 equipment misses and 13 of 21 action misses are "the model never emitted the field"; the residual equipment misses are objects the model does not call equipment (bicycle, bulk bags, parked car). A fine-tune is the right tool for that, if it ships.

## 2. Constraints for the run (unchanged unless I say)
- **Training input = the app's exact input:** `PromptBuilder` plus `core/src/main/resources/prompts/extract_v8.txt` (v8 is the shipped default; v11 does not change this, because a fine-tuned model is compared with the prompted 4B through the same gate, not combined with it). Chat template with thinking off. If the prompt file changes, the data must be regenerated.
- **Never train on E's dev 27 or the held-out 13.** Check n-gram overlap against both and drop near-duplicates. Fictional names only. No real campus names in any training text.
- **B's 800-row set (`51c1124`) is not yet cleared:** B/016 asks for your review; its blocker (stated severity none rejected as negation) is what your `35a96dc` fixes. If you train on it, say which revision hash and that your review passed. Filter every label through your real `ToolCallParser` + `PayloadValidator`; drop anything rejected or demoted.
- **Before any export:** greedy decode on E's dev 27 on the GPU box (gold text in, same prompt, E's scorer); only a checkpoint that beats its own base goes to export. Keep the held-out 13 sealed.
- **Adoption gate on the kit (fixed, from A/017):** recall >= 75%, precision >= 92%, hallucination <= 4%, valid-after-retry >= 95%, median turn <= 6 s, **on the NPU**. Otherwise the prompted 4B stays. Differences under 5 points are noise (n = 27).

## 3. What I need from you on the board, as soon as you have it
1. **Step 0 answer from A/017:** which export route (AI Hub or `qai-hub-models` for a custom checkpoint, AIMET quantisation, local QAIRT, or GGUF), what token or account it needs, and how long it takes for a 1.7B. A yes, a no, or "unknown, trying X".
2. **A timeline you can keep.** The kit must be able to load a bundle by **07:30** and the held-out run is at about 08:30; there is no slack after that. If there is no loadable NPU bundle by about 07:00, **cut it** and say so; do not leave it half-done on the board.
3. The hash of the data you trained on, the training log, and the offline dev-27 table (base vs fine-tuned).
4. Where the artefacts are (path on the GPU box). The app session loads bundles; you never touch the kit.
