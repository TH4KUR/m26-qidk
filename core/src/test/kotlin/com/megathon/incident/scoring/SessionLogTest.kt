package com.megathon.incident.scoring

import com.megathon.incident.session.StageRecord
import com.megathon.incident.testing.Compute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionLogTest {
    private fun line(capture: Int?, words: Int = 5) = AudioScoreLine(capture = capture, words = words, engine = "Whisper small", compute = "NPU", processingMs = 900, realTimeFactor = 4.4)

    private fun rec(id: String, at: Long, stages: List<LogStage>, audio: List<AudioScoreLine> = emptyList()) =
        SessionLogRecord(atEpochMs = at, reportId = id, stages = stages, audio = audio)

    private fun st(stage: String, compute: String, ms: Long?, engine: String = "e") = LogStage(stage, engine, compute, ms)

    // ---- the line format -------------------------------------------------------------------------------------------------

    @Test
    fun aRecordIsOneLineAndReadsBackIdentical() {
        val r = rec("report-1", 1000, listOf(st("Hear", "NPU", 900), st("Read", "NPU", 2100)), listOf(line(88)))
        val text = SessionLogCodec.encode(r)
        assertFalse(text.contains('\n'))
        assertEquals(listOf(r), SessionLogCodec.parse(text))
    }

    @Test
    fun aBadOrBlankLineIsSkippedNeverFatal() {
        val a = rec("a", 1, listOf(st("Hear", "CPU", 10)))
        val b = rec("b", 2, listOf(st("Hear", "CPU", 20)))
        val text = SessionLogCodec.encode(a) + "\n\n{not json\n" + SessionLogCodec.encode(b) + "\n"
        assertEquals(listOf("a", "b"), SessionLogCodec.parse(text).map { it.reportId })
    }

    @Test
    fun aLineWithFieldsFromALaterVersionStillReads() {
        val text = """{"v":2,"atEpochMs":5,"reportId":"x","stages":[],"audio":[],"somethingNew":1}"""
        assertEquals("x", SessionLogCodec.parse(text).single().reportId)
    }

    @Test
    fun theRecordIsBuiltFromTheRealStageRecordsAndScores() {
        val stages = listOf(StageRecord("Hear", "Whisper small", Compute.NPU, 900L), StageRecord("Speak", "Piper", Compute.CPU, null))
        val score = AudioScore.of(ShortArray(16_000 * 3) { 3000 }, 16_000, RecognitionInput("Whisper small", Compute.NPU, 900L, "the lift is stuck"), null)
        val r = SessionLogRecord.of("report-9", 42L, stages, listOf(score))
        assertEquals(listOf(LogStage("Hear", "Whisper small", "NPU", 900L), LogStage("Speak", "Piper", "CPU", null)), r.stages)
        assertEquals(score.toLine(), r.audio.single())
        assertEquals(4, r.audio.single().words)
    }

    @Test
    fun theLineHoldsNoTranscriptText() {
        val score = AudioScore.of(ShortArray(16_000) { 3000 }, 16_000, RecognitionInput("e", Compute.CPU, 100L, "Priya Sharma reported a leak in Block C"), PriorityExplainer.default().explain("Priya Sharma reported a leak in Block C", emptyList()))
        val text = SessionLogCodec.encode(SessionLogRecord.of("r", 1L, emptyList(), listOf(score)))
        for (w in listOf("Priya", "Sharma", "leak", "Block")) assertFalse(text.contains(w), w)
    }

    // ---- the aggregate ---------------------------------------------------------------------------------------------------

    @Test
    fun noRecordsGivesAnEmptyHonestSummary() {
        val s = SessionLogAggregator.aggregate(emptyList())
        assertEquals(0, s.reports)
        assertNull(s.averageAudioScore)
        assertTrue(s.recent.isEmpty())
        assertTrue(s.stageMedians.all { it.medianMs == null && it.count == 0 })
        assertTrue(s.computeMix.isEmpty())
    }

    @Test
    fun mediansArePooledOverEveryTimedStageAndAnEvenCountAveragesTheMiddlePair() {
        val s = SessionLogAggregator.aggregate(
            listOf(
                rec("a", 1, listOf(st("Hear", "NPU", 1000), st("Read", "NPU", 3000))),
                rec("b", 2, listOf(st("Hear", "NPU", 3000), st("Read", "CPU", 5000), st("Tags", "NPU", 200))),
                rec("c", 3, listOf(st("Hear", "CPU", 2000), st("Hear", "CPU", null))),
            ),
        )
        assertEquals(3, s.reports)
        val hear = s.stageMedians.single { it.stage == "Hear" }
        assertEquals(3, hear.count)        // the stage with no time is not counted
        assertEquals(2000L, hear.medianMs) // 1000, 2000, 3000
        val read = s.stageMedians.single { it.stage == "Read" }
        assertEquals(2, read.count)
        assertEquals(4000L, read.medianMs) // (3000 + 5000) / 2
        assertEquals(200L, s.stageMedians.single { it.stage == "Tags" }.medianMs)
        assertNull(s.stageMedians.single { it.stage == "Speak" }.medianMs)
    }

    @Test
    fun theComputeMixCountsEveryStageRecordWhereItReallyRan() {
        val s = SessionLogAggregator.aggregate(
            listOf(rec("a", 1, listOf(st("Hear", "NPU", 1), st("Read", "NPU", 1), st("Speak", "CPU", 1), st("Hear", "SIMULATED", 1)))),
        )
        assertEquals(listOf(ComputeCount("NPU", 2), ComputeCount("CPU", 1), ComputeCount("SIMULATED", 1)), s.computeMix)
    }

    @Test
    fun theAverageAudioScoreIsOverReportsThatWereScoredAndAReportAveragesItsTurns() {
        val s = SessionLogAggregator.aggregate(
            listOf(
                rec("a", 1, emptyList(), listOf(line(80), line(100))), // 90
                rec("b", 2, emptyList(), listOf(line(60))),            // 60
                rec("c", 3, emptyList(), emptyList()),                 // not scored
                rec("d", 4, emptyList(), listOf(line(null))),          // no audio was measured
            ),
        )
        assertEquals(75.0, s.averageAudioScore!!, 1e-9)
        assertEquals(2, s.scoredReports)
        assertEquals(4, s.reports)
    }

    @Test
    fun theSameReportSavedTwiceCountsOnceAndTheLaterLineWins() {
        val s = SessionLogAggregator.aggregate(
            listOf(
                rec("a", 1, listOf(st("Hear", "NPU", 1000)), listOf(line(50))),
                rec("a", 9, listOf(st("Hear", "NPU", 3000)), listOf(line(70))),
            ),
        )
        assertEquals(1, s.reports)
        assertEquals(3000L, s.stageMedians.single { it.stage == "Hear" }.medianMs)
        assertEquals(70.0, s.averageAudioScore!!, 1e-9)
    }

    @Test
    fun recentRowsAreNewestFirstLimitedAndOneLineEach() {
        val records = (1..15).map { rec("r$it", it * 100L, listOf(st("Hear", "NPU", it * 10L), st("Read", "CPU", it * 20L)), listOf(line(it))) }
        val s = SessionLogAggregator.aggregate(records, lastN = 10)
        assertEquals(10, s.recent.size)
        assertEquals("r15", s.recent.first().reportId)
        assertEquals("r6", s.recent.last().reportId)
        val top = s.recent.first()
        assertEquals(15, top.audioScore)
        assertEquals(150L, top.hearMs)
        assertEquals(300L, top.readMs)
        assertEquals("NPU+CPU", top.compute)
    }

    @Test
    fun aRowWithNoTimesSaysSoWithNulls() {
        val s = SessionLogAggregator.aggregate(listOf(rec("a", 1, emptyList())))
        val row = s.recent.single()
        assertNull(row.audioScore)
        assertNull(row.hearMs)
        assertNull(row.readMs)
        assertEquals("", row.compute)
    }
}
