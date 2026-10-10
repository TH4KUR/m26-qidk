# On-device TTS for English (Indian-accented), Hindi and Telugu on Snapdragon 8 Elite (SM8750P): open-weight model catalog

Researched 2026-10-10. Tags: **[V]** = verified (I saw it in the HF API / model card / repo / release listing / on the QIDK itself via adb); **[INF]** = my inference, not stated by a source. HF sizes and param counts come from `https://huggingface.co/api/models/<id>?blobs=true` (the safetensors `total` field) on 2026-10-10. Device facts come from read-only `adb` queries on the team's QIDK (serial `cacf48a9`, Android 16, airplane mode on) on 2026-10-10.

Sarvam Bulbul is excluded: it is cloud API or contact-sales only (per the assignment brief; not re-researched).

---

## Q1. AI4Bharat: Indic-TTS (FastPitch+HiFi-GAN), Indic Parler-TTS, IndicF5, and 2025-2026 releases

### Takeaway
AI4Bharat has no small, mobile-ready model that covers en + hi + te. Indic-TTS v1 (2023) uses one FastPitch+HiFi-GAN pair per language. It has hi, te, Indian English (`en`) and a combined `en+hi` pack, all under an MIT repo license, but it ships only as ~1.5 GB Coqui training checkpoints with no ONNX export. Indic Parler-TTS (0.94B, Apache-2.0) covers en/hi/te in one model and has named Hindi, Telugu and Indian-English speakers, but it is far too heavy for real-time on a phone. IndicF5 (350M, MIT) has no English and needs a reference clip. The only small (40M) AI4Bharat VITS model, `vits_rasa_13`, has Telugu but neither Hindi nor English.

### Cited Findings
**Indic-TTS (github.com/AI4Bharat/Indic-TTS)**
- [V] Repo license MIT; last push 2024-11-08; 408 stars. — [GitHub API: AI4Bharat/Indic-TTS](https://github.com/AI4Bharat/Indic-TTS)
- [V] Release `v1-checkpoints-release` (2023-01-18) has 16 zips of about 1.5 GB each: as, bn, brx, **en+hi**, **en**, gu, **hi**, kn, ml, mni, mr, or, pa, raj, ta, **te**. — [Indic-TTS releases](https://github.com/AI4Bharat/Indic-TTS/releases/tag/v1-checkpoints-release)
- [V] `te.zip` contents (read from the remote zip's central directory): `te/fastpitch/best_model.pth` 637.3 MB, `te/fastpitch/config.json`, `te/fastpitch/speakers.pth`, `te/hifigan/best_model.pth` 1016.4 MB, `te/hifigan/config.json`. — [te.zip](https://github.com/AI4Bharat/Indic-TTS/releases/download/v1-checkpoints-release/te.zip)
- [V] Architecture: "monolingual models with FastPitch and HiFi-GAN V1, trained jointly on male and female speakers". Inference goes through Coqui TTS (`--model_path <LANG>/fastpitch/best_model.pth --vocoder_path <LANG>/hifigan/best_model.pth`). — [Indic-TTS README](https://github.com/AI4Bharat/Indic-TTS)
- [V] Paper: "Towards Building Text-To-Speech Systems for the Next Billion Users", ICASSP 2023. MOS for Hindi: 4.00 (ground truth 4.33; DON Lab Tacotron2+WaveGlow 3.70; Vakyansh GlowTTS 3.16). MOS for Telugu: 3.66 (ground truth 4.42; DON Lab 3.40; Vakyansh 2.61). The training data is the IndicTTS database, "over 272 hours" across 13 languages. The paper gives no parameter counts and no RTF. — [arXiv 2211.09536](https://arxiv.org/abs/2211.09536), [ar5iv full text](https://ar5iv.labs.arxiv.org/html/2211.09536)
- [V] The README says the models are also published on Bhashini ULCA. — [Indic-TTS README](https://github.com/AI4Bharat/Indic-TTS)

**Indic Parler-TTS (`ai4bharat/indic-parler-tts`)**
- [V] **937,803,241 params**, F32 `model.safetensors` 3,751 MB. License **Apache-2.0**, gated "auto" (click-through). Created 2024-10-28, card modified 2026-10-06. HF language tags include `en`, `hi`, `te`. — [HF API](https://huggingface.co/ai4bharat/indic-parler-tts)
- [V] Fine-tuned from `indic-parler-tts-pretrained` (same 937.8M size) on 1,806 h. It supports 21 languages officially, including English, Hindi and Telugu, and "officially supports Indian English accents through its English voices". It has 69 named speakers. Hindi: Rohit, Divya, Aman, Rani (recommended: Rohit, Divya). Telugu: Prakash, Lalitha, Kiran (recommended: Prakash, Lalitha). English: 21 speakers (recommended: Thoma, Mary). The language is detected from the prompt text, and the voice and style are set by a natural-language description. — [Indic Parler-TTS card](https://huggingface.co/ai4bharat/indic-parler-tts)
- [V] Native Speaker Score (MOS-like, %) after fine-tuning: Hindi 84.79 ± 2.09; Telugu 88.54 ± 1.86. — [Indic Parler-TTS card](https://huggingface.co/ai4bharat/indic-parler-tts)
- [V] A community ONNX export exists: `naklitechie/indic-parler-tts-ONNX` (transformers.js, Apache-2.0, 2026-04-30). It has fp32, fp16 and q4f16 variants. In q4f16 alone the external data files are decoder 428.9 MB, decoder_with_past 190.8 MB and text_encoder 226.1 MB. — [HF](https://huggingface.co/naklitechie/indic-parler-tts-ONNX)

**IndicF5 (`ai4bharat/IndicF5`)**
- [V] **350,681,834 params**, F32 1,403 MB, license **MIT**, gated auto, created 2025-03-11. Languages: as, bn, gu, mr, **hi**, kn, ml, or, pa, ta, **te**. **There is no English.** Trained on 1,417 h (Rasa, IndicTTS, LIMMITS, IndicVoices-R). Output is 24 kHz. Every call needs a reference prompt audio plus its transcript (F5-style voice cloning). The card now says "Indic-Speak … has better text-to-speech capabilities compared to IndicF5. We recommend using Indic-Speak." — [IndicF5 card](https://huggingface.co/ai4bharat/IndicF5)

**Small AI4Bharat VITS models (2024)**
- [V] `ai4bharat/vits_rasa_13`: VITS (HF transformers custom code), **40,150,384 params**, 160.7 MB, **CC-BY-4.0**, gated auto, created 2024-12-31. Languages: as, bn, brx, doi, kn, mai, ml, mr, ne, pa, sa, ta, **te**. It has no `hi` and no `en`. — [HF API](https://huggingface.co/ai4bharat/vits_rasa_13)
- [V] An ungated re-upload with the identical param count (40,150,384), `shethjenil/IndicTTS`, has a config showing 24 kHz, a character tokenizer (`phonemize: false`) and 20 speaker names. The only Telugu entry is **"Telugu-Female"**. — [HF shethjenil/IndicTTS](https://huggingface.co/shethjenil/IndicTTS)
- [V] `ai4bharat/vits-hi-itts-base`, `vits-hi-proximal-chha`, `vits-hi-asr-enhanced`, `vits-hi-synthetic`, `vits-multilingual-itts` and `vits-multilingual-all` (2024-10-24) are raw `G_*.pth` (~483–512 MB) and `D_*.pth` (561 MB) training checkpoints. The cards have no license and no docs. — [HF vits-hi-itts-base](https://huggingface.co/ai4bharat/vits-hi-itts-base), [HF vits-multilingual-all](https://huggingface.co/ai4bharat/vits-multilingual-all)
- [V] 2026 release: `ai4bharat/bhili-tts` (2026-05-21, MIT, Parler 937.8M, Bhili only). It is not relevant here. — [HF](https://huggingface.co/ai4bharat/bhili-tts)

### Inferences
- [INF] Each Indic-TTS `.pth` is probably a full Coqui training checkpoint with optimizer state, and the HiFi-GAN one probably includes the discriminator. The deployable generator weights should therefore be much smaller than 637 MB + 1016 MB, but I found no published parameter count. Converting these to ONNX means exporting FastPitch and HiFi-GAN yourself. Coqui's built-in ONNX export is for VITS, not FastPitch. This is too risky for a 24 h hackathon.
- [INF] Indic Parler-TTS is autoregressive (Parler/MusicGen-style decoder over DAC codec tokens) with about 0.94B params. Even in q4f16 ONNX it is around 850 MB, and on a phone CPU it will run far slower than real time. It is fine as an offline **pre-generation** tool on a laptop: render the fixed prompts ("How severe is the incident?") to WAV in hi/te/en-IN and bundle the audio. The summary still needs live TTS.
- [INF] `vits_rasa_13` (40M) is the right size for a phone but only covers Telugu (one female voice). It is gated and uses custom HF code, so it would need an ONNX export, for example via the transformers.js/optimum VITS path that `Xenova/mms-tts-*` uses.

### Gaps
- No FastPitch or HiFi-GAN param counts, and no CPU/mobile RTF, for Indic-TTS v1.
- I did not check whether Bhashini/ULCA hosts lighter or ONNX versions of the Indic-TTS models (the ULCA site needs login and JS).
- I found no AI4Bharat 2025-2026 TTS release beyond IndicF5 (2025-03) and bhili-tts (2026-05) in the `ai4bharat` HF org listing.

---

## Q2. Meta MMS-TTS (facebook/mms-tts-hin, mms-tts-tel, mms-tts-eng)

### Takeaway
MMS-TTS is a set of single-speaker VITS models, one per language, at about **36.3M params / 145 MB fp32** each. License is **CC-BY-NC-4.0** (non-commercial; fine for a hackathon, but flag it). Input is raw native-script characters, with no uroman and no phonemizer. sherpa-onnx ships a pre-converted `vits-mms-eng`. `hin` and `tel` are not pre-converted but can be converted with sherpa-onnx's documented script. A transformers.js ONNX build of Hindi already exists (38 MB quantized).

### Cited Findings
- [V] `facebook/mms-tts-hin`: 36,291,120 params; `mms-tts-tel`: 36,289,776; `mms-tts-eng`: 36,284,592. Each `model.safetensors` is about 145 MB (F32). All three have license `cc-by-nc-4.0`, architecture tag `vits`, paper arXiv 2305.13516, created 2023-08/09. — [HF mms-tts-hin](https://huggingface.co/facebook/mms-tts-hin), [HF mms-tts-tel](https://huggingface.co/facebook/mms-tts-tel), [HF mms-tts-eng](https://huggingface.co/facebook/mms-tts-eng)
- [V] Each `tokenizer_config.json` has `is_uroman: false`, `phonemize: false`, `normalize: true`, so input is native script. — [mms-tts-hin tokenizer_config](https://huggingface.co/facebook/mms-tts-hin/raw/main/tokenizer_config.json), [mms-tts-tel tokenizer_config](https://huggingface.co/facebook/mms-tts-tel/raw/main/tokenizer_config.json)
- [V] Vocab check. `hin`: 72 symbols, ASCII digits only 0,1,2,3,4,8, **no Latin letters**. `tel`: 65 symbols, only the digit `6`, **no Latin letters**. `eng`: 38 symbols including a–z. — [mms-tts-hin vocab.json](https://huggingface.co/facebook/mms-tts-hin/raw/main/vocab.json), [mms-tts-tel vocab.json](https://huggingface.co/facebook/mms-tts-tel/raw/main/vocab.json)
- [V] sherpa-onnx `tts-models` release has `vits-mms-eng.tar.bz2` (107.7 MB, 2023-12-14) and also deu, fra, nan, rus, spa, tha, ukr. There is **no `vits-mms-hin` or `vits-mms-tel`**. — [sherpa-onnx tts-models release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models)
- [V] sherpa-onnx documents converting any MMS language with `export lang=eng; python3 ./vits-mms.py`, which produces `model.onnx` and `tokens.txt`, and says "you can replace it with other languages". The script rejects uroman models (`raise ValueError("We don't support uroman!")`). — [sherpa-onnx MMS docs](https://k2-fsa.github.io/sherpa/onnx/tts/mms.html)
- [V] `Xenova/mms-tts-hin` (transformers.js ONNX) has `model.onnx` 114.3 MB, `model_fp16.onnx` 58.1 MB and `model_quantized.onnx` 38.4 MB. — [HF Xenova/mms-tts-hin](https://huggingface.co/Xenova/mms-tts-hin)
- [V] Community Telugu fine-tunes exist, for example `onecxi/mms-telugu-female-indic` and `-male-indic` (CC-BY-NC-4.0, 83.0M params, 332 MB, 2025-07). — [HF onecxi/mms-telugu-female-indic](https://huggingface.co/onecxi/mms-telugu-female-indic)

### Inferences
- [INF] Since `hin` and `tel` are `is_uroman: false`, the sherpa-onnx `vits-mms.py` path should work for them as it does for `eng`. This takes about 30 minutes on a laptop and produces roughly 110 MB fp32 ONNX per language, or about 35–40 MB int8.
- [INF] The hin/tel vocabularies have no Latin letters and only some digits. Any English word, acronym ("FIR", "SOS"), number or date inside a Hindi or Telugu sentence must therefore be normalized in the app into native-script words before synthesis, or it will be silently dropped.
- [INF] MMS speaker identity and prosody differ between hin, tel and eng, because each is a separate single-speaker model trained on religious-text recordings. Switching languages mid-conversation will sound like three different people.

### Gaps
- I did not extract MMS-TTS MOS numbers for hin/tel from arXiv 2305.13516.
- No measured RTF for MMS VITS on a Snapdragon CPU. Architecturally it is the same VITS family as Piper medium (see Q3 for Piper numbers).

---

## Q3. Piper voices (hi_IN, te_IN) and sherpa-onnx pre-converted Indic TTS models

### Takeaway
Piper now has **3 Hindi voices and 3 Telugu voices**. Each is a medium-quality, single-speaker VITS at about 63 MB fp32 ONNX, using espeak-ng phonemes at 22.05 kHz. The Telugu voices were added 2025-08-26. There is **no `en_IN` Piper voice**. sherpa-onnx ships all three Hindi voices pre-converted (fp32/fp16/int8, int8 ≈ 21 MB) and even as ready-made **Android system TTS-engine APKs**. The Telugu voices are not yet in the sherpa-onnx release and need its `add_meta_data.py` step. Dataset licenses differ by voice (CC-BY-NC-SA-4.0, CC-BY-4.0, IITM IndicTTS license).

### Cited Findings
- [V] `rhasspy/piper-voices` (repo license MIT, last modified 2026-09-17) contains:
  - `hi/hi_IN/pratham/medium` (63.5 MB)
  - `hi/hi_IN/priyamvada/medium` (63.5 MB)
  - `hi/hi_IN/rohan/medium` (63.0 MB)
  - `te/te_IN/maya/medium` (63.0 MB)
  - `te/te_IN/padmavathi/medium` (63.5 MB)
  - `te/te_IN/venkatesh/medium` (63.5 MB)
  - also `ml_IN` (arjun, meera), `ur_PK`, `bn_BD`
  - **no `en_IN` voice**

  — [HF rhasspy/piper-voices](https://huggingface.co/rhasspy/piper-voices)
- [V] The commit "Add telugu voices" is dated 2025-08-26. — [piper-voices commits](https://huggingface.co/rhasspy/piper-voices/commits/main)
- [V] Every hi/te voice's `.onnx.json` has `phoneme_type: espeak`, espeak voice `hi` or `te`, 22,050 Hz, `num_speakers: 1`. — e.g. [te_IN-maya-medium.onnx.json](https://huggingface.co/rhasspy/piper-voices/blob/main/te/te_IN/maya/medium/te_IN-maya-medium.onnx.json)
- [V] Dataset licenses per MODEL_CARD:
  - **pratham** and **priyamvada** (hi): "License: CC-BY-NC-SA-4.0", dataset listed as AI4Bharat indicnlp_corpus, trained by PravalX.
  - **rohan** (hi): IIT Madras IndicTTS "Hindi Mono Male", IITM license PDF, "Finetuned from U.S. English lessac voice (medium quality)".
  - **maya** (te): IITM IndicTTS, IITM license, fine-tuned from lessac medium.
  - **padmavathi** and **venkatesh** (te): AI4Bharat IndicVoices-R, **CC-BY-4.0**, trained by PravalX.

  — [pratham MODEL_CARD](https://huggingface.co/rhasspy/piper-voices/blob/main/hi/hi_IN/pratham/medium/MODEL_CARD), [rohan MODEL_CARD](https://huggingface.co/rhasspy/piper-voices/blob/main/hi/hi_IN/rohan/medium/MODEL_CARD), [maya MODEL_CARD](https://huggingface.co/rhasspy/piper-voices/blob/main/te/te_IN/maya/medium/MODEL_CARD), [padmavathi MODEL_CARD](https://huggingface.co/rhasspy/piper-voices/blob/main/te/te_IN/padmavathi/medium/MODEL_CARD), [venkatesh MODEL_CARD](https://huggingface.co/rhasspy/piper-voices/blob/main/te/te_IN/venkatesh/medium/MODEL_CARD)
- [V] `rhasspy/piper` is **archived** (last push 2025-08-26). Development moved to `OHF-Voice/piper1-gpl` (GPL-3.0, active, last push 2026-10-06). Voices stay in the MIT `piper-voices` repo. — [GitHub rhasspy/piper](https://github.com/rhasspy/piper), [GitHub OHF-Voice/piper1-gpl](https://github.com/OHF-Voice/piper1-gpl)
- [V] sherpa-onnx `tts-models` release (646 assets, checked 2026-10-10):
  - `vits-piper-hi_IN-{rohan,priyamvada,pratham}-medium` at 67.2 MB fp32, ~36 MB fp16, ~21 MB int8 (tar.bz2, 2025-12-05).
  - `vits-piper-ml_IN-{meera,arjun}-medium`.
  - **No `te_IN` Piper, no `vits-mms-hin`/`-tel`, no Telugu model of any kind.**
  - Other Indic entries: `vits-mimic3-gu_IN-cmu-indic_low` and `vits-mimic3-bn-multi_low`.
  - sherpa-onnx latest release: v1.13.8 (2026-09-10).

  — [sherpa-onnx tts-models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models), [sherpa-onnx releases](https://github.com/k2-fsa/sherpa-onnx/releases)
- [V] sherpa-onnx has `scripts/piper/add_meta_data.py` (plus `generate.py`, `dynamic_quantization.py`), which converts a Piper ONNX for sherpa-onnx. — [sherpa-onnx scripts/piper](https://github.com/k2-fsa/sherpa-onnx/tree/master/scripts/piper)
- [V] sherpa-onnx publishes **Android TTS-engine APKs** (system TTS engines). For v1.13.8 this includes `sherpa-onnx-1.13.8-arm64-v8a-hin-tts-engine-vits-piper-hi_IN-{pratham,priyamvada,rohan}-medium.apk` and `...-hin-tts-engine-sherpa-onnx-supertonic-3-tts-int8-2026-05-11.apk`, hosted at `huggingface.co/csukuangfj2/sherpa-onnx-apk/.../tts-engine-new/1.13.8/`. There is **no Telugu engine APK**. — [sherpa-onnx TTS engine APKs](https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html)
- [V] RTF data:
  - Galaxy S10 (Exynos 9820), sherpa-onnx, 4 threads, English (VoicePing, 2026-02-15): Piper ryan-low **0.077**, amy-low **0.076**, Android System TTS 0.058, Matcha-icefall LJSpeech 0.135, Kitten nano 0.387. — [VoicePing offline TTS eval](https://voiceping.net/en/blog/research-offline-tts-eval/)
  - Raspberry Pi 4: Piper `en_US-lessac-medium` RTF between about 0.36 and 0.77 depending on thread count (1–4). — [sherpa-onnx VITS docs](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html)
- [V] A third-party GGUF of a Hindi Piper voice exists (`pronoobie/piper-voices-hindi`, `hi_IN-rohan-medium.gguf` 31.4 MB). It is not needed. — [HF](https://huggingface.co/pronoobie/piper-voices-hindi)

### Inferences
- [INF] Piper is the strongest **CPU** candidate. Each voice has about 15–20M params (sherpa-onnx medium VITS; compare Qualcomm's Piper-EN split: encoder 7.51M + flow 7.39M + decoder 1.66M, see Q5). Medium voices are probably 2–3× slower than "low", which still puts them well under RTF 0.3 on the 8 Elite's Oryon cores, which are much faster than an Exynos 9820. A 2–3 s prompt should render in about 100–300 ms.
- [INF] Fastest integration path: install the sherpa-onnx Hindi TTS-engine APK and pick that engine in the existing Android `TextToSpeech` code (`new TextToSpeech(ctx, listener, "<engine package>")`). This keeps the team's current TTS code unchanged and replaces Google's closed engine with open weights. Telugu needs either a self-converted `te_IN` voice inside the team's own sherpa-onnx integration (AAR plus `add_meta_data.py`) or a self-built engine APK.
- [INF] English: there is no Indian-accent Piper voice. Choose between en_GB/en_US Piper, MMS-eng, Google `en-IN` (Q8), Indic Parler pre-rendered clips, or SYSPIN English VITS (Q6).
- [INF] License care: the pratham/priyamvada voices are CC-BY-NC-SA (OK for a hackathon, but share-alike and non-commercial). The rohan/maya voices are tied to the IITM IndicTTS license. Padmavathi/venkatesh (CC-BY-4.0 data) are the cleanest Telugu choice.
- [INF] Frontend: Piper hi/te depends on espeak-ng's Hindi and Telugu rules (sherpa-onnx bundles `espeak-ng-data`). espeak-ng Telugu is rule-based and may mispronounce English loanwords or digits. Pre-normalize numbers and dates into native-script words in the app.

### Gaps
- No MOS or listening-test data for any Piper hi_IN/te_IN voice.
- No measured RTF for Piper medium hi/te on SM8750 specifically.
- I did not read the IITM IndicTTS license PDF (`iitm.ac.in/donlab/indictts/downloads/license.pdf`), so commercial or redistribution terms for rohan/maya are unknown.

---

## Q4. Kokoro-82M and multilingual versions (Hindi? Telugu?)

### Takeaway
Kokoro-82M (StyleTTS2-based, Apache-2.0) v1.0 has **4 Hindi voices (2F/2M), graded "C"**, and **no Telugu**. ONNX builds are plentiful: 325 MB fp32, about 86–92 MB 8-bit. sherpa-onnx now accepts `--kokoro-lang hi` (espeak-ng frontend). On phones Kokoro is much slower than Piper (RTF > 1 on a Galaxy S10), so for this use case it is a second choice behind Piper or Supertonic for Hindi.

### Cited Findings
- [V] `hexgrad/Kokoro-82M`: "open-weight TTS model with 82 million parameters", license **Apache-2.0**, base model `yl4579/StyleTTS2-LJSpeech`. `kokoro-v1_0.pth` is 327.2 MB. v1.0 (2025-01-27) has "8 & 54" languages and voices, trained on "few hundred hrs". — [HF hexgrad/Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M)
- [V] VOICES.md lists Hindi with `lang_code='h'` and voices `hf_alpha`, `hf_beta` (female) and `hm_omega`, `hm_psi` (male). All have target quality B and **overall grade C**. Training duration is listed as "MM minutes", meaning short. No Telugu or other Indic language is listed. — [Kokoro VOICES.md](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md)
- [V] The kokoro GitHub README says espeak-ng is "used for English OOD fallback and some non-English languages", with code `'h' => Hindi hi`. — [hexgrad/kokoro README](https://github.com/hexgrad/kokoro)
- [V] `hexgrad/Kokoro-82M-v1.1-zh` (2025-02-27, Apache-2.0) is a Chinese-focused update. — [HF](https://huggingface.co/hexgrad/Kokoro-82M-v1.1-zh)
- [V] `onnx-community/Kokoro-82M-v1.0-ONNX`: `model.onnx` 325.5 MB, fp16 163.2 MB, `model_quantized` 92.4 MB, q8f16 86.0 MB, q4f16 154.6 MB. — [HF onnx-community/Kokoro-82M-v1.0-ONNX](https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX)
- [V] sherpa-onnx assets: `kokoro-multi-lang-v1_0` (349.9 MB tar), `kokoro-int8-multi-lang-v1_0` (132.3 MB), `kokoro-int8-multi-lang-v1_1` (147.0 MB), `kokoro-int8-en-v0_19` (103.2 MB). — [sherpa-onnx tts-models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models)
- [V] The sherpa-onnx docs page says multi-lang v1.0 has 53 speakers, with IDs 31–34 = hf_alpha, hf_beta, hm_omega, hm_psi, but also "we only add English and Chinese support for it". It reports RTF on a Raspberry Pi 4 (4 threads) of 3.191 for multi-lang v1.0 and 2.774 for en v0.19. — [sherpa-onnx Kokoro docs](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html)
- [V] The current sherpa-onnx source contradicts that docs page. It registers `--kokoro-lang` with help text "Example values: en (English), es (Spanish), fr (French), **hi (hindi)**, it (Italian), pt-br". The multi-lang frontend accepts either `--kokoro-lexicon` or `--kokoro-lang`, and `lang` can also be overridden per call. — [offline-tts-kokoro-model-config.cc](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/offline-tts-kokoro-model-config.cc), [offline-tts-kokoro-impl.h](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/offline-tts-kokoro-impl.h)
- [V] Android numbers (Galaxy S10, sherpa-onnx, 4 threads): Kokoro en v0.19 RTF **1.133**; Kokoro int8 multi-lang v1.1 RTF **2.423**. — [VoicePing](https://voiceping.net/en/blog/research-offline-tts-eval/)
- [V] HF search found no usable Hindi or Telugu Kokoro fine-tune. `pheonix-delta/kokoro-hindi-studio-24k` (2026-09-06) contains only a test file. — [HF pheonix-delta/kokoro-hindi-studio-24k](https://huggingface.co/pheonix-delta/kokoro-hindi-studio-24k)

### Inferences
- [INF] One Kokoro model plus `kokoro-lang` switching could cover **en + hi** with a consistent quality level. Telugu would still need Piper, MMS or SYSPIN. Kokoro's Hindi voices are low-data (grade C), so quality may be below the dedicated Piper Hindi voices.
- [INF] On the 8 Elite CPU (roughly 2–3× a Galaxy S10 per core), Kokoro RTF would probably land around 0.3–0.6. That is acceptable for short prompts but adds latency to a long summary. Kokoro's StyleTTS2 graph has dynamic shapes, which makes QNN/HTP conversion harder than for VITS.

### Gaps
- No published listening scores for Kokoro Hindi. sherpa-onnx's `--kokoro-lang hi` path is in the source but not exercised in any doc I found.

---

## Q5. MeloTTS and Qualcomm AI Hub TTS models (languages, NPU exports)

### Takeaway
MeloTTS (MIT) covers EN (with an **EN_INDIA accent speaker**), ES, FR, ZH, JP and KR. It has **no Hindi or Telugu**. Qualcomm's HF org has six TTS models, all pre-exported for the "Voice AI" runtime (QAIRT 2.50) with Snapdragon 8 Elite timings: **MeloTTS-EN/ES/ZH and PiperTTS-EN/DE/IT**. None is Indic. The QIDK itself ships a Qualcomm TTS service built from a "melo/npu" code base, but no model weights were found on the device.

### Cited Findings
- [V] MeloTTS languages: English, Spanish, French, Chinese, Japanese, Korean. English speakers: EN-US, EN-BR, **EN_INDIA**, EN-AU, EN-Default. "This library is under MIT License". "Fast enough for CPU real-time inference". — [GitHub myshell-ai/MeloTTS](https://github.com/myshell-ai/MeloTTS)
- [V] `myshell-ai/MeloTTS-English` has a `checkpoint.pth` of 207.9 MB, MIT. — [HF](https://huggingface.co/myshell-ai/MeloTTS-English)
- [V] Qualcomm HF org (263 models) has these TTS entries: `qualcomm/MeloTTS-EN`, `MeloTTS-ES`, `MeloTTS-ZH`, `PiperTTS-EN`, `PiperTTS-DE`, `PiperTTS-IT`. Nothing for Hindi, Telugu or Kokoro. — [HF qualcomm org](https://huggingface.co/qualcomm)
- [V] `qualcomm/MeloTTS-EN` (MIT, ai-hub-models v0.64.0):
  - Runtime VOICE_AI, precision `mixed_with_float`, QAIRT 2.50. Downloadable zips for "Snapdragon 8 Elite For Galaxy", 8 Gen 3, 8 Elite Gen 5 and others.
  - "Compatible with the Qualcomm Voice AI SDK", downloadable from Qualcomm Package Manager.
  - Max decoded sequence length 512 tokens.
  - Components: bert_wrapper 94.5M params / 360 MB, encoder 8.30M, flow 20.1M, decoder 14.5M, t5_encoder 15.1M, t5_decoder 5.72M.
  - 8 Elite (Galaxy) per-call NPU times: bert 3.307 ms, encoder 16.502 ms, flow 42.215 ms, decoder 42.777 ms, t5_encoder 0.518 ms, t5_decoder 0.279 ms. Identical numbers are listed for "Dragonwing Q-8750".

  — [HF qualcomm/MeloTTS-EN](https://huggingface.co/qualcomm/MeloTTS-EN), [AI Hub MeloTTS-EN](https://aihub.qualcomm.com/models/melotts_en)
- [V] `qualcomm/PiperTTS-EN` (MIT):
  - Checkpoint `rhasspy/piper-checkpoints`. Max decoded sequence length 64 tokens.
  - Components: encoder 7.51M, flow 7.39M, decoder 1.66M, sdp 1.04K, plus charsiu T5 G2P encoder 15.1M and decoder 5.72M.
  - 8 Elite NPU times: encoder 9.302 ms, flow 8.448 ms, decoder 4.981 ms, sdp 7.437 ms, charsiu encoder 0.523 ms, charsiu decoder 0.286 ms.
  - Users can re-export with "Custom weights (e.g., fine-tuned checkpoints)" and custom input shapes via the `qai_hub_models` library.

  — [HF qualcomm/PiperTTS-EN](https://huggingface.co/qualcomm/PiperTTS-EN)
- [V] On the team's QIDK: `com.qualcomm.qti.voiceai.speech` (`/system_ext/priv-app/QualcommSpeechService/QualcommSpeechService.apk`, versionName 1.0.0) registers `android.intent.action.TTS_SERVICE`.
  - Its `lib/arm64-v8a/libtts.so` (2.06 MB) contains build paths `.../voiceai_tts.apss.1.0_Android/voiceai/tts-noship/melo/npu/rpc/...` and strings `KEY_MODEL_PATH`, `loadModel`, "Failed to read model file!", "language %s is not supported".
  - No `*melo*` or `*tts*` model files were found under /vendor, /system, /product or /odm, and the app's data dir has no model.

  — QIDK adb measurement 2026-10-10 (local file /system_ext/priv-app/QualcommSpeechService/QualcommSpeechService.apk); see also the project skill (local file `/home/th4kur/Desktop/m26-qidk/.claude/skills/qidk-npu-android/SKILL.md`, §1b)

### Inferences
- [INF] MeloTTS-EN with the EN_INDIA speaker is the only NPU-ready **Indian-accented English** path. I could not confirm whether the AI Hub / Voice AI MeloTTS-EN pack exposes the EN_INDIA speaker ID or only EN-US. Per call, the NPU chain is about 105 ms (bert + enc + flow + dec) for up to 512 tokens.
- [INF] Qualcomm PiperTTS uses the same VITS layout as the Piper hi_IN/te_IN voices, so the `qai_hub_models pipertts_en` "custom weights" export may in principle accept a Hindi or Telugu Piper `.ckpt`. Two obstacles: (a) the bundled charsiu G2P is English-only, so hi/te phonemes would have to come from espeak-ng on the CPU; (b) piper-voices publishes ONNX, not the PyTorch `.ckpt`, for most hi/te voices. This is NPU-team territory and is risky in 24 h.
- [INF] The preinstalled Qualcomm TTS service looks like a MeloTTS-on-NPU engine that expects a model path from the Voice AI SDK package. Without weights it probably cannot speak. Short strings "hi"/"te" also appear in `libtts.so`, but at 2 characters this is inconclusive evidence of Hindi/Telugu support.

### Gaps
- I did not determine which languages the preinstalled Qualcomm `TextToSpeechService` supports, or whether installing Voice AI SDK assets from QPM would activate it. QPM needs a login.
- I did not verify whether the AI Hub MeloTTS-EN bundle includes the EN_INDIA speaker embedding.

---

## Q6. Other Indic TTS (IITM/SMTL, SYSPIN/IISc, Bhashini, Veena, Svara, Indri, XTTS-v2, Supertonic, Indic-Speak, community fine-tunes)

### Takeaway
2025-2026 brought several **large LLM-codec Indic TTS models**: Svara-TTS v1 (3.3B, hi/te/Indian-en, Apache-2.0), Indic-Speak (3.3B, 23 languages including en/hi/te, custom license, gated) and Veena (3.8B, hi/en only, Apache-2.0). All are far too heavy for real-time on-device TTS. The useful small finds are:
- **Supertonic 3** (Supertone, ~99M params, ONNX, OpenRAIL-M): **Hindi + English in one model**, no Telugu. sherpa-onnx int8 is 129 MB, and a Hindi Android TTS-engine APK exists.
- **SYSPIN VITS** (IISc SPIRE Lab, MIT on the card): separate Coqui VITS models for Hindi, Telugu and English, male and female.
- **IITM FastSpeech2_HS** (CC-BY-4.0): FastSpeech2 + HiFi-GAN for Hindi and Telugu, male and female, 22 kHz and 48 kHz.

XTTS-v2 has Hindi but no Telugu and is non-commercial.

### Cited Findings
**Small or medium, potentially on-device**
- [V] **Supertonic 3** (`Supertone/supertonic-3`, created 2026-05-06):
  - License OpenRAIL-M (model; sample code MIT). "Runs with ONNX Runtime entirely on your device". "About 99M parameters across the public ONNX assets".
  - Files: `vector_estimator.onnx` 256.5 MB, `vocoder.onnx` 101.4 MB, `text_encoder.onnx` 36.4 MB, `duration_predictor.onnx` 3.7 MB, `unicode_indexer.json` (character input, no G2P). Preset voices M1–M2 and F1–F5 (sampled; more may exist).
  - 31 languages including **hi** and **en**, **not te**. Hindi CER **5.34** on Minimax-MLS-test (VoxCPM2 5.85, OmniVoice 5.14).
  - Average RTF 0.3× in airplane mode on an Onyx Boox Go 6 e-reader.

  — [HF Supertone/supertonic-3](https://huggingface.co/Supertone/supertonic-3), [GitHub supertone-inc/supertonic](https://github.com/supertone-inc/supertonic)
- [V] sherpa-onnx ships `sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2` (128.8 MB) and an engine APK `sherpa-onnx-1.13.8-arm64-v8a-hin-tts-engine-sherpa-onnx-supertonic-3-tts-int8-2026-05-11.apk`. — [sherpa-onnx tts-models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models), [sherpa-onnx engine APKs](https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html)
- [V] **SYSPIN VITS (IISc SPIRE Lab data)**:
  - `SYSPIN/vits_Hindi_Female`, `vits_Hindi_Male`, `vits_Telugu_Female`, `vits_Telugu_Male`, `vits_English_Female`, plus Bhojpuri, Gujarati, Bengali, Kannada, Marathi, Maithili, Magahi and Chhattisgarhi. Created 2025-02 to 2025-04, card license **MIT**.
  - Each ships one `best_model.pth` (997.9 MB) and a `config.json`, with inference via Coqui `tts --model_path ... --config_path ...`.
  - The Telugu config shows `model: vits`, 22,050 Hz, `use_phonemes: false`, `multilingual_cleaners`, and a character set of native Telugu script plus some ASCII letters and digits. English is also character-based.

  — [HF SYSPIN/vits_Telugu_Female](https://huggingface.co/SYSPIN/vits_Telugu_Female), [HF SYSPIN/vits_Hindi_Female](https://huggingface.co/SYSPIN/vits_Hindi_Female), [HF SYSPIN/vits_English_Female](https://huggingface.co/SYSPIN/vits_English_Female)
- [V] **IITM SMTL FastSpeech2_HS** (`smtiitm/Fastspeech2_HS`, CC-BY-4.0, last push 2026-02-05):
  - FastSpeech2 with hybrid segmentation for 16 Indian languages, male and female. HiFi-GAN vocoders provided for Aryan and Dravidian families.
  - Hindi M/F and Telugu M/F available as both 22 kHz (main branch) and 48 kHz "New HQ" models. There is no English row in the coverage table.
  - Weights come via Git LFS. Supports `<alpha=…>` speed and `<sil=…>` pause tags. Copyright "Speech Technology Consortium, Bhashini, MeitY".

  — [GitHub smtiitm/Fastspeech2_HS](https://github.com/smtiitm/Fastspeech2_HS)
- [V] **Indri 0.1-124m** (`11mlabs/indri-0.1-124m-tts`): the safetensors total is **139.6M params** (558.5 MB F32), although the card says 124M. GPT-2-based, decodes Kyutai Mimi audio tokens. Languages English and Hindi (code-mixed). The HF tag says CC-BY-SA-4.0 but the card says "not for commercial usage … research showcase". 400 tok/s on an RTX6000Ada. — [HF 11mlabs/indri-0.1-124m-tts](https://huggingface.co/11mlabs/indri-0.1-124m-tts)
- [V] **Kyutai Pocket TTS** (`kyutai/pocket-tts`): CC-BY-4.0, en/fr/de/pt/it/es, no Indic. sherpa-onnx int8 is 98.3 MB. A community "Indian English male (IndicTTS)" fine-tune exists, `prasadvittaldev/pocket-tts-indian-english-male-indictts` (2026-09-15, 0 downloads, unvetted). — [HF kyutai/pocket-tts](https://huggingface.co/kyutai/pocket-tts), [HF prasadvittaldev/...](https://huggingface.co/prasadvittaldev/pocket-tts-indian-english-male-indictts)
- [V] **KittenTTS** (nano/micro/mini) and **Matcha-icefall** on sherpa-onnx are English or Chinese only (asset names: `kitten-*-en-*`, `matcha-icefall-en_US-ljspeech`, `matcha-icefall-zh-*`, `matcha-tts-fa_en-*`). — [sherpa-onnx tts-models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models)
- [V] Other community Telugu VITS: `multilingual-tts/VITS-OpenBible-Telugu` (CC-BY-SA-4.0, Coqui, 997.8 MB checkpoint, 2026-05-19). — [HF](https://huggingface.co/multilingual-tts/VITS-OpenBible-Telugu)

**Large (LLM plus audio codec), not realistic on-device in real time**
- [V] **Svara-TTS v1** (`kenpath/svara-tts-v1`, 2025-10-26): **3,300,867,072 params** (F32 safetensors 13.2 GB), **Apache-2.0**. Orpheus-style SNAC tokens, base `canopylabs/3b-hi-ft-research_release`. 19 languages including Hindi, Telugu and **Indian English**. About 50 speakers addressed as "Language (Gender)". Emotion tags. 2000+ h from SYSPIN, RASA, IndicTTS and SPICOR. The card claims GGUF exports are "suitable for edge/CPU scenarios". — [HF kenpath/svara-tts-v1](https://huggingface.co/kenpath/svara-tts-v1)
- [V] **Indic-Speak** (`bodhan-ai/indic-speak`, 2026-08-31): 3,300,928,512 params BF16 (6.6 GB) plus a `vocos/best.pt` vocoder (467 MB). Base `meta-llama/Llama-3.2-3B`. 23 languages, with English, Hindi and Telugu in the "Production" tier. License "**Indic Open Model License v1.0**", gated with a form. Reports NORESQA MOS 4.46 ± 0.26, WER/CER 26.49/17.86 and "~200 ms" response time (hardware not stated). It handles code-mixing and STEM text. — [HF bodhan-ai/indic-speak](https://huggingface.co/bodhan-ai/indic-speak)
- [V] **Veena** (`maya-research/Veena`, 2025-06-24): HF reports **3,783,054,336 params** BF16 (7.6 GB); the card says "3B parameter … Llama". **Hindi and English only** (code-mixed), 4 voices (kavya, agastya, maitri, vinaya), SNAC 24 kHz, Apache-2.0, "sub-80ms latency on H100". — [HF maya-research/Veena](https://huggingface.co/maya-research/Veena)
- [V] **Coqui XTTS-v2** (`coqui/XTTS-v2`): 17 languages: en, es, fr, de, it, pt, pl, tr, ru, nl, cs, ar, zh-cn, hu, ko, ja, **hi**. **No Telugu.** License: Coqui Public Model License (non-commercial). `model.pth` 1.87 GB. — [HF coqui/XTTS-v2](https://huggingface.co/coqui/XTTS-v2)
- [V] Parler-TTS Mini v1 (877.8M, Apache-2.0) is English only. It is the base family of Indic Parler. — [HF parler-tts/parler-tts-mini-v1](https://huggingface.co/parler-tts/parler-tts-mini-v1)

### Inferences
- [INF] Svara, Indic-Speak and Veena are Orpheus-style 3B LLMs. Real-time SNAC output needs about 80+ audio tokens/s, which is far beyond a 3B model on a phone CPU. Even with an NPU LLM runtime they would compete with the incident-extraction LLM for memory and NPU time. Drop them for live TTS. Svara or Indic-Speak could be used offline on a laptop to **pre-render fixed prompts** in natural Hindi, Telugu and Indian English.
- [INF] SYSPIN VITS is attractive because it is MIT-carded and has Hindi, Telugu and Indian(-accent) English from one data family with matched M/F voices. However, the checkpoint is a 998 MB Coqui training file (generator plus discriminator plus optimizer), and it needs Coqui → ONNX export. Coqui VITS has an `export_onnx` path, and sherpa-onnx already runs `vits-coqui-*` models. The generator would probably be about 35–45M params (around 150 MB fp32). Whether `vits_English_Female` actually sounds Indian is unverified; SPIRE Lab's SYSPIN/SPICOR are Indian-speaker corpora.
- [INF] Supertonic 3 + Piper te_IN is a tidy two-model combination: one model for en+hi, plus about 20–60 MB for Telugu. Note that Supertonic's English voices are not Indian-accented.
- [INF] Bhashini: the AI4Bharat Indic-TTS models and the IITM/SMTL models are what Bhashini serves. I found no separate, downloadable "Bhashini on-device TTS" package.

### Gaps
- No mobile RTF for SYSPIN VITS, FastSpeech2_HS, Supertonic 3 on Snapdragon, or Indri.
- I did not check Microsoft/Google open TTS releases for Indic (for example Google "Gemma"-family TTS or Microsoft VibeVoice) in this pass. HF searches for Telugu TTS surfaced none.
- I did not verify GGUF availability or speed for Svara on Android.

---

## Q7. Which candidates are realistically small (<~100M) and fast (RTF << 1 on phone CPU/NPU) while covering hi + te + en?

### Takeaway
**No single open model under ~100M covers English, Hindi and Telugu.** Every one-model option is 350M to 3.8B (Indic Parler, Svara, Indic-Speak) or lacks a language (IndicF5 has no en; Kokoro, Supertonic and Veena have no te; vits_rasa_13 has no hi or en). The realistic plan is **one small VITS-family model per language, run through sherpa-onnx on the CPU**, with Hindi and Telugu taken from Piper (or MMS or SYSPIN) and English from Piper/MMS/Kokoro, or MeloTTS EN_INDIA on the NPU. Android Google TTS with `en-IN`/`hi-IN`/`te-IN` offline packs remains the lowest-effort fallback (Q8).

### Cited Findings (catalog)
| Model (ID) | Arch | Params / disk | en | hi | te | Voices | License | Exports | Source |
|---|---|---|---|---|---|---|---|---|---|
| Piper `hi_IN-{pratham,priyamvada,rohan}-medium` | VITS (Piper), espeak-ng | ~63 MB fp32 ONNX; sherpa int8 ~21 MB | – | ✔ | – | 1 each (rohan = "Hindi Mono Male" per card; pratham/priyamvada genders not stated) | data: CC-BY-NC-SA-4.0 / IITM | ONNX, sherpa-onnx, **Android engine APK** | [piper-voices](https://huggingface.co/rhasspy/piper-voices), [sherpa APKs](https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html) |
| Piper `te_IN-{maya,padmavathi,venkatesh}-medium` | VITS (Piper), espeak-ng `te` | 63.0–63.5 MB ONNX | – | – | ✔ | 1 each | data: IITM / CC-BY-4.0 | ONNX (needs sherpa `add_meta_data.py`) | [piper-voices](https://huggingface.co/rhasspy/piper-voices) |
| MMS-TTS `facebook/mms-tts-{hin,tel,eng}` | VITS, chars | 36.3M / 145 MB; ONNX q8 38 MB (hin) | ✔ | ✔ | ✔ (3 models) | 1 each | **CC-BY-NC-4.0** | sherpa `vits-mms-eng`; script for others; Xenova ONNX hin | [HF](https://huggingface.co/facebook/mms-tts-tel), [sherpa MMS](https://k2-fsa.github.io/sherpa/onnx/tts/mms.html) |
| `ai4bharat/vits_rasa_13` | VITS, chars, 24 kHz | 40.2M / 161 MB | – | – | ✔ (F) | 20 spk / 13 langs | CC-BY-4.0, gated | HF transformers only | [HF](https://huggingface.co/ai4bharat/vits_rasa_13) |
| SYSPIN `vits_{Hindi,Telugu,English}_{Female,Male}` | Coqui VITS, chars | 998 MB training ckpt | ✔ (F) | ✔ | ✔ | 1 per model | MIT (card) | Coqui .pth only | [HF](https://huggingface.co/SYSPIN/vits_Telugu_Female) |
| IITM `smtiitm/Fastspeech2_HS` | FastSpeech2 + HiFi-GAN | not stated | – | ✔ M/F | ✔ M/F | per lang/gender | CC-BY-4.0 | PyTorch (config.yaml + stats .npz; ESPnet-style = INF) | [GitHub](https://github.com/smtiitm/Fastspeech2_HS) |
| AI4Bharat Indic-TTS v1 | FastPitch + HiFi-GAN | ~1.5 GB zip per lang | ✔ (`en`, `en+hi`) | ✔ | ✔ | M+F per lang | MIT (repo) | Coqui .pth only | [releases](https://github.com/AI4Bharat/Indic-TTS/releases/tag/v1-checkpoints-release) |
| Kokoro-82M v1.0 | StyleTTS2 + iSTFTNet | 82M / 325 MB fp32, ~90 MB q8 | ✔ | ✔ (grade C) | – | 54 total, 4 hi | Apache-2.0 | ONNX, sherpa-onnx (`--kokoro-lang hi`) | [HF](https://huggingface.co/hexgrad/Kokoro-82M) |
| Supertonic 3 | duration predictor + text encoder + vector estimator + vocoder (flow-matching label INF) | ~99M / ~398 MB fp32; sherpa int8 129 MB | ✔ | ✔ | – | 7 presets | OpenRAIL-M | ONNX, sherpa-onnx, **hi engine APK** | [HF](https://huggingface.co/Supertone/supertonic-3) |
| MeloTTS-EN (EN_INDIA spk) | VITS-family + BERT features (arch label INF) | ~208 MB ckpt; AI Hub split ~158M | ✔ (Indian accent) | – | – | 5 accents | MIT | **Qualcomm AI Hub / Voice AI, 8 Elite** | [GitHub](https://github.com/myshell-ai/MeloTTS), [HF qualcomm](https://huggingface.co/qualcomm/MeloTTS-EN) |
| Qualcomm PiperTTS-EN | VITS + charsiu G2P | ~16.6M VITS + 20.8M G2P | ✔ | – | – | 1 | MIT | AI Hub / Voice AI | [HF](https://huggingface.co/qualcomm/PiperTTS-EN) |
| Indri-0.1-124m | GPT-2 + Mimi codec | 139.6M / 559 MB | ✔ | ✔ | – | many spk | NC (card) | PyTorch | [HF](https://huggingface.co/11mlabs/indri-0.1-124m-tts) |
| IndicF5 | F5 flow-matching (DiT) | 351M / 1.4 GB | – | ✔ | ✔ | zero-shot (ref clip) | MIT | PyTorch | [HF](https://huggingface.co/ai4bharat/IndicF5) |
| Indic Parler-TTS | Parler (T5 enc + AR dec, DAC) | 938M / 3.75 GB; ONNX q4f16 ~0.85 GB | ✔ (Indian) | ✔ | ✔ | 69 named | Apache-2.0 | ONNX (community, transformers.js) | [HF](https://huggingface.co/ai4bharat/indic-parler-tts) |
| Svara-TTS v1 | Orpheus (Llama 3B) + SNAC | 3.3B / 13 GB | ✔ (Indian) | ✔ | ✔ | ~50 | Apache-2.0 | GGUF mentioned | [HF](https://huggingface.co/kenpath/svara-tts-v1) |
| Indic-Speak | Llama-3.2-3B + Vocos | 3.3B / 6.6 GB | ✔ | ✔ | ✔ | several/lang | Indic Open Model License, gated | PyTorch | [HF](https://huggingface.co/bodhan-ai/indic-speak) |
| Veena | Llama 3B + SNAC | 3.78B / 7.6 GB | ✔ | ✔ | – | 4 | Apache-2.0 | PyTorch (4-bit bnb) | [HF](https://huggingface.co/maya-research/Veena) |
| XTTS-v2 | GPT + HiFi-GAN decoder | ~1.9 GB | ✔ | ✔ | – | cloning | CPML (NC) | PyTorch | [HF](https://huggingface.co/coqui/XTTS-v2) |

- [V] Measured Android RTF (Galaxy S10, 4 threads, English): Android System TTS 0.058; Piper low 0.076–0.077; Matcha 0.135; Kitten nano 0.387; Kokoro 1.133 (en v0.19) and 2.423 (int8 multi-lang v1.1). — [VoicePing](https://voiceping.net/en/blog/research-offline-tts-eval/)
- [V] NPU timings exist only for Qualcomm's MeloTTS-EN/ES/ZH and PiperTTS-EN/DE/IT exports (8 Elite: about 105 ms of NPU time per Melo call; Piper components 5–9 ms each). — [HF qualcomm/MeloTTS-EN](https://huggingface.co/qualcomm/MeloTTS-EN), [HF qualcomm/PiperTTS-EN](https://huggingface.co/qualcomm/PiperTTS-EN)

### Text-frontend notes (where Indic TTS on Android breaks)
- [V] Piper hi/te uses **espeak-ng** phonemization (`hi`, `te` voices). sherpa-onnx publishes `espeak-ng-data.tar.bz2`/`.zip` as release assets (that its per-voice tarballs/APKs bundle it is [INF]). — [piper-voices te_IN json](https://huggingface.co/rhasspy/piper-voices/blob/main/te/te_IN/maya/medium/te_IN-maya-medium.onnx.json), [sherpa-onnx tts-models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models)
- [V] MMS hin/tel take **raw native-script characters**, with no Latin letters and partial digits in the vocab (see Q2). SYSPIN and vits_rasa_13 are also character models (`use_phonemes: false` / `phonemize: false`). Supertonic uses a `unicode_indexer` (characters, no G2P). Kokoro Hindi uses espeak-ng via misaki. Indic Parler takes plain native-script text plus a description. — sources in Q2/Q4/Q6
- [INF] Rules that work for every candidate:
  - (1) Store prompts in **native script** (Devanagari for hi, Telugu script for te). Do not use romanized text.
  - (2) **Pre-normalize numbers, dates, times, units and acronyms** into words in the app before TTS, especially for the summary ("severity 3" → "तीन" / "మూడు"). A tiny per-language number-to-words function is enough for a 24 h build.
  - (3) For code-mixed English terms inside Hindi or Telugu, either transliterate them to native script or split the sentence and send the English part to the English voice.
  - (4) Strip or replace characters outside each model's `tokens.txt`.
  - (5) Unicode-normalize (NFC) and remove zero-width joiners if the model vocab lacks them. The SYSPIN Telugu vocab shows a stray U+200E, so watch invisible characters.

### Inferences (recommendation ladder for this team)
1. [INF] **Baseline (already planned): Android `TextToSpeech` with Google voices `en-IN`, `hi-IN`, `te-IN`.** Download the packs while online, then demo in airplane mode. Closed weights, CPU. Disclose it.
2. [INF] **Best open-weights CPU option: sherpa-onnx (Android AAR, ONNX Runtime CPU)** with
   - Piper `hi_IN-rohan` or `-priyamvada` (int8 ~21 MB)
   - Piper `te_IN-padmavathi` or `-venkatesh` (convert with `add_meta_data.py`; int8 via `dynamic_quantization.py`)
   - Piper en_GB/en_US or `vits-mms-eng` for English

   Expected RTF is about 0.05–0.2 on 8 Elite CPU (inferred). Total assets are under 100 MB in int8. The quickest Hindi demo is to install the prebuilt sherpa-onnx Hindi engine APK and select it as the TTS engine.
3. [INF] **Alternative one-model en+hi:** Supertonic 3 int8 (129 MB) or Kokoro int8 (132 MB), plus Piper te_IN for Telugu.
4. [INF] **NPU stretch:** MeloTTS-EN (EN_INDIA if the Voice AI pack exposes it) via the QIDK ASR-LLM-TTS sample for English only, keeping hi/te on CPU sherpa-onnx. VITS models (Piper, MMS) are the most QNN-friendly Indic candidates if the NPU researcher wants to port one. Qualcomm already ships a Piper VITS NPU export with a 64-token static input, which suggests static-shape chunking is needed.
5. [INF] **Polish trick:** pre-render the fixed prompt set with Indic Parler-TTS (Rohit/Divya, Prakash/Lalitha, Thoma/Mary) or Svara on a laptop, ship them as WAV assets, and use live on-device TTS only for the dynamic summary. This still counts as on-device at runtime, but disclose to judges that the prompts are pre-recorded synthetic audio.

### Gaps
- No Indic model has a published RTF on SM8750 CPU or NPU. The team should measure Piper hi/te on the kit (sherpa-onnx `--debug` prints RTF).
- I found no comparative listening study between Piper hi/te, MMS, SYSPIN and Google voices.

---

## Q8. Does Android's built-in Google TTS have offline en-IN, hi-IN and te-IN voices? How are they installed? Do they work without network?

### Takeaway
Yes. On the team's QIDK, Google's Speech Services (`com.google.android.tts`, build 20250303) has a voice manifest that lists **offline `.zvoice` packs for `en-in` (16.2 MB), `hi-in` (17.6 MB) and `te-in` (11.0 MB)**, plus smaller "seanet" variants (~4 MB) and larger "news" voices for en-in and hi-in. **Only `en-us` is installed now** (bundled in the APK), and the kit is in airplane mode. The team must download the hi/te/en-IN packs once via Settings → Text-to-speech → Google engine → "Install voice data" with network on, then test offline.

### Cited Findings
- [V] QIDK default TTS engine is Google (`com.google.android.tts`). The project skill measured it, and adb confirms the package at `/product/app/SpeechServicesByGoogle/SpeechServicesByGoogle.apk`, versionName `googletts.google-speech-apk_20250303.01_p1.734589102`, versionCode 210547004. — QIDK adb measurement 2026-10-10; local file `/home/th4kur/Desktop/m26-qidk/.claude/skills/qidk-npu-android/SKILL.md` §1b
- [V] Installed voice packs (`/data/user_de/0/com.google.android.tts/files/superpacks/`): `en-us/en-us-x-multi-r81` (19 MB) and an empty `en-au` dir (3.5 KB). The APK bundles `assets/voices/en-us/en-us-x-multi-r81.zvoice` (14.2 MB). **No hi-in, te-in or en-in files exist on the device.** — QIDK adb measurement 2026-10-10
- [V] The on-device manifest `superpacks/manifests/ttsvoices-3101` (JSON) lists downloadable packs with sizes and download URLs:

  | Pack | Size |
  |---|---|
  | `en-in-x-multi-r63` | 16,153,846 B |
  | `en-in-x-multi-seanet-r63` | 3,766,133 B |
  | `en-in-x-news-lemonbalm-r63` | 20,961,727 B |
  | `en-in-x-news-darwinnrio-lemonbalm-r63` | 66,270,494 B |
  | `hi-in-x-multi-r60` | 17,587,485 B |
  | `hi-in-x-multi-seanet-r60` | 3,686,342 B |
  | `hi-in-x-news-lemonbalm-r60` | 11,509,889 B |
  | `hi-in-x-news-darwinnrio-lemonbalm-r60` | 56,810,528 B |
  | `te-in-x-multi-r53` | 11,006,116 B |
  | `te-in-x-multi-seanet-r53` | 4,198,800 B |

  Telugu has no "news" variant. Example URL: `https://dl.google.com/android/tts/v26/te-in/te-in-x-multi-r53.zvoice`. — QIDK adb measurement 2026-10-10 (manifest file above)
- [V] Kit network state: `airplane_mode_on = 1`. — QIDK adb measurement 2026-10-10
- [V] Google's install steps are "Open your device Settings" → "Select **Install voice data**" → "Choose the language that you want to install". The help page does not list languages or discuss offline behavior. — [Google Accessibility Help: Text-to-speech output](https://support.google.com/accessibility/android/answer/6006983?hl=en)
- [V] Android System TTS was the fastest engine measured on a Galaxy S10: RTF 0.058 (English). — [VoicePing](https://voiceping.net/en/blog/research-offline-tts-eval/)

### Inferences
- [INF] Once a `.zvoice` pack is installed, Google TTS synthesizes on-device. Check this in the app before the demo:
  - Call `TextToSpeech.getVoices()` and pick a `Voice` whose locale is `hi-IN`/`te-IN`/`en-IN`, whose `isNetworkConnectionRequired()` is false, and whose `getFeatures()` does not contain `notInstalled`.
  - Then run the prompts with Wi-Fi off. Without the pack, the engine may fall back to a network voice or fail.
- [INF] The "multi" naming suggests several speakers per pack; exact voice names are only visible from `getVoices()` after install.
- [INF] Installing needs a network connection once. A QSSI userdebug image should work because Play Services and Play Store (`com.android.vending`) are present. If Wi-Fi is not allowed at the venue, do it before the event. Sideloading `.zvoice` files into `superpacks/` with `adb root` might work but is unsupported and untested.
- [INF] "darwinnrio" in the larger news voices suggests a variant tuned for Google's own accelerators. On Snapdragon these run on the CPU, so judges should be told Google TTS runs on the CPU, not on Snapdragon NPU acceleration.

### Gaps
- I did not test actual offline synthesis for hi-IN/te-IN on the kit, because the packs are not installed and the device is in airplane mode.
- I did not fetch the official Android `Voice` API reference text (the developer.android.com fetch returned only the index). The API method names above are from general knowledge [INF].
- I found no official Google list of offline-capable TTS locales; the on-device manifest is the best evidence.
