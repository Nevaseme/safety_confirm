import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {indexedDB} from 'fake-indexeddb';
import {Store,SyncEngine} from '../web/sync.mjs';
test('profile and selected group commit atomically, including abort after metadata write',async()=>{
 const record=JSON.parse(fs.readFileSync(new URL('../fixtures/protocol.json',import.meta.url),'utf8'))[0].record;
 const store=await Store.open(crypto.randomUUID(),indexedDB);
 await store.saveProfile(record);assert.equal(await store.read('meta','group'),record.groupId);
 const original=store.db.transaction.bind(store.db);
 store.db.transaction=(name,mode)=>{const tx=original(name,mode);if(mode==='readwrite'){
  const objectStore=tx.objectStore.bind(tx);tx.objectStore=n=>{const s=objectStore(n);if(n==='meta'){const put=s.put.bind(s);s.put=(...args)=>{const req=put(...args);req.addEventListener('success',()=>tx.abort());return req;};}return s;};
 }return tx;};
 await assert.rejects(store.saveProfile({...record,groupId:'other'}));
 assert.equal(await store.read('meta','group'),record.groupId);assert.equal((await store.all('other')).length,0);
});
test('shared Kotlin wire fixtures store both versions and ACK after commit',async()=>{
 const data=JSON.parse(fs.readFileSync(new URL('../fixtures/protocol.json',import.meta.url),'utf8'));
 const store=await Store.open(crypto.randomUUID(),indexedDB),sent=[];
 const engine=new SyncEngine('fixture','cccccccc-cccc-4ccc-8ccc-cccccccccccc',store,(_,m)=>sent.push(m));
 await engine.connected('A');sent.length=0;
 for(const message of data)await engine.receive('A',message);
 assert.equal((await store.all('fixture')).length,1);assert.equal((await store.events('fixture')).length,1);
 assert.deepEqual(sent.map(x=>x.type),['ACK','CARE_ACK']);
});
test('rescue save failure does not ACK or announce to another peer',async()=>{
 const data=JSON.parse(fs.readFileSync(new URL('../fixtures/protocol.json',import.meta.url),'utf8'));
 const store=await Store.open(crypto.randomUUID(),indexedDB),sent=[];
 const engine=new SyncEngine('fixture','cccccccc-cccc-4ccc-8ccc-cccccccccccc',store,(_,m)=>sent.push(m));
 await engine.connected('A');await engine.connected('C');sent.length=0;
 store.mergeEvent=async()=>{throw Error('quota');};await engine.receive('A',data[1]);assert.equal(sent.length,0);
});
test('transaction aborted after put success leaves no record and no ACK',async()=>{
 const data=JSON.parse(fs.readFileSync(new URL('../fixtures/protocol.json',import.meta.url),'utf8'));
 const store=await Store.open(crypto.randomUUID(),indexedDB),sent=[];
 const engine=new SyncEngine('fixture','cccccccc-cccc-4ccc-8ccc-cccccccccccc',store,(_,m)=>sent.push(m));
 await engine.connected('A');sent.length=0;
 const original=store.db.transaction.bind(store.db);
 store.db.transaction=(name,mode)=>{const tx=original(name,mode);if(mode==='readwrite'){
  const objectStore=tx.objectStore.bind(tx);tx.objectStore=n=>{const s=objectStore(n),put=s.put.bind(s);s.put=(...args)=>{const req=put(...args);req.addEventListener('success',()=>tx.abort());return req;};return s;};
 }return tx;};
 await engine.receive('A',data[1]);assert.equal(sent.length,0);assert.equal((await store.events('fixture')).length,0);
});
