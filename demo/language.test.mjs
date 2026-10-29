import test from 'node:test';
import assert from 'node:assert/strict';
import {translateUI} from './language.mjs';
import {visibilityCsv,statisticsCsv} from './visibility-model.mjs';

test('approved work states remain distinct from availability and urgency',()=>{
  assert.deepEqual(['未対応','対応予定','対応中','対応済み'].map(s=>translateUI(s)),['Not started','Acknowledged','In progress','Resolved']);
  assert.equal(translateUI('対応中','en','activity'),'Busy');
  assert.equal(translateUI('未設定','en','urgency'),'Not assessed');
  assert.equal(translateUI('SOS','en','self'),'I need help');
  assert.equal(translateUI('SOS'),'SOS');
});
test('location templates do not swallow their preceding labels',()=>{
  assert.equal(translateUI('最後のスマホ位置　14:58取得・2分前・精度 約9m'),'Last phone location: Recorded at 14:58 · 2 min ago · Accuracy: about 9 m');
  assert.equal(translateUI('築1年'),'1 year old');
  assert.equal(translateUI('1階建'),'1 floor');
  assert.equal(translateUI('位置不明 4人'),'Location unknown: 4');
  assert.equal(translateUI('東町内会 道路閉塞 3件'),'East neighborhood, Blocked road: 3');
  assert.equal(translateUI('10月21日の地震'),'Earthquake on Oct 21');
});
test('Japanese locale preserves every input byte',()=>{
  for(const s of ['対応予定','\n  未対応・通報　','緊急度 高','1階建'])assert.equal(translateUI(s,'ja'),s);
});
test('localized CSV preserves user text, formula protection and disclosure',()=>{
  const row={id:'report-1',category:'道路閉塞',title:'未対応',description:'対応予定・道路閉塞',status:'対応予定',urgency:'未設定',regionIds:['east'],assignees:[],materials:[],reporter:{name:'非公開氏名',address:'=private-address',regionId:'east'}};
  const csv=visibilityCsv([row],{role:'resident',regionId:'west'},[],{},(s,ctx)=>translateUI(s,'en',ctx));
  assert.match(csv,/'=private-address/);assert.ok(!csv.includes('非公開氏名'));
  assert.match(csv,/'?"Acknowledged"/);assert.match(csv,/Not assessed/);
  assert.match(csv,/"未対応"/);assert.match(csv,/対応予定・道路閉塞/);
  assert.match(csv,/Blocked road/);assert.match(csv,/East neighborhood/);
  assert.match(statisticsCsv([],translateUI),/No response/);
  assert.equal(row.status,'対応予定');
});
