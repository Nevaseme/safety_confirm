package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.*
import org.junit.Assert.*
import org.junit.Test

class LocalSituationTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val now = 1_800_000_000_000L
    private fun position() = PhonePosition("test", id, 1, now, 32.79, 130.81, 12.5)
    private val resident get() = Resident("test", id, 1, now, "住民", 35.0, 139.0, "2階", "北の寝室", Safety.SOS)

    @Test fun positionIsSeparateFromRegisteredHomeAndRemainsUntilReplaced() {
        val position = position()
        val person = residentMapPerson(resident, emptyList(), emptyList(), position)
        assertEquals(MapPoint(32.79, 130.81), person.point)
        assertEquals(MapPoint(35.0, 139.0), person.home!!.point)
        assertTrue(person.hasPosition(now + 3 * DAY))
        assertTrue(person.hasPosition(now + 3 * DAY + 1))
        assertTrue(person.hasPosition(now + 30 * DAY))
        assertEquals(now, person.acquiredAt)
        assertFalse(residentMapPerson(resident, emptyList(), emptyList()).hasPosition(now))
    }

    @Test fun unrelatedPositionCannotAppearOnAnotherResidentsMarker() {
        val wrong = position().copy(originId = "00000000-0000-0000-0000-000000000002")
        assertNull(residentMapPerson(resident, emptyList(), emptyList(), wrong).point)
        assertNull(residentMapPerson(resident, emptyList(), emptyList(), position().copy(groupId = "other")).point)
    }

    @Test fun coordinatesAndAccuracyMustBeFiniteAndValid() {
        position().validate()
        listOf(position().copy(latitude = Double.NaN), position().copy(longitude = 181.0),
            position().copy(accuracyMeters = -1.0), position().copy(accuracyMeters = Double.POSITIVE_INFINITY),
            position().copy(revision = 0), position().copy(acquiredAt = -1)).forEach {
            assertThrows(IllegalArgumentException::class.java) { it.validate() }
        }
    }

    @Test fun positionUpdatesUseRevisionAndNeverRefreshTimeOnDuplicates() {
        val old = position()
        assertFalse(shouldReplacePosition(old, old))
        assertFalse(shouldReplacePosition(old.copy(revision = 2), old.copy(acquiredAt = now + 1000)))
        assertTrue(shouldReplacePosition(old, old.copy(revision = 2, acquiredAt = now - 1000)))
        assertThrows(IllegalArgumentException::class.java) { shouldReplacePosition(old, old.copy(latitude = 0.0)) }
    }

    @Test fun locationRequestIntervalsMatchTheAgreedSettings() {
        assertEquals(300_000L, positionRequestInterval(false))
        assertEquals(60_000L, positionRequestInterval(true))
    }

    @Test fun memoBelongsToAnAnswerAndAllowsExactlyFiveHundredCharacters() {
        val memo = SosMemo("test", id, id, id, 1, now, "助".repeat(500))
        memo.validate()
        memo.copy(text = "").validate()
        assertThrows(IllegalArgumentException::class.java) { memo.copy(text = "助".repeat(501)).validate() }
    }

    @Test fun filtersUseOneSourceAndAreaMembershipUpdatesWithoutStaleRows() {
        val people = MapSafety.entries.mapIndexed { index, state ->
            MapPerson("$index", "住民$index", state, MapPoint(32.79, 130.81), now)
        } + MapPerson("unknown", "不明者", MapSafety.SOS)
        assertEquals(5, situationList(people, null, false, null, now).size)
        assertEquals(listOf("0", "unknown"), situationList(people, MapSafety.SOS, false, null, now).map { it.id })
        assertEquals(listOf("unknown"), situationList(people, null, true, null, now).map { it.id })
        assertEquals(listOf("0", "1"), situationList(people, null, false, setOf("0", "1", "gone"), now).map { it.id })
        assertEquals(MapSafety.RESCUED, situationList(people.map { if (it.id == "0") it.copy(state = MapSafety.RESCUED) else it }, null, false, setOf("0"), now).single().state)
    }
}
