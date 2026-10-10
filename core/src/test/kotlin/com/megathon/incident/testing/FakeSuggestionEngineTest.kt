package com.megathon.incident.testing

import com.megathon.incident.suggest.SuggestionEngine
import com.megathon.incident.suggest.SuggestionEngineContract
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeSuggestionEngineTest : SuggestionEngineContract() {

    override fun engine(): SuggestionEngine = FakeSuggestionEngine(clock)

    @Test
    fun fakeIsLabelledSimulated() {
        assertEquals(Compute.SIMULATED, engine().compute)
    }
}
