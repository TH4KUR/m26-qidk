#!/usr/bin/env bash
# Stage and push the Piper TTS bundle (open-weight VITS voices run by sherpa-onnx on the CPU). The native library and the
# voice files are NOT in git (the library is about 5 MB, the voices about 80 MB).
#
#   tts_bundle.sh check [TTS_DIR]                 local only: are the voice files in TTS_DIR and do their SHA-256 match?
#   tts_bundle.sh stage [BUNDLE_DIR]              copy libsherpa-onnx-jni.so (and libonnxruntime.so, which it needs) into
#                                                 app/src/speech/jniLibs (gitignored), so the next Gradle build packs them
#   tts_bundle.sh push  [TTS_DIR]                 push the voice folders into the app's private folder on the kit,
#                                                 files/tts/<voice>/ (needs `adb root`; the app must be installed once)
#
# Supertonic 3 (one model for Hindi and English, about 145 MB; sherpa-onnx release `sherpa-onnx-supertonic-3-tts-int8-2026-05-11`):
#
#   tts_bundle.sh check-supertonic [TTS_DIR]      local only: are its seven files in TTS_DIR/<folder> with the recorded size and SHA-256?
#   tts_bundle.sh push-supertonic  [TTS_DIR]      push those seven files to files/tts/<folder>/ and compare the device SHA-256 of each
#
# BUNDLE_DIR defaults to $HOME/sherpa-bundle/bundle, TTS_DIR to $HOME/tts-models.
# Then launch with Piper: adb shell am start -n com.megathon.incident/.MainActivity --es tts PIPER
set -euo pipefail

PKG=com.megathon.incident
here="$(cd "$(dirname "$0")" && pwd)"
app="$(cd "$here/.." && pwd)"
cmd="${1:-}"

# folder | model file | sha256 of the model file | size in bytes (the same values as PiperVoices.kt in :core)
VOICES=(
  "vits-piper-en_US-lessac-medium|en_US-lessac-medium.onnx|4ba07d8549906668ee855fd9abf9faf66c5db74742712ff026a159f7277fca9f|63149198"
  "vits-piper-hi_IN-rohan-medium-int8|hi_IN-rohan-medium.onnx|4f2dc5324c762fc676eb65be30b79b65c91848f47c3697ff86de69f3315c5da6|18301724"
)

# Supertonic 3: folder, then name|size|sha256 per file (the same values as SupertonicVoices in :core).
SUPERTONIC_FOLDER=sherpa-onnx-supertonic-3-tts-int8-2026-05-11
SUPERTONIC_FILES=(
  "duration_predictor.int8.onnx|3700147|c3eb91414d5ff8a7a239b7fe9e34e7e2bf8a8140d8375ffb14718b1c639325db"
  "text_encoder.int8.onnx|36416150|c7befd5ea8c3119769e8a6c1486c4edc6a3bc8365c67621c881bbb774b9902ff"
  "vector_estimator.int8.onnx|78400833|20cd86fa5c6effedfda0e7cffe5b0569ca401c440a0c3a1d72bf39286c0db3fd"
  "vocoder.int8.onnx|25991073|e923d60f53f95eb1ce235f1dc33ec56d9c057823c96fa6f8acf98f32b0da6152"
  "tts.json|8253|42078d3aef1cd43ab43021f3c54f47d2d75ceb4e75f627f118890128b06a0d09"
  "unicode_indexer.bin|262144|8402ca48e5189a8950138580b0fff64db6f072f24ac07cd54ba8b2fbb9883b30"
  "voice.bin|517168|67d5209b0ee8ce6c74105ffbe12fe6a7628aea3b4ba2fcb308a4a67938a93ce8"
)

need() { [ -e "$1" ] || { echo "missing: $1"; exit 1; }; }

check_supertonic() {
  local dir="$1/$SUPERTONIC_FOLDER" bad=0 entry name size sha got
  for entry in "${SUPERTONIC_FILES[@]}"; do
    IFS='|' read -r name size sha <<<"$entry"
    if [ ! -f "$dir/$name" ]; then echo "MISSING  $name"; bad=1; continue; fi
    got="$(stat -L -c %s "$dir/$name")"
    if [ "$got" != "$size" ]; then echo "BAD SIZE $name ($got, expected $size)"; bad=1; continue; fi
    if [ "$(sha256sum "$dir/$name" | cut -d' ' -f1)" != "$sha" ]; then echo "BAD HASH $name"; bad=1; continue; fi
    echo "OK       $name"
  done
  return $bad
}

check_voices() {
  local dir="$1" ok=0 entry folder model sha size actual
  for entry in "${VOICES[@]}"; do
    IFS='|' read -r folder model sha size <<<"$entry"
    if [ ! -f "$dir/$folder/$model" ] || [ ! -f "$dir/$folder/tokens.txt" ] || [ ! -d "$dir/$folder/espeak-ng-data" ]; then
      echo "MISSING  $folder (needs $model, tokens.txt, espeak-ng-data/)"; continue
    fi
    actual="$(sha256sum "$dir/$folder/$model" | cut -d' ' -f1)"
    if [ "$actual" = "$sha" ]; then echo "OK       $folder"; ok=$((ok + 1)); else echo "BAD HASH $folder ($actual)"; fi
  done
  [ "$ok" -gt 0 ] || { echo "no usable voice in $dir"; return 1; }
}

case "$cmd" in
  check)
    check_voices "${2:-$HOME/tts-models}"
    ;;
  stage)
    bundle="${2:-$HOME/sherpa-bundle/bundle}"
    need "$bundle/libsherpa-onnx-jni.so"; need "$bundle/libonnxruntime.so"
    out="$app/src/speech/jniLibs/arm64-v8a"; mkdir -p "$out"
    # The JNI library is loaded in-process (System.loadLibrary("sherpa-onnx-jni")) only when Piper is selected.
    # libonnxruntime.so is the same file speech_bundle.sh stages; it is only copied when it is not there yet.
    for f in libsherpa-onnx-jni.so libonnxruntime.so; do
      if [ -e "$out/$f" ]; then echo "already staged: $f"; else cp "$bundle/$f" "$out/$f"; echo "staged: $f"; fi
    done
    echo "staged into $out:"; ls -la "$out" | tail -n +2
    ;;
  push)
    dir="${2:-$HOME/tts-models}"
    check_voices "$dir"
    adb root >/dev/null; adb wait-for-device
    uid="$(adb shell stat -c %u /data/data/$PKG | tr -d '\r')"
    [ -n "$uid" ] || { echo "install the app first: $PKG has no data dir"; exit 1; }
    base=/data/data/$PKG/files/tts
    adb shell "mkdir -p $base"
    for entry in "${VOICES[@]}"; do
      IFS='|' read -r folder model sha size <<<"$entry"
      [ -f "$dir/$folder/$model" ] || continue
      adb shell "rm -rf $base/$folder && mkdir -p $base/$folder"
      adb push "$dir/$folder/." "$base/$folder/"
      echo "device sha256 of $model (expected $sha):"
      adb shell "sha256sum $base/$folder/$model"
    done
    adb shell "chown -R $uid:$uid $base && chmod -R u+rX,go-rwx $base && restorecon -R $base"
    echo "pushed to $base:"; adb shell "ls -la $base" | grep -v '^total'
    ;;
  check-supertonic)
    check_supertonic "${2:-$HOME/tts-models}" || { echo "supertonic bundle is NOT usable"; exit 1; }
    ;;
  push-supertonic)
    dir="${2:-$HOME/tts-models}"
    check_supertonic "$dir" || { echo "supertonic bundle is NOT usable"; exit 1; }
    adb root >/dev/null; adb wait-for-device
    uid="$(adb shell stat -c %u /data/data/$PKG | tr -d '\r')"
    [ -n "$uid" ] || { echo "install the app first: $PKG has no data dir"; exit 1; }
    base=/data/data/$PKG/files/tts/$SUPERTONIC_FOLDER
    adb shell "mkdir -p $base"
    for entry in "${SUPERTONIC_FILES[@]}"; do
      IFS='|' read -r name size sha <<<"$entry"
      adb push "$dir/$SUPERTONIC_FOLDER/$name" "$base/$name" >/dev/null
    done
    adb shell "chown -R $uid:$uid /data/data/$PKG/files/tts && chmod -R u+rX,go-rwx $base && restorecon -R $base" >/dev/null 2>&1
    for entry in "${SUPERTONIC_FILES[@]}"; do
      IFS='|' read -r name size sha <<<"$entry"
      got="$(adb shell sha256sum "$base/$name" | tr -d '\r' | cut -d' ' -f1)"
      [ "$got" = "$sha" ] && echo "device OK  $name" || { echo "DEVICE HASH DIFFERS $name ($got)"; exit 1; }
    done
    ;;
  *)
    # The usage text is the leading comment block (line 2 up to the first line that is not a comment).
    awk 'NR > 1 { if (/^#/) print; else exit }' "$0"; exit 2 ;;
esac
