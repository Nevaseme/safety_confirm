// Emulator interoperability peer: simulated IndexedDB; no resident data written to disk.
import fs from 'node:fs';
import WebSocket from 'ws';
import {indexedDB} from 'fake-indexeddb';
import {Store,SyncEngine,incidentView} from '../web/sync.mjs';
const groupArg=process.argv[2];
if(!/^[A-Za-z0-9_-]{1,40}$/.test(groupArg||''))throw Error('Pass a fresh test group (1-40 ASCII letters, digits, _ or -)');
const config=JSON.parse(fs.readFileSync(new URL('../.local/config.json',import.meta.url),'utf8'));
const group=process.argv[2],id=crypto.randomUUID(),store=await Store.open(crypto.randomUUID(),indexedDB);
const ws=new WebSocket(`wss://127.0.0.1:${config.port}/socket`,{ca:fs.readFileSync(new URL('../.local/ca.pem',import.meta.url))});let queue=Promise.resolve(),replied=false,careId=null,receipt=false;
const sync=new SyncEngine(group,id,store,(to,body)=>ws.send(JSON.stringify({kind:'data',to,body})),e=>{console.error(e);process.exitCode=1;},e=>{if(e.incident&&e.originId===careId)receipt=true;});
const timer=setTimeout(()=>{console.error('Timed out');ws.close();process.exitCode=1;},60000);
ws.on('open',()=>ws.send(JSON.stringify({kind:'join',role:'B',group,token:config.token})));
ws.on('error',e=>{console.error(e);clearTimeout(timer);process.exitCode=1});
ws.on('message',raw=>{queue=queue.then(async()=>{const m=JSON.parse(raw);
 if(m.kind==='error')throw Error(m.message||'Router rejected operation');
 if(m.kind==='joined'){console.log('READY: JS B joined, phase '+m.phase);if(m.phase!=='AB')ws.send(JSON.stringify({kind:'phase',phase:'AB'}));}
 if(m.kind==='peer-up'||m.kind==='error')console.log(JSON.stringify(m));
 if(m.kind==='peer-up')await sync.connected(m.peer);
 if(m.kind==='data')await sync.receive(m.from,m.body);
 const rows=await store.incidents(group),start=rows.find(e=>e.kind==='START'),view=start?incidentView(rows,start.eventId):null;
 if(!replied&&view&&Object.values(view.answers).some(e=>e.safety==='SOS')){replied=true;careId=crypto.randomUUID();await store.appendIncident({groupId:group,eventId:careId,incidentId:start.eventId,authorId:id,authorName:'JS確認担当',createdAt:Date.now(),kind:'CARE',subjectId:start.authorId,action:'CHECKING',note:'JS保存後に返信'});await sync.changed();console.log('PASS: native START/SOS committed, JS care sent');}
 if(m.kind==='peer-down'){sync.disconnected(m.peer);if(replied&&receipt){console.log('PASS: native acknowledged JS care; peer left');clearTimeout(timer);ws.close();}else{throw Error('peer left before completion');}}
 }).catch(e=>{console.error(e);clearTimeout(timer);ws.close();process.exitCode=1;});});
