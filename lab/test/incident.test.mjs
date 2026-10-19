import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import * as api from '../web/sync.mjs';
const A='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',B='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const start=(id=crypto.randomUUID())=>({groupId:'incident-test',eventId:id,incidentId:id,authorId:A,authorName:'訓練A',sequence:1,createdAt:100,kind:'START',title:'地震訓練',area:'訓練地域',training:true});
const child=(s,kind,extras={})=>({...s,eventId:crypto.randomUUID(),sequence:2,kind,title:'',area:'',...extras});
const open=()=>api.Store.open(crypto.randomUUID(),indexedDB);

test('initial received incident stays selected across other START, END and reload',async()=>{
 const db=await open(),a=start(),b=start();await db.mergeIncident(a);
 assert.equal(await db.resolveIncidentSelection(a.groupId),a.eventId);
 await db.mergeIncident(b);assert.equal(await db.resolveIncidentSelection(a.groupId),a.eventId);
 await db.mergeIncident(child(a,'END'));assert.equal(await db.resolveIncidentSelection(a.groupId),a.eventId);
 const name=db.name;db.close();const reopened=await api.Store.open(name,indexedDB);
 assert.equal(await reopened.resolveIncidentSelection(a.groupId),a.eventId);
});

test('pending answer keeps clicked incident even when selection changes during profile read',async()=>{
 const {appendScopedIncident}=await import('../web/incidents.mjs');
 assert.equal(typeof appendScopedIncident,'function');
 let release,saved;const scope={groupId:'incident-test',incidentId:'clicked-A',authorId:A};
 const db={get:()=>new Promise(resolve=>{release=resolve;}),appendIncident:e=>{saved=e;return e;}};
 const pending=appendScopedIncident(db,scope,'ANSWER',{safety:'SOS'});
 scope.incidentId='received-B';scope.groupId='other';release({name:'訓練者'});await pending;
 assert.equal(saved.incidentId,'clicked-A');assert.equal(saved.groupId,'incident-test');assert.equal(saved.safety,'SOS');
});
test('incident storage exists and duplicate start cannot erase an answer; new incident is unanswered',async()=>{
 const db=await open();assert.equal(typeof db.mergeIncident,'function');
 const first=start(),second=start();await db.mergeIncident(first);await db.mergeIncident(child(first,'ANSWER',{safety:'SOS'}));await db.mergeIncident(first);await db.mergeIncident(second);
 assert.equal(api.incidentView(await db.incidents('incident-test'),first.eventId).answers[A].safety,'SOS');
 assert.equal(Object.keys(api.incidentView(await db.incidents('incident-test'),second.eventId).answers).length,0);
});
test('answers use author sequence rather than clock; foreign close and cross-incident correction do not affect state',async()=>{
 const db=await open();assert.equal(typeof db.mergeIncident,'function');const s=start();
 for(const e of [s,child(s,'ANSWER',{sequence:3,safety:'SAFE',createdAt:1}),child(s,'ANSWER',{sequence:2,safety:'SOS',createdAt:99999}),child(s,'END',{authorId:B})])await db.mergeIncident(e);
 const view=api.incidentView(await db.incidents(s.groupId),s.eventId);assert.equal(view.answers[A].safety,'SAFE');assert.equal(view.closed,false);
 await db.mergeIncident(child(s,'END',{sequence:4}));assert.equal(api.incidentView(await db.incidents(s.groupId),s.eventId).closed,true);
});
test('same id conflicting payload and same author sequence conflict reject without overwriting',async()=>{
 const db=await open();assert.equal(typeof db.mergeIncident,'function');const s=start();await db.mergeIncident(s);
 await assert.rejects(db.mergeIncident({...s,title:'different'}));await db.mergeIncident(child(s,'ANSWER',{safety:'SAFE'}));
 await assert.rejects(db.mergeIncident(child(s,'ANSWER',{safety:'SOS'})));assert.equal((await db.incidents(s.groupId)).length,2);
});
test('v3 ACK follows durable save; failure sends no ACK, forwarding or legacy status',async()=>{
 const db=await open();assert.equal(typeof db.mergeIncident,'function');const sent=[],errors=[],s=start();
 const engine=new api.SyncEngine(s.groupId,B,db,(to,m)=>sent.push({to,m}),e=>errors.push(e));await engine.connected('A');await engine.connected('C');sent.length=0;
 const merge=db.mergeIncident.bind(db);db.mergeIncident=async()=>{throw Error('disk full')};
 await engine.receive('A',{version:3,groupId:s.groupId,type:'INCIDENT_RECORD',entries:[],incident:s});assert.equal(sent.length,0);
 db.mergeIncident=merge;await engine.receive('A',{version:3,groupId:s.groupId,type:'INCIDENT_RECORD',entries:[],incident:s});
 assert.deepEqual(sent.map(x=>x.m.type),['INCIDENT_ACK','INCIDENT_SUMMARY']);assert.equal((await db.incidents(s.groupId)).length,1);assert.equal((await db.all(s.groupId)).length,0);
});
test('offline B restart relays A incident and answer to C without A; duplicates quiesce',async()=>{
 const stores=await Promise.all([open(),open(),open()]);assert.equal(typeof stores[0].mergeIncident,'function');const s=start();
 await stores[0].mergeIncident(s);await stores[0].mergeIncident(child(s,'ANSWER',{safety:'SOS'}));
 let count=0,queue=[];const engines=stores.map((db,i)=>new api.SyncEngine(s.groupId,[A,B,crypto.randomUUID()][i],db,(to,m)=>queue.push([i,+to,m])));
 const pump=async()=>{while(queue.length){assert.ok(++count<100);const [from,to,m]=queue.shift();await engines[to].receive(String(from),m);}};
 await engines[0].connected('1');await engines[1].connected('0');await pump();engines[0].disconnected('1');engines[1].disconnected('0');
 const name=stores[1].name;stores[1].close();stores[1]=await api.Store.open(name,indexedDB);engines[1]=new api.SyncEngine(s.groupId,B,stores[1],(to,m)=>queue.push([1,+to,m]));
 await engines[1].connected('2');await engines[2].connected('1');await pump();await engines[1].changed();await pump();
 assert.equal(api.incidentView(await stores[2].incidents(s.groupId),s.eventId).answers[A].safety,'SOS');assert.equal((await stores[2].incidents(s.groupId)).length,2);
});
test('journal upgrade retains v1 profiles and care; transaction abort prevents ACK',async()=>{
 const name=crypto.randomUUID(),s=start();
 const old=await new Promise((resolve,reject)=>{const q=indexedDB.open(name,1);q.onupgradeneeded=()=>{q.result.createObjectStore('residents',{keyPath:['groupId','originId']});q.result.createObjectStore('events',{keyPath:['groupId','eventId']});q.result.createObjectStore('meta');};q.onsuccess=()=>resolve(q.result);q.onerror=()=>reject(q.error);});
 await new Promise((resolve,reject)=>{const tx=old.transaction(['residents','events'],'readwrite');tx.objectStore('residents').put({groupId:s.groupId,originId:A,safety:'SOS'});tx.objectStore('events').put({groupId:s.groupId,eventId:'legacy',action:'RESCUED'});tx.oncomplete=resolve;tx.onabort=reject;});old.close();
 const store=await api.Store.open(name,indexedDB);assert.equal((await store.all(s.groupId))[0].safety,'SOS');assert.equal((await store.events(s.groupId))[0].action,'RESCUED');
 const original=store.db.transaction.bind(store.db);store.db.transaction=(name,mode)=>{const tx=original(name,mode);if(mode==='readwrite'){const objectStore=tx.objectStore.bind(tx);tx.objectStore=n=>{const os=objectStore(n),put=os.put.bind(os);os.put=(...args)=>{const q=put(...args);q.addEventListener('success',()=>tx.abort());return q;};return os;};}return tx;};
 const sent=[],engine=new api.SyncEngine(s.groupId,B,store,(_,m)=>sent.push(m));await engine.connected('A');sent.length=0;
 await engine.receive('A',{version:3,groupId:s.groupId,type:'INCIDENT_RECORD',incident:s});assert.equal(sent.length,0);assert.equal((await store.incidents(s.groupId)).length,0);
});
test('append sequences serialize, closed incident rejects writes and foreign correction cannot cross scope',async()=>{
 const db=await open(),s=start(),t=start();await db.mergeIncident(s);await db.mergeIncident(t);
 await Promise.all([db.appendIncident(child(s,'ANSWER',{safety:'SOS'})),db.appendIncident(child(s,'ANSWER',{safety:'SAFE'}))]);
 assert.deepEqual((await db.incidents(s.groupId)).filter(e=>e.kind==='ANSWER').map(e=>e.sequence).sort(),[2,3]);
 const care=await db.appendIncident(child(s,'CARE',{subjectId:A,action:'RESCUED'}));
 await assert.rejects(db.appendIncident(child(t,'CARE',{subjectId:A,action:'NEEDS_HELP',supersedes:care.eventId})));
 await db.appendIncident(child(s,'END'));await assert.rejects(db.appendIncident(child(s,'ANSWER',{safety:'SOS'})));
 assert.equal(api.incidentView(await db.incidents(s.groupId),s.eventId).answers[A].safety,'SAFE');
});
