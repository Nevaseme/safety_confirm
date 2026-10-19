import test from 'node:test';
import assert from 'node:assert/strict';
import { indexedDB } from 'fake-indexeddb';
import { SyncEngine, Store, currentEvents } from '../web/sync.mjs';

const ids = ['aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','cccccccc-cccc-4ccc-8ccc-cccccccccccc'];
const resident = (revision = 1) => ({groupId:'demo',originId:ids[0],revision,updatedAt:1,name:'訓練A',latitude:35,longitude:139,floor:'2階',room:'北側',safety:revision===1?'SOS':'SAFE'});
async function network() {
  const stores = await Promise.all(ids.map((id,i)=>Store.open(`test-${crypto.randomUUID()}-${i}`, indexedDB)));
  const queue=[], links=new Set(), receipts=[];
  const nodes=stores.map((store,i)=>new SyncEngine('demo',ids[i],store,(to,msg)=>{if(links.has([i,+to].sort().join()))queue.push([i,+to,msg]);},()=>{},()=>receipts.push(i)));
  async function drain(){ let n=0; while(queue.length){assert.ok(++n<1000,'no endless sending');const [from,to,msg]=queue.shift();if(links.has([from,to].sort().join()))await nodes[to].receive(String(from),msg);} }
  return {stores,nodes,receipts,drain,async connect(a,b){links.add([a,b].sort().join());await nodes[a].connected(String(b));await nodes[b].connected(String(a));await drain();},disconnect(a,b){links.delete([a,b].sort().join());nodes[a].disconnected(String(b));nodes[b].disconnected(String(a));}};
}
test('A → B, A disconnected, B restarted → C carries saved SOS and rescue event',async()=>{
 const n=await network(); const r=resident(); await n.stores[0].merge(r);
 await n.connect(0,1); n.disconnect(0,1);
 const event={groupId:'demo',eventId:crypto.randomUUID(),subjectId:ids[0],authorId:ids[1],authorName:'確認B',createdAt:2,action:'RESCUED',note:'本人を確認',supersedes:null};
 await n.stores[1].mergeEvent(event);
 const name=n.stores[1].name; n.stores[1].close(); n.stores[1]=await Store.open(name,indexedDB); n.nodes[1].store=n.stores[1];
 await n.connect(1,2);
 assert.deepEqual(await n.stores[2].get('demo',ids[0]),r);
 assert.deepEqual(await n.stores[2].events('demo'),[event]);
 assert.ok(n.receipts.length>0);
});
test('old and duplicate records do not replace newer revision, self cannot be overwritten',async()=>{
 const n=await network();await n.stores[1].merge(resident(2));await n.connect(0,1);
 await n.nodes[1].receive('0',{version:1,groupId:'demo',type:'RECORD',record:resident()});
 assert.equal((await n.stores[1].get('demo',ids[0])).revision,2);
 await n.nodes[0].receive('1',{version:1,groupId:'demo',type:'RECORD',record:resident(99)});
 assert.equal(await n.stores[0].get('demo',ids[0]),undefined);
});
test('failed save sends no ACK; wrong group and unsupported version ignored',async()=>{
 const n=await network();await n.connect(0,1);const sent=[];n.nodes[1].send=(_,m)=>sent.push(m);
 n.stores[1].merge=async()=>{throw Error('disk full');};
 for(const m of [{version:1,groupId:'demo'},{version:1,groupId:'other'},{version:99,groupId:'demo'}])await n.nodes[1].receive('0',{...m,type:'RECORD',record:resident()});
 assert.equal(sent.length,0);
});
test('event corrections preserve history and cannot erase another author',async()=>{
 const base={groupId:'demo',eventId:crypto.randomUUID(),subjectId:ids[0],authorId:ids[1],authorName:'B',createdAt:1,action:'RESCUED',note:'',supersedes:null};
 const foreign={...base,eventId:crypto.randomUUID(),authorId:ids[2],action:'NEEDS_HELP',supersedes:base.eventId};
 assert.equal(currentEvents([base,foreign]).length,2);
 const correction={...base,eventId:crypto.randomUUID(),createdAt:2,action:'NEEDS_HELP',supersedes:base.eventId};
 assert.deepEqual(currentEvents([base,correction]),[correction]);
 const store=await Store.open(crypto.randomUUID(),indexedDB); await store.mergeEvent(base);await store.mergeEvent(correction);
 assert.equal((await store.events('demo')).length,2);
 await assert.rejects(()=>store.mergeEvent({...base,note:'altered'}));
});
