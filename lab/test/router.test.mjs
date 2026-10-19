import test from 'node:test';
import assert from 'node:assert/strict';
import { Router } from '../router.mjs';
test('A to C is blocked; only B carries data after A leaves; router does not replay or ACK',()=>{
 const router=new Router('secret');const inbox={A:[],B:[],C:[]};const peers={};
 for(const role of ['A','B','C']){peers[role]={send:s=>inbox[role].push(JSON.parse(s)),close:()=>{}};router.message(peers[role],JSON.stringify({kind:'join',role,token:'secret',group:'demo'}));}
 for(const x of Object.values(inbox))x.length=0;
 router.message(peers.A,JSON.stringify({kind:'data',to:'C',body:{type:'RECORD'}}));assert.equal(inbox.C.length,0);
 router.message(peers.A,JSON.stringify({kind:'data',to:'B',body:{type:'RECORD'}}));assert.equal(inbox.B[0].from,'A');assert.equal(inbox.A.length,0);
 router.message(peers.B,JSON.stringify({kind:'phase',phase:'BC'}));assert.equal(router.phase,'AB');
 router.leave(peers.A); router.message(peers.B,JSON.stringify({kind:'phase',phase:'BC'})); assert.equal(router.phase,'BC');
 assert.equal(inbox.C.filter(x=>x.kind==='data').length,0);
 router.message(peers.B,JSON.stringify({kind:'data',to:'C',body:{type:'RECORD'}}));assert.equal(inbox.C.filter(x=>x.kind==='data').length,1);
});
test('unauthorized, duplicate roles and different groups cannot exchange',()=>{
 const router=new Router('secret');const logs=[];let closed=0;const fake=()=>({send:m=>logs.push(JSON.parse(m)),close:()=>closed++});
 const a=fake(),b=fake(),bad=fake();router.message(bad,JSON.stringify({kind:'join',role:'A',token:'wrong',group:'demo'}));
 assert.equal(closed,1);router.message(a,JSON.stringify({kind:'join',role:'A',token:'secret',group:'demo'}));router.message(b,JSON.stringify({kind:'join',role:'B',token:'secret',group:'other'}));
 logs.length=0;router.message(a,JSON.stringify({kind:'data',to:'B',body:{}}));assert.equal(logs.length,0);
 router.message(fake(),JSON.stringify({kind:'join',role:'A',token:'secret',group:'demo'}));assert.equal(closed,2);
});
