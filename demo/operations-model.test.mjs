import test from 'node:test';
import assert from 'node:assert/strict';
import {CATEGORIES,REGIONS,seedCases,filterCases,summarizeCases,caseCategory,casesCsv,saveMaterial} from './operations-model.mjs';

const sample=[
  {id:'a',category:'SOS',regionIds:['east','east-ward'],assignees:[]},
  {id:'b',category:'道路閉塞',regionIds:['central'],assignees:['self','north']},
  {id:'c',category:'建物倒壊',regionIds:['west'],assignees:['north']},
  {id:'d',category:'火災',regionIds:[],assignees:[]}
];
test('region totals equal the four categories and expose exactly the matching case IDs',()=>{
  const city=summarizeCases(sample,'city');
  assert.equal(city.total,3);
  assert.deepEqual(city.counts,{'SOS':1,'道路閉塞':1,'建物倒壊':1,'その他':0});
  assert.equal(summarizeCases(sample,'east-ward').total,2);
  assert.deepEqual(summarizeCases(sample,'east').ids,['a']);
  assert.deepEqual(summarizeCases(sample,'city','道路閉塞').ids,['b']);
  assert.deepEqual(summarizeCases(sample,'unassigned').ids,['d']);
  assert.equal(summarizeCases(sample,'all').total,4);
  for(const region of ['all','unassigned',...REGIONS.map(r=>r.id)]){
    const summary=summarizeCases(sample,region);
    assert.equal(Object.values(summary.counts).reduce((a,b)=>a+b,0),summary.total);
    for(const type of CATEGORIES)assert.equal(summarizeCases(sample,region,type).total,summary.counts[type]);
  }
});
test('repeated IDs and multiple region memberships never inflate a parent total',()=>{
  const duplicate={...sample[0],regionIds:['east','central']};
  assert.equal(summarizeCases([...sample,duplicate],'city').total,3);
  assert.equal(summarizeCases([...sample,duplicate],'east-ward').total,2);
  assert.equal(summarizeCases([...sample,duplicate],'central').total,2);
});
test('assignment filters count cases once and all includes other responders',()=>{
  assert.deepEqual(filterCases(sample,'none').map(r=>r.id),['a','d']);
  assert.deepEqual(filterCases(sample,'mine').map(r=>r.id),['b']);
  assert.equal(filterCases(sample,'all').length,4);
  assert.equal(caseCategory('通行止め'),'その他');
});
test('category and region corrections immediately change matching totals',()=>{
  const rows=structuredClone(sample);
  rows[1].category='建物倒壊';rows[1].regionIds=['west'];
  assert.equal(summarizeCases(rows,'east-ward','道路閉塞').total,0);
  assert.equal(summarizeCases(rows,'west','建物倒壊').total,2);
});
test('seed supports all categories and counts every SOS person once',()=>{
  const rows=seedCases([{id:'p0',name:'架空の人',s:'sos',memo:'助けて'},{id:'p1',s:'safe'}]);
  assert.equal(rows.filter(r=>r.category==='SOS').length,1);
  assert.ok(CATEGORIES.every(type=>rows.some(r=>caseCategory(r.category)===type)));
});
test('material updates validate quantities and keep the original unit',()=>{
  const row={materials:[]};
  saveMaterial(row,{id:'m',name:'水',unit:'本',needed:3,arranged:1});
  assert.equal(row.materials[0].shortfall,2);
  assert.throws(()=>saveMaterial(row,{id:'m',name:'水',unit:'箱',needed:3,arranged:1}));
  assert.throws(()=>saveMaterial(row,{id:'n',name:'水',unit:'本',needed:-1,arranged:1}));
});
test('CSV uses the same cases and protects quotes, newlines, formula text and safe names',()=>{
  const csv=casesCsv([{...sample[0],title:'=1+1',description:'一行\n二行',materials:[],history:[],status:'未対応',personId:'p'}],[{id:'p',s:'safe'}]);
  assert.ok(csv.startsWith('\uFEFF'));
  assert.ok(!csv.includes('=1+1'));
  const other=casesCsv([{...sample[1],title:'=1+1',description:'一行\n二行',materials:[],history:[],status:'未対応'}]);
  assert.ok(other.includes("'=1+1"));assert.ok(other.includes('一行\n二行'));assert.ok(other.endsWith('\r\n'));
});
