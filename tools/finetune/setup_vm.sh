#!/usr/bin/env bash
# Environment for the Qwen3-1.7B LoRA run and the Qualcomm quantize/export step, on a Linux x86-64 box with an NVIDIA GPU.
# Versions are the ones pinned by qai_hub_models/models/qwen3_1_7b/requirements.txt (torch 2.10.0, transformers 5.7.0,
# aimet-onnx 2.34.0), so the checkpoint saved by training loads under the same stack that quantizes it.
#
#   bash setup_vm.sh            # creates ~/venv
#
# Needs: Python 3.10 to 3.13, CUDA driver for torch 2.10 (nvidia-smi must work). Does NOT configure the AI Hub token:
# run `qai-hub configure --api_token <token>` yourself, interactively. Never paste the token anywhere else.
set -euo pipefail
python3 -m venv ~/venv
source ~/venv/bin/activate
pip install -U pip wheel
pip install "torch==2.10.0" "transformers==5.7.0" "accelerate==1.12.0" peft safetensors sentencepiece
pip install qai-hub-models
qai-hub-models install qwen3_1_7b
python - <<'PY'
import torch, transformers, peft
print("torch", torch.__version__, "cuda", torch.cuda.is_available(), torch.cuda.get_device_name(0) if torch.cuda.is_available() else "-")
print("transformers", transformers.__version__, "peft", peft.__version__)
PY
nvidia-smi --query-gpu=name,memory.total --format=csv,noheader
df -h ~ | tail -1
free -g | head -2
