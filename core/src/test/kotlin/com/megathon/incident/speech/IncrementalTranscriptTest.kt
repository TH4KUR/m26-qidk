package com.megathon.incident.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncrementalTranscriptTest {
    @Test
    fun windowsWithoutSharedAudioAreJoinedAndRepeatedWordsStay() {
        val t = IncrementalTranscript()
        t.addWindow("it was very very hot", overlapped = false)
        t.addWindow("very hot indeed", overlapped = false)
        assertEquals("it was very very hot very hot indeed", t.text)
        assertFalse(t.ambiguous)
    }

    @Test
    fun overlappingWindowsDoNotDuplicateTheSharedWords() {
        val t = IncrementalTranscript()
        t.addWindow("the pump is leaking in room", overlapped = false)
        t.addWindow("in room three near the lift", overlapped = true, maxOverlapWords = 6)
        assertEquals("the pump is leaking in room three near the lift", t.text)
        assertFalse(t.ambiguous)
    }

    @Test
    fun aWordClippedAtTheWindowEdgeIsRepairedByTheLaterWindow() {
        val t = IncrementalTranscript()
        t.addWindow("smoke in the corridor near room thr", overlapped = false)
        t.addWindow("near room three on the second floor", overlapped = true, maxOverlapWords = 6)
        assertEquals("smoke in the corridor near room three on the second floor", t.text)
    }

    @Test
    fun aRepeatedPhraseInsideAWindowIsNotCollapsed() {
        val t = IncrementalTranscript()
        t.addWindow("no no no the door is stuck", overlapped = false)
        t.addWindow("is stuck and no no one can leave", overlapped = true, maxOverlapWords = 6)
        assertEquals("no no no the door is stuck and no no one can leave", t.text)
    }

    @Test
    fun noMatchInASharedRegionKeepsEveryWordAndFlagsTheAmbiguity() {
        val t = IncrementalTranscript()
        t.addWindow("a fire in lab one", overlapped = false)
        t.addWindow("fine at lab won there is smoke", overlapped = true, maxOverlapWords = 6)
        assertTrue(t.ambiguous)
        assertEquals("a fire in lab one fine at lab won there is smoke", t.text)
    }

    @Test
    fun revisionChangesOnlyWhenTheTextChanges() {
        val t = IncrementalTranscript()
        assertEquals(0, t.revision)
        assertTrue(t.addWindow("hello there", overlapped = false))
        assertEquals(1, t.revision)
        assertFalse(t.addWindow("   ", overlapped = false))
        assertEquals(1, t.revision)
    }

    @Test
    fun theUnsettledTailIsReportedSeparatelyFromTheStablePrefix() {
        val t = IncrementalTranscript()
        t.addWindow("one two three four five six", overlapped = false, tailWords = 2)
        assertEquals("one two three four", t.stableText)
        assertEquals("five six", t.tailText)
        t.addWindow("five six seven eight", overlapped = true, maxOverlapWords = 4, tailWords = 1)
        assertEquals("one two three four five six seven", t.stableText)
        assertEquals("eight", t.tailText)
        t.finish()
        assertEquals("", t.tailText)
        assertEquals("one two three four five six seven eight", t.stableText)
    }
}
