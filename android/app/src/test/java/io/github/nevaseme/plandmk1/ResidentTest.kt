package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ResidentTest {
    private val valid = Resident("demo", UUID.randomUUID().toString(), 1, 0, "住民", 35.0, 139.0, "2階", "北側")
    @Test fun acceptsJapaneseProfileAndUnreportedState() {
        valid.validate(); assertEquals(Safety.UNREPORTED, valid.safety)
    }
    @Test fun rejectsNonFiniteCoordinatesAndInvalidInput() {
        listOf(valid.copy(latitude = Double.NaN), valid.copy(longitude = 181.0), valid.copy(name = " "), valid.copy(groupId = "../x"), valid.copy(room = "あ".repeat(241))).forEach {
            assertThrows(IllegalArgumentException::class.java) { it.validate() }
        }
    }
}
