package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import org.junit.Assert.*
import org.junit.Test

class EarthquakeReportsTest {
    private val members = (1..10).map { "member-$it" }.toSet()
    private fun report(id: String, member: Int, minute: Long, group: String = "trial", training: Boolean = false) =
        EarthquakeReport(id, group, "member-$member", minute * 60_000, training)
    private fun windows(vararg reports: EarthquakeReport) = pendingEarthquakeWindows(reports.toList(), "trial", members, false)

    @Test fun agreedThresholdsIncludeMinimumAndTenPersonCap() {
        val examples = mapOf(1 to 2, 2 to 2, 3 to 2, 5 to 2, 7 to 3, 10 to 4, 27 to 9, 28 to 10, 100 to 10, Int.MAX_VALUE to 10)
        examples.forEach { (total, expected) -> assertEquals(expected, requiredEarthquakeReporters(total)) }
        assertFalse(pendingEarthquakeWindows(listOf(report("a", 1, 0)), "trial", setOf("member-1"), false).single().hasEnoughReports)
    }

    @Test fun countsPeopleOnceAndExcludesOtherGroupsAndTraining() {
        val a = report("a", 1, 0)
        val window = windows(a, a, report("b", 1, 1), report("c", 2, 2), report("d", 11, 3),
            report("e", 3, 4, group = "other"), report("f", 4, 5, training = true)).single()
        assertEquals(setOf("member-1", "member-2"), window.memberIds)
        assertEquals(4, window.requiredReporters)
        assertFalse(window.hasEnoughReports)
    }

    @Test fun thirtyMinutesIsInclusiveAndNotExtendedByLaterReports() {
        val result = windows(report("a", 1, 0), report("b", 2, 20), report("c", 3, 30), report("d", 4, 31))
        assertEquals(listOf(0L, 31 * 60_000L), result.map { it.firstReportedAt })
        assertEquals(listOf(3, 1), result.map { it.memberIds.size })
    }

    @Test fun earlierArrivalRebuildsUnformedWindowsRegardlessOfInputOrder() {
        val before = listOf(report("b", 2, 20), report("c", 3, 40))
        assertEquals(1, windows(*before.toTypedArray()).size)
        val after = before + report("a", 1, 0)
        val expected = windows(*after.toTypedArray())
        assertEquals(listOf(setOf("member-1", "member-2"), setOf("member-3")), expected.map { it.memberIds })
        assertEquals(expected, windows(*after.reversed().toTypedArray()))
    }

    @Test fun enoughDistinctMembersMakesCandidateEligible() {
        assertTrue(windows(*(1..4).map { report("r$it", it, it.toLong()) }.toTypedArray()).single().hasEnoughReports)
    }
}
