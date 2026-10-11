package com.megathon.incident.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `extract_v12.txt` is v11 plus exactly two additions (a presence rule for witnesses, one worked example). It is not the
 * shipped default; this test keeps it from drifting from v11 in any other way, so a measured difference is that change.
 */
class PromptV12Test {
    private fun prompt(name: String) = PromptV12Test::class.java.getResourceAsStream("/prompts/$name")!!
        .bufferedReader(Charsets.UTF_8).readText().replace("\r\n", "\n")

    private val PRESENCE_SENTENCE = " A person who was with the speaker, nearby or present when it happened (\"my friend Rahul was with me\") " +
        "is a witness too, not persons_involved, unless the worker says they were hurt or affected."

    private val v11 = prompt("extract_v11.txt").trimEnd('\n').lines()
    private val v12 = prompt("extract_v12.txt").trimEnd('\n').lines()

    @Test fun everyV11LineIsInV12InOrderExceptTheOneRuleThatWasExtended() {
        val changed = v11.indices.filter { v11[it] != v12[it] }
        assertEquals(1, changed.size, "only the persons/witnesses rule may differ: $changed")
        val old = v11[changed.single()]
        assertTrue(old.contains("persons_involved:") && old.contains("witnesses:"))
        assertEquals(old, v12[changed.single()].replace(PRESENCE_SENTENCE, ""), "removing the one added sentence must give v11 back")
    }

    @Test fun theOnlyAdditionsAreThePresenceSentenceAndOneWorkedExample() {
        assertEquals(v11.size + 2, v12.size)
        val tail = v12.takeLast(2)
        assertTrue(tail[0].startsWith("Worker: ") && tail[0].contains("was with me"), tail[0])
        assertTrue(tail[1].startsWith("""{"tool":"update_report","arguments":{""") && tail[1].contains(""""witnesses":["Rahul"]"""), tail[1])
    }

    @Test fun thePresenceRuleSaysUnlessHurt() {
        val rule = v12[v11.indices.first { v11[it] != v12[it] }]
        assertTrue("with the speaker" in rule && "unless the worker says they were hurt or affected" in rule, rule)
    }
}
