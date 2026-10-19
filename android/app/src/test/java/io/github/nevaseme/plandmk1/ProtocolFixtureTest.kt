package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ProtocolFixtureTest {
    @Test fun crossPlatformFixturesRoundTripWithoutFieldLoss() {
        val text = javaClass.classLoader!!.getResource("protocol.json")!!.readText()
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val messages = json.decodeFromString<List<WireMessage>>(text)
        messages[0].record!!.validate(); messages[1].event!!.validate()
        assertEquals(Json.parseToJsonElement(text), Json.parseToJsonElement(json.encodeToString(messages)))
    }
}
