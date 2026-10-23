import test from 'node:test';
import assert from 'node:assert/strict';
import {people} from './data.mjs';
import {NOW,THREE_DAYS} from './clustering.mjs';
import {elapsedLabel,scopedPeople,filterPeople,listCounts} from './people-view.mjs';

test('group counts retain old positions while never-received positions remain unknown',()=>{
  const counts=listCounts(people);
  assert.equal(counts.all,26);
  assert.equal(counts.unknown,1);
  assert.equal(counts.sos+counts.unanswered+counts.safe+counts.unknown,counts.all);
  const staleSOS={...people[0],acquiredAt:NOW-THREE_DAYS-1};
  assert.equal(filterPeople([staleSOS],'sos').length,1);
  assert.equal(filterPeople([staleSOS],'unknown').length,0);
});
test('area list uses the exact member ids, independent of global list',()=>{
  const ids=['p0','p1','p8','p0'];
  const area=scopedPeople(people,ids);
  assert.deepEqual(area.map(p=>p.id),['p0','p1','p8']);
  assert.equal(listCounts(area).sos,2);
  assert.equal(listCounts(area).safe,1);
  assert.deepEqual(scopedPeople(people,[]),[]);
  assert.equal(scopedPeople(people).length,26);
});
test('rescue and correction update the selected filter without duplicating a person',()=>{
  const row={...people[0]};
  const area=[row];
  assert.equal(filterPeople(area,'sos').length,1);
  row.s='rescued';
  assert.equal(filterPeople(area,'sos').length,0);
  assert.equal(filterPeople(area,'safe').length,1);
  row.s='sos';
  assert.equal(filterPeople(area,'sos').length,1);
  assert.equal(listCounts(area).all,1);
});
test('elapsed time starts at the first report and changes at minute boundaries',()=>{
  const start=100000;
  assert.equal(elapsedLabel(start-1,start),'0分前');
  assert.equal(elapsedLabel(start+59999,start),'0分前');
  assert.equal(elapsedLabel(start+60000,start),'1分前');
  assert.equal(elapsedLabel(start+3600000,start),'1時間前');
  assert.equal(elapsedLabel(start+3660000,start),'1時間1分前');
  assert.equal(elapsedLabel(),'28分前');
});
