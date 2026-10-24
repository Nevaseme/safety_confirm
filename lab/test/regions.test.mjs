import test from 'node:test';
import assert from 'node:assert/strict';
import {regionalSummary,validateRegionCatalog} from '../web/regions.mjs';
const catalog={version:1,regions:[{id:'city',name:'試験市'},{id:'ward',name:'試験区',parentId:'city'},{id:'a',name:'A町内会',parentId:'ward'},{id:'b',name:'B町内会',parentId:'ward'}]};
const target=n=>({kind:'REPORT',id:`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`});
test('town reports roll up once with source IDs, reassignment and unassigned cases',()=>{
 const cases=[{target:target(1),regionIds:['a']},{target:target(2),regionIds:['b']}];
 const result=regionalSummary(catalog,cases);
 assert.equal(result.regions.find(r=>r.regionId==='city').count,2);
 const repeated=regionalSummary(catalog,[...cases,{target:target(1),regionIds:['b','ward']},{target:target(3),regionIds:[]}]);
 assert.deepEqual(repeated.regions.find(r=>r.regionId==='city').targets,[target(1),target(2)]);
 assert.deepEqual(repeated.unassigned,[target(3)]);
 assert.equal(regionalSummary(catalog,[{target:target(1),regionIds:['b']}]).regions.find(r=>r.regionId==='a').count,0);
});
test('invalid hierarchy and unknown assignments are rejected',()=>{
 for(const regions of [[{id:'a',name:'A',parentId:'b'},{id:'b',name:'B',parentId:'a'}],[{id:'a',name:'A',parentId:'missing'}],[{id:'a',name:'A'},{id:'a',name:'A'}]])assert.throws(()=>validateRegionCatalog({version:1,regions}));
 assert.throws(()=>regionalSummary(catalog,[{target:target(1),regionIds:['unknown']}]));
});


test('stored corrected reports count once after reopen and stay within selected incident',async()=>{
 const {indexedDB}=await import('fake-indexeddb');const {Store}=await import('../web/sync.mjs');
 const id=()=>crypto.randomUUID(),group='trial',a=id(),b=id(),name=id();let store=await Store.open(name,indexedDB);
 const roster={version:1,groupId:group,members:[{memberId:a,activityName:'確認班',role:'ADMIN',deviceIds:[a]},{memberId:b,activityName:'連絡班',role:'MEMBER',deviceIds:[b]}]};
 await store.importRoster(roster,a);
 const first=id();for(const [actor,at,eventId] of [[a,0,first],[b,1,id()]])await store.commandQuake({groupId:group,eventId,actorDeviceId:actor,operatedAt:at,action:'REPORT',training:false,title:'地震',area:'地域'});
 const reportId=id(),change={type:'REPORT',reportId,category:'道路閉塞',latitude:35,longitude:139,description:'倒木'};
 const e={groupId:group,eventId:reportId,actorDeviceId:a,operatedAt:2,incidentId:first,change,replaces:[]};
 await store.appendResponse(e);await store.appendResponse({...e,eventId:id(),operatedAt:3,change:{...change,description:'通行止め'},replaces:[reportId]});
 store.close();store=await Store.open(name,indexedDB);
 const result=await store.reportRegionSummary(group,first,catalog,{[reportId]:['a','b']});
 assert.equal(result.regions.find(r=>r.regionId==='city').count,1);
 assert.deepEqual(result.regions.find(r=>r.regionId==='city').targets,[{kind:'REPORT',id:reportId}]);
 await assert.rejects(store.reportRegionSummary(group,id(),catalog,{}));store.close();
});
