package io.github.nevaseme.plandmk1
import io.github.nevaseme.plandmk1.core.*
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class PlaceTest {
    private val author=UUID.randomUUID().toString()
    private val building=UUID.randomUUID().toString()
    private fun root()=PlaceEvent("test",building,author,"訓練者",1,1,PlaceKind.BUILDING,buildingId=building,label="訓練住宅",latitude=35.0,longitude=139.0)
    @Test fun cyclicReceivedCorrectionsRetainForwardOrderedHead() {
        val first=UUID.randomUUID().toString();val second=UUID.randomUUID().toString();val incident=UUID.randomUUID().toString()
        val x=PlaceEvent("test",first,author,"A",1,1,PlaceKind.CARE,buildingId=building,incidentId=incident,action=BuildingAction.NEEDS_HELP,scope="全棟",unresolved="上階",supersedes=second)
        val y=x.copy(eventId=second,sequence=2,supersedes=first);x.validate();y.validate()
        assertEquals(listOf(y),currentBuildingCare(listOf(x,y)))
    }
    @Test fun linksAndBuildingCareStaySeparateFromPersonalSafetyAndOtherIncidents() {
        val root=root();root.validate()
        val incident=UUID.randomUUID().toString()
        val care=PlaceEvent("test",UUID.randomUUID().toString(),author,"訓練者",2,2,PlaceKind.CARE,buildingId=building,incidentId=incident,action=BuildingAction.COMPLETED,scope="1階",unresolved="2階未確認")
        care.validate();assertEquals(0,buildingCare(listOf(root,care),building,"other").size)
        assertEquals(BuildingAction.COMPLETED,currentBuildingCare(buildingCare(listOf(root,care),building,incident)).single().action)
        try {care.copy(unresolved="").validate();fail("unknowns required")}catch(_:IllegalArgumentException){}
        val home=root.copy(eventId=UUID.randomUUID().toString(),kind=PlaceKind.HOME,sequence=3,label="",latitude=null,longitude=null)
        val unlink=home.copy(eventId=UUID.randomUUID().toString(),sequence=4,createdAt=0,buildingId=null)
        assertNull(homeFor(listOf(root,home,unlink),author)!!.buildingId)
    }
}
