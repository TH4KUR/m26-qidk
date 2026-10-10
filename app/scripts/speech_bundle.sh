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
set -euo pipefail

PKG=com.megathon.incident
here="$(cd "$(dirname "$0")" && pwd)"
app="$(cd "$here/.." && pwd)"
cmd="${1:-}"; bundle="${2:-$HOME/sherpa-bundle/bundle}"
WHISPER=sherpa-onnx-qnn-SM8750-binary-whisper-small
SENSE=sherpa-onnx-qnn-SM8750-binary-10-seconds-sense-voice-zh-en-ja-ko-yue-2025-09-09-int8

need() { [ -e "$1" ] || { echo "missing: $1"; exit 1; }; }

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
  *)
    sed -n '2,12p' "$0"; exit 2 ;;
esac
