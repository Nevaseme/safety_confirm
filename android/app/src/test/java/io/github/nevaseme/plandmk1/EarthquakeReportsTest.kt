package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import org.junit.Assert.*
import org.junit.Test

class EarthquakeReportsTest {
    private val members = (1..10).map { "member-$it" }.toSet()
    private fun report(id: String, member: Int, minute: Long, group: String = "trial", training: Boolean = false) =
        EarthquakeReport(id, group, "member-$member", minute * 60_000, training)
    private fun groups(vararg reports: EarthquakeReport) = pendingEarthquakeGroups(reports.toList(), "trial", members, false)

    @Test fun agreedThresholdsIncludeMinimumAndTenPersonCap() {
        val examples = mapOf(1 to 2, 2 to 2, 3 to 2, 5 to 2, 7 to 3, 10 to 4, 27 to 9, 28 to 10, 100 to 10, Int.MAX_VALUE to 10)
        examples.forEach { (total, expected) -> assertEquals(expected, requiredEarthquakeReporters(total)) }
        assertFalse(pendingEarthquakeGroups(listOf(report("a", 1, 0)), "trial", setOf("member-1"), false).single().hasEnoughReports)
    }

    @Test fun countsPeopleOnceAndExcludesOtherGroupsAndTraining() {
        val a = report("a", 1, 0)
        val window = groups(a, a, report("b", 1, 1), report("c", 2, 2), report("d", 11, 3),
            report("e", 3, 4, group = "other"), report("f", 4, 5, training = true)).single()
        assertEquals(setOf("member-1", "member-2"), window.memberIds)
        assertEquals(4, window.requiredReporters)
        assertFalse(window.hasEnoughReports)
    }

    @Test fun reportsNeverExpireAndEarlierArrivalDoesNotSplitThem() {
        val before=listOf(report("b",2,20),report("c",3,60*24*30))
        val expected=groups(report("a",1,0),*before.toTypedArray()).single()
        assertEquals(setOf("member-1","member-2","member-3"),expected.memberIds)
        assertEquals(0L,expected.firstReportedAt)
        assertEquals(listOf(expected),groups(*before.reversed().toTypedArray(),report("a",1,0)))
    }

    @Test fun enoughDistinctMembersMakesCandidateEligible() {
        assertTrue(groups(*(1..4).map { report("r$it", it, it.toLong()) }.toTypedArray()).single().hasEnoughReports)
    }
}
