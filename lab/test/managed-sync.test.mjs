import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {readFile} from 'node:fs/promises';
import {Store,SyncEngine} from '../web/sync.mjs';
import {quakeStates,formationCandidates,responseHeads,validateQuakeCommand,managedIncidentOptions} from '../web/managed-records.mjs';
const id=()=>crypto.randomUUID(),group='trial';
const members=Array.from({length:4},(_,i)=>{const memberId=id();return {memberId,activityName:'確認'+i,role:i===0?'ADMIN':'MEMBER',deviceIds:[memberId]};});
const roster={version:1,groupId:group,members},ids=members.map(m=>m.memberId);
const report=(actor,at)=>({groupId:group,eventId:id(),actorDeviceId:actor,operatedAt:at,action:'REPORT',training:false,title:'地震',area:'地域'});
const response=(actor,incident,change,eventId=id(),replaces=[])=>({groupId:group,eventId,actorDeviceId:actor,operatedAt:200,sequence:1,incidentId:incident,change,replaces});
const open=()=>Store.open(id(),indexedDB);
test('oversized response is rejected before saving and one send failure does not block the next record',async()=>{
 const s=await open();await s.importRoster(roster,ids[0]);const sent=[],errors=[];
 const huge=response(ids[0],null,{type:'AVAILABILITY',state:'AVAILABLE'});huge.change={type:'PROGRESS',target:{kind:'PERSON',id:ids[1]},status:'DONE',result:'あ'.repeat(9000)};huge.incidentId=id();
 await assert.rejects(s.mergeResponse(huge));assert.equal((await s.responseEvents(group)).length,0);
 const first=response(ids[0],null,{type:'AVAILABILITY',state:'AVAILABLE'}),second={...response(ids[0],null,{type:'AVAILABILITY',state:'WORKING'}),sequence:2};
 await s.mergeResponse(first);await s.mergeResponse(second);
 const node=new SyncEngine(group,ids[0],s,(_,m)=>{if(m.response?.eventId===first.eventId)throw Error('send failure');sent.push(m);},e=>errors.push(e));await node.connected('B');sent.length=0;
 await node.receive('B',{version:6,groupId:group,type:'RESPONSE_REQUEST',entries:[first,second].map(e=>({originId:e.eventId,revision:1}))});
 assert.ok(sent.some(m=>m.response?.eventId===second.eventId));assert.equal(errors.length,1);s.close();
});
test('shared Kotlin and JavaScript wire fixture loads all response kinds',async()=>{
 const data=JSON.parse(await readFile(new URL('../../android/app/src/test/resources/managed-v6.json',import.meta.url),'utf8'));
 const s=await open();await s.importRoster(data.roster,data.roster.members[0].deviceIds[0]);
 for(const event of data.quakes.toReversed())await s.mergeQuake(event);
 for(const event of data.responses.toReversed())await s.mergeResponse(event);
 assert.equal(quakeStates(data.roster,await s.quakeEvents('trial')).length,1);
 assert.equal((await s.responseHeads('trial')).length,6);s.close();
});

test('isolated votes form after synchronization and relay durable work after original sender leaves',async()=>{
 const stores=await Promise.all(ids.slice(0,3).map(open)),queue=[],errors=[];
 for(let i=0;i<stores.length;i++)await stores[i].importRoster(roster,ids[i]);
 const nodes=stores.map((s,i)=>new SyncEngine(group,ids[i],s,(to,m)=>queue.push([i,Number(to),m]),e=>errors.push(e)));
 async function drain(){let count=0;while(queue.length){assert.ok(++count<1000,'bounded delivery');const[from,to,m]=queue.shift();await nodes[to].receive(String(from),m);}}
 const first=report(ids[0],1),second=report(ids[1],2);
 await stores[0].commandQuake(first);await stores[1].commandQuake(second);
 assert.equal(quakeStates(roster,await stores[0].quakeEvents(group)).length,0);
 await nodes[0].connected('1');await nodes[1].connected('0');await drain();
 for(const s of stores.slice(0,2))assert.equal(quakeStates(roster,await s.quakeEvents(group)).length,1);
 const reportId=id();await stores[0].appendResponse(response(ids[0],first.eventId,{type:'REPORT',reportId,category:'道路閉塞',latitude:35,longitude:139,description:'倒木'},reportId));
 const target={kind:'REPORT',id:reportId};
 await stores[0].appendResponse(response(ids[0],first.eventId,{type:'ASSIGNMENT',target,memberId:ids[0],active:true}));
 await nodes[0].changed();await drain();nodes[0].disconnected('1');nodes[1].disconnected('0');
 const name=stores[1].name;stores[1].close();nodes[1].store=await Store.open(name,indexedDB);
 await nodes[1].connected('2');await nodes[2].connected('1');await drain();
 assert.equal((await stores[2].responseHeads(group)).length,2);
 const end={groupId:group,eventId:id(),actorDeviceId:ids[0],operatedAt:300,action:'END',training:false,targetId:first.eventId};
 await stores[0].commandQuake(end);await nodes[0].connected('1');await nodes[1].connected('0');await drain();
 assert.equal(quakeStates(roster,await stores[2].quakeEvents(group))[0].closed,true);
 assert.deepEqual(errors,[]);for(const node of nodes)node.store.close();
});
test('reordered formations unify, cancellations cannot reopen and local cancelled votes cannot form',()=>{
 const left=[report(ids[0],0),report(ids[1],1)],right=[report(ids[2],100),report(ids[3],101)];
 const form=(rs,actor)=>{const f=formationCandidates(roster,rs)[0];return {groupId:group,eventId:id(),actorDeviceId:actor,operatedAt:101,action:'FORM',training:false,targetId:f.incidentId,reportIds:f.reportIds};};
 const f1=form(left,ids[0]),f2=form(right,ids[2]),end={groupId:group,eventId:id(),actorDeviceId:ids[0],operatedAt:200,action:'END',training:false,targetId:f2.targetId};
 const history=[...left,...right,f1,f2,end],state=quakeStates(roster,history);
 assert.equal(state.length,1);assert.equal(state[0].closed,true);assert.deepEqual(state,quakeStates(roster,history.toReversed()));
 assert.throws(()=>validateQuakeCommand(roster,[...left,{groupId:group,eventId:id(),actorDeviceId:ids[0],operatedAt:1,action:'CANCEL',training:false,targetId:left[0].eventId}],f1));
});
test('end stores its affected incident IDs so late earlier proof cannot reopen them',async()=>{
 const s=await open();await s.importRoster(roster,ids[0]);
 const batches=[0,20,40].map(minute=>[report(ids[0],minute*60000),report(ids[1],minute*60000+1)]);
 const forms=batches.map(rs=>{const f=formationCandidates(roster,rs)[0];return {groupId:group,eventId:id(),actorDeviceId:ids[0],operatedAt:rs[1].operatedAt,action:'FORM',training:false,targetId:f.incidentId,reportIds:f.reportIds};});
 for(const i of [1,2])for(const e of [...batches[i],forms[i]])await s.mergeQuake(e);
 await s.commandQuake({groupId:group,eventId:id(),actorDeviceId:ids[0],operatedAt:3000000,action:'END',training:false,targetId:forms[2].targetId});
 const end=(await s.quakeEvents(group)).find(e=>e.action==='END');assert.equal(end.endedIncidentIds.length,2);
 for(const e of [...batches[0],forms[0]])await s.mergeQuake(e);
 assert.ok(quakeStates(roster,await s.quakeEvents(group)).every(s=>s.closed));s.close();
});
test('failed writes have no ACK; unknown roster, conflicting IDs and unauthorized updates are rejected',async()=>{
 const s=await open(),sent=[],errors=[],node=new SyncEngine(group,ids[1],s,(_,m)=>sent.push(m),e=>errors.push(e));await node.connected('A');sent.length=0;
 const r=report(ids[0],1);await node.receive('A',{version:6,groupId:group,type:'QUAKE_RECORD',quake:r});assert.equal(sent.length,0);
 await s.importRoster(roster,ids[1]);await node.receive('A',{version:6,groupId:group,type:'QUAKE_RECORD',quake:r});assert.ok(sent.some(m=>m.type==='QUAKE_ACK'));
 sent.length=0;await node.receive('A',{version:6,groupId:group,type:'QUAKE_RECORD',quake:{...r,title:'conflict'}});assert.equal(sent.length,0);
 const original=s.mergeResponse;s.mergeResponse=async()=>{throw Error('full');};
 await node.receive('A',{version:6,groupId:group,type:'RESPONSE_RECORD',response:response(ids[0],null,{type:'AVAILABILITY',state:'AVAILABLE'})});assert.equal(sent.length,0);s.mergeResponse=original;
 await s.commandQuake(report(ids[1],2));
 const reportId=id(),event=await s.appendResponse(response(ids[1],r.eventId,{type:'REPORT',reportId,category:'道路',latitude:35,longitude:139,description:'倒木'},reportId));
 await assert.rejects(s.appendResponse(response(ids[2],r.eventId,{...event.change,description:'改変'},id(),[event.eventId])));
 await assert.rejects(s.appendResponse(response(ids[1],r.eventId,{type:'PRIORITY',target:{kind:'REPORT',id:reportId},urgency:'HIGH',reason:'通行不能'})));
 const a=await s.appendResponse(response(ids[1],r.eventId,{...event.change,description:'訂正A'},id(),[event.eventId]));
 const b={...response(ids[0],r.eventId,{...event.change,description:'訂正B'},id(),[event.eventId]),sequence:10};await s.mergeResponse(b);
 assert.deepEqual(new Set(responseHeads(roster,await s.responseEvents(group)).map(e=>e.eventId)),new Set([a.eventId,b.eventId]));
 assert.equal(errors.length,3);s.close();
});


test('unlimited reporting uses admin end rather than elapsed time, including reordered delivery',async()=>{
 const s=await open();await s.importRoster(roster,ids[0]);const day=86400000;
 const old=[report(ids[0],0),report(ids[1],day*7)];
 for(const e of old)await s.commandQuake(e);
 assert.equal(quakeStates(roster,await s.quakeEvents(group)).length,1);
 const end={groupId:group,eventId:id(),actorDeviceId:ids[0],operatedAt:day*8,action:'END',training:false,targetId:old[0].eventId};
 await s.commandQuake(end);await s.commandQuake(report(ids[2],day*3));
 const fresh=[report(ids[0],day*9),report(ids[1],day*20)];
 for(const e of fresh)await s.commandQuake(e);
 await s.mergeQuake({...end,eventId:id(),operatedAt:day*30});
 await s.commandQuake(report(ids[3],day*40));
 const events=await s.quakeEvents(group),states=quakeStates(roster,events);
 assert.deepEqual(states.map(s=>s.closed),[true,false]);
 assert.equal(states[1].formation.incidentId,fresh[0].eventId);
 assert.deepEqual(quakeStates(roster,events.toReversed()),states);
 const name=s.name;s.close();const reopened=await Store.open(name,indexedDB);
 assert.deepEqual(quakeStates(roster,await reopened.quakeEvents(group)),states);reopened.close();
});


test('late earlier report updates elapsed origin while preserving the formed incident ID',()=>{
 const reports=[report(ids[0],100),report(ids[1],101)];
 const f=formationCandidates(roster,reports)[0];
 const formed={groupId:group,eventId:id(),actorDeviceId:ids[0],operatedAt:101,action:'FORM',training:false,targetId:f.incidentId,reportIds:f.reportIds};
 const history=[...reports,formed,report(ids[2],0)];const state=quakeStates(roster,history)[0];
 assert.equal(state.formation.incidentId,reports[0].eventId);assert.equal(state.formation.firstReportedAt,0);
 const start={groupId:group,eventId:reports[0].eventId,incidentId:reports[0].eventId,authorId:ids[0],authorName:'確認班',sequence:1,createdAt:100,kind:'START',title:'地震',area:'地域',training:false};
 assert.equal(managedIncidentOptions([start],roster,history)[0].start.createdAt,0);
});
