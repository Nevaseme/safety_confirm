import test from 'node:test';
import assert from 'node:assert/strict';
import {createTownFixtures} from './town-fixtures.mjs';
import {people,homes} from './data.mjs';
import {hasPosition,STATES} from './clustering.mjs';
import {safetyStatistics,projectPerson,municipalMapBounds} from './visibility-model.mjs';

const inside=([lng,lat],[[w,s],[e,n]])=>lng>=w&&lng<=e&&lat>=s&&lat<=n;
test('all-pattern fixtures cover eight safety/position pairs with both observations in every town',()=>{
  const baseline=createTownFixtures(people,homes,{observations:true,namedResidents:true});
  const fixture=createTownFixtures(people,homes,{observations:true,namedResidents:true,allSafetyPatterns:true});
  assert.equal(fixture.people.length,45);assert.equal(new Set(fixture.people.map(p=>p.id)).size,45);
  assert.deepEqual(fixture.homes,baseline.homes);assert.deepEqual(fixture.bounds,baseline.bounds);assert.deepEqual(fixture.reports,baseline.reports);
  for(const old of baseline.people){
    const current=fixture.people.find(p=>p.id===old.id);
    for(const key of ['name','s','homeId','regionId','location','acquiredAt','bedroom','memo'])assert.deepEqual(current[key],old[key]);
  }
  for(const regionId of ['east','central','west']){
    const members=fixture.people.filter(p=>p.regionId===regionId);
    assert.equal(members.length,15);
    assert.deepEqual(safetyStatistics(members).find(t=>t.regionId===regionId).counts,{sos:2,unanswered:2,safe:9,rescued:2});
    for(const state of STATES)for(const known of [true,false]){
      const target=members.find(p=>p.s===state&&hasPosition(p)===known&&p.impact&&p.enclosed);
      assert.ok(target,`${regionId}/${state}/${known}`);
      assert.equal(target.impactAt,'14:32');assert.equal(target.enclosedAt,'14:33');
      if(!known){assert.equal(target.location,null);assert.equal(target.acquiredAt,null);}
      if(state==='safe'){assert.ok(target.answeredAt>target.enclosedAt);assert.ok(target.history[0].time>target.enclosedAt);}
      if(state==='rescued'){assert.equal(target.priorSafety,'sos');assert.ok(target.history[0].time>target.enclosedAt);}
    }
  }
  assert.deepEqual(createTownFixtures(people,homes,{observations:true,namedResidents:true}),baseline);
});
test('all-pattern data preserves unanswered waiting and regional disclosure including sensor records',()=>{
  const fixture=createTownFixtures(people,homes,{observations:true,namedResidents:true,allSafetyPatterns:true});
  for(const person of fixture.people.filter(p=>p.regionId==='east')){
    const timing={eewReceivedAt:0,now:180000};
    const projected=projectPerson(person,{role:'resident',regionId:'east'},timing,{usualPlaces:true});
    assert.ok(projected);assert.equal(projected.impact,person.impact);assert.equal(projected.enclosed,person.enclosed);
    if(person.s==='unanswered')assert.equal(projectPerson(person,{role:'resident',regionId:'east'},{...timing,now:179999},{usualPlaces:true}),null);
    for(const viewer of [{role:'resident',regionId:'west'},{role:'municipal',regionId:'east'}])assert.equal(projectPerson(person,viewer,timing,{usualPlaces:true}),null);
  }
});
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
