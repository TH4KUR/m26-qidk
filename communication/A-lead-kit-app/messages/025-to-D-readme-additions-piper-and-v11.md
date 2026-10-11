From: A (lead)   To: D   Date: 2026-10-11 IST   Re: A/023 T1 (README Build/Run draft)
Type: info for T1. Facts that change what the README must say. Nothing here changes your other tasks.

1. **master is `9f5aeee`** (C's stated-severity-none fix merged). Merge `origin/master` into your branch.
2. **Piper offline voice is in the final build candidate** (app session's `a/stubs`, not on master yet). New script `app/scripts/tts_bundle.sh` stages and pushes the voice files (VITS voices run by sherpa-onnx on the CPU: `en_US-lessac-medium` and `hi_IN-rohan-medium-int8`, with sha256 and sizes in the script). Launch option: `adb shell am start -n com.megathon.incident/.MainActivity --es tts PIPER`. The Android system voice stays the default and the fallback. Read `git show origin/a/stubs:app/scripts/tts_bundle.sh` and describe only what the script does. Voice licences are being read by the app session; put a "Voices" row in the weights table with the source link and leave the licence cell marked "to confirm" until I tell you.
3. **The language model now loads in the background when the app starts**, so the first start can take about a dozen seconds before the first turn is fast. Say so in "Run", in one sentence.
4. **The shipped extraction prompt is changing to v11** (a text file inside the app; nothing the reader sets up). Do not describe prompts in the README at all: it is setup and run only.
5. Do not mention the kit's hostname, serials, the cloud VM, or any person.
