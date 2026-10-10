# signals_probe: YamNet and SenseVoice on public proxy audio

Experiment by person B (run by an AI assistant), 2026-10-10/11. Log: `docs/experiments/B-20261011-signals-probe.md`.
Purpose: see the EXACT strings and score ranges the two signal models produce, so `YamNetSignalMapper.kt` and
`SenseVoiceSignalMapper.kt` can be checked against reality. CPU only. **No audio or model file belongs in this
repo**; data and outputs live in `~/megathon-data/signals_probe/`, the venv in `~/.venvs/megathon-signals`.

Files here: `probe_yamnet.py`, `probe_sensevoice.py`, `analyze_candidates.py`, `fetch_data.py`, `sv_variants.py`
(exploratory), `yamnet_class_map.csv`.

## yamnet_class_map.csv
The 521 AudioSet display names in YamNet's output order (columns `index,mid,display_name`). Copied unchanged from
<https://github.com/tensorflow/models/blob/master/research/audioset/yamnet/yamnet_class_map.csv>
(**Apache-2.0**, TensorFlow models repo). It is identical in order to the `labels.txt` shipped with the
Qualcomm AI Hub export (checked with `diff`).

## Rerun (exact commands)
Downloaded files are untrusted data: scripts stay here, data elsewhere, Python that reads data runs with `-I`.
```bash
python3 -m venv ~/.venvs/megathon-signals
~/.venvs/megathon-signals/bin/pip install numpy onnxruntime soundfile scipy sherpa-onnx ai-edge-litert
D=~/megathon-data/signals_probe; T=$PWD/tools/signals_probe; PY=~/.venvs/megathon-signals/bin/python
mkdir -p $D/models $D/out

# models
curl -L -o $D/models/yamnet_class_map.csv https://raw.githubusercontent.com/tensorflow/models/master/research/audioset/yamnet/yamnet_class_map.csv
curl -L -o $D/models/yamnet_google_mediapipe.tflite https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite   # Apache-2.0, 4 MB
curl -L -o /tmp/yamnet-onnx-float.zip https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/yamnet/releases/v0.64.0/yamnet-onnx-float.zip
unzip -j /tmp/yamnet-onnx-float.zip -d $D/models                                                      # yamnet.onnx + yamnet.data + labels.txt
curl -L -o /tmp/sv.tar.bz2 https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2   # 1.0 GB (fp32 + int8)
mkdir -p $D/models/sensevoice && tar -xjf /tmp/sv.tar.bz2 -C $D/models/sensevoice --wildcards '*/tokens.txt' '*/model.int8.onnx' '*/LICENSE' '*/README.md' '*/test_wavs/*'

# data (public proxy clips, never into the repo)
$PY $T/fetch_data.py esc50   $D/esc50      # 75 clips (15 categories x 5), ~32 MB
$PY $T/fetch_data.py cremad  $D/cremad     # 60 clips, ~5 MB
$PY $T/fetch_data.py ravdess $D/ravdess    # 80 clips, ~33 MB

# 1. does every AudioSet name in the Kotlin mapper exist? (no model needed)
$PY -I $T/probe_yamnet.py --class-map $T/yamnet_class_map.csv --check-only \
  --mapper-kt core/src/main/kotlin/com/megathon/incident/signals/YamNetSignalMapper.kt
# 2. YamNet on ESC-50 (both backends; per-clip top-5 and per-category summary)
$PY -I $T/probe_yamnet.py --class-map $T/yamnet_class_map.csv \
  --mapper-kt core/src/main/kotlin/com/megathon/incident/signals/YamNetSignalMapper.kt \
  --onnx $D/models/yamnet.onnx --tflite $D/models/yamnet_google_mediapipe.tflite \
  --clips $D/esc50 --out $D/out > $D/out/yamnet_run.txt
# 3. candidate rule changes on the stored scores (no model needed)
$PY -I $T/analyze_candidates.py $T/yamnet_class_map.csv $D/out/yamnet_per_clip.json onnx
# 4. SenseVoice: test wavs + acted emotion sets (raw result fields, confusion tables)
SV=$D/models/sensevoice/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17
$PY -I $T/probe_sensevoice.py $SV --wavs $SV/test_wavs --wavs $D/cremad --wavs $D/ravdess --out $D/out/sv_all.jsonl
# 5. SenseVoice sound-event tags on ESC-50
$PY -I $T/probe_sensevoice.py $SV --wavs $D/esc50 --esc50-meta $D/esc50/esc50.csv --out $D/out/sv_esc50.jsonl
```
Optional extra used in the log: a public-domain smoke alarm recording, Wikimedia Commons `File:Smoke alarm.ogg`
(convert with `ffmpeg -i Smoke_alarm.ogg -ac 1 -ar 16000 smoke16k.wav`, cut into 5 s wavs, pass with `--clips/--meta`).

## Data sources and licences
| Data / model | Source | Licence | Use here |
|---|---|---|---|
| ESC-50 (75 clips) | github.com/karolpiczak/ESC-50 (`audio/`, `meta/esc50.csv`); first 5 clips of each category in meta order | CC BY-NC (dataset); clips from Freesound with CC licences | dev-only probe, non-commercial |
| CREMA-D (60 clips) | github.com/CheyneyComputerScience/CREMA-D `AudioWAV/` (Git LFS media URL), actors 1001-1010, sentence IEO | ODbL 1.0 (database) + DbCL 1.0 (contents) | acted emotion sanity check |
| RAVDESS speech (80 clips) | HF mirror `xbgoose/ravdess` via datasets-server (Zenodo original) | CC BY-NC-SA 4.0 | acted emotion sanity check |
| Smoke alarm (7 x 5 s) | Wikimedia Commons `File:Smoke alarm.ogg` (artist cori) | public domain per the file page | one real smoke-alarm check |
| YamNet class map | tensorflow/models | Apache-2.0 | committed here |
| YamNet ONNX (AI Hub export v0.64.0) | `qualcomm/YamNet` on Hugging Face | HF card says `mit`; original weights: see torch_audioset LICENSE (not verified here) | main backend |
| yamnet.tflite (Google, via MediaPipe) | storage.googleapis.com/mediapipe-models/... | Apache-2.0 (text embedded in the file) | cross-check backend |
| SenseVoice int8 (sherpa-onnx) | k2-fsa/sherpa-onnx release `asr-models` | FunASR model licence (see the LICENSE in the tarball) | emotion/event tags |

All clips are public proxies: studio-acted or Freesound field recordings, **not our recordings and not the kit mic**.
