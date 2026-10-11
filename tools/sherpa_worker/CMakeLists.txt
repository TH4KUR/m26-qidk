# Not a standalone project: this is the fragment that adds the worker to a sherpa-onnx checkout.
# See README.md. `build_worker.sh` copies worker.cc into sherpa-onnx/csrc/ and appends this to sherpa-onnx/csrc/CMakeLists.txt,
# inside the same `if(SHERPA_ONNX_ENABLE_BINARY)` block that already declares sherpa-onnx-offline (line ~509 at the pinned revision).
add_executable(sherpa-onnx-offline-worker sherpa-onnx-offline-worker.cc)
target_link_libraries(sherpa-onnx-offline-worker sherpa-onnx-core)
if(NOT WIN32)
  target_link_libraries(sherpa-onnx-offline-worker -pthread)
endif()
install(TARGETS sherpa-onnx-offline-worker DESTINATION bin)
