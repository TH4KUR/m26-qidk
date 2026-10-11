#!/usr/bin/env bash
# Stage and push the sherpa-onnx QNN speech bundle. The bundle is built by the lead on the build VM and is NOT in git
# (it is about 900 MB and includes Qualcomm runtime libraries and model files).
#
#   speech_bundle.sh stage [BUNDLE_DIR]   copy the CLI and ONNX Runtime into app/src/speech/jniLibs (gitignored), so the
#                                         next Gradle build packs them into the APK
#   speech_bundle.sh push  [BUNDLE_DIR]   push the QNN libraries and the models into the app's private folder on the kit
#                                         (needs `adb root`; the app must be installed once so its folder and uid exist)
#
# BUNDLE_DIR defaults to $HOME/sherpa-bundle/bundle.
#
# EXPERIMENTAL Hindi lane (Nemotron-3.5 ASR, additive; English is untouched). Three more commands, same conventions:
#
#   speech_bundle.sh check-hindi [ONLINE_DIR] [MODEL_DIR]   local only, no adb: verify the online CLI, libonnxruntime.so and the
#                                         three context binaries exist with the sizes recorded when the bundle was verified
#   speech_bundle.sh stage-online [ONLINE_DIR]   copy the ONLINE sherpa-onnx CLI into app/src/speech/jniLibs (gitignored) as
#                                         libsherpa_online.so, next to the Whisper libsherpa_offline.so. libonnxruntime.so is the
#                                         same file for both, so ONE copy serves both children.
#   speech_bundle.sh push-hindi  [MODEL_DIR]     push the model folder (about 650 MB, never in the APK or git) to
#                                         files/sherpa/nemotron-hi/ (needs `adb root`; run `push` first so files/sherpa/qnn exists:
#                                         the Hindi lane uses the same QNN 2.40 libraries, never GenieX's 2.45)
#
# ONLINE_DIR defaults to $HOME/sherpa-bundle/online.
# MODEL_DIR defaults to $HOME/models-hi-te/sherpa-onnx-qnn-SM8750-binary-nemotron-3.5-asr-streaming-0.6b-1120ms.
set -euo pipefail

PKG=com.megathon.incident
here="$(cd "$(dirname "$0")" && pwd)"
app="$(cd "$here/.." && pwd)"
cmd="${1:-}"; bundle="${2:-$HOME/sherpa-bundle/bundle}"
WHISPER=sherpa-onnx-qnn-SM8750-binary-whisper-small
SENSE=sherpa-onnx-qnn-SM8750-binary-10-seconds-sense-voice-zh-en-ja-ko-yue-2025-09-09-int8

# Hindi lane (experimental): default locations and the sizes recorded when the bundle was verified. Keep these equal to
# NemotronHindiBundle.EXPECTED_BYTES in core (the app checks the same sizes on the device).
ONLINE_DEFAULT="$HOME/sherpa-bundle/online"
NEMOTRON_DEFAULT="$HOME/models-hi-te/sherpa-onnx-qnn-SM8750-binary-nemotron-3.5-asr-streaming-0.6b-1120ms"
ONLINE_CLI_BYTES=2186176
ENCODER_BYTES=610848768
DECODER_BYTES=29978624
JOINER_BYTES=9732096

need() { [ -e "$1" ] || { echo "missing: $1"; exit 1; }; }
size_of() { stat -L -c %s "$1"; } # -L: a symlinked model folder (kept on another disk) must report the file's size, not the link's
need_size() { # file expected_bytes
  need "$1"
  local got; got="$(size_of "$1")"
  [ "$got" = "$2" ] || { echo "wrong size: $1 is $got bytes, expected $2"; exit 1; }
}

case "$cmd" in
  stage)
    need "$bundle/sherpa-onnx-offline"; need "$bundle/libonnxruntime.so"
    out="$app/src/speech/jniLibs/arm64-v8a"; mkdir -p "$out"
    # Android only extracts files named lib*.so, so the executable is renamed. It is run as a child process (not loaded).
    cp "$bundle/sherpa-onnx-offline" "$out/libsherpa_offline.so"
    cp "$bundle/libonnxruntime.so" "$out/libonnxruntime.so"
    echo "staged into $out:"; ls -la "$out" | tail -n +2
    ;;
  push)
    for f in libQnnHtp.so libQnnSystem.so libQnnHtpPrepare.so libQnnHtpV79Stub.so libQnnHtpV79Skel.so; do need "$bundle/$f"; done
    for f in encoder.bin decoder.bin tokens.txt; do need "$bundle/$WHISPER/$f"; done
    adb root >/dev/null; adb wait-for-device
    uid="$(adb shell stat -c %u /data/data/$PKG | tr -d '\r')"
    [ -n "$uid" ] || { echo "install the app first: $PKG has no data dir"; exit 1; }
    base=/data/data/$PKG/files/sherpa
    adb shell "mkdir -p $base/qnn $base/whisper-small $base/sense-voice"
    for f in libQnnHtp.so libQnnSystem.so libQnnHtpPrepare.so libQnnHtpV79Stub.so libQnnHtpV79Skel.so; do adb push "$bundle/$f" "$base/qnn/$f"; done
    for f in encoder.bin decoder.bin tokens.txt; do adb push "$bundle/$WHISPER/$f" "$base/whisper-small/$f"; done
    if [ -e "$bundle/$SENSE/model.bin" ]; then
      for f in model.bin tokens.txt; do adb push "$bundle/$SENSE/$f" "$base/sense-voice/$f"; done
    fi
    adb shell "chown -R $uid:$uid $base && chmod -R u+rX,go-rwx $base && chmod 755 $base/qnn/*.so && restorecon -R $base"
    echo "pushed to $base:"; adb shell "ls -la $base/qnn $base/whisper-small" | grep -v '^total'
    ;;
  check-hindi)
    online="${2:-$ONLINE_DEFAULT}"; model="${3:-$NEMOTRON_DEFAULT}"
    need_size "$online/sherpa-onnx" "$ONLINE_CLI_BYTES"
    need "$online/libonnxruntime.so"
    need_size "$model/encoder.bin" "$ENCODER_BYTES"
    need_size "$model/decoder.bin" "$DECODER_BYTES"
    need_size "$model/joiner.bin" "$JOINER_BYTES"
    need "$model/tokens.txt"; [ -s "$model/tokens.txt" ] || { echo "empty: $model/tokens.txt"; exit 1; }
    echo "hindi bundle OK (local): online CLI $ONLINE_CLI_BYTES B, encoder $ENCODER_BYTES B, decoder $DECODER_BYTES B, joiner $JOINER_BYTES B, tokens.txt"
    ;;
  stage-online)
    online="${2:-$ONLINE_DEFAULT}"
    need "$online/sherpa-onnx"; need "$online/libonnxruntime.so"
    got="$(size_of "$online/sherpa-onnx")"
    [ "$got" = "$ONLINE_CLI_BYTES" ] || echo "warning: online CLI is $got bytes, the verified build was $ONLINE_CLI_BYTES"
    out="$app/src/speech/jniLibs/arm64-v8a"; mkdir -p "$out"
    # One libonnxruntime.so serves both children. If a different one is already staged for Whisper, stop: two builds of the
    # same name cannot both be packed.
    if [ -e "$out/libonnxruntime.so" ]; then
      cmp -s "$online/libonnxruntime.so" "$out/libonnxruntime.so" \
        || { echo "libonnxruntime.so in $online differs from the one already staged for Whisper in $out; resolve before staging"; exit 1; }
    else
      cp "$online/libonnxruntime.so" "$out/libonnxruntime.so"
    fi
    # Android only extracts files named lib*.so, so the executable is renamed. It is run as a child process (not loaded).
    cp "$online/sherpa-onnx" "$out/libsherpa_online.so"
    echo "staged into $out:"; ls -la "$out" | tail -n +2
    ;;
  push-hindi)
    model="${2:-$NEMOTRON_DEFAULT}"
    need_size "$model/encoder.bin" "$ENCODER_BYTES"
    need_size "$model/decoder.bin" "$DECODER_BYTES"
    need_size "$model/joiner.bin" "$JOINER_BYTES"
    need "$model/tokens.txt"
    adb root >/dev/null; adb wait-for-device
    uid="$(adb shell stat -c %u /data/data/$PKG | tr -d '\r')"
    [ -n "$uid" ] || { echo "install the app first: $PKG has no data dir"; exit 1; }
    base=/data/data/$PKG/files/sherpa
    adb shell "[ -e $base/qnn/libQnnHtp.so ]" || { echo "run 'speech_bundle.sh push' first: $base/qnn (the QNN 2.40 libraries) is missing"; exit 1; }
    adb shell "mkdir -p $base/nemotron-hi"
    for f in encoder.bin decoder.bin joiner.bin tokens.txt; do adb push "$model/$f" "$base/nemotron-hi/$f"; done
    if [ -e "$model/info.txt" ]; then adb push "$model/info.txt" "$base/nemotron-hi/info.txt"; fi
    adb shell "chown -R $uid:$uid $base/nemotron-hi && chmod -R u+rX,go-rwx $base/nemotron-hi && restorecon -R $base/nemotron-hi"
    echo "pushed to $base/nemotron-hi:"; adb shell "ls -la $base/nemotron-hi" | grep -v '^total'
    # A truncated push is the usual failure: compare the device sizes with the verified ones.
    for pair in "encoder.bin:$ENCODER_BYTES" "decoder.bin:$DECODER_BYTES" "joiner.bin:$JOINER_BYTES"; do
      f="${pair%%:*}"; want="${pair##*:}"
      got="$(adb shell stat -c %s $base/nemotron-hi/$f | tr -d '\r')"
      [ "$got" = "$want" ] || { echo "WRONG SIZE on device: $f is $got bytes, expected $want"; exit 1; }
    done
    echo "device sizes match"
    ;;
  *)
    # The usage text is the leading comment block (line 2 up to the first line that is not a comment).
    awk 'NR > 1 { if (/^#/) print; else exit }' "$0"; exit 2 ;;
esac
