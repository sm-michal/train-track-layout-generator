package org.example.legotrack

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SerializationTest {
    @Test
    fun testGenerationRequestSerialization() {
        val request = GenerationRequest(
            inventory = mapOf("straight" to 10, "curve_r40" to 16),
            maxSolutions = 5,
            outputDir = "custom_output"
        )
        val json = Json.encodeToString(GenerationRequest.serializer(), request)
        val decoded = Json.decodeFromString<GenerationRequest>(json)

        assertEquals(request, decoded)
    }
}
