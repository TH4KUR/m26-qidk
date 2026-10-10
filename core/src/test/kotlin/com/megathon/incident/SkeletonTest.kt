package com.megathon.incident

import kotlin.test.Test
import kotlin.test.assertEquals

class SkeletonTest {
    @Test
    fun coreIsWiredUp() {
        assertEquals("incident-core", CoreInfo.NAME)
    }
}
