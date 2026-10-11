#!/usr/bin/env bash
# Adds the resident worker to a sherpa-onnx checkout and prints the build steps. Does not build anything itself.
#   build_worker.sh /path/to/sherpa-onnx
# The checkout must be at the pinned revision below (or a later one you re-checked: ReadWave, OfflineRecognizer::DecodeStream
# and OfflineRecognizerConfig::Register must still exist with these signatures).
set -euo pipefail
PINNED=9db1af1871ed78cee9357c23898e5a7d8702d21a
src="${1:?path to a sherpa-onnx checkout}"
here="$(cd "$(dirname "$0")" && pwd)"
have="$(git -C "$src" rev-parse HEAD)"
[ "$have" = "$PINNED" ] || echo "warning: checkout is $have, not the pinned $PINNED; re-verify the API before trusting the build" >&2
cp "$here/worker.cc" "$src/sherpa-onnx/csrc/sherpa-onnx-offline-worker.cc"
grep -q "sherpa-onnx-offline-worker" "$src/sherpa-onnx/csrc/CMakeLists.txt" || {
  # Insert after the line that declares sherpa-onnx-offline, so it sits inside the same binary-building block.
  awk -v frag="$(cat "$here/CMakeLists.txt" | grep -v '^#')" '{print} /add_executable\(sherpa-onnx-offline sherpa-onnx-offline.cc\)/ {print frag}' \
    "$src/sherpa-onnx/csrc/CMakeLists.txt" > "$src/sherpa-onnx/csrc/CMakeLists.txt.new"
  mv "$src/sherpa-onnx/csrc/CMakeLists.txt.new" "$src/sherpa-onnx/csrc/CMakeLists.txt"
}
cat <<MSG
Worker source added to $src. Now build for the kit exactly as the CLI was built (QNN on, arm64):
  cd $src && export SHERPA_ONNX_ENABLE_QNN=ON && ./build-android-arm64-v8a.sh
Then: app/scripts/speech_bundle.sh stage  (copies build-android-arm64-v8a/install/bin/sherpa-onnx-offline-worker as libsherpa_worker.so)
MSG
