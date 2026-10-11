package com.megathon.incident.session

import com.megathon.incident.domain.IncidentReport
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Which prompt ships is a measured decision, so the test pins it by content hash: changing the default (or editing either
 * file) must be a deliberate act that changes a hash here.
 *
 * v11 is the default because it passed the pre-registered dev gate on the kit (recall 100/135 = +9 points over the v8 control,
 * precision 94.3%, hallucination 0/81, valid after retry 97%, median turn 4.9 s). That choice is dev-informed: v11 was written
 * after the dev misses were known, and the held-out 13 is the only unbiased check.
 */
class DefaultPromptTest {
    private fun resource(name: String): ByteArray {
        val stream = PromptBuilder::class.java.getResourceAsStream("/prompts/$name")
        assertNotNull(stream, "missing resource /prompts/$name")
        return stream.use { it.readBytes() }
    }

    private fun sha256Prefix(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)

    @Test fun theShippedDefaultPromptIsV11() {
        val v11 = resource("extract_v11.txt")
        assertEquals("1ae9711fdb3f0bdb", sha256Prefix(v11), "extract_v11.txt must be byte-for-byte the file that was measured")
        val system = PromptBuilder.default().build("There was a leak.", IncidentReport.empty("r1", 0)).system
        assertEquals(String(v11, Charsets.UTF_8).trim(), system, "the app default must be the v11 text")
    }

    @Test fun v8StaysInTheTreeUnchangedAsTheMeasuredReference() {
        assertEquals("7330201b559b88ad", sha256Prefix(resource("extract_v8.txt")))
    }
}
