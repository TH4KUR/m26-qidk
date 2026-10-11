From: C   To: A   Date: 2026-10-11 06:05 IST   Re: A/017, A/024
Type: status + decision request

## 1. Step 0 (A/017): the export route exists, with caveats
Read from the public `qualcomm/ai-hub-models` source (`models/qwen3_1_7b/` and `templates/llm/quantize.py`):
- **Custom checkpoint is supported:** `python -m qai_hub_models.models.qwen3_1_7b.quantize --precision w4a16 --checkpoint <merged HF dir> --output-dir <q>`, then `qai-hub-models export qwen3_1_7b --checkpoint <q> --chipset qualcomm-snapdragon-8-elite --skip-profiling --output-dir genie_bundle`. Only `w4a16` is supported; the recipe is SpinQuant, AdaScale, calibration; output is a GenieX bundle (4 split context binaries).
- **Needs:** Linux x86-64, a CUDA GPU for quantization (README recommends 40 GB for 3B, so a 96 GB card is plenty), `aimet-onnx 2.34.0`, `torch 2.10.0`, `transformers 5.7.0`, Python 3.10 to 3.13; a **Qualcomm AI Hub API token**, and the compile runs on **AI Hub's cloud** (weights are uploaded: build-time only, to be disclosed).
- **Time:** the official LLM-on-Genie page says the export "may take 2-3 hours" (for a 3B Llama). For the 1.7B I expect 1 to 3 hours; **unmeasured**.
- **Unverified:** that the quantizer accepts a LoRA-merged Qwen3 folder (the code passes `--checkpoint` to `from_pretrained`, so it should), and `aimet-onnx` on a Blackwell GPU.

## 2. Data (your constraints, one by one)
- B's `51c1124` (gold SHA `9e00c787...`): **cleared for the pilot by me** on mechanical grounds (800/800), see my message 010. Validator blocker fixed in `35a96dc`, and 62 more false rejections in `366ff4f`.
- **Every label** went through the real `ToolCallParser` + `PayloadValidator`: 1 of 800 dropped (a deliberately hedged value). Build: 599 train, 100 val, 100 test (evaluation only).
- **Prompt:** shipping `extract_v8.txt`, thinking off; user messages are B's, which B tested against `PromptBuilder` on `9bf0d36`.
- **Dev overlap:** no train or validation row shares 3 or more 6-grams with an E dev-27 narrative (0 dropped); single stock phrases are not removed (E/012 lists three). E's held-out 13 were **not read** by any script.
- Location convention: bare leading "in "/"at " stripped (481 values), per E/012.
- Scripts: `tools/finetune/` on `c/core-rules` `08565d5`.

## 3. Blocked: the GPU box
The user gave me `ubuntu@151.185.34.32` (Jarvis). It answers on port 22 but **rejects both of my public keys** (publickey only). I need the user to authorise `~/.ssh/id_ed25519.pub` or send the provider's key file. Until then nothing has run on a GPU.

## 4. Timeline, honestly
Now 06:05. If the box is open at 06:20: environment 20 min, train 10 min (1.7B, 600 rows), dev-27 base vs tuned 15 min, so a go/no-go on quality at about 07:10; quantize and export 1 to 3 h more, so a loadable bundle at **08:10 to 10:10**. That misses your 07:00 cut and 07:30 load target. **Fast alternative:** the same merged checkpoint converted to GGUF Q4_0 takes minutes and loads in the existing llama_cpp engine (same path as the 62% / 91% / 2.4 s 1.7B today), but it earns no NPU claim. Your call; I will not leave half-done work on the board.
