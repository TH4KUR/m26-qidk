package com.megathon.incident.testing

import com.megathon.incident.llm.LlmEngine

class FakeLlmEngineContractTest : LlmEngineContractTest() {
    override fun create(): LlmEngine = FakeLlmEngine()
}
