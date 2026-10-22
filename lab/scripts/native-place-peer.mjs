// Emulator interoperability peer: simulated IndexedDB; no resident data written to disk.
import fs from 'node:fs';
import WebSocket from 'ws';
import {indexedDB} from 'fake-indexeddb';
import {Store,SyncEngine} from '../web/sync.mjs';
import {placeBuildings,validHome} from '../web/places.mjs';
const groupArg=process.argv[2];
if(!/^[A-Za-z0-9_-]{1,40}$/.test(groupArg||''))throw Error('Pass a fresh test group (1-40 ASCII letters, digits, _ or -)');
const config=JSON.parse(fs.readFileSync(new URL('../.local/config.json',import.meta.url),'utf8')),group=process.argv[2],id=crypto.randomUUID(),store=await Store.open(crypto.randomUUID(),indexedDB);
await store.merge({groupId:group,originId:id,revision:1,updatedAt:1,name:'JS確認者',latitude:35,longitude:139,floor:'1階',room:'南',safety:'UNREPORTED'});
const ws=new WebSocket(`wss://127.0.0.1:${config.port}/socket`,{ca:fs.readFileSync(new URL('../.local/ca.pem',import.meta.url))});let queue=Promise.resolve(),replied=false,careId=null,receipt=false;
const sync=new SyncEngine(group,id,store,(to,body)=>ws.send(JSON.stringify({kind:'data',to,body})),e=>{console.error(e);process.exitCode=1;},e=>{if(e.originId===careId)receipt=true;});
const timer=setTimeout(()=>{console.error('Timed out');ws.close();process.exitCode=1;},60000);
ws.on('open',()=>ws.send(JSON.stringify({kind:'join',role:'B',group,token:config.token})));
ws.on('error',e=>{console.error(e);clearTimeout(timer);process.exitCode=1;});
ws.on('message',raw=>{queue=queue.then(async()=>{const m=JSON.parse(raw);
 if(m.kind==='error')throw Error(m.message||'Router rejected operation');
 if(m.kind==='joined'){if(m.phase!=='AB')ws.send(JSON.stringify({kind:'phase',phase:'AB'}));console.log('READY: JS B joined');}
 if(m.kind==='peer-up')await sync.connected(m.peer);if(m.kind==='data')await sync.receive(m.from,m.body);
 const rows=await store.places(group),h=placeBuildings(rows)[0],start=(await store.incidents(group)).find(e=>e.kind==='START');
 if(!replied&&h&&start&&validHome(rows,h.authorId)){
  replied=true;careId=crypto.randomUUID();await store.appendPlace({groupId:group,eventId:careId,authorId:id,authorName:'JS確認者',createdAt:Date.now(),kind:'CARE',buildingId:h.buildingId,incidentId:start.eventId,action:'CHECKING',scope:'北玄関',unresolved:'2階は未確認',note:'建物の記録を返信'});await sync.changed();console.log('PASS: native building+HOME committed; JS scoped CARE sent');
 }
 if(m.kind==='peer-down'){sync.disconnected(m.peer);if(replied&&receipt){console.log('PASS: native acknowledged building CARE');clearTimeout(timer);ws.close();}else throw Error('left before exact care ACK');}
 }).catch(e=>{console.error(e);clearTimeout(timer);ws.close();process.exitCode=1;});});
