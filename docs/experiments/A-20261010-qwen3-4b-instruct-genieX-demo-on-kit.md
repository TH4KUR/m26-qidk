# Experiment log: Qwen3-4B-Instruct-2507 in the GenieX demo app on the kit

Date: 2026-10-10 (night, IST). Measured by the user on the kit, by hand, in the GenieX reference chat app (`com.geniex.demo`, built from `qualcomm/ai-hub-apps`, `geniex_chat_android`, which pins `geniex-android` 0.3.5). Recorded by the lead from the user's report and a screenshot.

## Goal
First on-device measurement of a 4B instruction model on the kit's NPU, in English and Hindi, as a candidate for the form-filling LLM (spec D3).

## Setup
| Item | Value |
|---|---|
| Model | `Qwen3-4B-Instruct-2507`, shown as "(NPU)" in the app's picker (a Qualcomm bundle for the `qairt` runtime; exact bundle revision not recorded) |
| App | GenieX reference chat app, free-form chat, default settings |
| Prompts | free chat in Hindi and English (not an extraction task) |

## Results (n = 1 session per language; one screenshot)
| Measure | Hindi | English |
|---|---|---|
| Decoding speed | **21.84 tok/s** (screenshot: "Generated Tokens: 1192; Decoding Speed: 21.84 tok/s") | about **25 tok/s** (user's report, not on the screenshot) |
| Time to first token | 74.90 ms (27 prompt tokens) | not recorded |
| Prefill speed | 360.50 tok/s on the 27-token prompt (short prompts under-use the NPU, so this is not a ceiling) | not recorded |

## Observations
1. **A 4B model runs on this kit's NPU.** The earlier crash reports for 4B and 7B models were for an 8 Gen 2 chip (llama.cpp#26123), not this one.
2. **Corrupted Devanagari output** (visible in the screenshot, e.g. "अनुà¤Ńव" where "अनुभव" was meant, and "�Ń" where "भी" was meant). **Root cause is a known GenieX bug** (issue #1140, "qairt runtime: CJK detokenizer output is mojibake"): an off-by-one in the qairt detokenizer's byte-level reverse table. Any byte-level piece containing the byte `0xAD` is corrupted (`0xAD` becomes `Ń`, U+0143). The letter "भ" (U+092D) is UTF-8 `E0 A4 AD`, so every "भ" is hit. The reporter says the fix landed in `geniex-proc` PR #10 and shipped in **GenieX 0.3.16**. Our app and the demo pin **0.3.5**; the latest on Maven Central is 0.8.0. The `llama_cpp` (GGUF) runtime uses a different detokenizer and is not described as affected.
3. **Free-form Hindi quality is weak.** Several sentences in the screenshot are incoherent, and the model answered an English question in Hindi (probably because earlier Hindi turns were in the chat history). It also generated 1192 tokens without stopping. None of this is yet an extraction result.

## Verdict
- 4B is fast enough to consider: at about 22 to 25 tok/s, an 80-token structured answer is roughly 3 to 4 s of decoding [inferred], plus prefill.
- **Hindi extraction is not yet shown to work.** Free chat quality is a warning, not a measurement of extraction.
- Do not ship Hindi output on GenieX 0.3.5 on the `qairt` runtime: it corrupts common letters.

## Limitations
One session per language, free chat only, no extraction task, no accuracy measure, no English screenshot, model bundle revision not recorded, speeds read off the app's own readout.

## Next
1. Bump `geniex-android` to 0.3.16 or later in our app and repeat the Hindi prompt (owner: the app track).
2. Run the real extraction prompt (the deck example and Hindi variants) with a token cap, on both `qairt` and `llama_cpp` runtimes.
3. Check what QAIRT/QNN version the newer GenieX AAR ships, against sherpa-onnx's 2.40.0.251030 (the coexistence test).
