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

need() { [ -e "$1" ] || { echo "missing: $1"; exit 1; }; }

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
  *)
    sed -n '2,13p' "$0"; exit 2 ;;
esac
