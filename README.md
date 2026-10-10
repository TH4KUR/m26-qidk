# m26-qidk

Megathon X 2026 (IIIT Hyderabad), Qualcomm problem statement: **AI Incident Report Assistant**. Voice in, structured incident report out, with every AI model running on-device on the Snapdragon NPU of a QIDK HDK8750 (SM8750P, Snapdragon 8 Elite).

## Contents

| Path | What it is |
|---|---|
| `Qualcomm_Problem_Statement_for_MegathonX.pptx` | The official problem-statement deck |
| `exa-results/qidk-incident-assistant-feasibility-2026-10-10.md` | Feasibility and due-diligence report: QIDK stack, competitors, risks, scorecard, demo strategy |
| `docs/superpowers/specs/2026-10-10-incident-assistant-design.md` | Design spec (decisions D1–D15) |
| `docs/superpowers/plans/2026-10-10-incident-assistant-qidk.md` | Task-by-task 24 h implementation plan, test-first, with team split and fallbacks |
| `.claude/skills/qidk-npu-android/` | Claude Code skill: running models on the QIDK NPU from Android (GenieX, WhisperKit, QNN packaging, known failures) |
| `.claude/skills/incident-report-prior-art/` | Claude Code skill: prior-art catalog, reusable repos, incident schema, eval datasets |

## Stack (planned)

- **STT:** WhisperKit Android with the QNN delegate (NPU)
- **LLM:** Qualcomm GenieX Android SDK on the NPU, with a GBNF grammar and a native `update_report` tool call
- **Logic:** deterministic grounding, merge and mandatory-field follow-up in Kotlin
- **TTS:** Android offline TTS (stretch goal: MeloTTS on the NPU)

Status: research and plan only, no app code yet.
