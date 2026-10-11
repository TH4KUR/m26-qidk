// tools/sherpa_worker/worker.cc
//
// A resident variant of sherpa-onnx-offline: it creates the offline recognizer ONCE, then decodes WAV files on request.
// Protocol v1, JSON lines (stdout carries protocol only; every log line goes to stderr):
//
//   worker -> client   {"v":1,"ready":true,"init_ms":N}                 after the recognizer exists
//                      {"v":1,"ready":false,"error":"..."}              and the process exits with status 1
//   client -> worker   {"v":1,"id":N,"cmd":"transcribe","wav":"/abs/path.wav","language":"en"}
//   worker -> client   {"v":1,"id":N,"text":"...","decode_ms":N}  or  {"v":1,"id":N,"error":"..."}
//   client -> worker   {"v":1,"id":N,"cmd":"quit"}                      the worker exits with status 0
//
// It takes the same recognizer flags as sherpa-onnx-offline (--tokens, --provider=qnn, --whisper.qnn-*, ...) and no wave files.
// The language is fixed by --whisper-language at start, because the recognizer is built once; the "language" field of a
// request is checked against it and a mismatch is an error, never a silent switch.
// Built against k2-fsa/sherpa-onnx commit 9db1af1871ed78cee9357c23898e5a7d8702d21a (see README.md). NOT yet compiled or run.

#include <stdio.h>
#include <unistd.h>

#include <chrono>
#include <iostream>
#include <memory>
#include <string>
#include <vector>

#include "sherpa-onnx/csrc/offline-recognizer.h"
#include "sherpa-onnx/csrc/parse-options.h"
#include "sherpa-onnx/csrc/wave-reader.h"

namespace {

// Escapes a string for a JSON string literal.
std::string Escape(const std::string &s) {
  std::string out;
  for (unsigned char c : s) {
    switch (c) {
      case '"': out += "\\\""; break;
      case '\\': out += "\\\\"; break;
      case '\n': out += "\\n"; break;
      case '\r': out += "\\r"; break;
      case '\t': out += "\\t"; break;
      default:
        if (c < 0x20) {
          char buf[8];
          snprintf(buf, sizeof(buf), "\\u%04x", c);
          out += buf;
        } else {
          out += static_cast<char>(c);
        }
    }
  }
  return out;
}

// The client writes flat JSON with string and integer values only, so a small scanner is enough.
bool GetString(const std::string &line, const std::string &key, std::string *value) {
  const std::string needle = "\"" + key + "\"";
  size_t at = line.find(needle);
  if (at == std::string::npos) return false;
  at = line.find(':', at + needle.size());
  if (at == std::string::npos) return false;
  at = line.find('"', at);
  if (at == std::string::npos) return false;
  std::string out;
  for (size_t i = at + 1; i < line.size(); ++i) {
    if (line[i] == '\\' && i + 1 < line.size()) {
      out += line[++i];
    } else if (line[i] == '"') {
      *value = out;
      return true;
    } else {
      out += line[i];
    }
  }
  return false;
}

bool GetInt(const std::string &line, const std::string &key, long long *value) {
  const std::string needle = "\"" + key + "\"";
  size_t at = line.find(needle);
  if (at == std::string::npos) return false;
  at = line.find(':', at + needle.size());
  if (at == std::string::npos) return false;
  try {
    *value = std::stoll(line.substr(at + 1));
    return true;
  } catch (...) {
    return false;
  }
}

// Protocol goes to a private duplicate of the original stdout. File descriptor 1 is then pointed at stderr, so a library that
// prints with printf/std::cout (ONNX Runtime, QNN, sherpa) can never put a stray line into the protocol stream.
FILE *g_proto = nullptr;

void Emit(const std::string &json) {
  fputs(json.c_str(), g_proto);
  fputc('\n', g_proto);
  fflush(g_proto);
}

void EmitError(long long id, const std::string &message) {
  Emit("{\"v\":1,\"id\":" + std::to_string(id) + ",\"error\":\"" + Escape(message) + "\"}");
}

}  // namespace

int main(int argc, char *argv[]) {
  const int proto_fd = dup(STDOUT_FILENO);
  if (proto_fd < 0 || dup2(STDERR_FILENO, STDOUT_FILENO) < 0) return 2;
  g_proto = fdopen(proto_fd, "w");
  if (g_proto == nullptr) return 2;

  sherpa_onnx::ParseOptions po("Resident sherpa-onnx offline recognizer: JSON lines on stdin/stdout. See tools/sherpa_worker/README.md");
  sherpa_onnx::OfflineRecognizerConfig config;
  config.Register(&po);
  po.Read(argc, argv);

  fprintf(stderr, "%s\n", config.ToString().c_str());
  if (!config.Validate()) {
    Emit("{\"v\":1,\"ready\":false,\"error\":\"invalid recognizer config\"}");
    return 1;
  }

  const auto begin_init = std::chrono::steady_clock::now();
  sherpa_onnx::OfflineRecognizer recognizer(config);  // the only place the models are loaded
  const auto init_ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - begin_init).count();
  fprintf(stderr, "recognizer created in %lld ms\n", static_cast<long long>(init_ms));
  Emit("{\"v\":1,\"ready\":true,\"init_ms\":" + std::to_string(init_ms) + "}");

  std::string line;
  while (std::getline(std::cin, line)) {
    if (line.empty()) continue;
    long long id = -1;
    std::string cmd;
    if (!GetInt(line, "id", &id) || !GetString(line, "cmd", &cmd)) {
      EmitError(id, "malformed request");
      continue;
    }
    if (cmd == "quit") return 0;
    if (cmd != "transcribe") {
      EmitError(id, "unknown command: " + cmd);
      continue;
    }

    std::string wav, language;
    if (!GetString(line, "wav", &wav)) {
      EmitError(id, "missing wav");
      continue;
    }
    if (GetString(line, "language", &language) && !language.empty() &&
        language != config.model_config.whisper.language) {
      EmitError(id, "worker was started for language '" + config.model_config.whisper.language + "', not '" + language + "'");
      continue;
    }

    int32_t sample_rate = -1;
    bool ok = false;
    std::vector<float> samples = sherpa_onnx::ReadWave(wav, &sample_rate, &ok);
    if (!ok || samples.empty()) {
      EmitError(id, "cannot read " + wav);
      continue;
    }

    const auto begin = std::chrono::steady_clock::now();
    auto stream = recognizer.CreateStream();  // a fresh stream per request; the recognizer (the models) is reused
    stream->AcceptWaveform(sample_rate, samples.data(), samples.size());
    recognizer.DecodeStream(stream.get());
    const auto decode_ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - begin).count();

    Emit("{\"v\":1,\"id\":" + std::to_string(id) + ",\"text\":\"" + Escape(stream->GetResult().text) + "\",\"decode_ms\":" +
         std::to_string(decode_ms) + "}");
  }
  return 0;  // stdin closed: the app is gone or released the worker
}
