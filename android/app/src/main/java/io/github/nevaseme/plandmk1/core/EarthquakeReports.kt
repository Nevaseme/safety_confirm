package io.github.nevaseme.plandmk1.core

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

data class EarthquakeReportGroup(
    val firstReportedAt: Long,
    val reportIds: List<String>,
    val memberIds: Set<String>,
    val requiredReporters: Int,
) {
    val hasEnoughReports: Boolean get() = memberIds.size >= requiredReporters
}

/** Rebuilds unformed candidates from verified, uncancelled reports. Persisted formations are handled separately. */
fun pendingEarthquakeGroups(
    reports: Collection<EarthquakeReport>,
    groupId: String,
    approvedMemberIds: Set<String>,
    training: Boolean,
): List<EarthquakeReportGroup> {
    val eligible = reports.filter { it.groupId == groupId && it.training == training && it.memberId in approvedMemberIds }
    require(eligible.all { it.operatedAt >= 0 && it.reportId.isNotBlank() })
    val unique = eligible.groupBy { it.reportId }.values.map { copies ->
        require(copies.all { it == copies.first() }) { "同じ報告IDに異なる内容があります" }
        copies.first()
    }.sortedWith(compareBy<EarthquakeReport> { it.operatedAt }.thenBy { it.reportId })
    if(unique.isEmpty())return emptyList()
    return listOf(EarthquakeReportGroup(unique.first().operatedAt, unique.map {it.reportId}, unique.map {it.memberId}.toSet(),requiredEarthquakeReporters(approvedMemberIds.size)))
}
