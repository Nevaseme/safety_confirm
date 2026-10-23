package io.github.nevaseme.plandmk1.core

private const val REPORT_WINDOW_MS = 30 * 60_000L

fun requiredEarthquakeReporters(memberCount: Int): Int {
    require(memberCount >= 0)
    return ((memberCount.toLong() + 2) / 3).coerceIn(2, 10).toInt()
}

data class EarthquakeReport(
    val reportId: String,
    val groupId: String,
    val memberId: String,
    val operatedAt: Long,
    val training: Boolean,
)

data class EarthquakeReportWindow(
    val firstReportedAt: Long,
    val reportIds: List<String>,
    val memberIds: Set<String>,
    val requiredReporters: Int,
) {
    val hasEnoughReports: Boolean get() = memberIds.size >= requiredReporters
}

/** Rebuilds unformed candidates from verified, uncancelled reports. Persisted formations are handled separately. */
fun pendingEarthquakeWindows(
    reports: Collection<EarthquakeReport>,
    groupId: String,
    approvedMemberIds: Set<String>,
    training: Boolean,
): List<EarthquakeReportWindow> {
    val eligible = reports.filter { it.groupId == groupId && it.training == training && it.memberId in approvedMemberIds }
    require(eligible.all { it.operatedAt >= 0 && it.reportId.isNotBlank() })
    val unique = eligible.groupBy { it.reportId }.values.map { copies ->
        require(copies.all { it == copies.first() }) { "同じ報告IDに異なる内容があります" }
        copies.first()
    }.sortedWith(compareBy<EarthquakeReport> { it.operatedAt }.thenBy { it.reportId })
    val batches = mutableListOf<MutableList<EarthquakeReport>>()
    unique.forEach { report ->
        val current = batches.lastOrNull()
        if (current == null || report.operatedAt - current.first().operatedAt > REPORT_WINDOW_MS) {
            batches.add(mutableListOf(report))
        } else current.add(report)
    }
    val needed = requiredEarthquakeReporters(approvedMemberIds.size)
    return batches.map { batch ->
        EarthquakeReportWindow(batch.first().operatedAt, batch.map { it.reportId }, batch.map { it.memberId }.toSet(), needed)
    }
}
