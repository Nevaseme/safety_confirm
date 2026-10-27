import test from 'node:test';
import assert from 'node:assert/strict';
import {createTownFixtures} from './town-fixtures.mjs';
import {people,homes} from './data.mjs';
import {safetyStatistics,projectPerson,municipalMapBounds} from './visibility-model.mjs';

const inside=([lng,lat],[[w,s],[e,n]])=>lng>=w&&lng<=e&&lat>=s&&lat<=n;
test('detail examples restore observations without changing town membership, locations or privacy',()=>{
  const baseline=createTownFixtures(people,homes),fixture=createTownFixtures(people,homes,{observations:true});
  assert.deepEqual(fixture.homes,baseline.homes);assert.deepEqual(fixture.bounds,baseline.bounds);assert.deepEqual(fixture.reports,baseline.reports);
  assert.deepEqual(fixture.people.map(p=>[p.id,p.regionId,p.homeId,p.address,p.s,p.location]),baseline.people.map(p=>[p.id,p.regionId,p.homeId,p.address,p.s,p.location]));
  const target=fixture.people.find(p=>p.regionId==='east'&&p.s==='sos');
  assert.equal(target.impact,true);assert.equal(target.enclosed,true);
  const context={role:'resident',regionId:'east'},timing={eewReceivedAt:0,now:1000};
  const visible=projectPerson(target,context,timing);
  for(const key of ['impact','enclosed','impactAt','enclosedAt','acquiredAt','accuracy','answeredAt'])assert.equal(visible[key],target[key]);
  for(const key of ['address','homeId','bedroom'])assert.ok(!(key in visible));
  assert.equal(projectPerson(target,{role:'municipal',regionId:'east'},timing),null);
  assert.equal(projectPerson(target,{role:'resident',regionId:'west'},timing),null);
  const unknown=fixture.people.find(p=>p.regionId==='east'&&p.s==='unanswered'&&!p.location);
  assert.equal(projectPerson(unknown,context,{...timing,now:179999}),null);
  assert.equal(projectPerson(unknown,context,{...timing,now:180000}).address,unknown.address);
});
test('each demo town contains several households, residents and all safety states',()=>{
  const fixture=createTownFixtures(people,homes);
  assert.equal(new Set(fixture.people.map(p=>p.id)).size,36);
  for(const id of ['west','central','east']){
    const members=fixture.people.filter(p=>p.regionId===id),houses=fixture.homes.filter(h=>h.regionId===id);
    assert.equal(members.length,12);assert.equal(houses.length,4);
    assert.equal(new Set(members.map(p=>p.homeId)).size,4);
    assert.deepEqual(safetyStatistics(members).find(t=>t.regionId===id).counts,{sos:1,unanswered:2,safe:8,rescued:1});
    for(const p of members){
      const home=fixture.homes.find(h=>h.id===p.homeId);
      assert.equal(home.regionId,id);assert.equal(p.address,home.name);
      assert.ok(inside(home.location,fixture.bounds[id]));
      if(p.location)assert.ok(inside(p.location,fixture.bounds[id]));
    }
  }
  assert.equal(fixture.people.find(p=>p.id==='p5').regionId,'east');
  assert.ok(fixture.people.some(p=>p.regionId==='central'&&p.s==='unanswered'&&!p.location));
});
test('west, central and east occupy separate correctly ordered map areas; reports stay in their own area',()=>{
  const fixture=createTownFixtures(people,homes);
  assert.ok(fixture.bounds.west[1][0]<fixture.bounds.central[0][0]);
  assert.ok(fixture.bounds.central[1][0]<fixture.bounds.east[0][0]);
  for(const id of ['west','central','east'])assert.ok(inside(fixture.centers[id],fixture.bounds[id]));
  for(const [id,report] of Object.entries(fixture.reports))assert.ok(inside(report.location,fixture.bounds[report.regionId]),id);
  const city=municipalMapBounds([],fixture.centers);
  for(const center of Object.values(fixture.centers))assert.ok(inside(center,city));
});
test('fixture creation preserves legacy data and disclosure rules in the corrected geography',()=>{
  const before=JSON.stringify({people,homes}),fixture=createTownFixtures(people,homes);
  assert.equal(JSON.stringify({people,homes}),before);
  const target=fixture.people.find(p=>p.regionId==='central'&&p.s==='unanswered');
  const timing={eewReceivedAt:0,now:179999};
  assert.equal(projectPerson(target,{role:'resident',regionId:'central'},timing),null);
  assert.ok(projectPerson(target,{role:'resident',regionId:'central'},{...timing,now:180000}));
  assert.equal(projectPerson(target,{role:'resident',regionId:'east'},{...timing,now:180000}),null);
  assert.equal(projectPerson(target,{role:'municipal',regionId:'central'},{...timing,now:180000}),null);
});
