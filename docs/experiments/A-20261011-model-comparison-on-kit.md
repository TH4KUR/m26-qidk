# Model comparison on the kit: Qwen3-8B vs Qwen3-4B-Instruct-2507 (A, 2026-10-11)

Same build for both rows: throwaway branch `a/try-salvage` (`a/stubs` `eb1e013` = v8 prompt + earlier-turn context, merged with `lead/recall-salvage` `71ad07d`, plus one debug-only commit `c3d2f7e` that adds `--es model <store name>`). E's 27 dev cases (34 turns), held-out 13 never run, one run each, GenieX 0.3.16, QAIRT 2.45.0.260326154327, sampler temp 0.1 top-k 1 seed 42, max 320 tokens, context reset per call. The gold text is the input (no Whisper). Raw rows are not committed (they carry real names).

Bundles: **Qwen3-8B** `qwen3_8b-geniex_qairt-w4a16-qualcomm_snapdragon_8_elite_for_galaxy.zip` (Qualcomm AI Hub release v0.64.0, 4.57 GB zip, 5.1 GB on disk, 5 context binaries, context 4096, QnnHtp, NPU). The zip lacked GenieX's store metadata files, so `geniex.json`, `text-generator.json` (from the bundle's `genie_config.json`, key renamed) and `tool-versions.yaml` were written by hand to mirror the 4B's store entry; it loaded and ran on the NPU first try. **Qwen3-4B-Instruct-2507** is the app's current model (same release family).

| | 4B + patch (`dev11-salvage`) | 8B + patch (`dev12-8b`) |
|---|---|---|
| mandatory precision / recall | 96% (74/77) / 86% (74/86) | 94% (74/79) / 86% (74/86) |
| overall precision / recall | 96% (88/92) / 65% (88/135) | **90% (112/124) / 83% (112/135)** |
| hallucination (overall) | 1/81 (1%) | **7/81 (9%)** (4 of the 6 "no actions" cases got an action) |
| wrong values | 3 | 5 |
| valid first call / after retry | 31/34 (91%) / 32/34 (94%) | 34/34 (100%) / 34/34 (100%) |
| median / max seconds per turn | 4.8 / 14.1 | **7.9 / 13.1** |
| first token (median) / decode | ~0.9 s / ~20 tok/s | ~1.2 s / ~12 tok/s |
| model load | ~9 s | ~17.6 s |
| equipment recall | 1/19 (5%) | **10/19 (53%)** |
| immediate actions recall | 8/21 (38%) | **20/21 (95%)** |
| persons / witnesses recall | 4/7, 1/2 | 6/7, 2/2 |

## Gate (fixed by the lead before the run)
Switch only if overall recall >= 70%, overall precision >= 90%, hallucination <= 4%, valid-after-retry >= 90% and median turn <= 6 s.
- recall 83%: pass. precision 90.3%: pass (on the line). valid 100%: pass.
- **hallucination 9%: FAIL. median turn 7.9 s: FAIL.**
- **Decision: do not switch; stay on Qwen3-4B-Instruct-2507.** Differences under about 5 points are noise (one run, 27 cases).

## What it tells us
- Capacity is what the 4B lacks on the hard fields: equipment 5% to 53% and actions 38% to 95% with nothing changed but the model. Prompt tuning alone is unlikely to close that fully; this is the case for a task-specific fine-tune of the 4B or 1.7B.
- The 8B is about 1.65x slower per call and loads in ~18 s, so it does not fit a 6 s turn. It also over-fills "no actions" cases (the hallucination count).
- Not tried: Qwen3-1.7B (speed candidate; not downloaded yet at the time of writing), Gemma-4-E4B-it (9.08 GB, not approved).

## Caveats
One run per row; the dev set was used to tune prompts (optimistic); a first 8B attempt was cut at 19 calls when the app was swiped away and was discarded; the 8B store metadata was hand-written (the zip had no GenieX store files), which worked but is not Qualcomm's own packaging path.
