package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.core.*
import org.junit.Assert.*
import org.junit.Test

class SituationPresentationTest {
    private val now = 1_800_000_000_000L
    private fun person(id: String, state: MapSafety = MapSafety.SOS, age: Long = 0) =
        MapPerson(id, "試験の名前", state, MapPoint(32.79, 130.819), now - age)

    @Test fun lastPositionExpiresByAcquisitionRatherThanReception() {
        assertTrue(person("a", age = 3 * DAY).hasPosition(now))
        assertFalse(person("b", age = 3 * DAY + 1).hasPosition(now))
        assertFalse(person("c").copy(point = null).hasPosition(now))
    }

    @Test fun safeAndRescuedHaveNoPublicName() {
        assertEquals("無事の人", person("a", MapSafety.SAFE).publicName)
        assertEquals("救助済みの人", person("b", MapSafety.RESCUED).publicName)
        assertEquals("試験の名前", person("c").publicName)
    }

    @Test fun markerCacheChangesOnlyWhenTheVisibleAgeOrStateChanges() {
        val p = person("a")
        assertEquals(personMarkerKey(p, now), personMarkerKey(p, now + 5 * 60_000))
        assertNotEquals(personMarkerKey(p, now + 5 * 60_000), personMarkerKey(p, now + 6 * 60_000))
        assertEquals(personMarkerKey(p, now + 60 * 60_000), personMarkerKey(p, now + 119 * 60_000))
        assertNotEquals(personMarkerKey(p, now + 119 * 60_000), personMarkerKey(p, now + 120 * 60_000))
        assertNotEquals(personMarkerKey(p, now), personMarkerKey(p.copy(state = MapSafety.RESCUED), now))
    }

    @Test fun zoomSplitsClustersAndSafeFilterDoesNotRemoveSOS() {
        val people = listOf(person("a"), person("b", MapSafety.SAFE).copy(point = MapPoint(32.7903, 130.8193)))
        assertEquals(1, mapClusters(people, 14.0, true, now).size)
        assertEquals(2, mapClusters(people, 16.0, true, now).size)
        assertEquals(listOf("a"), mapClusters(people, 14.0, false, now).flatMap { it.people }.map { it.id })
        assertTrue(mapClusters(listOf(person("old", age = 4 * DAY)), 14.0, true, now).isEmpty())
    }

    @Test fun unansweredMustAnswerBeforeMapButExistingAnswerSurvivesRecreation() {
        assertEquals(MainDestination.HOME, mainDestination(false, false))
        assertEquals(MainDestination.ANSWER, mainDestination(true, false))
        assertEquals(MainDestination.MAP, mainDestination(true, true))
    }

    @Test fun markerReuseKeepsUnchangedGroupsEvenWhenOrderOrStatusChanges() {
        val a = person("a"); val b = person("b"); val c = person("c")
        val previous = listOf(MapCluster(listOf(a, b)), MapCluster(listOf(c)))
        val next = listOf(MapCluster(listOf(c.copy(state = MapSafety.RESCUED))), MapCluster(listOf(b, a)))
        assertEquals(listOf(1, 0), reuseClusterMarkers(previous, next))
    }

    @Test fun markerReuseKeepsSurvivorsAcrossMergeAndSplit() {
        val a = person("a"); val b = person("b"); val c = person("c")
        val merged = listOf(MapCluster(listOf(a, b)), MapCluster(listOf(c)))
        val split = listOf(MapCluster(listOf(a)), MapCluster(listOf(b)), MapCluster(listOf(c)))
        assertEquals(listOf(0, null, 1), reuseClusterMarkers(merged, split))
        assertEquals(listOf(0, 2), reuseClusterMarkers(split, merged))
    }

    @Test fun markerReuseDoesNotAssignAnUnrelatedPersonOrReuseAnIndexTwice() {
        val previous = listOf(MapCluster(listOf(person("a"))))
        assertEquals(listOf(null, 0), reuseClusterMarkers(previous,
            listOf(MapCluster(listOf(person("new"))), MapCluster(listOf(person("a"))))))
        assertEquals(emptyList<Int?>(), reuseClusterMarkers(previous, emptyList()))
    }

    @Test fun tallClusterBubblesMergeWithoutLosingPeopleAndStillSplitAtStreetZoom() {
        val people = MapSafety.entries.map { person(it.name, it).copy(point = MapPoint(0.0, 0.0)) } +
            person("near").copy(point = MapPoint(.00386, 0.0)) +
            person("far").copy(point = MapPoint(.1, 0.0))
        val clusters = mapClusters(people, 14.0, true, now)
        assertEquals(listOf(1, 5), clusters.map { it.people.size }.sorted())
        assertEquals(people.map { it.id }.sorted(), clusters.flatMap { it.people }.map { it.id }.sorted())
        assertEquals(6, mapClusters(people, 16.0, true, now).size)
    }

    @Test fun rescueRemainsEffectiveWithAnOlderCheckingRecordAndRevocationHistory() {
        val resident = Resident("g", "a", 1, 10, "対象者", 32.79, 130.819, "1階", "寝室", Safety.SOS)
        val events = listOf(
            CareEvent("g", "old", "a", "b", "確認者", 11, CareAction.CHECKING),
            CareEvent("g", "revoked", "a", "c", "別の人", 12, CareAction.REVOKED, supersedes = "missing"),
            CareEvent("g", "rescue", "a", "d", "救助者", 20, CareAction.RESCUED),
        )
        assertEquals(MapSafety.RESCUED, residentMapState(resident, events))
        assertEquals(MapSafety.SOS, residentMapState(resident.copy(updatedAt = 21), events))
        assertEquals("メンバー", publicRecordAuthor("a", listOf(resident), events))
        assertFalse(residentMapPerson(resident, emptyList(), events).hasPosition(now))
    }

    @Test fun buildingAssociationUsesFootprintAndExcludesAdjacentHomeAndCourtyard() {
        val outline = listOf(MapPoint(0.0, 0.0), MapPoint(0.0, 1.0), MapPoint(1.0, 1.0), MapPoint(1.0, 0.0))
        val courtyard = listOf(MapPoint(.4, .4), MapPoint(.4, .6), MapPoint(.6, .6), MapPoint(.6, .4))
        assertTrue(insideFootprint(MapPoint(.2, .2), listOf(outline, courtyard)))
        assertFalse(insideFootprint(MapPoint(.5, .5), listOf(outline, courtyard)))
        assertFalse(insideFootprint(MapPoint(.5, 1.01), listOf(outline, courtyard)))
    }
}
