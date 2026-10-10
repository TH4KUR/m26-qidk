From: A   To: C   Date: 2026-10-11 04:16 IST   Re: A/014, A/015
Type: task, **GO/NO-GO GATED. Do NOT start any GPU job, download or paid service until the user says go.** Read 014 and 015 first: merging the recall patch is still your first job and is not replaced by this.

## Why (kit evidence, E's 27 dev cases, same build, one run each)
| Model (NPU, recall patch, prompt v8) | Recall | Precision | Equipment | Actions | Hallucination | Median turn |
|---|---|---|---|---|---|---|
| Qwen3-4B-Instruct-2507 (primary) | 88/135 (65%) | 96% | 1/19 | 8/21 | 1% | 4.8 s |
| Qwen3-8B | 112/135 (83%) | 90% | 10/19 | 20/21 | 9% | 7.9 s |
The 8B shows the headroom is capacity on the hard fields, but it fails our speed and hallucination gates, so the 4B stays primary. Idea: teach the 4B (or 1.7B) our one task by fine-tuning, and run it on the NPU.

## Scope change from the lead (v2): the export, not the training, decides whether this happens
The training is routine. The risk is **getting a custom checkpoint onto the NPU**: 4-bit quantisation with calibration (AIMET), Hexagon compile and a GenieX bundle (SM8750, QAIRT 2.45, w4a16, context 4096). Nobody has verified that Qualcomm's export tooling accepts a custom or merged checkpoint, what token or account it needs, how long it takes, or whether the GPU box can run it. A model that only runs on CPU/GPU is **not adoptable** (the judges score the NPU).
1. **Step 0, 30 minutes, no GPU spend:** read the `qai-hub-models` Qwen3 export recipe and the Genie bundle docs. Answer in one message: does it accept a local checkpoint? what token, quota or cloud compile does it need? expected time for a 1.7B and a 4B? Say GO or NO-GO to the lead.
2. **Go/no-go at 05:15** (hard): no clear yes on the export means **cut the task** and say so. Do not train something you cannot ship.
3. Only after a GO from you **and** the user: build data, train the **1.7B first** (cheapest, finds export problems early), then the 4B if time allows.
4. **A bundle the kit can load by 07:30.** If not, cut. The held-out 13 run once at about 08:30 and code freezes at 09:30; there is no slack after 07:30.

## Data (the hard part)
- Separate training set of **1,500 to 3,000** synthetic examples. **Never train on E's dev 27 or the held-out 13**; check n-gram overlap against both and drop near-duplicates.
- **Fictional names and places only** (the fictional `site.json`); nothing real. Cover all 12 incident types, severity stated or not, relative and absolute times ("right now", "yesterday morning", "ten minutes ago"), corrections, multi-turn follow-ups, persons vs witnesses, group words that are NOT witnesses ("a lot of people"), **equipment and actions (the weak fields)**, denials ("nobody was hurt"), place codes with an article ("a B2"), Whisper-style errors, and "nothing new" updates.
- **Labels from a stronger teacher, not from the kit** (the kit is shared and 8B runs at 12 tok/s, so labelling thousands there is not possible): Qwen3-8B or larger on the GPU box. An external LLM API for build-time labelling needs the user's explicit yes and a disclosure in the report (no cloud at runtime is the rule). Then **filter every example through your real `ToolCallParser` + `PayloadValidator`** (with the recall patch) and drop anything rejected or demoted. Hand-check a random 100. Teach the 8B's weaknesses away: drop examples that fill a field with no evidence in the sentence (its 9% hallucination).
- **Inputs byte-identical to the app:** build them with the real `PromptBuilder` (system prompt `extract_v8.txt` or B's latest frozen prompt; user message = optional "Earlier the worker said" lines, "Report so far: {...}" with the output key names, then the worker sentence), chat template with thinking off. If the prompt changes after data generation, regenerate.
- **Output contract:** `{"tool":"update_report","arguments":{...}}`; persons and witnesses are lists; every evidence value one string and every evidence key also a field with a value; no null, [] or ""; each key once; strings 120 chars at most; severity only if stated; nothing new: `{"tool":"update_report","arguments":{}}`. Train some examples where the answer is *empty*; the 8B over-fills.

## Training
LoRA (rank 16 to 64) on all linear layers, loss on assistant tokens only, 2 to 3 epochs, lr about 1e-4 to 2e-4, bf16, max length 2048. Keep a validation split. **Before any export**: decode greedily on E's dev 27 on the GPU (gold text in, same prompt) with E's scorer; only a checkpoint that beats its own base goes to export. Watch for a perfect template with wrong content, lost multi-turn behaviour, invented names and over-filling.

## Adoption gate (fixed now; do not tune it after seeing results)
On the kit, same build as `dev11-salvage` (run by the app session, not you): recall >= 75%, precision >= 92%, hallucination <= 4%, valid-after-retry >= 95%, median turn <= 6 s, on the NPU. Otherwise the 4B stays. Differences under 5 points are noise (n = 27). Dev is the only measurement set; held-out once, at the end.

## Deliverables (to the lead via the board)
Step 0 answer and go/no-go; a one-page data card (count, sources, filters, fictional-names statement, blind-check result); training log and the offline dev-27 table, base vs fine-tuned; a GenieX bundle per model (sizes, quantisation, QAIRT version) or an honest "export blocked because ..."; bundles handed by path to the app/kit session, which loads them (you never touch the kit).

## Priorities (do not starve these)
1. Merge the recall patch (014, 015). 2. B's prompt work and the held-out discipline. 3. This task, only if step 0 says GO. The lead's view: a half-day experiment with an unverified export step on a day with a hard 11:00 deadline; a clean NO-GO at 05:15 is a good outcome.
