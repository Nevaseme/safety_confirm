import test from 'node:test';
import assert from 'node:assert/strict';
import {townSummary,clusterScreenItems} from './map-overview-model.mjs';

const people=[{id:'p',regionId:'east',s:'sos',name:'非公開の個人',address:'非公開住所'},{id:'q',regionId:'east',s:'unanswered'},{id:'r',regionId:'west',s:'sos'}];
const material=(name,unit,needed,arranged)=>({name,unit,needed,arranged});
const rows=[
  {id:'a',regionIds:['east'],status:'未対応',urgency:'高',materials:[material('水','本',20,10),material('水','箱',4,1)]},
  {id:'b',regionIds:['east'],status:'対応予定',urgency:'高',materials:[material('水','本',12,4)]},
  {id:'c',regionIds:['east'],status:'対応済み',urgency:'高',materials:[material('水','本',50,0)]},
  {id:'d',regionIds:['east'],status:'対応中',urgency:'高',cancelled:true,materials:[material('水','本',100,0)]},
  {id:'s',personId:'p',regionIds:['east'],status:'対応中',urgency:'高',materials:[material('毛布','枚',5,2)]},
  {id:'z',regionIds:['west'],status:'対応中',urgency:'高',materials:[material('水','本',200,0)]}
];
test('town summary separates safety from unfinished high reports and excludes completed/cancelled requests',()=>{
  const summary=townSummary('east',people,rows);
  assert.deepEqual(summary.safety,{sos:1,unanswered:1,safe:0,rescued:0});
  assert.equal(summary.highReports,2);assert.equal(summary.needsMaterials,true);
  assert.deepEqual(summary.shortages,[{name:'水',unit:'本',quantity:18},{name:'水',unit:'箱',quantity:3},{name:'毛布',unit:'枚',quantity:3}]);
  assert.ok(!JSON.stringify(summary).includes('非公開'));assert.ok(!JSON.stringify(summary).includes('personId'));
});
test('same case is counted once; fulfilled requests and empty regions have no outstanding shortage',()=>{
  assert.equal(townSummary('east',people,[...rows,rows[0]]).highReports,2);
  assert.equal(townSummary('central',people,rows).needsMaterials,false);
  const full=[{...rows[0],materials:[material('水','本',5,8)]}];
  assert.deepEqual(townSummary('east',people,full).shortages,[]);
});
test('mixed SOS/report collisions become one selectable group without dropping any target',()=>{
  const nodes=clusterScreenItems([{id:'sos-a',kind:'sos',x:100,y:100,width:72,height:64},{id:'road-a',kind:'report',x:120,y:100,width:44,height:44},{id:'road-b',kind:'report',x:300,y:100,width:44,height:44}]);
  assert.equal(nodes.length,2);assert.deepEqual(nodes[0].items.map(i=>i.id),['sos-a','road-a']);
  assert.equal(nodes[0].x,110);assert.equal(nodes[0].sos,1);assert.equal(nodes[0].reports,1);
  assert.deepEqual(nodes.flatMap(n=>n.items.map(i=>i.id)).sort(),['road-a','road-b','sos-a']);
});
test('larger group labels also merge when needed so final hit areas do not overlap',()=>{
  const items=[0,25,90,160,300].map((x,i)=>({id:String(i),kind:i===0?'sos':'report',x,y:100,width:44,height:44}));
  const nodes=clusterScreenItems(items);
  for(let a=0;a<nodes.length;a++)for(let b=a+1;b<nodes.length;b++){
    const p=nodes[a],q=nodes[b];assert.ok(Math.abs(p.x-q.x)>=(p.width+q.width)/2+6||Math.abs(p.y-q.y)>=(p.height+q.height)/2+6);
  }
  assert.equal(nodes.reduce((sum,n)=>sum+n.items.length,0),5);
});
