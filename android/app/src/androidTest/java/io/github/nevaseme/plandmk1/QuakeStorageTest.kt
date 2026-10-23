package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class QuakeStorageTest {
    @Test fun reportFormationCancellationAndAdminEndSurviveReopen()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="quake-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,PlanDatabase::class.java,name).build()
        var db=open()
        val a=UUID.randomUUID().toString();val b=UUID.randomUUID().toString()
        val roster=MemberRoster(groupId="trial",members=listOf(GroupMember(a,"管理者",MemberRole.ADMIN,listOf(a)),GroupMember(b,"担当B",MemberRole.MEMBER,listOf(b))))
        fun report(actor:String,time:Long)=QuakeEvent("trial",UUID.randomUUID().toString(),actor,time,QuakeAction.REPORT,false,title="地震",area="地域")
        try {
            var store=QuakeStore(db);store.importRoster(roster.encode(),a)
            val first=report(a,1);val second=report(b,2)
            assertTrue(store.command(first).isEmpty())
            assertEquals(1,store.command(second).size)
            val before=store.events("trial")
            val cancel=QuakeEvent("trial",UUID.randomUUID().toString(),b,1,QuakeAction.CANCEL,false,targetId=second.eventId)
            try {store.command(cancel);fail("already formed")}catch(_:IllegalArgumentException){}
            assertEquals(before,store.events("trial"))
            store.merge(cancel)
            assertFalse(store.states("trial").single().closed)
            val end=QuakeEvent("trial",UUID.randomUUID().toString(),a,3,QuakeAction.END,false,targetId=first.eventId)
            try {store.command(end.copy(actorDeviceId=b));fail("admin only")}catch(_:IllegalArgumentException){}
            store.command(end)
            db.close();db=open();store=QuakeStore(db)
            assertEquals(roster,store.roster("trial"))
            assertTrue(store.states("trial").single().closed)
            assertFalse(store.merge(before.single {it.action==QuakeAction.FORM}))
            assertTrue(store.states("trial").single().closed)
            assertTrue(store.states("other").isEmpty())
        } finally {db.close();context.deleteDatabase(name)}
    }
}
