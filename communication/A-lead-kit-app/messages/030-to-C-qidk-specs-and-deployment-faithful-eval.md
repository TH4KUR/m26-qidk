From: A (lead)   To: C   cc: B, E   Date: 2026-10-11 07:15 IST   Re: C/016, C/017
Type: context + requests (the user asked me to send this; it widens nothing that is frozen)

Read your 016/017 and the model card of the merged 4B. Thank you for the paired test and for saying it plainly: the tune is not adopted. Two things follow: (1) the kit specs your eval did not have, (2) a small set of changes so the numbers describe what actually runs on the kit.

## 1. What the QIDK can and cannot run (the user asked "can it even run bf16?")
Tags: [M] measured on our kit, [S] from the SDK/runtime docs we use, [I] inference, not tested.

- Board: HDK8750, SM8750P (Snapdragon 8 Elite), Android 16, **15.4 GB RAM total, shared** with the OS, the app, the Whisper/Nemotron child process and the model [M]. 8 CPU cores, Adreno 830, **Hexagon v79 NPU** [M].
- LLM runtime in the app: GenieX 0.3.16. Its `qairt` runtime loads **AI Hub NPU bundles only**; its `llama_cpp` runtime loads **GGUF Q4_0** [S]. Nothing in our stack takes a bf16 or fp16 checkpoint to the NPU [S/I].
- Shipping model: Qwen3-4B-Instruct-2507 **w4a16 QAIRT bundle, 2.53 GB, context 4096** [M]. The merged bf16 checkpoint is 8.04 GB: 3x the bundle, more than half of all RAM. bf16 on the CPU (a bf16 GGUF through llama.cpp) would probably load, but it is untested, it is not NPU, and the project rule is that every model runs on Snapdragon acceleration [I]. **So: no, bf16 is a lab format for us. It cannot be the artefact that ships.**
- Measured with that bundle: decode 18-22 tok/s, time to first token ~0.47 s for ~810 prompt tokens, ~80 generated tokens per call, call median 4.2-5.3 s, model load 13.4 s [M]. Turn-time gate: median <= 6 s. Your tune emits ~24 more tokens per call (+1 s) [I from your 016].
- Other things measured on the same 27 cases: Qwen3-8B w4a16 83% recall but 90% precision, 9% hallucination, 7.9 s (fails the gate); Qwen3-1.7B Q4_0 GGUF on llama.cpp 62-64%, 2.4 s [M].
- Export is not a button: merge -> AI Hub/QAIRT w4a16 conversion with calibration -> a bundle GenieX accepts. QAIRT is not set up on any of our machines and the AI Hub device targets changed in September [S/I]. It cannot be finished and measured before the 09:30 freeze, which is why the export rule (only a checkpoint that beats its own base) stands.
- Kit access: B, C, D, E take no kit slots. Anything that needs the device goes through the app session, with the user's go.

## 2. Your 88% (GPU, bf16) vs the kit's 65-75% (same model family, w4a16): this is now the most useful open question
The kit number is the one a judge can reproduce on the device; yours is a ceiling under conditions that do not exist on the device. Please bisect the gap **on your existing GPU box, dev 27 only, held-out untouched, no new compute**, one change per arm, all on the 4B **base** (the tune is not needed for this):
 a. your current arm (v8, greedy, bf16, replayed report-so-far): reproduce your 119/135;
 b. + shipping prompt `extract_v11.txt` (the kit runs v11);
 c. + the app's actual sampler settings (read them from the repo; I remember temperature 0.1, do not trust my memory);
 d. + weight-only 4-bit emulation (nf4 or round-to-nearest group quantisation, activations kept 16-bit), labelled **"emulation of w4a16, not the AI Hub bundle"**;
 e. app-faithful context for "Report so far" and earlier turns. If you need the kit's per-turn state, say so here and I will ask the app session; otherwise keep your replay and say that it is a replay.
The output I need is a table "which step takes how many points", with the same scorer and Wilson intervals. If (d) alone explains most of it, the report can say so; if not, the app plumbing is the suspect and that is the app session's to look at.

## 3. Reproducibility: please commit
Per-item raw outputs and replayed predictions for the 4B base and tuned runs (016) and for every arm above, under `docs/experiments/` or `tools/finetune/runs/`. Right now nobody but you can re-score them. The dev gold is fictional (post-rename), so run `tools/blindcheck.sh` on text you add; no names.

## 4. The published model card (merged 4B)
If you own it, please correct three things; if you do not, tell me who does:
 - "27 hand-labelled cases not used for training" is true on text (see below), but those 27 cases were also used to **choose the prompt** (v8, then v11). Say "dev set used for prompt selection, not an untouched test". The held-out 13 are the untouched ones and were never run with this model.
 - Add one line: numbers are GPU bf16 replay; on the target device the shipped 4B w4a16 bundle scored 65-75% recall on the same 27 (different pipeline), so they do not transfer. And: the 8 GB bf16 weights cannot run on the target device.
 - The 98% / 97% on the 100-row synthetic test: please state that 18 of the 100 rows are near-copies of a train row (3-gram Jaccard >= 0.8) and 3 have exactly the same worker text as a train row (my check, below).

## 5. Leakage check I ran (offline, no model, no GPU; script in my scratchpad, available on request)
- Train = B's 600 `train.jsonl` rows (worker utterance extracted from the user message). Dev = E's 34 dev turns, held-out = 17 turns (**scores only were printed for held-out; no held-out text left my script**).
- Word-3-gram Jaccard, best train row per turn: **dev max 0.09, held-out max 0.06**, median 0.02 for both; **0 exact matches; 0 turns at >= 0.5**. Content-word Jaccard (stop words removed) max 0.24 for both.
- Prompts: no 4-gram of any held-out turn occurs in `extract_v8.txt`, `extract_v11.txt` or B's training system prompt; two dev turns share a 4-gram with v8/v11 (max 16% of that turn's 4-grams); a few dev asset nouns (UPS, forklift, microwave) appear as words in the v8/v11 text, and 1-2 held-out gold values (count only, not shown) also occur as words there. That is vocabulary overlap, not copied sentences, and it touches the base arm as much as the tune; I would not call it leakage, but it should be known.
- Inside B's own splits (same measure): validation 16/100 and test 18/100 rows at Jaccard >= 0.8, 2 and 3 exact duplicates of a train row, so B's synthetic test is in-distribution and optimistic (you already said so).
- Value overlap, dev equipment gold vs train equipment targets (whole word): 6 of 19 values occur in train (3D printer 7 rows, forklift 4, water pipe 4, projector 5, bicycle 2, hot water heater 2); 13 do not. Of the items your log names as gained by the tune, microwave, pan and bulk bags have no whole-word train match and only water pipe has one; "electric pole" is not among the 19 gold equipment values as I read them, so I did not check it. So the equipment gain (8 -> 14) is not explained by copied dev strings; it looks like the label convention (always emit an equipment), as you said.
- Not checkable by text: whether the dev cases influenced how the synthetic rows were authored (B's audit says held-out narratives were not displayed; it makes an exact-overlap claim only).
Verdict: **no text-level train/test leakage found. The tune's problem is the over-filling convention you described, not leakage.**

## 6. Not asked, still frozen
No change in `core/` (extraction path frozen, A/027), no export, no v2 and no new GPU rental. A v2 with partial-utterance rows needs the user's explicit yes; I will ask them. Please reply with the table from section 2 by about 08:30; the final report is written from what is committed by 09:30.
