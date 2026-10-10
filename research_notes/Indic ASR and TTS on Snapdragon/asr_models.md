# Open-weight ASR for English (Indian-accented), Hindi and Telugu on an on-device Snapdragon 8 Elite (SM8750P)

Legend: **[V]** means verified this session in the model card, the HF API (`huggingface.co/api/models/...`, which gives licence, gating, file sizes and safetensors parameter counts), a paper, or a repo. **[I]** means inferred or estimated by me. "Gated: auto" means you click to accept terms on HF and need an HF login/token to download, with no manual approval. Research date: 2026-10-10. NPU deployment details are out of scope (another researcher covers them), so only NPU-relevance hints are given.

**Caveat on comparing WERs:** Hindi and Telugu WERs from different sources cannot be compared directly. Whisper's default normalizer strips Indic diacritics/matras, which roughly halves Hindi WER compared with Indic normalization. For example, collabora/whisper-small-hindi scores 7.17 % (Whisper normalization) vs 15.10 % (Indic normalization) on the same FLEURS test [V] ([card](https://huggingface.co/collabora/whisper-small-hindi)). Several fine-tunes also train on FLEURS train/dev, so their FLEURS-test numbers are in-domain [V for vasista22 ([card](https://huggingface.co/vasista22/whisper-telugu-small))].

---

## Q1. Which AI4Bharat models exist and are current (Oct 2026), with Hindi/Telugu WERs, licences, ONNX exports and mobile deployments?

### Takeaway
AI4Bharat's current open ASR line is **IndicConformer**. One option is the 600M multilingual hybrid CTC/RNNT model (22 Indic languages, MIT, ships as ONNX, but **no English**). The other is the 120M per-language "large" hybrid CTC/RNNT models (MIT, `.nemo`), which community repos have converted to ONNX at **~138 MB int8 for Hindi and for Telugu**. These are the most mobile-friendly strong Indic models. IndicWhisper (Vistaar) is older (2023), Whisper-medium-sized (~769M) and distributed as zip files, not on HF. IndicWav2Vec is stale (2022), and its Telugu repo is empty. The newest flagship, **Indic-Transcribe** (Bodhan AI + AI4Bharat, Aug-Sep 2026), has 1.2B params (Canary-style) under a custom licence and is too large for phone-class use without heavy quantization.

### Cited Findings
**AI4Bharat HF org inventory (API, 2026-10-10)** [V] ([API](https://huggingface.co/api/models?author=ai4bharat&limit=500)):
- ASR repos: `indic-conformer-600m-multilingual` (533,810 downloads/month, modified 2026-10-08), `indicconformer_stt_{as,bn,brx,doi,gu,hi,kn,ks,kok,mai,ml,mni,mr,ne,or,pa,sa,sat,sd,ta,te,ur}_hybrid_ctc_rnnt_large` (all dated 2024-09/11), `IndicConformer` (2025-03, modified 2026-03), `indic-seamless`, `indicwav2vec-hindi`, `indicwav2vec_v1_*`, `MahaDhwani_pretrained_conformer`. New 2026 repos (Bhili): `bhili-asr-conformer-600m`, `bhili-asr-conformer-600m-onnx`, `bhili-asr-nemotron-600m`, `bhili-asr-canary-1.2b`.

**ai4bharat/indic-conformer-600m-multilingual**
- 600M-param multilingual Conformer hybrid CTC+RNNT covering 22 scheduled Indian languages (hi and te included; **English is not in the list**). MIT licence [V] ([card](https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual)). Gated: auto [V] ([API](https://huggingface.co/api/models/ai4bharat/indic-conformer-600m-multilingual)).
- The only WER on the card is **Hindi 13.2** on `ARTPARK-IISc/Vaani-Benchmark-V1.0`. There is no Telugu number on the card [V] ([card](https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual)).
- Inference is `model(wav, "hi", "ctc"|"rnnt")`, so **a language code is required up front** [V] ([card](https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual)).
- The repo is already ONNX: `assets/encoder.onnx` with external weights, `ctc_decoder.onnx` (23.1 MB), `joint_enc/joint_pred/joint_pre_net.onnx`, one `joint_post_net_<lang>.onnx` per language (0.7 MB each) and `language_masks.json`. Total repo is 2,556 MB [V] ([API](https://huggingface.co/api/models/ai4bharat/indic-conformer-600m-multilingual?blobs=true)). This means a shared encoder plus per-language vocabulary masks [I].
- The card notes that "Indic-Transcribe offers superior quality for 25 languages" [V] ([card](https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual)).
- Community int8: `yashwork-byte/indic-conformer-600m-int8-onnx` quantizes the encoder from 2.4 GB fp32 to a self-contained **0.62 GB int8** (652.6 MB file). It cuts peak RSS from ~2.6 GB to ~1.1 GB, and the author measured "≈ +0.5 WER points on one of six spot-checked clips". It was built "for memory-constrained on-device use ... next to an LLM on an 8 GB device" [V] ([card](https://huggingface.co/yashwork-byte/indic-conformer-600m-int8-onnx)). `livinNector/indic-conformer-600m-int8-onnx` is an identical layout (MIT) [V] ([API](https://huggingface.co/api/models/livinNector/indic-conformer-600m-int8-onnx)).
- Other derivatives found [V] ([API](https://huggingface.co/api/models?filter=base_model:quantized:ai4bharat/indic-conformer-600m-multilingual)): `christopherthompson81/indicconformer-600m-onnx`, `phequals/indic-conformer-600m-multilingual-coreml-rnnt` (iOS CoreML), `Singla0009/IndicConformer-GGUF`, `sunilmahendrakar/indic-conformer-600m-multilingual`.
- **SraVaani paper, Table 3** (WER averaged over the eval datasets per language, jiwer with lowercasing and punctuation removal; IndicConformer-600M run with its RNNT decoder) [V] ([arXiv 2608.08235](https://arxiv.org/abs/2608.08235)):
  - **Hindi** (8 datasets, 51.56 h): Gemini 3 Flash 14.5, Sarvam Saaras v3 14.5, **IndicConformer-600M 13.8**, SraVaani-1.0 14.0.
  - **Telugu** (6 datasets, 17.77 h): 28.9 / 28.7 / **26.4** / 25.1.
  - Mean over 17 languages: 39.4 / 33.9 / 30.2 / 28.4.
  - The paper notes that "IndicConformer requires an explicit language identifier" and has no English support, and that IndicConformer is best on 5 of 8 CommonVoice splits.
- On **Voice of India** (unscripted telephonic speech, 15 languages, 536 h, 36,691 speakers) "Indic Conformer" scored WER **Hindi 8.2 / Telugu 23.7**. For comparison: Sarvam Audio 5.0/18.2, Saarika 2.5 6.2/18.9, Gemini 3 Pro 6.0/21.9, ElevenLabs Scribe v2 7.7/23.0 [V] ([arXiv 2604.19151](https://arxiv.org/html/2604.19151v1)). A search-result summary instead gave IndicConformer 6.5 (hi) / 20.0 (te), probably OI-WER from the leaderboard [unverified] ([voiceofindia.ai](https://voiceofindia.ai/)).

**ai4bharat/indicconformer_stt_{hi,te}_hybrid_ctc_rnnt_large (per-language)**
- "120M parameters", "conformer-Large", "17 conformer blocks with 512 as the model dimension", hybrid CTC-RNNT, MIT. It needs AI4Bharat's NeMo fork and is called with `language_id='te'`. The card has no WER [V] ([card](https://huggingface.co/ai4bharat/indicconformer_stt_te_hybrid_ctc_rnnt_large)).
- `.nemo` file is 523.2 MB (hi and te). Gated: auto [V] ([API hi](https://huggingface.co/api/models/ai4bharat/indicconformer_stt_hi_hybrid_ctc_rnnt_large), [API te](https://huggingface.co/api/models/ai4bharat/indicconformer_stt_te_hybrid_ctc_rnnt_large)).
- **ONNX exports exist:** `OpenVoiceOS/ai4bharat-indicconformer-te-onnx` and `-hi-onnx` (MIT, 2026-07-04) ship fp32 (`model.onnx` + 481.4 MB `model.onnx_data`) and **`model.int8.onnx` at 137.7 MB**, built for the `onnx-asr` library (istupakov) and the OVOS STT plugin. Exports for ta/ml/bn/kn/pa/or/gu/mr/ur/ne also exist [V] ([te card](https://huggingface.co/OpenVoiceOS/ai4bharat-indicconformer-te-onnx), [API](https://huggingface.co/api/models/OpenVoiceOS/ai4bharat-indicconformer-te-onnx?blobs=true)).
- `meetsync/indic-conformer-onnx-sherpa` (MIT, 2026-03) claims a sherpa-onnx export at 197 MB int8 / 493 MB fp32 covering 8 languages (as, bn, brx, gu, hi, kn, ks, mr; **no Telugu**). It states a "~8-12 %" WER without a benchmark [V] ([card](https://huggingface.co/meetsync/indic-conformer-onnx-sherpa)). The card contradicts itself: it says it was converted from the hi per-language model, but its script loads "IndicConformer-600M-Multi.nemo", and its sherpa config passes an RNNT "transducer" with empty decoder/joiner. **Treat it as unreliable** [I].
- No published per-language WER for the 120M models was found. The IndicVoices paper's own "IndicASR" was a "130M conformer based model" trained only on IndicVoices, multilingual across 22 languages. On the IndicVoices test it scored **Hindi 15.0 / Telugu 26.8** WER, against Whisper 34.0/151.9, MMS 38.9/62.4, Azure 27.2/53.3 and Google USM 20.5/40.3 [V] ([arXiv 2403.01926](https://arxiv.org/html/2403.01926)). IndicVoices contains deliberate code-mixing (e.g. English banking terms) [V] (same).

**ai4bharat/IndicConformer** (2025-03, modified 2026-03): a single 2,520 MB `.nemo`, CC-BY-4.0, gated auto. The card is an unfilled NeMo template ("PUT-YOUR-MODEL-SIZE-HERE"), with no languages or WER [V] ([card](https://huggingface.co/ai4bharat/IndicConformer), [API](https://huggingface.co/api/models/ai4bharat/IndicConformer)).

**IndicWhisper (Vistaar, 2023)**
- Repo [github.com/AI4Bharat/vistaar](https://github.com/AI4Bharat/vistaar): MIT ("applies to all the fine-tuned language models"). There are 12 per-language checkpoints in HF format, distributed as zips on `indicwhisper.objectstore.e2enetworks.net` (not the HF Hub) [V]. The links are still live: `telugu_models.zip` is 1,411,425,348 bytes and `hindi_models.zip` is 4,262,614,425 bytes (HTTP 200, checked 2026-10-10) [V].
- The base size is not stated in the README or abstract. A community re-upload, `parthiv11/indic_whisper_hi_multi_gpu`, has `_name_or_path: openai/whisper-medium`, d_model 1024 and 24 encoder layers, so IndicWhisper is **Whisper-medium (~769M)** [V for the re-upload config ([config](https://huggingface.co/parthiv11/indic_whisper_hi_multi_gpu/raw/main/config.json)); I for official].
- Trained on 10.7K hours across 12 languages. "IndicWhisper has the lowest WER in 39 out of the 59 benchmarks" [V] ([arXiv 2305.15386](https://arxiv.org/abs/2305.15386)).
- Vistaar WER, **Hindi**: Kathbath 10.3, Kathbath-Hard 12.0, IndicTTS 7.6, MUCS 12.0, Gramvaani 26.8, average 13.6. **Telugu**: Kathbath 25.0, Kathbath-Hard 27.8, FLEURS 25.4, IndicTTS 33.8, MUCS 32.1, average 28.8 [V] ([README](https://github.com/AI4Bharat/vistaar)).
- **Inconsistency in the README:** the per-language table gives Hindi CommonVoice 11.4 / FLEURS 15.0, while the Hindi comparison table gives FLEURS 11.4 / CommonVoice 15.0 (columns swapped). Hindi FLEURS is therefore either 11.4 or 15.0 [V] (same).
- Hindi comparison averages (same README): Google STT 23.9, IndicWav2vec 21.0, Azure 20.0, NVIDIA-medium 19.4, NVIDIA-large 18.6, IndicWhisper 13.6 [V].

**IndicWav2Vec (2022)**: `ai4bharat/indicwav2vec-hindi` is Apache-2.0 with a 1,262 MB `pytorch_model.bin` (≈315M params, so wav2vec2-large size [I]). `ai4bharat/indicwav2vec_v1_telugu` contains **only a README and .gitattributes (0 MB of weights)** [V] ([API](https://huggingface.co/api/models/ai4bharat/indicwav2vec_v1_telugu?blobs=true)). Its Hindi Vistaar average is 21.0 [V] ([Vistaar](https://github.com/AI4Bharat/vistaar)).

**ai4bharat/indic-seamless**: 1.50B params, 6.0 GB fp32, **CC-BY-NC-4.0**. Languages: en, as, bn, gu, hi, ta, te, ur, kn, ml, mr, sd, ne [V] ([API](https://huggingface.co/api/models/ai4bharat/indic-seamless)). Too large for phones [I].

**Bhili (2026)**: `bhili-asr-conformer-600m-onnx` is a fine-tune of the 600M model (MIT, uses the Marathi tokenizer) and documents the ONNX component layout (encoder/ctc_decoder/rnnt_decoder/joint_*.onnx plus `preprocessor.ts` TorchScript) [V] ([card](https://huggingface.co/ai4bharat/bhili-asr-conformer-600m-onnx)). `bhili-asr-nemotron-600m` (gated manual) ships `indic_nemotron_bhili_sft...nemo` (2,556 MB) and a cache-aware streaming inference script [V] ([API](https://huggingface.co/api/models/ai4bharat/bhili-asr-nemotron-600m)). This hints at an internal "Indic Nemotron" streaming base model that has not been released for hi/te [I].

**Indic-Transcribe (Bodhan AI + AI4Bharat, 2026)**
- `bodhan-ai/indic-transcribe-core` (2026-08-19) and `-flex` (2026-08-07): 1,222,553,584 params (F32 safetensors 4.89 GB plus a `.nemo`), `base_model: nvidia/canary-1b-v2`, licence "other" (Bodhan AI Open Model License), gated auto. Languages: en, as, bn, brx, doi, gu, hi, kn, ks, kok, mai, ml, mni, mr, ne, or, pa, sa, sat, sd, ta, te, ur, bho, bhb [V] ([API](https://huggingface.co/api/models/bodhan-ai/indic-transcribe-core)).
- Flex card: FastConformer encoder (811M) + Transformer decoder (419M). Voice of India WER: **Hindi 4.1, Telugu 13.1**, average 11.3. Core averages 8.7 [V] ([flex card](https://huggingface.co/bodhan-ai/indic-transcribe-flex)).
- Three output modes: native script, mixed script (English words and numbers in Latin), and full romanization. Optional LID via `return_lid=True`, but card-reported LID accuracy is **Hindi 0.26** and Malayalam/Tamil 0.98 [V] (same).
- Licence summary from the card: commercial use allowed with credit, share-alike for derivatives, written approval for hosting as a service, and a separate licence above 500M MAU or $250M revenue [V] (same).
- The card says streaming is unavailable and points to an "Indic-Transcribe-lite" for real-time use. **No lite model appears in the bodhan-ai HF org** (only indic-ocr, indic-transcribe-core, indic-transcribe-flex, indic-translate, indic-speak) [V] ([API](https://huggingface.co/api/models?author=bodhan-ai)).

**Sarvam** (Saaras/Saarika/Sarvam Audio): cloud API or sales contact only, so excluded. It appears only as a commercial reference point in Voice of India [V] ([arXiv 2604.19151](https://arxiv.org/html/2604.19151v1)).

### Inferences
- For a phone, the practical AI4Bharat picks are (a) **per-language 120M IndicConformer hi and te in int8 ONNX (~138 MB each)**, or (b) **600M multilingual int8 (~650 MB encoder plus small heads)** if one Indic model for both languages is preferred. Neither handles English, so English must come from Whisper or another model.
- Conformer CTC heads are static-shape-friendly (fixed-length chunks, no autoregressive loop), which makes them better NPU candidates than an RNNT or Whisper decoder [I]. The NPU researcher should check the Hexagon v79 op coverage of the Conformer depthwise-conv and relative-position attention.
- IndicConformer output is native script. English loanwords in code-mixed speech will most likely be transliterated into Devanagari or Telugu script, because the training data (IndicVoices) is native-script with code-mixing [I].

### Gaps
- No official per-language WER (Kathbath/FLEURS/IndicVoices) was found for the 120M `indicconformer_stt_te/hi` models or for Telugu on the 600M model's card. Voice of India Telugu (23.7) is the only Telugu number found for the 600M model.
- The WER impact of the OpenVoiceOS int8 per-language exports is unmeasured.
- The Voice of India per-model table for OmniASR-LLM 1B/7B was not extracted.

---

## Q2. How do stock multilingual Whisper sizes perform on Hindi and Telugu? Which community fine-tunes exist? Is whisper-small usable for Telugu?

### Takeaway
Stock Whisper is **unusable for Telugu at every size**: FLEURS WER is 98-105 % for tiny through large-v2, and 151.9 % on IndicVoices. For Hindi it is unusable at tiny/base (>100 %) and poor at small (38.4 %). **Fine-tuned Whisper models change this completely:** vasista22's Telugu models reach 17.2 (tiny), 13.4 (base) and 11.6 (small) % on FLEURS, and Hindi fine-tunes reach 7-10 % (Whisper normalization). These are the same architecture as the team's current Whisper path, which makes them the lowest-friction upgrade.

### Cited Findings
**Whisper paper, Table 13 (FLEURS WER %)** [V] ([arXiv 2212.04356](https://arxiv.org/abs/2212.04356), Appendix D.2.4):

| Model | Hindi | Telugu | Tamil | Urdu | Bengali | English |
|---|---|---|---|---|---|---|
| tiny (39M) | 102.3 | 105.1 | 99.9 | 65.2 | 101.6 | 12.4 |
| base (74M) | 101.1 | 105.2 | 58.7 | 52.0 | 100.6 | 8.9 |
| small (244M) | 38.4 | **98.2** | 35.2 | 37.3 | 104.4 | 6.1 |
| medium (769M) | 26.8 | 82.8 | 23.1 | 28.2 | 100.6 | 4.4 |
| large | 26.9 | 100.6 | 20.6 | 25.0 | 104.9 | 4.5 |
| large-v2 | 21.5 | 99.0 | 17.5 | 22.6 | 104.1 | 4.2 |

- The paper names Telugu among the worst outliers relative to its training-data trend, and attributes this partly to "our byte level BPE tokenizer being a poor match for these languages" [V] (same).
- On IndicVoices, "Whisper" scored Hindi 34.0 and Telugu 151.9 WER (the size is not given in the extracted text, probably large) [V] ([arXiv 2403.01926](https://arxiv.org/html/2403.01926)).
- Collabora reports the stock OpenAI baselines on FLEURS-Hindi under its own normalization: tiny 172.60, base 149.17, small 67.37 WER [V] ([tiny-hindi](https://huggingface.co/collabora/whisper-tiny-hindi), [base-hindi](https://huggingface.co/collabora/whisper-base-hindi), [small-hindi](https://huggingface.co/collabora/whisper-small-hindi)).
- large-v3 / large-v3-turbo per-language FLEURS numbers for hi/te were **not found**. The openai/whisper `language-breakdown.svg` is drawn as paths, not text.

**Telugu fine-tunes**
- `vasista22/whisper-telugu-{tiny,base,small,medium,large-v2}` (2022-12, Whisper fine-tuning sprint). FLEURS te_in test WER: **tiny 17.15, base 13.39, small 11.59, medium 9.47, large-v2 9.65** [V] ([tiny](https://huggingface.co/vasista22/whisper-telugu-tiny), [base](https://huggingface.co/vasista22/whisper-telugu-base), [small](https://huggingface.co/vasista22/whisper-telugu-small), [medium](https://huggingface.co/vasista22/whisper-telugu-medium), [large-v2](https://huggingface.co/vasista22/whisper-telugu-large-v2)).
- Training data: CSTD IIIT-H, ULCA, Shrutilipi, the Microsoft Indian-language corpus, **FLEURS train+dev** and Babel. Licence Apache-2.0 (seen on the small card; the other sizes are the same family [I]). Language must be forced with `language="te"` [V] ([small card](https://huggingface.co/vasista22/whisper-telugu-small)).
- `ARTPARK-IISc/whisper-medium-vaani-telugu` (2024-12) and `ARTPARK-IISc/whisper-large-v3-vaani-telugu` (2025-09) exist. No WER table was found on the medium card [V] ([API listing](https://huggingface.co/api/models?author=ARTPARK-IISc)).
- `vanshnawander/whisper-tiny-telugu` (2025-12, Kathbath-trained) reports WER **97.83** / CER 77.13, down from 402.96 / 171.09 for the base model [V] ([card](https://huggingface.co/vanshnawander/whisper-tiny-telugu)). `vanshnawander/whisper-small-telugu` reports Shrutilipi WER 69.7 / CER 28.9 [V] ([card](https://huggingface.co/vanshnawander/whisper-small-telugu)). A small-data tiny fine-tune is therefore still poor.
- Romanized/"Tinglish" Telugu variants exist: `jayasuryajsk/whisper-large-v3-Telugu-Romanized`, `cvas-544/autotinglishsub-whisper-telugu` (+ct2), `ukta-app/whisper-telugu-ct2`, `kavithajohn/whisper-telugu-medium-ct2`. Details were not checked [V existence only] ([search API](https://huggingface.co/api/models?search=whisper-telugu)).

**Hindi fine-tunes**
- `vasista22/whisper-hindi-small`: FLEURS 9.02, CommonVoice-11 14.12. `-medium`: 6.82 / 11.38. `-large-v2`: 6.8 / 10.98 [V] ([small](https://huggingface.co/vasista22/whisper-hindi-small), [medium](https://huggingface.co/vasista22/whisper-hindi-medium), [large-v2](https://huggingface.co/vasista22/whisper-hindi-large-v2)). Trained on GramVaani, ULCA and FLEURS train+dev [V] (same). An ONNX copy exists at `onnx-community/whisper-hindi-small-ONNX` [V existence] ([search](https://huggingface.co/api/models?search=whisper-hindi)).
- `collabora/whisper-{tiny,base,small}-hindi` (2025, **CC-BY-4.0**, ~3,000 h: Shrutilipi, IITM SpringLab, CV11, IndicVoices-R, Lahaja, FLEURS and others). FLEURS-Hindi WER (Whisper norm / Indic norm): **tiny 10.10 / 18.94, base 8.49 / 17.42, small 7.17 / 15.10** [V] (cards linked above). Faster-whisper (CTranslate2) variants exist [V existence].
- `ARTPARK-IISc/whisper-tiny-vaani-hindi` (718 h). WER: Kathbath 23.91, Kathbath-noisy 29.92, FLEURS 26.39, CommonVoice 37.95, MUCS 39.00, Gramvaani 42.34, IndicTTS 11.77, Vaani 33.33 [V] ([card](https://huggingface.co/ARTPARK-IISc/whisper-tiny-vaani-hindi)).
- `ARTPARK-IISc/whisper-small-vaani-hindi`. WER: Kathbath 18.66, Kathbath-noisy 21.15, FLEURS 19.08, CommonVoice 26.27, MUCS 28.44, Gramvaani 32.49, Vaani 26.62 [V] ([card](https://huggingface.co/ARTPARK-IISc/whisper-small-vaani-hindi)). These are probably Indic-normalized, which would explain why they are higher than collabora's Whisper-norm numbers [I].
- **Oriserve Hindi→Hinglish** (Apache-2.0, ~550 h of noisy Indian-accented Hindi, proprietary data, **outputs romanized Hinglish**, e.g. "vah bas din mein kitni baar chalti hai?") [V] ([Swift card](https://huggingface.co/Oriserve/Whisper-Hindi2Hinglish-Swift)):
  - **Swift**: whisper-base, 72.6M params, 290 MB. WER on Hinglish references: FLEURS 35.09, CV 38.65, IndicVoices 65.21.
  - **Apex**: large-v3-turbo, 809M, bf16 1.6 GB, 2025-10. FLEURS 29.79, CV20 35.96, IndicVoices 47.64.
  - **Prime**: large-v3, 1.54B. 28.68 / 32.43 / 60.82.
  - Sources: [API](https://huggingface.co/api/models/Oriserve/Whisper-Hindi2Hinglish-Apex), [Apex](https://huggingface.co/Oriserve/Whisper-Hindi2Hinglish-Apex), [Prime](https://huggingface.co/Oriserve/Whisper-Hindi2Hinglish-Prime) [V].
  - ONNX q8 of Swift: `Hirecentive-D3l/Whisper-Hindi2Hinglish-Swift-ONNX`, encoder 23 MB + merged decoder 79 MB (optimum export with past, QUInt8) [V] ([card](https://huggingface.co/Hirecentive-D3l/Whisper-Hindi2Hinglish-Swift-ONNX)). `arunava-ctrl/Whisper-Hindi2Hinglish-Apex-ONNX` also exists [V existence].
- Many 2026 Hindi/Hinglish experiment repos from `dianavdavidson/*` (IndicWhisper / whisper-medium on IV/Vaani/MUCS, "hinglish_mixed_scripts") exist but are undocumented [V existence] ([search](https://huggingface.co/api/models?search=indicwhisper)).

**NPU-relevance**: Qualcomm AI Hub publishes **multilingual** `qualcomm/Whisper-Tiny`, `Whisper-Base`, `Whisper-Small` (2025-08), `Whisper-Small-Quantized` (2025-10), `Whisper-Medium-En` and `Whisper-Large-V3-Turbo`, as well as the `-En` variants [V] ([API](https://huggingface.co/api/models?author=qualcomm)). Whisper-Small ships `QNN_CONTEXT_BINARY` / `PRECOMPILED_QNN_ONNX` downloads for "Snapdragon 8 Elite For Galaxy Mobile" (QAIRT 2.50), with MHA replaced by single-head attention and linears by convs, for 30 s clips [V] ([qualcomm/Whisper-Small](https://huggingface.co/qualcomm/Whisper-Small)). Whisper-Tiny is 9.39M (encoder) + 28.4M (decoder) params, 35.9 MB + 109 MB float [V] ([qualcomm/Whisper-Tiny](https://huggingface.co/qualcomm/Whisper-Tiny)).

### Inferences
- **Whisper-small multilingual is unusable for Telugu** (98.2 % FLEURS) and weak for Hindi (38.4 %). Stock tiny/base produce garbage for both. Stock Whisper LID for Telugu is also unlikely to be reliable [I].
- The **fine-tuned base/small checkpoints are drop-in architecture-compatible** with the AI Hub / WhisperKit Whisper graphs (same dims; the vocab is unchanged in vasista22/collabora because they use the stock tokenizer). Swapping weights into the existing NPU Whisper export pipeline is therefore plausibly the cheapest Indic path [I]. The NPU researcher should confirm whether WhisperKit Android or the AI Hub exporter accepts a custom HF checkpoint.
- Watch the token budget. Whisper's byte-level BPE produces many tokens per Telugu word, so decoder steps (and NPU decoder latency) per utterance are several times higher than for English [I, based on the paper's tokenizer remark].
- vasista22's FLEURS numbers are optimistic (in-domain FLEURS train data plus Whisper normalization). Expect noticeably worse results on spontaneous, noisy incident-report speech; the ARTPARK/Kathbath-style numbers of 18-30 % for tiny/small Hindi are more realistic [I].
- Oriserve Swift's romanized Hinglish output may suit an English-centric on-device LLM for field extraction (no Devanagari handling needed), but it is Hindi-only [I].

### Gaps
- No Telugu or Hindi Whisper-large-v3 / v3-turbo per-language FLEURS numbers from an official source.
- No WER on a common benchmark (e.g. Kathbath te) for the vasista22 Telugu models.
- Licence not verified on ARTPARK whisper-*-vaani cards.

---

## Q3. Other candidates (MMS, Omnilingual ASR, NVIDIA, sherpa-onnx, Gemma 3n, Moonshine, SenseVoice, Vosk, IIT/ARTPARK/Bhashini, Qwen3-ASR, Dolphin, 2025-26 releases)

### Takeaway
The most interesting non-AI4Bharat options are:
- **Meta Omnilingual CTC-300M**: 1,600+ languages including en/hi/te, Apache-2.0, already packaged for sherpa-onnx at **~293 MB int8**.
- **ARTPARK-IISc SraVaani** (2026, MIT, 430M FastConformer covering en+hi+te, with a cache-aware streaming variant).
- **Dolphin base/small** (hi+te listed, sherpa-onnx int8 81/192 MB, but no published hi/te WER).
- **NVIDIA Nemotron 3.5 ASR streaming** (excellent Hindi and English, streaming, sherpa-onnx int8 475 MB) has **no Telugu**. **Qwen3-ASR** also has Hindi but no Telugu. Moonshine and SenseVoice have no Indic support.
- **Vosk** has tiny Hindi/Telugu/Indian-English Kaldi models, but the Telugu one is poor (87.9 % FLEURS WER).

### Cited Findings
**Meta Omnilingual ASR (Nov 2025, v2 Feb 2026)**
- Apache 2.0, 1,600+ languages, language codes like `hin_Deva` / `tel_Telu`. "Currently only audio files shorter than 40 seconds are accepted" for the CTC/LLM suites; the "Unlimited" LLM variants accept longer audio [V] ([repo](https://github.com/facebookresearch/omnilingual-asr)).
- Sizes [V] (same):
  - CTC: `omniASR_CTC_300M` 325,494,996 params / 1.3 GiB, `CTC_1B` 975M / 3.7 GiB, `CTC_3B` 3.08B, `CTC_7B` 6.5B (plus v2).
  - LLM: `LLM_300M` 1.63B total params / 6.1 GiB, LLM_1B 2.28B, and larger. The LLM models are "ASR with optional language conditioning". The CTC models take no language prompt [I from the README's table wording].
- Per-language CER for the **7B-LLM** only: hin_Deva **3.6** (236.7 training h), tel_Telu **6.5** (229.3 h), eng_Latn 3.0. Other Indian languages: ben 2.8, mar 3.7, tam 5.2 [V] ([CSV](https://github.com/facebookresearch/omnilingual-asr/blob/main/per_language_results_table_7B_llm_asr.csv)).
- **sherpa-onnx pre-converted**: `sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12` (293 MB), `...300M-ctc-v2-int8-2026-02-05` (292 MB), fp32 300M (1,236 MB), `...1B-ctc-int8` (786 MB) and `...1B-ctc-v2-int8` (787 MB) [V] ([sherpa-onnx asr-models release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models)). Community GGUF builds also exist (`cstr/omniASR-CTC-300M-v2-GGUF`, etc.) [V existence] ([search](https://huggingface.co/api/models?search=omniASR)).
- Voice of India evaluated OmniASR LLM 1B and 7B as its only open models besides IndicConformer [V] ([arXiv 2604.19151](https://arxiv.org/html/2604.19151v1)).

**Meta MMS**
- `facebook/mms-1b-all`: 964.8M params, 3.86 GB fp32, **CC-BY-NC-4.0**, languages include hi and te, per-language adapter files (e.g. `adapter.jpn.safetensors` 20.3 MB) [V] ([API](https://huggingface.co/api/models/facebook/mms-1b-all)). The language adapter must be selected up front [I, from the standard MMS usage].
- IndicVoices test WER: MMS Hindi 38.9, Telugu 62.4 [V] ([arXiv 2403.01926](https://arxiv.org/html/2403.01926)).
- LID models `facebook/mms-lid-126/256/512/1024/4017` exist [V] ([API](https://huggingface.co/api/models?author=facebook)). They are ~1B-param wav2vec2-based, so heavy as a LID front-end [I].

**NVIDIA**
- `nvidia/nemotron-3.5-asr-streaming-0.6b` (2026-05-15, 1.28M downloads/month): 637,997,088 params, licence **OpenMDW-1.1**, not gated [V] ([API](https://huggingface.co/api/models/nvidia/nemotron-3.5-asr-streaming-0.6b)).
  - Architecture: cache-aware FastConformer (24 layers) + RNNT with a 128-dim language-ID prompt. Pass `target_lang` (e.g. `hi-IN`) or `auto` [V] ([card](https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b)).
  - Languages: Hindi (hi-IN) is in the "transcription-ready" tier, English only as en-US/en-GB, **no Telugu or any other Indic language** [V] (same).
  - FLEURS Hindi WER with LangID: **8.13 (80 ms chunk), 7.97, 7.41, 7.05, 6.81 (1.12 s)**; with auto-detect 11.47 → 8.23. English 9.43 → 7.91 [V] (same).
  - Exports: GGUF q8_0 742 MB in the repo [V] ([API](https://huggingface.co/api/models/nvidia/nemotron-3.5-asr-streaming-0.6b)), and sherpa-onnx int8 at **475 MB per chunk-size** (80/160/320/560/1120 ms, 2026-06-11) [V] ([sherpa release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models)).
- `nvidia/parakeet-tdt-0.6b-v3` and `canary-1b-v2` are European-language models (no Hindi) [I, from memory; not re-verified this session]. sherpa-onnx has a parakeet-tdt-0.6b-v3 int8 export (487 MB) [V].
- `salesken/Hindi-FastConformer-Streaming-ASR`: Apache-2.0, 460 MB `.nemo` (≈115M params [I]). It is fine-tuned from `stt_en_fastconformer_hybrid_large_streaming_multi` on "Aibharath Hindi" data, supports streaming latencies of 0/80/480/1040 ms and CTC/RNNT. No WER is published [V] ([card](https://huggingface.co/salesken/Hindi-FastConformer-Streaming-ASR)).

**ARTPARK-IISc (IISc Bangalore, Vaani project)**
- `ARTPARK-IISc/SraVaani-1.0` (2026-02, modified 2026-09, MIT, gated auto): "~430 million parameter FastConformer ... hybrid TDT-CTC", fp16 TorchScript `model-asr.fp16.ts` 908.8 MB. Covers 65 Indian languages/dialects including hi, te and en; Urdu and Kashmiri are not supported. ~31,270 h of fine-tuning data. An ONNX version is claimed [V] ([card](https://huggingface.co/ARTPARK-IISc/SraVaani-1.0), [API](https://huggingface.co/api/models/ARTPARK-IISc/SraVaani-1.0)).
  - Average WER: **Hindi 12.4** (Vaani 10.8), **Telugu 25.1** (Vaani 33.5), **English 14.8** [V] ([card](https://huggingface.co/ARTPARK-IISc/SraVaani-1.0)). Paper: arXiv 2608.08235 [V].
  - Paper Table 3, averaged over the same datasets for all systems: Hindi 14.0 vs IndicConformer-600M 13.8, Telugu 25.1 vs 26.4. SraVaani is therefore roughly on par with IndicConformer-600M on hi/te while adding English. Fine-tuning used 3,615 h of Hindi, 2,036 h of Telugu and 1,709 h of English [V] ([arXiv 2608.08235](https://arxiv.org/abs/2608.08235)).
- `ARTPARK-IISc/SraVaani-0.5-live` (2026-07, MIT, 49,925 downloads/month): cache-aware streaming FastConformer with a CTC head, fp16 TorchScript 898 MB. Lookahead is switchable at runtime: 0/80/480/1040 ms. Covers 63 languages with a "5000-piece SentencePiece BPE, multi-script Indic + Latin (code-switch)" vocabulary [V] ([card](https://huggingface.co/ARTPARK-IISc/SraVaani-0.5-live)).
  - Streaming WER from 0 ms to 1040 ms lookahead: **Hindi 22.1 → 18.0, Telugu 34.6 → 30.3, English 31.5 → 24.6**. The card says it is "real-time-safe on CPU" [V] (same). The card does not mention a language-ID input [V].
- `ARTPARK-IISc/Vaani-FastConformer-Hindi` / `-Telugu` (2025-12, MIT, 1,753.8 MB `.nemo` each) [V] ([API](https://huggingface.co/api/models/ARTPARK-IISc/Vaani-FastConformer-Telugu)). `ARTPARK-IISc/Vaani-LID_v0` (2026-02) is a LID model [V existence] ([API listing](https://huggingface.co/api/models?author=ARTPARK-IISc)).

**SPRING Lab (IIT Madras)**: its HF org lists only TTS/VC/MT models (Indic-Mio, F5-Hindi, SPRING_F5, EZ-VC, shiksha-MT) and **no ASR checkpoints** [V] ([API](https://huggingface.co/api/models?author=SPRINGLab)). Its data (IITM ~900 h Hindi, IndicVoices-R) is used in collabora's fine-tunes [V] ([collabora card](https://huggingface.co/collabora/whisper-tiny-hindi)).

**Bhashini**: no open, Hub-downloadable Bhashini ASR checkpoints were found in this sweep [gap; not specifically searched beyond the HF orgs].

**Qwen3-ASR (Alibaba, 2026-01)**
- `Qwen/Qwen3-ASR-0.6B`: Apache-2.0, 938M total params (bf16, 1.88 GB). It covers 30 languages plus 22 Chinese dialects. **Hindi is included; Telugu is not.** It has built-in LID and offline/streaming modes [V] ([card](https://huggingface.co/Qwen/Qwen3-ASR-0.6B), [API](https://huggingface.co/api/models/Qwen/Qwen3-ASR-0.6B)).
- A sherpa-onnx int8 build is 879 MB [V] ([sherpa release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models)). GGUF builds come from ggml-org and unsloth [V existence].
- Hindi fine-tunes: `Sumitdas06/anulekh-hindi-asr-v1` (782.4M params, frozen 186.4M audio tower, ~30 h Hindi, Q8_0 GGUF, Apache-2.0) [V] ([card](https://huggingface.co/Sumitdas06/anulekh-hindi-asr-v1)) and `tryorato/orato-asr-hindi-v1` (hi+en, 782M) [V] ([API](https://huggingface.co/api/models/tryorato/orato-asr-hindi-v1)).

**Dolphin (DataoceanAI + Tsinghua)**: Apache-2.0, 40 "Eastern" languages plus 22 Chinese dialects, ASR+VAD+LID. The base model is 140M params. The language list includes **hi, te, ta, bn, ur** [V] ([card](https://huggingface.co/DataoceanAI/dolphin-small), [languages.md](https://github.com/DataoceanAI/Dolphin/blob/main/languages.md)). sherpa-onnx CTC exports: base int8 81 MB, small int8 192 MB (fp32 300/775 MB) [V] ([sherpa release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models)). No hi/te WER was found.

**Google Gemma 3n**
- `google/gemma-3n-E2B-it` has 5.44B raw params (bf16 10.9 GB), Gemma licence, gated manual [V] ([API](https://huggingface.co/api/models/google/gemma-3n-E2B-it)).
- Audio: "maximum length of 30 seconds", "6.25 tokens" per second for Gemma 3n (25 for Gemma 4). The docs page **does not list ASR languages**, and Hindi/Telugu are not mentioned [V] ([Gemma audio docs](https://ai.google.dev/gemma/docs/capabilities/audio)).
- A Hindi psychiatric-domain LoRA (`Lekhansh/gemma-3n-e4b-it-asr-psychiatric-domain-hindi`) exists [V existence via search] ([HF](https://huggingface.co/Lekhansh/gemma-3n-e4b-it-asr-psychiatric-domain-hindi)).

**Moonshine**: official checkpoints cover en, zh, ar, ko, ja, vi, uk, es and de (tiny/base/streaming). **There is no Hindi or Telugu**; only community Bengali (`Sanjidh090/moonshine-base-bn`) and Urdu (`shaeel12/moonshine-tiny-urdu`) fine-tunes exist [V] ([search](https://huggingface.co/api/models?search=moonshine)).

**SenseVoice**: zh/en/yue/ja/ko; no Hindi [I, well known, not re-verified].

**Vosk (Kaldi, CPU, streaming, Apache 2.0)** [V] ([Vosk models](https://alphacephei.com/vosk/models)):

| Model | Size | Reported WER | Vosk description |
|---|---|---|---|
| `vosk-model-small-hi-0.22` | 42 MB | 20.89 (IITM Challenge), 24.72 (MUCS) | lightweight Hindi |
| `vosk-model-hi-0.22` | 1.5 GB | 14.85 (CV), 14.83 (IITM), 13.11 (MUCS) | server Hindi |
| `vosk-model-small-te-0.42` | 58 MB | **87.9 (FLEURS)** | lightweight Telugu |
| `vosk-model-small-en-in-0.4` | 36 MB | 49.05 (NPTEL) | mobile Indian English |
| `vosk-model-en-in-0.5` | 1 GB | 36.12 (NPTEL) | generic Indian English |

**sherpa-onnx pretrained list (asr-models release, 498 assets)**: it has **no dedicated Hindi or Telugu Zipformer/NeMo models**. The Indic-capable entries are multilingual Whisper (tiny 116 MB, base 208 MB, small 639 MB, turbo 564 MB, ...), Omnilingual CTC, Dolphin, Nemotron 3.5 (hi), Qwen3-ASR (hi) and the multilingual Moonshine variants (no Indic) [V] ([sherpa release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models)).

### Inferences
- **Omnilingual CTC-300M int8 (293 MB, sherpa-onnx)** is the most deployment-ready single model for en+hi+te on Android CPU. Its accuracy on hi/te at 300M is unknown and needs a quick test; at 300M it will likely trail IndicConformer by a wide margin on Telugu [I]. A CTC graph is NPU-friendly in principle [I].
- **SraVaani-1.0** is the strongest single *open* model covering en+hi+te with published numbers, but it ships as a 900 MB fp16 TorchScript. Android use would need the claimed ONNX version or a re-export, then int8 quantization (~450 MB [I]).
- **Nemotron 3.5** is the best streaming Hindi+English option (6.8-8.1 % FLEURS Hindi), but it cannot do Telugu. It could be the en+hi engine with IndicConformer-te alongside, at ~475 MB + 138 MB [I].
- **Gemma 3n**: only worth considering if the team also uses it as the LLM. Its Indic ASR quality is undocumented, and at 5.4B raw params it is far above the size budget for a dedicated ASR [I].

### Gaps
- No hi/te WER for Omnilingual CTC-300M/1B, Dolphin base/small, or Gemma 3n audio.
- ARTPARK Vaani-FastConformer hi/te WER and the Vaani-LID_v0 details are unknown (gated cards not fetched).
- Bhashini/ULCA open ASR checkpoints were not located.

---

## Q4. Which candidates are small enough for mobile AND good on Hindi + Telugu + English? Single multilingual model or LID + per-language models?

### Takeaway
**No verified single model under ~300M params is good on all three.** The realistic hackathon design is **an explicit language choice (UI toggle or first-turn prompt) plus per-language engines**:
- **English**: keep Whisper tiny.en/base.en on the NPU, or better base.en/small.en.
- **Hindi and Telugu**: either (a) IndicConformer-120M int8 ONNX for hi and for te (~138 MB each, MIT), or (b) fine-tuned Whisper base/small for hi and for te (vasista22 / collabora), which reuse the same Whisper NPU pipeline.
- **Single-model fallback**: Omnilingual CTC-300M int8 (293 MB) or SraVaani-1.0 (430M), both untested on device.

### Master catalog (summary table)
Sizes are on-disk as found. All rows are [V] unless marked. "LID" means a language code is required at inference.

| Model (HF/GitHub ID) | Params | Disk | Arch | en / hi / te | Licence | Key WER (benchmark) | Streaming | Existing exports | LID |
|---|---|---|---|---|---|---|---|---|---|
| ai4bharat/indicconformer_stt_te_hybrid_ctc_rnnt_large | 120M | .nemo 523 MB; ONNX int8 137.7 MB (OpenVoiceOS/ai4bharat-indicconformer-te-onnx) | Conformer-L hybrid CTC/RNNT | – / – / ✓ | MIT (gated auto) | none published | offline | ONNX (onnx-asr) | yes (`te`) |
| ai4bharat/indicconformer_stt_hi_hybrid_ctc_rnnt_large | 120M | same; ONNX int8 137.7 MB (OpenVoiceOS/...-hi-onnx) | same | – / ✓ / – | MIT | none published | offline | ONNX | yes (`hi`) |
| ai4bharat/indic-conformer-600m-multilingual | 600M | ONNX fp32 ~2.5 GB; int8 enc 0.62 GB (yashwork-byte/...-int8-onnx) | Conformer hybrid CTC/RNNT | ✗ / ✓ / ✓ (22 Indic) | MIT (gated auto) | hi 13.2 (Vaani); VoI hi 8.2 / te 23.7 | offline | ONNX, int8 ONNX, CoreML, GGUF | yes |
| IndicWhisper (AI4Bharat/vistaar zips) | ~769M (medium) [I] | te zip 1.41 GB, hi zip 4.26 GB | Whisper enc-dec | – / ✓ / ✓ (separate ckpts) | MIT | hi Kathbath 10.3; te Kathbath 25.0, FLEURS 25.4 | offline | HF format | yes (per-ckpt) |
| vasista22/whisper-telugu-{tiny,base,small} | 39M / 74M / 244M [I std] | ~150 MB / ~290 MB / ~970 MB fp32 [I] | Whisper | – / – / ✓ | Apache-2.0 | FLEURS te 17.15 / 13.39 / 11.59 | offline | HF (convertible to whisper.cpp/ONNX [I]) | yes (forced `te`) |
| vasista22/whisper-hindi-small | 244M [I] | ~970 MB fp32 [I] | Whisper | – / ✓ / – | Apache-2.0 [I] | FLEURS 9.02, CV11 14.12 | offline | onnx-community/whisper-hindi-small-ONNX, CT2 | yes |
| collabora/whisper-{tiny,base,small}-hindi | 39M / 74M / 244M | – | Whisper | – / ✓ / – | CC-BY-4.0 | FLEURS hi 10.10 / 8.49 / 7.17 (Whisper-norm); 18.94 / 17.42 / 15.10 (Indic-norm) | offline | faster-whisper (CT2) | yes |
| Oriserve/Whisper-Hindi2Hinglish-Swift | 72.6M | 290 MB; ONNX q8 23+79 MB | Whisper-base | (en) / ✓ (romanized) / – | Apache-2.0 | FLEURS 35.1 (Hinglish refs) | offline | ONNX q8 (Hirecentive-D3l) | yes |
| facebookresearch omniASR_CTC_300M (v2) | 325M | 1.3 GiB; sherpa int8 292-293 MB | wav2vec2 + CTC | ✓ / ✓ / ✓ (1,600+) | Apache-2.0 | 7B-LLM only: hi CER 3.6, te 6.5 (300M unknown) | offline (<40 s) | sherpa-onnx int8, GGUF | no (CTC) [I] |
| ARTPARK-IISc/SraVaani-1.0 | ~430M | fp16 TorchScript 909 MB | FastConformer TDT-CTC | ✓ / ✓ / ✓ (65) | MIT (gated auto) | avg hi 12.4, te 25.1, en 14.8 | offline | TorchScript; ONNX claimed | not stated |
| ARTPARK-IISc/SraVaani-0.5-live | ~430M base | fp16 TS 898 MB | cache-aware FastConformer CTC | ✓ / ✓ / ✓ (63) | MIT | hi 18.0-22.1, te 30.3-34.6, en 24.6-31.5 | **streaming** | TorchScript | not stated |
| nvidia/nemotron-3.5-asr-streaming-0.6b | 638M | 2.55 GB fp32; GGUF q8 742 MB; sherpa int8 475 MB | cache-aware FastConformer RNNT + LID prompt | ✓ / ✓ / ✗ | OpenMDW-1.1 | FLEURS hi 6.81-8.13 (LangID) | **streaming** | sherpa-onnx int8, GGUF | prompt or `auto` |
| Qwen/Qwen3-ASR-0.6B | 938M total | 1.88 GB bf16; sherpa int8 879 MB | audio enc + Qwen3 LLM dec | ✓ / ✓ / ✗ | Apache-2.0 | (no hi table extracted) | offline/streaming | sherpa-onnx, GGUF, MLX | auto LID |
| DataoceanAI Dolphin base / small | 140M / ? | sherpa int8 81 MB / 192 MB | CTC-attention (sherpa uses CTC) | ? / ✓ / ✓ (40 Eastern) | Apache-2.0 | none found for hi/te | offline | sherpa-onnx | built-in LID |
| facebook/mms-1b-all | 965M | 3.86 GB + adapters | wav2vec2 CTC + adapters | ✓ / ✓ / ✓ | **CC-BY-NC-4.0** | IndicVoices hi 38.9, te 62.4 | offline | HF | yes (adapter) [I] |
| bodhan-ai/indic-transcribe-flex / -core | 1.22B | 4.9 GB fp32 | FastConformer + Transformer dec (Canary) | ✓ / ✓ / ✓ (25-27) | custom Indic Open Model Licence | VoI hi 4.1, te 13.1 (flex) | offline | .nemo / safetensors | optional LID (hi LID acc 0.26) |
| Vosk small-hi / small-te / small-en-in | – | 42 / 58 / 36 MB | Kaldi | per-lang | Apache 2.0 | hi 20.89 (IITM); te 87.9 (FLEURS); en-in 49.05 (NPTEL) | streaming | Android lib | per-model |
| qualcomm/Whisper-Tiny / -Base / -Small (multilingual) | 37.8M (tiny) | tiny 36+109 MB float | Whisper (SHA/conv-optimized) | ✓ / weak / ✗ (stock) | per AI Hub | stock FLEURS: hi small 38.4; te small 98.2 | offline | **QNN context binary for 8 Elite** | auto/forced |

Sources for table rows are the per-model links in Q1-Q3 and Q5.

### Cited Findings
- IndicConformer-600M lacks English and needs a language code [V] ([card](https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual)). Per-language IndicConformer int8 ONNX is 137.7 MB each [V] ([API](https://huggingface.co/api/models/OpenVoiceOS/ai4bharat-indicconformer-te-onnx?blobs=true)).
- The fine-tuned Whisper sizes with hi/te WERs are listed in Q2 [V] (vasista22, collabora cards).
- Omnilingual 300M CTC int8 in sherpa-onnx is 293 MB [V] ([sherpa release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models)).
- Built-in LID is unreliable where measured: Indic-Transcribe reports Hindi LID accuracy 0.26 [V] ([flex card](https://huggingface.co/bodhan-ai/indic-transcribe-flex)). Nemotron 3.5 auto-detect costs ~1.4-3.3 WER points on Hindi vs a supplied LangID (8.23-11.47 vs 6.81-8.13) [V] ([card](https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b)).
- The yashwork-byte int8 600M repo states that the fp32 encoder "needs ~2.6 GB resident — too much next to an LLM on an 8 GB device"; int8 brings peak RSS to ~1.1 GB [V] ([card](https://huggingface.co/yashwork-byte/indic-conformer-600m-int8-onnx)).

### Inferences
- **Recommended design for a 24 h hackathon** [I]:
  1. **Language selected explicitly** (three-way toggle EN/HI/TE, or a remembered user preference). This avoids LID errors entirely, and a voice-driven report form can reasonably ask for language once.
  2. **EN**: the existing WhisperKit tiny.en, upgraded to base.en if latency allows (see Q5).
  3. **HI/TE, option A (lowest integration risk if the Whisper NPU path accepts custom weights)**: vasista22/whisper-telugu-base or -small, and collabora/whisper-base-hindi or vasista22/whisper-hindi-small, run through the same Whisper runtime, forcing the `<|te|>` / `<|hi|>` token.
  4. **HI/TE, option B (best accuracy per MB, CPU via onnxruntime/onnx-asr, NPU later)**: IndicConformer-120M int8 ONNX for hi and for te (~276 MB total). The CTC head gives single-pass decoding with no autoregressive loop.
  5. **Single-model alternative to evaluate if time permits**: Omnilingual CTC-300M int8 through sherpa-onnx's existing Android API (one model, three languages, ~293 MB), or SraVaani-1.0 if an ONNX export is obtainable.
- If automatic LID is required, the cheapest hack is to run a short first chunk through the multilingual Whisper-tiny decoder's language token. Given the >100 % Telugu WER this is likely unreliable for te [I]. Dolphin's built-in LID or ARTPARK Vaani-LID_v0 are alternatives, but both are unverified [I].
- **Code-mixed (Hinglish / Telugu-English) handling:**
  - IndicConformer and IndicWhisper output native script, with English words likely transliterated.
  - Oriserve outputs romanized Hinglish (Hindi only).
  - SraVaani-0.5-live's vocabulary is multi-script with code-switching, and it may write English words in Latin.
  - Indic-Transcribe has an explicit mixed-script mode but is 1.2B.
  - For an LLM field extractor, native-script Telugu output means the on-device LLM must read Telugu script. This should be checked with the chosen LLM [I].
- Static-shape and NPU friendliness [I]:
  - Whisper has an AI Hub precedent (QNN context binaries for 8 Elite).
  - Conformer/FastConformer CTC encoders are fixed-chunk feed-forward graphs, a good fit.
  - RNNT/TDT decoders and LLM-decoder ASR (Qwen3-ASR, Omnilingual-LLM, Gemma 3n) are harder.
  - Cache-aware streaming (Nemotron, SraVaani-live) needs cache tensors as explicit I/O.

### Gaps
- No on-device latency or WER measurements on Snapdragon for any Indic model were found.
- WER under the same normalization on the same benchmark (e.g. Kathbath te) for IndicConformer-120M vs vasista22 whisper-telugu-small vs Omnilingual-300M is missing. A 30-minute local evaluation on ~50 Kathbath/IndicVoices clips would settle it.

---

## Q5. Indian-accented English: which models do best (Svarah)? Does Whisper tiny.en / base.en degrade badly?

### Takeaway
On Svarah (9.6 h, 117 speakers, 65 locations), Whisper degrades roughly **3× vs LibriSpeech**: base 13.6 % vs 4.2 %, medium 8.3 %, large 7.2 %. NVIDIA's 120M Conformer-CTC scores 14.6 %, and wav2vec2-style models score 24-34 %. No published Svarah number exists for tiny.en or base.en, but base-class Whisper at ~13-14 % suggests tiny.en will be noticeably worse. Moving to base.en or small.en is the cheapest English accuracy gain.

### Cited Findings
- Svarah: "9.6 hours of transcribed English audio from 117 speakers across 65 geographic locations", with native speakers of 19 of the 22 scheduled languages. It mixes read, extempore and everyday use-case prompts (e.g. digital payments, government services) [V] ([arXiv 2305.15760](https://arxiv.org/abs/2305.15760)).
- Svarah Table 1, WER on Svarah / SAA-L1 / LibriSpeech-clean [V] (same paper):
  - Whisper base (74M): **13.6** / 2.9 / 4.2
  - Whisper medium (769M): **8.3** / 1.7 / 3.1
  - Whisper large (1550M): **7.2** / 1.6 / 2.7
  - Wav2Vec2-large: 24.9; HuBERT-large: 25.6; WavLM-large: 33.7; Data2Vec-large: 24.5
  - NVIDIA Conformer-large (120M, CTC): **14.6** / 1.1 / 2.1
  - Azure en-US 20.9; Azure en-IN 21.3; Google en-US 30.0; Google en-IN 20.7
  - The paper does not specify whether the Whisper rows are .en or multilingual checkpoints.
- "The gap between the WERs on Svarah and LibriSpeech ranges from 4.5 - 30.3". Whisper-Large and NVIDIA Conformer have the lowest standard deviation across accents (1.8 and 3.0). Native speakers of Assamese, Bodo and Nepali have the worst WER [V] (same).
- A 2025-26 community benchmark (`theshivam7/indian-asr-bench`) reportedly gives Svarah WER of Whisper large-v3 **7.11** and medium 7.89, and Parakeet-TDT-0.6B-v2 15.60 on "TIE_shorts" (Indian lecture speech). This comes from a search snippet only; the repo returned HTTP 404 on fetch [unverified] ([repo](https://github.com/theshivam7/indian-asr-bench)).
- Other Indian-English data points [V]:
  - SraVaani-1.0 English average WER 14.8 ([card](https://huggingface.co/ARTPARK-IISc/SraVaani-1.0)); SraVaani-0.5-live English 24.6-31.5 streaming ([card](https://huggingface.co/ARTPARK-IISc/SraVaani-0.5-live)).
  - Vosk Indian-English: small-en-in 49.05, en-in 36.12 on NPTEL ([Vosk](https://alphacephei.com/vosk/models)).
  - Indic-Transcribe-flex covers "Indian-accented English" across 19 states, but the extracted card summary gave no number ([card](https://huggingface.co/bodhan-ai/indic-transcribe-flex)).
  - Nemotron 3.5 lists only en-US/en-GB locales ([card](https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b)).
- Stock Whisper English FLEURS WER: tiny 12.4, base 8.9, small 6.1, medium 4.4 [V] ([arXiv 2212.04356](https://arxiv.org/abs/2212.04356), Table 13). These are native/global English, not Indian.

### Inferences
- **Whisper tiny.en on Indian English** is probably in the high-teens WER on Svarah-like speech, extrapolating from base at 13.6 and the tiny-vs-base gap on FLEURS (12.4 vs 8.9) [I]. Domain words in incident reports (place names, Indian names, vehicle numbers) will be worse. Mitigations include upgrading to base.en/small.en, initial-prompt biasing with expected vocabulary, and LLM post-correction [I].
- For accented English the ranking by size is Whisper medium/large > Whisper base ≈ NVIDIA Conformer-120M > wav2vec2-family. Among phone-sized options, **Whisper base.en / small.en remain the best documented choices** [I].
- Many Indian users will speak code-mixed English+Hindi/Telugu. An English-only model will drop or mangle the Indic words, which is one more argument for letting the user pick the language, or for SraVaani/Indic-Transcribe-style mixed-script models [I].

### Gaps
- No Svarah WER for tiny.en, base.en, small.en, large-v3-turbo, Parakeet, Moonshine or Nemotron was found in a primary source.
- Indian-English WER for IndicConformer is not applicable (no English); for Omnilingual-300M it is unknown.
