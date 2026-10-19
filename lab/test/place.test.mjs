import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store,SyncEngine} from '../web/sync.mjs';
const a='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',b='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',g='place-test';
const id=()=>crypto.randomUUID();
const event=(kind,extra={})=>({groupId:g,eventId:id(),authorId:a,authorName:'訓練A',sequence:1,createdAt:1,kind,...extra});
const building=()=>{const e=event('BUILDING',{label:'訓練住宅',latitude:35,longitude:139});return {...e,buildingId:e.eventId};};
const profile=originId=>({groupId:g,originId,revision:1,updatedAt:1,name:originId===a?'A':'B',latitude:35,longitude:139,floor:'2',room:'北',safety:'SOS'});
const setup=async()=>{const db=await Store.open(id(),indexedDB);await db.merge(profile(a));await db.merge(profile(b));return db;};
const start=async db=>{const s={...event('START'),incidentId:'',title:'訓練',area:'地区',training:true};s.incidentId=s.eventId;await db.mergeIncident(s);return s;};

test('received cyclic corrections cannot erase all building-care heads',async()=>{
 const {validatePlace,currentBuildingCare}=await import('../web/places.mjs');
 const buildingId=id(),incidentId=id(),first=id(),second=id();
 const x=event('CARE',{eventId:first,buildingId,incidentId,action:'NEEDS_HELP',scope:'全棟',unresolved:'上階',supersedes:second});
 const y={...x,eventId:second,sequence:2,supersedes:first};
 assert.deepEqual(currentBuildingCare([validatePlace(x),validatePlace(y)]).map(e=>e.eventId),[second]);
});

test('schema2 upgrade preserves identity and incident, out-of-order unit revisions retain resident links',async()=>{
 const name=id(),s={...event('START'),title:'旧訓練',area:'地区',training:true};s.incidentId=s.eventId;
 const old=await new Promise(resolve=>{const q=indexedDB.open(name,2);q.onupgradeneeded=()=>{for(const table of ['residents','events','incidents'])q.result.createObjectStore(table,{keyPath:['groupId',table==='residents'?'originId':'eventId']});q.result.createObjectStore('meta');};q.onsuccess=()=>resolve(q.result);});
 await new Promise((resolve,reject)=>{const tx=old.transaction(['meta','residents','incidents'],'readwrite');tx.objectStore('meta').put(a,'identity');tx.objectStore('residents').put(profile(a));tx.objectStore('incidents').put(s);tx.oncomplete=resolve;tx.onabort=reject;});old.close();
 const db=await Store.open(name,indexedDB);assert.equal(await db.identity(),a);assert.equal((await db.incidents(g))[0].eventId,s.eventId);assert.equal((await db.all(g))[0].safety,'SOS');
 const h=await db.appendPlace(building());let unit=event('UNIT',{buildingId:h.buildingId,label:'201'});unit.unitId=unit.eventId;unit=await db.appendPlace(unit);
 await db.appendPlace(event('HOME',{buildingId:h.buildingId,unitId:unit.unitId}));const edited=await db.appendPlace({...unit,eventId:id(),label:'202'});
 const {placeUnits,validHome}=await import('../web/places.mjs');assert.equal(placeUnits(await db.places(g),h.buildingId)[0].label,'202');assert.equal(validHome(await db.places(g),a).unitId,unit.unitId);
 const receiver=await setup();await receiver.mergePlace(edited);await receiver.mergePlace(h);assert.equal(placeUnits(await receiver.places(g),h.buildingId).length,0);await receiver.mergePlace(unit);assert.equal(placeUnits(await receiver.places(g),h.buildingId)[0].label,'202');
 const foreign={...edited,eventId:id(),authorId:b,label:'wrong'};await receiver.mergePlace(foreign);assert.equal(placeUnits(await receiver.places(g),h.buildingId)[0].label,'202');
 await assert.rejects(receiver.mergePlace({...unit,label:'conflict'}));
});

test('place put success followed by transaction abort emits no ACK or relay',async()=>{
 const db=await setup(),h=building();const original=db.db.transaction.bind(db.db);
 db.db.transaction=(tables,mode)=>{const tx=original(tables,mode);if(mode==='readwrite'&&[].concat(tables).includes('places')){const objectStore=tx.objectStore.bind(tx);tx.objectStore=name=>{const os=objectStore(name);if(name==='places'){const put=os.put.bind(os);os.put=(...args)=>{const q=put(...args);q.addEventListener('success',()=>tx.abort());return q;};}return os;};}return tx;};
 const sent=[],receiver=new SyncEngine(g,b,db,(_,m)=>sent.push(m));await receiver.connected('A');await receiver.connected('C');sent.length=0;
 await receiver.receive('A',{version:4,groupId:g,type:'PLACE_RECORD',place:h});assert.equal(sent.length,0);assert.equal((await db.places(g)).length,0);
});

test('two residents share one unit; latest own link uses sequence, unlinks and invalid cross-building unit rejects',async()=>{
 const db=await setup();assert.equal(typeof db.appendPlace,'function');const h=await db.appendPlace(building()),other=await db.appendPlace(building());
 let unit=event('UNIT',{buildingId:h.buildingId,label:'201号室'});unit.unitId=unit.eventId;unit=await db.appendPlace(unit);
 await db.appendPlace(event('HOME',{buildingId:h.buildingId,unitId:unit.unitId}));await db.appendPlace(event('HOME',{authorId:b,buildingId:h.buildingId,unitId:unit.unitId}));
 const {placeBuildings,homeFor}=await import('../web/places.mjs');let rows=await db.places(g);assert.equal(placeBuildings(rows).length,2);assert.equal(homeFor(rows,a).unitId,unit.unitId);assert.equal(homeFor(rows,b).unitId,unit.unitId);
 await assert.rejects(db.appendPlace(event('HOME',{buildingId:other.buildingId,unitId:unit.unitId})));
 await db.appendPlace(event('HOME',{createdAt:0}));rows=await db.places(g);assert.equal(homeFor(rows,a).buildingId,null);assert.equal(homeFor(rows,b).buildingId,h.buildingId);
});
test('building completion requires explicit scope and unknowns; correction and recheck stay within building and incident',async()=>{
 const db=await setup();assert.equal(typeof db.appendPlace,'function');const h=await db.appendPlace(building()),s=await start(db),t=await start(db);
 const care=event('CARE',{buildingId:h.buildingId,incidentId:s.eventId,action:'COMPLETED',scope:'1階共用部',unresolved:'2階は未確認'});
 await assert.rejects(db.appendPlace({...care,scope:''}));await assert.rejects(db.appendPlace({...care,unresolved:''}));const saved=await db.appendPlace(care);
 await assert.rejects(db.appendPlace(event('CARE',{...care,eventId:id(),incidentId:t.eventId,supersedes:saved.eventId})));
 await assert.rejects(db.appendPlace(event('CARE',{...care,eventId:id(),authorId:b,supersedes:saved.eventId})));
 await db.appendPlace({...care,eventId:id(),action:'RECHECK',supersedes:saved.eventId});
 const {buildingCare,currentBuildingCare}=await import('../web/places.mjs');const rows=await db.places(g);
 assert.equal(buildingCare(rows,h.buildingId,t.eventId).length,0);assert.equal(currentBuildingCare(buildingCare(rows,h.buildingId,s.eventId))[0].action,'RECHECK');
 await db.appendIncident({...s,eventId:id(),kind:'END',title:'',area:''});await assert.rejects(db.appendPlace({...care,eventId:id()}));
});
test('v4 ACK only after commit and B restart relays building links and scoped care to C',async()=>{
 const stores=await Promise.all([setup(),setup(),setup()]);assert.equal(typeof stores[0].appendPlace,'function');const h=await stores[0].appendPlace(building()),s=await start(stores[0]);
 await stores[0].appendPlace(event('HOME',{buildingId:h.buildingId}));await stores[0].appendPlace(event('CARE',{buildingId:h.buildingId,incidentId:s.eventId,action:'CHECKING',scope:'全棟',unresolved:'2階',note:'北入口'}));
 let queue=[],count=0;const engines=stores.map((db,i)=>new SyncEngine(g,[a,b,id()][i],db,(to,m)=>queue.push([i,+to,m])));
 const pump=async()=>{while(queue.length){assert.ok(++count<200);const [from,to,m]=queue.shift();await engines[to].receive(String(from),m);}};
 await engines[0].connected('1');await engines[1].connected('0');await pump();engines[0].disconnected('1');engines[1].disconnected('0');
 const name=stores[1].name;stores[1].close();stores[1]=await Store.open(name,indexedDB);engines[1]=new SyncEngine(g,b,stores[1],(to,m)=>queue.push([1,+to,m]));
 await engines[1].connected('2');await engines[2].connected('1');await pump();await engines[1].changed();await pump();assert.equal((await stores[2].places(g)).length,3);
 const out=[];const db=await setup(),receiver=new SyncEngine(g,b,db,(_,m)=>out.push(m));await receiver.connected('A');out.length=0;
 db.mergePlace=async()=>{throw Error('full')};await receiver.receive('A',{version:4,groupId:g,type:'PLACE_RECORD',place:h});assert.equal(out.length,0);
});
