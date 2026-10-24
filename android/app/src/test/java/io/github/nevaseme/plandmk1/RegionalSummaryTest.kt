package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import org.junit.Assert.*
import org.junit.Test

class RegionalSummaryTest {
    private val catalog=RegionCatalog(regions=listOf(RegionDefinition("city","試験市"),RegionDefinition("ward","試験区","city"),RegionDefinition("a","A町内会","ward"),RegionDefinition("b","B町内会","ward")))
    private fun target(n:Int)=ResponseTarget(TargetKind.REPORT,"00000000-0000-0000-0000-${n.toString().padStart(12,'0')}")
    @Test fun townCasesRollUpToCityWithEvidenceAndNoDoubleCounting() {
        val result=regionalSummary(catalog,listOf(RegionCase(target(1),listOf("a")),RegionCase(target(2),listOf("b"))))
        assertEquals(2,result.regions.single {it.regionId=="city"}.count)
        assertEquals(1,result.regions.single {it.regionId=="a"}.count)
        val repeated=regionalSummary(catalog,listOf(RegionCase(target(1),listOf("a","b","ward")),RegionCase(target(1),listOf("a")),RegionCase(target(2),listOf("b"))))
        assertEquals(setOf(target(1),target(2)),repeated.regions.single {it.regionId=="city"}.targets)
        assertEquals(2,repeated.regions.single {it.regionId=="ward"}.count)
    }
    @Test fun reassignmentAndUnassignedCasesRemainTraceable() {
        val result=regionalSummary(catalog,listOf(RegionCase(target(1),listOf("b")),RegionCase(target(2),emptyList())))
        assertEquals(0,result.regions.single {it.regionId=="a"}.count)
        assertEquals(1,result.regions.single {it.regionId=="city"}.count)
        assertEquals(setOf(target(2)),result.unassigned)
        assertEquals(catalog,RegionCatalog.decode(catalog.encode()))
    }
    @Test fun invalidHierarchyOrUnknownRegionCannotProduceMisleadingCounts() {
        val bad=listOf(catalog.copy(regions=catalog.regions+catalog.regions.first()),
            RegionCatalog(regions=listOf(RegionDefinition("a","A","missing"))),
            RegionCatalog(regions=listOf(RegionDefinition("a","A","b"),RegionDefinition("b","B","a"))))
        bad.forEach {try {regionalSummary(it,emptyList());fail()}catch(_:IllegalArgumentException){}}
        try {regionalSummary(catalog,listOf(RegionCase(target(1),listOf("unknown"))));fail()}catch(_:IllegalArgumentException){}
    }
}
