# AI Incident Report Assistant (QIDK HDK8750) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Also load the project skills `qidk-npu-android` and `incident-report-prior-art` before touching runtime or extraction code.

**Goal:** An offline Android app on the QIDK HDK8750 (Snapdragon 8 Elite). A worker speaks an incident; the app transcribes it on the NPU, extracts a grounded structured report with an on-NPU LLM via a visible tool call, asks for missing mandatory fields by voice, and lets the user review, submit and hear a summary.

**Architecture:**
- **Pure-Kotlin domain core:** schema, parser, normaliser, time resolver, grounding, merge, follow-up planner, scorer. Fully unit-tested on the JVM.
- **Thin adapters** for GenieX (LLM), WhisperKit (STT) and Android TTS.
- A **`ReportSession` state machine** drives a Compose UI.
- The LLM only ever proposes `update_report`. Deterministic code decides `ask_user` and `submit_report`.

**Tech Stack:**
- Kotlin 2.x, Jetpack Compose, coroutines/Flow, kotlinx.serialization, JUnit4 + kotlin.test
- GenieX Android SDK (`com.qualcomm.qti:geniex-android`)
- WhisperKit Android (`com.argmaxinc:whisperkit:0.3.3`, `com.qualcomm.qnn:qnn-runtime:2.34.0`, `com.qualcomm.qnn:qnn-litert-delegate:2.34.0`)
- Android `TextToSpeech`
- Laptop prompt iteration: llama.cpp `llama-server` (Windows release build)

**Spec:** `docs/superpowers/specs/2026-10-10-incident-assistant-design.md` (decisions D1–D15). Background: `exa-results/qidk-incident-assistant-feasibility-2026-10-10.md`.

## Global Constraints

- Target device: QIDK HDK8750, SoC `SM8750P`, Hexagon v79. GenieX AI Hub pulls use `chipset = "SM8750"`.
- **No cloud API at runtime.** Network is used only for the one-time model download (D14). The demo runs in airplane mode.
- LLM `compute_unit = "npu"`. WhisperKit runs with the QNN delegate. If any stage falls back to CPU, the metrics panel must say so.
- Package `com.megathon.incident`; minSdk 31, targetSdk 35, JDK 17, `abiFilters += "arm64-v8a"`.
- Mandatory fields, in this order: `INCIDENT_TYPE, LOCATION, DATE_TIME, SEVERITY` (D6). At most 2 follow-up questions per turn.
- LLM output contract: exactly the JSON in spec §4, wrapped as `{"tool":"update_report","arguments":{...}}`.
- Prompts live once in `app/src/main/assets/prompts/` and are read by both the app and the laptop eval script.
- Licences: copy code only from MIT/Apache repos (vox, incident-scribe, WhisperKitAndroid, llama.cpp). Repos with no licence (sm8750-edge-ai-lab, vic4code, konstro, OpsTune) are reference-only. local-dream is CC BY-NC: attribute it if the Plan C pattern is used.
- Unit tests: `gradlew.bat :app:testDebugUnitTest` (Windows) or `./gradlew :app:testDebugUnitTest`. Device tests: `gradlew.bat :app:connectedDebugAndroidTest`.

## Review Focus

1. **Silence or an empty transcript** (push-to-talk tapped, nothing said). Expect "I didn't catch that" with no LLM call and no state change. Test: Task 6 `emptyTranscriptDoesNotCallLlm`.
2. **A follow-up answer that is "I don't know" or off-topic.** The field stays missing, the app asks at most once more, then lets the user go to review with the field flagged. Test: Task 5 `unknownAnswerIsNotAFill` and Task 6 `secondUnansweredAskAllowsReview`.
3. **A correction utterance** ("actually it was Conference Room 3") must overwrite a CONFIRMED value when the new value is grounded, without wiping other fields. Test: Task 5 `groundedCorrectionOverwrites`.
4. **Relative times near midnight and "yesterday"** must resolve against the device clock, not the model. Test: Task 3 `yesterdayEveningResolvesToPreviousDate`.
5. **A hallucinated field** (the model emits `witnesses:["John"]` when nobody was mentioned) must not count as filled. Test: Task 4 `ungroundedValueBecomesUnconfirmed`.

---

## Phase 0 — Before the event (environment only; confirm with organisers whether pre-written code is allowed)

### Task 0: Workstation, accounts and kit bring-up

**Files:** none (environment). Record outcomes in `docs/setup-log.md`.

- [ ] **Step 1: Install tooling.** Android Studio Meerkat 2024.3.1 or newer (GenieX prerequisite), JDK 17, platform-tools (`adb`). Download the llama.cpp Windows release zip for the laptop eval. **Main path needs no QAIRT SDK, no Linux and no QPM.**
- [ ] **Step 2: Accounts.** Create a Hugging Face account (some GGUF repos). Create a Qualcomm ID (needed only for Plan B/C: `qpm-cli --login`).
- [ ] **Step 3: Set up the kit.**
  - Set DIP S5702-7 and S5702-8 ON *before* power-up (HDMI out).
  - Connect the 12 V adaptor and USB-C.
  - Run `adb devices`.
  - Run `adb shell getprop ro.soc.model` (expect `SM8750` or `SM8750P`).
  - Run `adb shell getprop ro.build.version.release`.
  - Run `adb shell grep MemTotal /proc/meminfo`.
  - Record all of it.
- [ ] **Step 4: GenieX reference app.**
  - `git clone -b release/geniex_chat_android https://github.com/qualcomm/ai-hub-apps`, open `geniex_chat_android` in Android Studio, sync, build, run `adb install -t app-debug.apk`.
  - Download **Qwen3-1.7B** and **Qwen3-4B-Instruct** from the in-app catalog and chat once with each.
  - Pass: tokens stream on the kit. Record the `geniex-android` version from its `build.gradle(.kts)`. That is the version to pin in Task 1.
- [ ] **Step 5: WhisperKit reference.**
  - `git clone https://github.com/argmaxinc/WhisperKitAndroid`, build its Android sample with the QNN dependencies, transcribe the slide sentence spoken into the headset.
  - Pass: correct transcript, and the log shows the QNN/NPU delegate.
- [ ] **Step 6: Laptop eval harness.**
  - Run `llama-server -m Qwen3-1.7B-Q4_0.gguf --port 8080` on the laptop.
  - Download `NuExtract-2.0-2B-Q4_0.gguf` and `Qwen3-1.7B-Q4_0.gguf` (unsloth). Copy both to USB.

### Task 0b: Gold evaluation set (owner P4)

**Files:**
- Create: `app/src/main/assets/eval/gold_v1.jsonl`
- Create: `docs/eval-guide.md` (labelling rules)

- [ ] **Step 1: Write 40 lines**, one JSON object each: `{"id":"g01","utterance":"...","gold":{"INCIDENT_TYPE":"water_leak_flood","LOCATION":"Conference Room 2","DATE_TIME_TEXT":"around 10 AM","SEVERITY":null,"IMMEDIATE_ACTIONS":"isolated the area",...},"missing_mandatory":["SEVERITY"]}`.
  - 10 hand-written facilities cases. Include the slide example verbatim as `g01`.
  - 22 rewritten as spoken first-person reports from `Simar123456/spectra-osha-sir` (`narrative_masked`, test split).
  - 8 multi-turn cases with `"followups":["Minor, nobody hurt"]` or corrections.
- [ ] **Step 2: Verify.** Run `python -c "import json;[json.loads(l) for l in open('app/src/main/assets/eval/gold_v1.jsonl',encoding='utf-8')]"`. Expected: no error, 40 lines.

---

## Phase 1 — Spike (hackathon hours 0:00–1:30). Decisions are recorded in `docs/setup-log.md`.

### Task S: Runtime and model decision

- [ ] **S1 (P1, 20 min):** run the GenieX reference app on the event kit. Pass: Qwen3-1.7B on the NPU streams tokens. **Fail:** switch the LLM to the QIDK `ASR-LLM-TTS` Genie path (spec D10), or Plan C (D15).
- [ ] **S2 (P2, 30 min):** WhisperKit sample with `OPENAI_TINY_EN` on the kit; speak the slide sentence 3 times. Pass: ≥ 2/3 exact on the key words (water leak, Conference Room 2, 10 AM). **Fail:** try `BASE_EN`. If that also fails, use Plan B STT (QIDK Voice AI ASR, Whisper-Small).
- [ ] **S3 (P4, 45 min):** on the laptop, run `tools/laptop_eval/run_eval.py` (Task 13 Step 5) with the first 10 gold lines against each candidate GGUF, then repeat the best two on the kit through the GenieX reference app's chat (paste the prompt). **Decision rule:** highest mean field accuracy; tie-break on latency. Record which model and which prompt style (`CHAT_JSON` or `NUEXTRACT`).
- [ ] **S4 (P3, parallel):** create the project skeleton (Task 1).
- [ ] **Gate at 1:30.** S1 and S2 pass → full plan. S1 fails → Plan C. S2 fails → Plan B STT. Never fall back to a cloud API.

---

## Phase 2 — Build

### Task 1: Project scaffold and domain model (owner P3)

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/megathon/incident/domain/Model.kt`
- Test: `app/src/test/java/com/megathon/incident/domain/ModelTest.kt`

**Interfaces — Produces:**
- `enum class IncidentType(val wire: String)`: WATER_LEAK_FLOOD("water_leak_flood"), FIRE_SMOKE("fire_smoke"), ELECTRICAL("electrical"), SPILL_CHEMICAL("spill_chemical"), INJURY("injury"), ILLNESS("illness"), NEAR_MISS("near_miss"), PROPERTY_DAMAGE("property_damage"), EQUIPMENT_FAILURE("equipment_failure"), SECURITY("security"), ENVIRONMENTAL("environmental"), OTHER("other")
- `enum class Severity(val wire: String)`: NONE, MINOR, MODERATE, SEVERE, CRITICAL (lower-case wire values)
- `enum class Field`: INCIDENT_TYPE, SEVERITY, LOCATION, DATE_TIME, PERSONS_INVOLVED, EQUIPMENT_ASSET, DESCRIPTION, IMMEDIATE_ACTIONS, WITNESSES, RECOMMENDED_FOLLOW_UP
- `enum class FieldStatus`: EMPTY, CONFIRMED, UNCONFIRMED, DERIVED, USER_EDITED
- `@Serializable data class FieldValue(val text: String?, val status: FieldStatus, val evidence: String? = null)`
- `@Serializable data class IncidentReport(val id: String, val createdAtEpochMs: Long, val fields: Map<Field, FieldValue>)` with `fun value(f: Field): FieldValue` (EMPTY when absent), `fun with(f: Field, v: FieldValue): IncidentReport`, `fun isFilled(f: Field): Boolean` (status in CONFIRMED, DERIVED, USER_EDITED), and `companion fun empty(id: String, nowMs: Long)`
- `@Serializable data class ExtractionPayload(val incident_type: String? = null, val severity: String? = null, val location: String? = null, val date_time_text: String? = null, val persons_involved: List<String> = emptyList(), val equipment_asset: String? = null, val immediate_actions: String? = null, val witnesses: List<String> = emptyList(), val evidence: Map<String, String> = emptyMap())`

- [ ] **Step 1: Gradle.** Compose BOM, kotlinx-serialization plugin, coroutines, `implementation("com.qualcomm.qti:geniex-android:<version recorded in Task 0 Step 4>")`, the three WhisperKit/QNN artifacts listed in Tech Stack, and WhisperKit's documented `jniLibs { useLegacyPackaging = true }`. Manifest: `RECORD_AUDIO`, `INTERNET`, `ACCESS_NETWORK_STATE`.
- [ ] **Step 2: Write the failing test** `ModelTest`:
```kotlin
@Test fun emptyReportHasNoFilledFields() { val r = IncidentReport.empty("r1", 0); assertTrue(Field.values().none { r.isFilled(it) }) }
@Test fun reportRoundTripsThroughJson() {
  val r = IncidentReport.empty("r1", 0).with(Field.LOCATION, FieldValue("Conference Room 2", FieldStatus.CONFIRMED, "near Conference Room 2"))
  assertEquals(r, Json.decodeFromString<IncidentReport>(Json.encodeToString(r)))
}
@Test fun unconfirmedDoesNotCountAsFilled() { val r = IncidentReport.empty("r1",0).with(Field.LOCATION, FieldValue("x", FieldStatus.UNCONFIRMED)); assertFalse(r.isFilled(Field.LOCATION)) }
```
- [ ] **Step 3: Run it and confirm it fails.** `gradlew.bat :app:testDebugUnitTest --tests "*ModelTest"`. Expected: compilation failure (types missing).
- [ ] **Step 4: Implement `Model.kt`** per the Interfaces block.
- [ ] **Step 5: Run it and confirm it passes.** Expected: 3 tests PASS. `gradlew.bat :app:assembleDebug` succeeds.
- [ ] **Step 6: Commit.** `git init` (first time), then `git add -A && git commit -m "feat: scaffold app and incident domain model"`.

### Task 2: Lenient JSON extraction and tool-call parsing (owner P4)

**Files:**
- Create: `app/src/main/java/com/megathon/incident/llm/ToolCallParser.kt`
- Test: `app/src/test/java/com/megathon/incident/llm/ToolCallParserTest.kt`

**Interfaces:**
- Consumes: `ExtractionPayload`
- Produces:
  - `object JsonExtractor { fun firstObject(raw: String): String? }` — strips ```` ``` ```` fences and `<think>…</think>` blocks, returns the first balanced `{…}` respecting string quotes, and removes trailing commas before `}`/`]`.
  - `sealed interface ToolCall { data class UpdateReport(val payload: ExtractionPayload): ToolCall; data class AskUser(val fields: List<Field>, val question: String): ToolCall; data class SubmitReport(val reportId: String): ToolCall }`
  - `object ToolCallParser { fun parse(raw: String): Result<ToolCall.UpdateReport> }` — accepts `{"tool":"update_report","arguments":{…}}`, and also a bare arguments object (small models often drop the wrapper). Uses `Json { ignoreUnknownKeys = true; isLenient = true }`. Converts the string `"null"` to null.

- [ ] **Step 1: Write the failing tests:**
```kotlin
@Test fun parsesWrappedCall() { val p = ToolCallParser.parse("""{"tool":"update_report","arguments":{"location":"Conference Room 2","evidence":{"location":"near Conference Room 2"}}}""").getOrThrow().payload; assertEquals("Conference Room 2", p.location) }
@Test fun parsesBareArgumentsInsideFencesAndThink() { val raw = "<think>hmm</think>\n```json\n{\"incident_type\":\"water_leak_flood\",}\n```"; assertEquals("water_leak_flood", ToolCallParser.parse(raw).getOrThrow().payload.incident_type) }
@Test fun stringNullBecomesNull() { assertNull(ToolCallParser.parse("""{"location":"null"}""").getOrThrow().payload.location) }
@Test fun garbageFails() { assertTrue(ToolCallParser.parse("Sure! Here is the report.").isFailure) }
@Test fun braceInsideStringDoesNotBreak() { assertEquals("Room {B}", ToolCallParser.parse("""{"location":"Room {B}"}""").getOrThrow().payload.location) }
```
- [ ] **Step 2: Run them and confirm they fail.** `gradlew.bat :app:testDebugUnitTest --tests "*ToolCallParserTest"`. Expected: compilation failure.
- [ ] **Step 3: Implement** `JsonExtractor` and `ToolCallParser` per Interfaces. A bare object is detected when there is no `"tool"` key.
- [ ] **Step 4: Run them and confirm they pass.** Expected: 5 PASS.
- [ ] **Step 5: Commit.** `git commit -am "feat: lenient tool-call JSON parser"`

### Task 3: Normalisers, severity rules and time resolver (owner P4)

**Files:**
- Create: `app/src/main/java/com/megathon/incident/domain/Normalizers.kt`
- Test: `app/src/test/java/com/megathon/incident/domain/NormalizersTest.kt`

**Interfaces — Produces:**
- `object EnumNormalizer { fun incidentType(raw: String?): IncidentType?; fun severity(raw: String?): Severity? }`. Matches the exact wire value first, then a synonym table: leak/flood/burst pipe/water → WATER_LEAK_FLOOD; smoke/fire/burning → FIRE_SMOKE; sparks/shock/short circuit/power outage → ELECTRICAL; spill/chemical/gas leak → SPILL_CHEMICAL; injured/hurt/cut/fell → INJURY; almost/nearly → NEAR_MISS; broke/damaged → PROPERTY_DAMAGE; machine/breakdown → EQUIPMENT_FAILURE; theft/intruder → SECURITY. Severity synonyms: no harm/none → NONE; minor/small/slight → MINOR; moderate → MODERATE; serious/major/bad → SEVERE; critical/life-threatening/emergency → CRITICAL. Unknown → null.
- `object SeverityRules { fun floor(transcript: String): Severity? }`: "unconscious|not breathing|fatal|explosion" → CRITICAL; "fire|hospital|ambulance|bleeding|broken bone" → SEVERE; otherwise null.
- `data class ResolvedTime(val dateTime: LocalDateTime, val approximate: Boolean)`
- `class DateTimeResolver(private val clock: Clock, private val zone: ZoneId) { fun resolve(text: String?): ResolvedTime? }`. Handles "around/about/at H(:MM) AM|PM", "H o'clock", "this morning|afternoon|evening" (09:00/14:00/19:00, approximate), "yesterday" (minus one day, combinable), "just now|now|minutes ago" (now), "N hours ago". A future time today with no day word → previous day. Returns null when nothing matches.

- [ ] **Step 1: Write the failing tests:**
```kotlin
private val clock = Clock.fixed(Instant.parse("2026-10-11T04:30:00Z"), ZoneOffset.UTC) // 04:30 local (UTC)
@Test fun leakSynonymMapsToWaterLeak() = assertEquals(IncidentType.WATER_LEAK_FLOOD, EnumNormalizer.incidentType("Water Leak"))
@Test fun unknownTypeIsNull() = assertNull(EnumNormalizer.incidentType("banana"))
@Test fun minorMapsToMinor() = assertEquals(Severity.MINOR, EnumNormalizer.severity("Minor, nobody was hurt"))
@Test fun fireFloorIsSevere() = assertEquals(Severity.SEVERE, SeverityRules.floor("there was smoke and a small fire in the pantry"))
@Test fun around10AmNotYetReachedMeansPreviousDay() { val r = DateTimeResolver(clock, ZoneOffset.UTC).resolve("around 10 AM")!!; assertEquals(LocalDateTime.parse("2026-10-10T10:00"), r.dateTime); assertTrue(r.approximate) } // 10 AM is in the future at 04:30, so previous day
@Test fun yesterdayEveningResolvesToPreviousDate() { val r = DateTimeResolver(clock, ZoneOffset.UTC).resolve("yesterday evening")!!; assertEquals(LocalDate.parse("2026-10-10"), r.dateTime.toLocalDate()) }
@Test fun twoHoursAgo() = assertEquals(LocalDateTime.parse("2026-10-11T02:30"), DateTimeResolver(clock, ZoneOffset.UTC).resolve("2 hours ago")!!.dateTime)
@Test fun noTimeIsNull() = assertNull(DateTimeResolver(clock, ZoneOffset.UTC).resolve("in the lobby"))
```
- [ ] **Step 2: Run them and confirm they fail.** `gradlew.bat :app:testDebugUnitTest --tests "*NormalizersTest"`. Expected: compilation failure.
- [ ] **Step 3: Implement** per Interfaces with case-insensitive regexes. No external date library.
- [ ] **Step 4: Run them and confirm they pass.** Expected: 8 PASS.
- [ ] **Step 5: Commit.** `git commit -am "feat: enum normalisers, severity floor, relative time resolver"`

### Task 4: Grounding validator (owner P4)

**Files:**
- Create: `app/src/main/java/com/megathon/incident/domain/GroundingValidator.kt`
- Test: `app/src/test/java/com/megathon/incident/domain/GroundingValidatorTest.kt`

**Interfaces — Produces:**
- `object GroundingValidator { fun isGrounded(value: String?, evidence: String?, transcript: String): Boolean }`
  - Normalise: lowercase, strip punctuation, collapse whitespace.
  - Grounded if the normalised evidence is a substring of the transcript. Otherwise, if the evidence is null, grounded when the value's tokens overlap the transcript at ≥ 0.8 (token-set ratio, ignoring stop-words "the,a,an,of,in,at,near,around").
  - A null or blank value → false.

- [ ] **Step 1: Write the failing tests:**
```kotlin
private val t = "There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area."
@Test fun exactEvidenceIsGrounded() = assertTrue(GroundingValidator.isGrounded("Conference Room 2", "near Conference Room 2", t))
@Test fun valueOnlyOverlapIsGrounded() = assertTrue(GroundingValidator.isGrounded("conference room 2", null, t))
@Test fun ungroundedValueBecomesUnconfirmed() = assertFalse(GroundingValidator.isGrounded("John", "witness John", t))
@Test fun punctuationAndCaseIgnored() = assertTrue(GroundingValidator.isGrounded("Area isolated", "ALREADY isolated the area", t))
@Test fun blankValueIsNotGrounded() = assertFalse(GroundingValidator.isGrounded(" ", "x", t))
```
- [ ] **Step 2: Run them and confirm they fail.** `gradlew.bat :app:testDebugUnitTest --tests "*GroundingValidatorTest"`. Expected: compilation failure.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them and confirm they pass.** Expected: 5 PASS.
- [ ] **Step 5: Commit.** `git commit -am "feat: evidence grounding validator"`

### Task 5: Report merge, mandatory policy, follow-up planner, derived fields (owner P4)

**Files:**
- Create: `app/src/main/java/com/megathon/incident/domain/ReportLogic.kt`
- Test: `app/src/test/java/com/megathon/incident/domain/ReportLogicTest.kt`

**Interfaces:**
- Consumes: Tasks 1, 3, 4.
- Produces:
  - `class ReportMerger(private val resolver: DateTimeResolver) { fun merge(current: IncidentReport, payload: ExtractionPayload, transcriptSoFar: String): IncidentReport }`. Rules:
    - (a) For each payload field: compute grounded with `GroundingValidator` using `payload.evidence[wireName]`. Grounded → CONFIRMED with evidence. Not grounded → UNCONFIRMED, but only if the current value is not already filled.
    - (b) A grounded new value **overwrites** an existing CONFIRMED value (corrections). It never overwrites USER_EDITED.
    - (c) A null payload field leaves the current value unchanged.
    - (d) `incident_type` and `severity` are stored as wire values after `EnumNormalizer`. An unmappable type → OTHER.
    - (e) If severity is still not filled, apply `SeverityRules.floor(transcriptSoFar)` as DERIVED.
    - (f) `date_time_text` → `resolver.resolve` → DATE_TIME = ISO string, DERIVED (CONFIRMED if the text is grounded), with "≈" prefix when approximate.
    - (g) Lists are joined with ", ".
  - `object MandatoryFieldPolicy { val MANDATORY = listOf(Field.INCIDENT_TYPE, Field.LOCATION, Field.DATE_TIME, Field.SEVERITY); fun missing(r: IncidentReport): List<Field> }`
  - `object FollowUpPlanner { fun next(missing: List<Field>): ToolCall.AskUser? }`. Takes the first ≤ 2 fields and joins their questions with " Also, ". Exact copy:
    - INCIDENT_TYPE → "What kind of incident was it?"
    - LOCATION → "Where exactly did it happen?"
    - DATE_TIME → "When did it happen?"
    - SEVERITY → "How severe is it — minor, moderate, severe, or critical — and was anyone hurt?"
  - `object DerivedFields { fun description(transcripts: List<String>): String; fun followUp(type: IncidentType?, sev: Severity?): String }`. Description = the transcripts joined with " " and trimmed to 400 characters at a sentence boundary. The follow-up table: WATER_LEAK_FLOOD → "Inspect plumbing, check for water damage and electrical risk; log a maintenance work order." FIRE_SMOKE → "Fire safety inspection; check extinguishers and alarms; file a fire incident report." ELECTRICAL → "Isolate circuit; licensed electrician inspection before re-energising." INJURY → "Medical follow-up; root-cause investigation; review PPE and training." SPILL_CHEMICAL → "Check SDS; contain and dispose per protocol; ventilation check." default → "Supervisor review and root-cause analysis." Append " Escalate to safety officer within 24 h." when sev ≥ SEVERE.

- [ ] **Step 1: Write the failing tests:**
```kotlin
private val clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC)
private val merger = ReportMerger(DateTimeResolver(clock, ZoneOffset.UTC))
private val t1 = "There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area."
private val p1 = ExtractionPayload(incident_type="Water Leak", location="Conference Room 2", date_time_text="around 10 AM", immediate_actions="isolated the area",
  evidence=mapOf("location" to "near Conference Room 2","date_time_text" to "around 10 AM","immediate_actions" to "isolated the area","incident_type" to "water leak"))
@Test fun slideExampleFillsAllButSeverity() { val r = merger.merge(IncidentReport.empty("r",0), p1, t1); assertEquals(listOf(Field.SEVERITY), MandatoryFieldPolicy.missing(r)); assertEquals("water_leak_flood", r.value(Field.INCIDENT_TYPE).text) }
@Test fun followUpAsksSeverityWithExactCopy() { assertEquals("How severe is it — minor, moderate, severe, or critical — and was anyone hurt?", FollowUpPlanner.next(listOf(Field.SEVERITY))!!.question) }
@Test fun atMostTwoQuestions() = assertEquals(2, FollowUpPlanner.next(MandatoryFieldPolicy.MANDATORY)!!.fields.size)
@Test fun groundedCorrectionOverwrites() { val r1 = merger.merge(IncidentReport.empty("r",0), p1, t1); val t2 = "$t1 Actually it was Conference Room 3."
  val r2 = merger.merge(r1, ExtractionPayload(location="Conference Room 3", evidence=mapOf("location" to "Conference Room 3")), t2)
  assertEquals("Conference Room 3", r2.value(Field.LOCATION).text); assertEquals("water_leak_flood", r2.value(Field.INCIDENT_TYPE).text) }
@Test fun unknownAnswerIsNotAFill() { val r1 = merger.merge(IncidentReport.empty("r",0), p1, t1); val r2 = merger.merge(r1, ExtractionPayload(severity="I don't know"), "$t1 I don't know"); assertFalse(r2.isFilled(Field.SEVERITY)) }
@Test fun userEditIsNeverOverwritten() { val r = IncidentReport.empty("r",0).with(Field.LOCATION, FieldValue("Lobby", FieldStatus.USER_EDITED)); assertEquals("Lobby", merger.merge(r, p1, t1).value(Field.LOCATION).text) }
@Test fun fireTranscriptDerivesSevere() { val r = merger.merge(IncidentReport.empty("r",0), ExtractionPayload(), "small fire in the pantry"); assertEquals(FieldStatus.DERIVED, r.value(Field.SEVERITY).status) }
@Test fun severeFollowUpEscalates() = assertTrue(DerivedFields.followUp(IncidentType.FIRE_SMOKE, Severity.SEVERE).endsWith("Escalate to safety officer within 24 h."))
```
- [ ] **Step 2: Run them and confirm they fail.** `gradlew.bat :app:testDebugUnitTest --tests "*ReportLogicTest"`. Expected: compilation failure.
- [ ] **Step 3: Implement** per Interfaces. For `unknownAnswerIsNotAFill`, the payload's "I don't know" severity maps to null in `EnumNormalizer`, so nothing is stored.
- [ ] **Step 4: Run them and confirm they pass.** Expected: 8 PASS.
- [ ] **Step 5: Commit.** `git commit -am "feat: merge, mandatory policy, follow-up planner, derived fields"`

### Task 6: Prompts, extractor with repair retry, and session state machine (owner P3 + P4)

**Files:**
- Create: `app/src/main/assets/prompts/chat_json_system.txt`, `app/src/main/assets/prompts/nuextract_template.json`
- Create: `app/src/main/java/com/megathon/incident/llm/LlmEngine.kt`, `.../llm/PromptBuilder.kt`, `.../llm/IncidentExtractor.kt`
- Create: `app/src/main/java/com/megathon/incident/session/ReportSession.kt`
- Test: `app/src/test/java/com/megathon/incident/llm/IncidentExtractorTest.kt`, `app/src/test/java/com/megathon/incident/session/ReportSessionTest.kt`

**Interfaces:**
- Consumes: Tasks 1–5, and `ReportRepository` from **Task 7 (complete Task 7 before this task's session tests; P3 does it in hours 1.5–6)**.
- Produces:
  - `data class LlmResult(val text: String, val prefillMs: Long?, val decodeMs: Long?, val tokensPerSec: Double?, val backend: String)`
  - `interface LlmEngine { suspend fun generate(system: String, user: String, maxTokens: Int): LlmResult }`
  - `enum class PromptStyle { CHAT_JSON, NUEXTRACT }`
  - `data class Prompt(val system: String, val user: String)`
  - `class PromptBuilder(private val chatSystem: String, private val nuTemplate: String) { fun build(style: PromptStyle, utterance: String, transcriptSoFar: String, current: IncidentReport): Prompt }`
  - `data class ExtractionOutcome(val call: ToolCall.UpdateReport?, val raw: List<String>, val attempts: Int, val llm: LlmResult?)`
  - `class IncidentExtractor(private val llm: LlmEngine, private val prompts: PromptBuilder, private val style: PromptStyle, private val maxTokens: Int = 320) { suspend fun extract(utterance: String, transcriptSoFar: String, current: IncidentReport): ExtractionOutcome }`. Attempt 1 is the normal prompt. If `ToolCallParser` fails, attempt 2 appends the repair user message (exact copy): `"Your previous answer was not valid JSON. Reply with ONLY the JSON object, nothing else."` Maximum 2 attempts.
  - `sealed interface SessionState { object Idle; object Transcribing; object Extracting; data class Asking(val question: String); object Review; data class Submitted(val reportId: String); data class Error(val message: String) }`
  - `data class ToolLogEntry(val tool: String, val json: String, val atMs: Long)`
  - `data class SessionUi(val state: SessionState, val report: IncidentReport, val transcripts: List<String>, val toolLog: List<ToolLogEntry>, val lastLlm: LlmResult?, val lastSttMs: Long?)`
  - `class ReportSession(private val extractor: IncidentExtractor, private val merger: ReportMerger, private val repo: ReportRepository, private val clock: Clock) { val ui: StateFlow<SessionUi>; suspend fun onUtterance(text: String, sttMs: Long?); fun editField(f: Field, text: String); suspend fun submit(); fun reset() }`
    - `onUtterance`: blank text → `Error("I didn't catch that — please try again.")` with no LLM call. Otherwise Extracting → merge → log `update_report` → `FollowUpPlanner.next(missing)` → log `ask_user` and go to Asking, else Review.
    - If the same field set was already asked twice in a row → Review, with missing fields shown flagged.
    - `submit()` fills DESCRIPTION and RECOMMENDED_FOLLOW_UP as DERIVED, saves via the repo, logs `submit_report`, then Submitted.

- [ ] **Step 1: Write `chat_json_system.txt`** (exact copy; the spike may swap only the examples):
```
You fill incident reports. Read the worker's words and call the tool update_report.
Reply with ONLY one JSON object, no prose:
{"tool":"update_report","arguments":{"incident_type":null,"severity":null,"location":null,"date_time_text":null,"persons_involved":[],"equipment_asset":null,"immediate_actions":null,"witnesses":[],"evidence":{}}}
Rules:
- incident_type is one of: water_leak_flood, fire_smoke, electrical, spill_chemical, injury, illness, near_miss, property_damage, equipment_failure, security, environmental, other.
- severity is one of: none, minor, moderate, severe, critical. Only set it if the worker states it.
- Use null or [] when the words do not say it. Never guess. Never invent names.
- For every non-null field, put the exact words you used in evidence, keyed by field name.
- date_time_text is the time phrase exactly as spoken (e.g. "around 10 AM").
Example:
Worker: "Smoke coming out of the printer on floor 3, I unplugged it, Ravi saw it too."
{"tool":"update_report","arguments":{"incident_type":"fire_smoke","severity":null,"location":"floor 3","date_time_text":null,"persons_involved":[],"equipment_asset":"printer","immediate_actions":"unplugged it","witnesses":["Ravi"],"evidence":{"incident_type":"Smoke","location":"floor 3","equipment_asset":"printer","immediate_actions":"I unplugged it","witnesses":"Ravi saw it too"}}}
```
  The user message is: `Report so far: <compact JSON of filled fields>\nWorker: "<utterance>"`, with ` /no_think` appended for Qwen3 models.
- [ ] **Step 2: Write `nuextract_template.json`** — the arguments object above with NuExtract types: `"incident_type":["water_leak_flood",…,"other"]`, `"severity":["none","minor","moderate","severe","critical"]`, `"location":"verbatim-string"`, `"date_time_text":"verbatim-string"`, `"persons_involved":["verbatim-string"]`, `"equipment_asset":"verbatim-string"`, `"immediate_actions":"verbatim-string"`, `"witnesses":["verbatim-string"]`. For NUEXTRACT style the system prompt is empty, and the user message is `# Template:\n<template>\n<transcriptSoFar>`. The app sets evidence = value for verbatim fields.
- [ ] **Step 2b: Write `update_report_tool.json`** (an OpenAI-style function: name `update_report`, a description, parameters = the spec §4 arguments object with enums and `"additionalProperties": false`). Generate `update_report.gbnf` on the laptop with `python llama.cpp/examples/json_schema_to_grammar.py <schema>`, where the schema is the full `{"tool":"update_report","arguments":{…}}` object. Commit both to `assets/prompts/`.
- [ ] **Step 3: Write the failing tests:**
```kotlin
class ScriptedLlm(private vararg val outs: String) : LlmEngine { var calls = 0; override suspend fun generate(system: String, user: String, maxTokens: Int) = LlmResult(outs[calls++], 10, 20, 30.0, "fake") }
@Test fun repairRetrySucceedsOnSecondAttempt() = runTest { val o = extractor(ScriptedLlm("Sure!", """{"location":"Lobby"}""")).extract("leak in the Lobby", "leak in the Lobby", IncidentReport.empty("r",0)); assertEquals(2, o.attempts); assertEquals("Lobby", o.call!!.payload.location) }
@Test fun twoFailuresGiveNullCall() = runTest { assertNull(extractor(ScriptedLlm("no","still no")).extract("x","x",IncidentReport.empty("r",0)).call) }
// ReportSessionTest
@Test fun emptyTranscriptDoesNotCallLlm() = runTest { val llm = ScriptedLlm(); val s = session(llm); s.onUtterance("   ", 5); assertEquals(0, llm.calls); assertTrue(s.ui.value.state is SessionState.Error) }
@Test fun slideExampleAsksSeverityThenReviews() = runTest { val s = session(ScriptedLlm(SLIDE_JSON, """{"severity":"minor","evidence":{"severity":"Minor"}}"""))
  s.onUtterance(SLIDE_TEXT, 100); assertTrue(s.ui.value.state is SessionState.Asking)
  s.onUtterance("Minor, nobody was hurt", 80); assertEquals(SessionState.Review, s.ui.value.state)
  assertEquals(listOf("update_report","ask_user","update_report"), s.ui.value.toolLog.map { it.tool }) }
@Test fun secondUnansweredAskAllowsReview() = runTest { val s = session(ScriptedLlm(SLIDE_JSON, "{}", "{}")); s.onUtterance(SLIDE_TEXT, 1); s.onUtterance("I don't know", 1); s.onUtterance("no idea", 1); assertEquals(SessionState.Review, s.ui.value.state) }
@Test fun submitLogsSubmitReportAndSaves() = runTest { /* reach Review as above */ s.submit(); assertTrue(s.ui.value.state is SessionState.Submitted); assertEquals("submit_report", s.ui.value.toolLog.last().tool) }
```
  `SLIDE_TEXT` is the slide sentence and `SLIDE_JSON` is the wrapped tool call from Task 5's `p1`, both as test constants. `ReportRepository` comes from Task 7; use a temp dir.
- [ ] **Step 4: Run them and confirm they fail.** `gradlew.bat :app:testDebugUnitTest --tests "*IncidentExtractorTest" --tests "*ReportSessionTest"`. Expected: compilation failure.
- [ ] **Step 5: Implement** `LlmEngine.kt`, `PromptBuilder.kt`, `IncidentExtractor.kt` and `ReportSession.kt` per Interfaces. The session keeps the `transcripts` list; `transcriptSoFar` = joined with " ".
- [ ] **Step 6: Run them and confirm they pass.** Expected: all PASS.
- [ ] **Step 7: Commit.** `git commit -am "feat: prompts, extractor with repair retry, report session state machine"`

### Task 7: Report storage and export (owner P3)

**Files:**
- Create: `app/src/main/java/com/megathon/incident/storage/ReportRepository.kt`
- Test: `app/src/test/java/com/megathon/incident/storage/ReportRepositoryTest.kt`

**Interfaces — Produces:** `class ReportRepository(private val dir: File) { fun save(r: IncidentReport): File; fun list(): List<IncidentReport>; fun exportJson(r: IncidentReport): String }`. File name `<id>.json`. `list()` sorts by `createdAtEpochMs` descending. `exportJson` produces flat `{"incident_type":…, …}` using field wire names, for a "submit" payload.

- [ ] **Step 1: Write the failing tests:** `saveThenListReturnsReport` (temp dir, one report, `list().single() == r`), `listNewestFirst`, and `exportJsonHasFlatWireKeys` (asserts `"location":"Conference Room 2"` is in the output).
- [ ] **Step 2: Run them and confirm they fail.** `gradlew.bat :app:testDebugUnitTest --tests "*ReportRepositoryTest"`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them and confirm they pass.** Expected: 3 PASS.
- [ ] **Step 5: Commit.** `git commit -am "feat: JSON report repository and export"`

### Task 8: GenieX LLM adapter and model manager (owner P1)

**Files:**
- Create: `app/src/main/java/com/megathon/incident/llm/GenieXLlmEngine.kt`, `.../llm/ModelCatalog.kt`
- Test: `app/src/androidTest/java/com/megathon/incident/llm/GenieXLlmEngineTest.kt`

**Interfaces:**
- Consumes: `LlmEngine`, `LlmResult` (Task 6).
- Produces:
  - `data class ModelSpec(val name: String, val precision: String?, val hub: String, val runtimeId: String, val chipset: String?, val localPath: String? = null)`
  - `object ModelCatalog { val QWEN3_4B_INSTRUCT: ModelSpec; val QWEN3_1_7B: ModelSpec; val GEMMA4_4B: ModelSpec; val NUEXTRACT_2B: ModelSpec; val SELECTED: ModelSpec /* set from spike S3 */ }`. Values:
    - Qwen3-1.7B = `("unsloth/Qwen3-1.7B-GGUF","Q4_0","HUGGINGFACE","llama_cpp",null)`
    - Gemma 4 4B = the model name and hub exactly as in the geniex_chat_android catalog code (QAT Q4_0, llama_cpp)
    - NuExtract = `("NuExtract-2.0-2B-Q4_0","Q4_0","LOCALFS","llama_cpp",null,"/sdcard/Download/NuExtract-2.0-2B-Q4_0.gguf")`
    - Qwen3-4B-Instruct = the AI Hub name exactly as shown in the geniex_chat_android catalog code, hub AIHUB, runtime `qairt`, chipset `"SM8750"`
  - `class GenieXLlmEngine(private val context: Context, private val spec: ModelSpec) : LlmEngine { suspend fun ensureReady(onProgress: (Float) -> Unit); override suspend fun generate(system: String, user: String, maxTokens: Int): LlmResult }`
    - `GenieXSdk.getInstance().init(context)`
    - `ModelManagerWrapper.pullFlow(ModelPullInput(...))` (skip if `getPaths(name)` exists)
    - `LlmWrapper.builder().llmCreateInput(LlmCreateInput(model_path, config = ModelConfig(nCtx = 2048), runtime_id = spec.runtimeId, compute_unit = "npu")).build()`
    - `applyChatTemplate(arrayOf(ChatMessage("system", system), ChatMessage("user", user)), toolsJson, false)`. `toolsJson` = the contents of `assets/prompts/update_report_tool.json` (an OpenAI-style function schema named `update_report` whose parameters match spec §4).
    - collect `generateStreamFlow(formattedText, GenerationConfig(maxTokens = maxTokens, samplerConfig = SamplerConfig(temperature = 0f, grammarString = gbnf)))`, concatenating `Token.text`. `gbnf` = `assets/prompts/update_report.gbnf`, used only when `spec.runtimeId == "llama_cpp"`.
    - The constructor takes `useGrammar: Boolean = true`, so the eval can compare grammar on and off.
    - read prefill/decode/tokens-per-second from `Completed(profile)`, `backend = "NPU (${spec.runtimeId})"`
  - Load once, keep a singleton, warm up with a 4-token prompt at app start.

- [ ] **Step 1: Write the failing device test:**
```kotlin
@Test fun npuReturnsJsonForSlideExample() = runBlocking {
  val e = GenieXLlmEngine(InstrumentationRegistry.getInstrumentation().targetContext, ModelCatalog.SELECTED); e.ensureReady {}
  val r = e.generate(readAsset("prompts/chat_json_system.txt"), "Report so far: {}\nWorker: \"$SLIDE_TEXT\" /no_think", 320)
  assertTrue(r.backend.startsWith("NPU")); assertTrue(ToolCallParser.parse(r.text).isSuccess, r.text) }
@Test fun grammarForcesValidJsonOnNpu() = runBlocking { /* llama_cpp model, useGrammar=true, a deliberately chatty system prompt ("Explain your reasoning first."); assert ToolCallParser.parse(r.text).isSuccess && r.text.trimStart().startsWith("{"). If this fails, the grammar is ignored on HTP: set useGrammar=false and rely on the repair retry (spec D4). */ }
@Test fun cpuAndNpuAgreeOnLocation() = runBlocking { /* llama_cpp models only: same prompt with compute_unit "cpu" via a test-only constructor flag; assert both parsed locations contain "Conference Room 2" (silent-corruption guard, skill qidk-npu-android §4) */ }
```
- [ ] **Step 2: Run it and confirm it fails.** `gradlew.bat :app:connectedDebugAndroidTest --tests "*GenieXLlmEngineTest"`. Expected: compilation failure.
- [ ] **Step 3: Implement** per Interfaces, mirroring the calls in `ai-hub-apps/release/geniex_chat_android`. Copy the exact AI Hub model name and chipset field from there. GenieX ships its own `com.geniex.sdk.bean.ToolCall`, so import it as `GxToolCall` to avoid clashing with our `llm.ToolCall`.
- [ ] **Step 4: Run it and confirm it passes** (kit connected, model pre-downloaded). Expected: 2 PASS. Record tokens/s from the log in `docs/setup-log.md`.
- [ ] **Step 5: Commit.** `git commit -am "feat: GenieX NPU LLM engine"`

### Task 9: Audio capture and WhisperKit STT (owner P2)

**Files:**
- Create: `app/src/main/java/com/megathon/incident/asr/AudioRecorder.kt`, `.../asr/SpeechToText.kt`, `.../asr/WhisperKitStt.kt`
- Create: `app/src/androidTest/assets/test_audio/slide_example.wav` (record it: 16 kHz mono, the slide sentence)
- Test: `app/src/androidTest/java/com/megathon/incident/asr/WhisperKitSttTest.kt`

**Interfaces — Produces:**
- `data class SttResult(val text: String, val ms: Long, val backend: String)`
- `interface SpeechToText { suspend fun transcribe(pcm16k: ShortArray): SttResult }`
- `class AudioRecorder { fun start(); fun stop(): ShortArray }`. `AudioRecord` with `MediaRecorder.AudioSource.VOICE_RECOGNITION`, 16000 Hz, mono, PCM_16BIT, max 30 s (Whisper window). `stop()` trims leading/trailing frames below RMS 300 (energy gate). It returns an empty array when nothing is left.
- `class WhisperKitStt(context: Context, model: String = WhisperKit.OPENAI_TINY_EN) : SpeechToText`. Build per the WhisperKitAndroid README with the QNN backend. An empty input → `SttResult("", 0, …)` without invoking the model.

- [ ] **Step 1: Write the failing device tests:** `transcribesSlideExample` (decode the WAV, `transcribe`, assert the lowercase text contains "water leak" and "conference room"), and `emptyAudioReturnsEmptyText`.
- [ ] **Step 2: Run them and confirm they fail.** `gradlew.bat :app:connectedDebugAndroidTest --tests "*WhisperKitSttTest"`.
- [ ] **Step 3: Implement.** Reference `sm8750-edge-ai-lab/.../core/speech/WhisperKitSttEngine.kt` and `PcmVoiceRecorder.kt` for the call shapes (reference only, no licence). Write our own.
- [ ] **Step 4: Run them and confirm they pass.** Expected: 2 PASS. Record ms and backend.
- [ ] **Step 5: Commit.** `git commit -am "feat: push-to-talk recorder and WhisperKit NPU STT"`

### Task 10: TTS and spoken summary (owner P2)

**Files:**
- Create: `app/src/main/java/com/megathon/incident/tts/Speaker.kt`, `.../tts/AndroidTtsSpeaker.kt`, `.../tts/SummaryText.kt`
- Test: `app/src/test/java/com/megathon/incident/tts/SummaryTextTest.kt`

**Interfaces — Produces:**
- `interface Speaker { fun speak(text: String); val isOffline: Boolean }`
- `class AndroidTtsSpeaker(context: Context) : Speaker`. Selects a `Voice` with `!isNetworkConnectionRequired` for `Locale.US`/`Locale("en","IN")`. `isOffline` is false if none is found (the UI shows a warning).
- `object SummaryText { fun of(r: IncidentReport): String }`. Exact pattern: `"Report ready. <type words> at <location>, <time words>. Severity <severity>. Actions taken: <actions>."`, skipping the parts for unfilled fields. Type words = the wire value with `_` → space.

- [ ] **Step 1: Write the failing test:** `summaryForSlideExample` asserts it starts with `"Report ready. water leak flood at Conference Room 2"` and contains `"Severity minor."`. `summarySkipsMissingFields` asserts no "null" substring.
- [ ] **Step 2: Run it and confirm it fails.** `gradlew.bat :app:testDebugUnitTest --tests "*SummaryTextTest"`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run it and confirm it passes.** Then check by hand on the kit with the radios off that `speak()` produces audio on the kit speakers or headset.
- [ ] **Step 5: Commit.** `git commit -am "feat: offline TTS readback"`

### Task 11: UI and ViewModel wiring (owner P3)

**Files:**
- Create: `app/src/main/java/com/megathon/incident/ui/MainViewModel.kt`, `.../ui/CaptureScreen.kt`, `.../ui/ReviewScreen.kt`, `.../ui/HistoryScreen.kt`, `.../ui/MetricsPanel.kt`, `.../ui/ToolCallLog.kt`, `.../MainActivity.kt`
- Test: `app/src/test/java/com/megathon/incident/ui/MainViewModelTest.kt`

**Interfaces:**
- Consumes: `ReportSession`, `SpeechToText`, `AudioRecorder`, `Speaker`, `ReportRepository`, `GenieXLlmEngine`.
- Produces: `class MainViewModel(session, stt, recorder, speaker, connectivity: () -> Boolean) : ViewModel { val ui: StateFlow<SessionUi>; val radiosOff: StateFlow<Boolean>; fun onMicDown(); fun onMicUp(); fun onEdit(f: Field, text: String); fun onSubmit() }`
  - `onMicUp` → `recorder.stop()` → `stt.transcribe` → `session.onUtterance(text, ms)`.
  - On an `Asking` state, call `speaker.speak(question)`. On `Submitted`, call `speaker.speak(SummaryText.of(report))`.

**Screens:**
- **CaptureScreen:**
  - hold-to-talk mic button
  - live transcript
  - a 10-row field card list: chip colour by `FieldStatus` (CONFIRMED green, DERIVED blue, UNCONFIRMED amber, EMPTY grey); tap a field to see its evidence highlighted in the transcript
  - `ToolCallLog` (collapsible, monospace JSON)
  - `MetricsPanel`: STT ms + backend, LLM prefill/decode ms + tokens/s + backend, end-to-end s, and a "Radios off ✓" badge
- **ReviewScreen:** editable fields (sets USER_EDITED), missing mandatory fields flagged red, Submit button.
- **HistoryScreen:** saved reports and an export JSON share sheet.

- [ ] **Step 1: Write the failing tests** with fakes: `micUpTranscribesAndForwardsToSession` (fake STT returns the slide text, then assert the session receives it), `askingStateIsSpoken` (fake speaker records the severity question copy from Task 5), `submittedStateSpeaksSummary`.
- [ ] **Step 2: Run them and confirm they fail.** `gradlew.bat :app:testDebugUnitTest --tests "*MainViewModelTest"`.
- [ ] **Step 3: Implement the ViewModel**, then the Compose screens.
- [ ] **Step 4: Run them and confirm they pass**, then check by hand on the kit over HDMI: the full slide flow works end to end in airplane mode.
- [ ] **Step 5: Commit.** `git commit -am "feat: capture/review/history UI with tool log and metrics"`

### Task 12: On-device evaluation (owner P4)

**Files:**
- Create: `app/src/main/java/com/megathon/incident/eval/EvalScorer.kt`
- Create: `app/src/androidTest/java/com/megathon/incident/eval/OnDeviceEvalTest.kt`
- Create: `tools/laptop_eval/run_eval.py`
- Test: `app/src/test/java/com/megathon/incident/eval/EvalScorerTest.kt`

**Interfaces — Produces:**
- `data class EvalItem(val id: String, val utterance: String, val gold: Map<Field, String?>, val missingMandatory: List<Field>, val followups: List<String> = emptyList())`
- `data class EvalReport(val n: Int, val perFieldAccuracy: Map<Field, Double>, val hallucinationRate: Double, val followUpPrecision: Double, val meanLatencyMs: Double)`
- `object EvalScorer { fun score(items: List<EvalItem>, predicted: List<IncidentReport>, asked: List<List<Field>>, latenciesMs: List<Long>): EvalReport }`
  - **Field match:** case-insensitive normalised equality, or token-set overlap ≥ 0.8 for free text; exact wire equality for enums.
  - **Hallucination:** gold null but the prediction is filled (CONFIRMED).
  - **Follow-up precision:** |asked ∩ gold missing| / |asked| (1.0 when nothing was asked and nothing was missing).

- [ ] **Step 1: Write the failing tests:** `perfectPredictionScoresOne`, `filledFieldWithNullGoldIsHallucination` (rate 1/N_null_slots), and `askingOnlyMissingFieldGivesPrecisionOne`.
- [ ] **Step 2: Run them and confirm they fail.** `gradlew.bat :app:testDebugUnitTest --tests "*EvalScorerTest"`.
- [ ] **Step 3: Implement `EvalScorer`.** Then implement `OnDeviceEvalTest`: load `eval/gold_v1.jsonl`, run every item (and its follow-ups) through a real `ReportSession` with `GenieXLlmEngine(ModelCatalog.SELECTED)` and a temp repo, and write `EvalReport` as JSON to `filesDir/eval_report.json` and logcat tag `EVAL`.
- [ ] **Step 4: Run them and confirm they pass**, then run `gradlew.bat :app:connectedDebugAndroidTest --tests "*OnDeviceEvalTest"`. Expected: the report is written. **Target:** mandatory-field accuracy ≥ 0.8, hallucination ≤ 0.1. If below target, iterate on the prompt examples (`chat_json_system.txt`) and the synonym tables only.
- [ ] **Step 5: Write `tools/laptop_eval/run_eval.py`.**
  - Reads the same gold file and the prompt assets.
  - POSTs to `http://127.0.0.1:8080/v1/chat/completions` (temperature 0, max_tokens 320), extracting JSON with the same rules as `JsonExtractor` (ported to Python).
  - Prints per-field accuracy. Used in spike S3.
- [ ] **Step 6: Commit.** `git commit -am "feat: eval scorer, on-device eval, laptop eval script"`

### Task 13: Demo hardening (owner all, hours 19–21)

**Files:**
- Modify: `MainActivity.kt` (warm-up), `MainViewModel.kt` (error recovery)
- Create: `docs/demo-script.md`

- [ ] **Step 1: Warm-up.** At launch: `ensureReady` + a 4-token generation, plus 1 s of silence through STT, behind a "Loading NPU models…" splash showing progress.
- [ ] **Step 2: Error recovery.** STT empty → spoken "I didn't catch that". LLM two failures → keep the transcript, show "Couldn't extract — please rephrase", and stay in the current state. Mic permission denied → a rationale dialog.
- [ ] **Step 3: Write `docs/demo-script.md`:**
  - the HDMI and DIP checklist
  - airplane mode
  - headset on the 3.5 mm jack
  - 3 rehearsed utterances: the slide example, a fire with an injury, a near-miss
  - the correction line
  - the judge Q&A crib (from report §11)
  - backup video path
- [ ] **Step 4: Verify.** Three full rehearsals on the kit without restarting the app. Record a backup video. Fill in the eval score card from `eval_report.json`.
- [ ] **Step 5: Commit.** `git commit -am "chore: demo hardening and script"`

---

## Stretch (only after Task 13 passes at hour ≤ 19)

- **Stretch A — NPU TTS:** MeloTTS via the Voice AI TTS SDK, following `qualcomm/qidk/GenAI-Solutions/ASR-LLM-TTS/readme_assets/tts_Readme.md`. Needs a QPM login and the V79 skel files. Implement as `MeloTtsSpeaker : Speaker`.
- **Stretch B — Whisper-Small:** switch `WhisperKitStt` to `BASE_EN` or `SMALL_EN` if the eval shows ASR as the bottleneck.

## Plan B / Plan C reference

- **Plan B STT** (gated): the QIDK ASR-LLM-TTS Whisper flow (`asr_Readme.md` Case 2). AI Hub Whisper-Small for "Snapdragon 8 Elite Mobile", Voice AI ASR 2.5 `whisper-sdk.jar`, QAIRT 2.45 libs, V79 skel pushed to the app files dir. Implement as `VoiceAiStt : SpeechToText`.
- **Plan C LLM:** build llama.cpp with Hexagon (Docker toolchain), ship `libllama_server.so` + `libggml-htp-v79.so`, and launch from a foreground service via `ProcessBuilder` with `ADSP_LIBRARY_PATH`/`DSP_LIBRARY_PATH` (skill `qidk-npu-android` §2). Call it with `response_format: {"type":"json_schema",…}`. Implement as `LlamaServerLlmEngine : LlmEngine`. Nothing else changes.

## Timeline and ownership (24 h)

| Hours | P1 Runtime | P2 Speech | P3 App | P4 Brain/Eval |
|---|---|---|---|---|
| 0–1.5 | S1 | S2 | S4 / Task 1 | S3 |
| 1.5–6 | Task 8 | Task 9 | Task 7, start Task 11 | Tasks 2–5 |
| 6–9 | Task 8 tokens/s, CPU vs NPU | Task 10 | Task 11 | Task 6 (with P3) |
| **9–10** | **Integration checkpoint: slide example end-to-end on the kit. If not working, freeze scope to Tasks 1–11 minus voice correction.** | | | |
| 10–14 | Help Task 12 on the device | Mic and noise tuning | UI polish, evidence highlight | Task 12; prompt iteration |
| 14–16 | Sleep shifts / buffer | | | |
| 16–19 | Stretch A? | Stretch B? | History/export | Grow the eval to 40, final numbers |
| 19–21 | **Feature freeze → Task 13** | | | |
| 21–23 | Rehearse ×3, backup video | | | |
| 23–24 | Buffer | | | |

## Repos used (final)

| Repo | Use | Licence |
|---|---|---|
| [qualcomm/ai-hub-apps `release/geniex_chat_android`](https://github.com/qualcomm/ai-hub-apps/tree/release/geniex_chat_android) | Reference for GenieX init, pull, load and stream (Task 8) | BSD-3 |
| GenieX SDK `com.qualcomm.qti:geniex-android` ([qualcomm/GenieX](https://github.com/qualcomm/GenieX)) | LLM on the NPU | BSD-3 |
| [argmaxinc/WhisperKitAndroid](https://github.com/argmaxinc/WhisperKitAndroid) | STT on the NPU (Task 9) | MIT (models: Qualcomm AI Hub licence) |
| [AryamaVMurthy/sm8750-edge-ai-lab](https://github.com/AryamaVMurthy/sm8750-edge-ai-lab) | Proof that WhisperKit and the NPU LLM work on an SM8750 QIDK; reference for recorder, STT and TTS shapes | none: reference only |
| [qualcomm/qidk GenAI-Solutions/ASR-LLM-TTS](https://github.com/qualcomm/qidk/tree/master/GenAI-Solutions/ASR-LLM-TTS) | Plan B STT, Stretch A TTS, Genie fallback | Qualcomm |
| [ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp) | Laptop eval server; Plan C | MIT |
| [saitejaMaryala/Megathon-2k25](https://github.com/saitejaMaryala/Megathon-2k25) (local-dream) | Plan C process-launch pattern | CC BY-NC 4.0 |
| [aryamirani/vox](https://github.com/aryamirani/vox) | Pipeline state names; fence-stripping JSON parse idea (Task 2) | MIT |
| [ajinb/incident-scribe](https://github.com/ajinb/incident-scribe) | "Never guess / null" prompt rule (Task 6) | Apache-2.0 |
| [vic4code/speech-form-filling-demo](https://github.com/vic4code/speech-form-filling-demo) | Submit-gate idea (Task 6) | none: idea only |
| [konstro-build/incident-report-ai](https://github.com/konstro-build/incident-report-ai) | Injected LLM seam → `LlmEngine` + `ScriptedLlm` tests | none: idea only |
| [numind/NuExtract-2.0-2B-GGUF](https://huggingface.co/numind/NuExtract-2.0-2B-GGUF) | Candidate extractor (`Q4_0`) | MIT |
| [unsloth/Qwen3-1.7B-GGUF](https://huggingface.co/unsloth/Qwen3-1.7B-GGUF), Qwen3-4B-Instruct (AI Hub qairt) | Candidate LLMs | Apache-2.0 |
| [Simar123456/spectra-osha-sir](https://huggingface.co/datasets/Simar123456/spectra-osha-sir) | Gold-set source (Task 0b) | public-domain text (tags conflict) |
