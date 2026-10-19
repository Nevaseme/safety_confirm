package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.residentsByUnit
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ResidenceGroupingTest {
    private fun id()=UUID.randomUUID().toString()
    @Test fun unitsUseIdsAndPendingOrRemovedHomeIsNotShownAsConfirmedMembership() {
        val a=id();val b=id();val person=id();val u=id();val v=id()
        fun building(key:String)=PlaceEvent("test",key,person,"本人",1,1,PlaceKind.BUILDING,buildingId=key,label="同じ建物名",latitude=35.0,longitude=139.0)
        fun unit(key:String,house:String)=PlaceEvent("test",key,person,"本人",2,2,PlaceKind.UNIT,buildingId=house,unitId=key,label="201号室")
        val me=Resident("test",person,1,1,"本人",35.0,139.0,"2階","北",Safety.SOS)
        val home=PlaceEvent("test",id(),person,"本人",3,3,PlaceKind.HOME,buildingId=a,unitId=u)
        val rows=listOf(building(a),building(b),unit(u,a),unit(v,b),home)
        assertEquals(listOf(me),residentsByUnit(rows,a,listOf(me)).single().people)
        assertTrue(residentsByUnit(rows,b,listOf(me)).single().people.isEmpty())
        val moved=home.copy(eventId=id(),sequence=4,buildingId=b,unitId=v)
        assertTrue(residentsByUnit(rows+moved,a,listOf(me)).single().people.isEmpty())
        assertEquals(listOf(me),residentsByUnit(rows+moved,b,listOf(me)).single().people)
        val pending=moved.copy(eventId=id(),sequence=5,unitId=id())
        assertNull(validHome(rows+moved+pending,person))
        assertTrue(residentsByUnit(rows+moved+pending,b,listOf(me)).single().people.isEmpty())
        val removed=home.copy(eventId=id(),sequence=6,buildingId=null,unitId=null)
        assertTrue(residentsByUnit(rows+removed,a,listOf(me)).single().people.isEmpty())
        assertEquals(Safety.SOS,me.safety)
    }
}
