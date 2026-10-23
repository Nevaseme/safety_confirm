import test from 'node:test';
import assert from 'node:assert/strict';
import { indexedDB } from 'fake-indexeddb';
import { Store,SyncEngine } from '../web/sync.mjs';

const ids=['aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','cccccccc-cccc-4ccc-8ccc-cccccccccccc'];
const position={groupId:'trial',originId:ids[0],revision:1,acquiredAt:100,latitude:35,longitude:139,accuracyMeters:12};
const memo={groupId:'trial',incidentId:ids[1],authorId:ids[0],answerId:ids[2],revision:1,updatedAt:100,text:'北の寝室'};
const open=()=>Store.open(crypto.randomUUID(),indexedDB);
test('position and memo are persisted, conflict checked, and restored after reopen',async()=>{
 let db=await open();await db.savePosition(position);await db.mergeMemo(memo);
 await db.mergeMemo({...memo,revision:2,text:'変更'});assert.equal(await db.mergeMemo(memo),false);
 await assert.rejects(db.mergeMemo({...memo,revision:3,authorId:ids[1]}));
 await assert.rejects(db.savePosition({...position,latitude:0}));
 const name=db.name;db.close();db=await Store.open(name,indexedDB);
 assert.deepEqual(await db.positions('trial'),[position]);assert.equal((await db.memos('trial'))[0].text,'変更');db.close();
});
test('save failure has no ACK, restored relay forwards after original sender leaves',async()=>{
 const stores=await Promise.all(ids.map(open));const queue=[];
 const nodes=ids.map((id,i)=>new SyncEngine('trial',id,stores[i],(to,m)=>queue.push([i,Number(to),m])));
 async function drain(){let n=0;while(queue.length){assert.ok(++n<100);const [from,to,m]=queue.shift();await nodes[to].receive(String(from),m);}}
 await stores[0].savePosition(position);await stores[0].mergeMemo(memo);
 await nodes[0].connected('1');await nodes[1].connected('0');await drain();
 nodes[0].disconnected('1');nodes[1].disconnected('0');
 const name=stores[1].name;stores[1].close();nodes[1].store=await Store.open(name,indexedDB);
 await nodes[1].connected('2');await nodes[2].connected('1');await drain();
 assert.deepEqual(await stores[2].positions('trial'),[position]);assert.deepEqual(await stores[2].memos('trial'),[memo]);
 const sent=[];nodes[2].send=(_,m)=>sent.push(m);stores[2].savePosition=async()=>{throw Error('full');};
 await nodes[2].receive('1',{version:5,groupId:'trial',type:'POSITION_RECORD',position:{...position,revision:2}});
 assert.equal(sent.length,0);
 for(const node of nodes)node.store.close();
});
test('stopping own position sharing suppresses requests while memo sharing continues',async()=>{
 const db=await open();await db.savePosition(position);await db.mergeMemo(memo);let allowed=true;const sent=[];
 const node=new SyncEngine('trial',ids[0],db,(_,m)=>sent.push(m),()=>{},()=>{},()=>allowed);
 await node.connected('B');assert.ok(sent.some(m=>m.type==='POSITION_SUMMARY'));allowed=false;sent.length=0;
 await node.changed();await node.receive('B',{version:5,groupId:'trial',type:'POSITION_REQUEST',entries:[{originId:ids[0],revision:1}]});
 assert.ok(sent.every(m=>!m.type.startsWith('POSITION_')));assert.ok(sent.some(m=>m.type==='MEMO_SUMMARY'));db.close();
});
