import test from 'node:test';
import assert from 'node:assert/strict';
import {disclosureStart,recordTrigger,canSeePerson,projectPerson,projectReport,visibilityCsv,safetyStatistics,reportIcon,viewingScope,municipalMapBounds,TOWN_CENTERS} from './visibility-model.mjs';

test('municipal scope covers every town independently of the last selected residential town',()=>{
  for(const regionId of ['east','central','west']){
    const scope=viewingScope({role:'municipal',regionId});
    assert.equal(scope.label,'架空市全域');assert.deepEqual(scope.townIds,['east','central','west']);
  }
  assert.deepEqual(viewingScope({role:'resident',regionId:'west'}).townIds,['west']);
  assert.equal(viewingScope({role:'area',regionId:'east'}).label,'東町内会');
});
test('municipal map bounds include all town summaries and public reports without personal safety positions',()=>{
  const point=[130.9,33.1];
  const bounds=municipalMapBounds([
    {location:point},
    {personId:'private-sos',location:[-170,-70]},
    {cancelled:true,location:[179,80]},
    {location:null}
  ]);
  for(const [lng,lat] of [...Object.values(TOWN_CENTERS),point]){
    assert.ok(lng>=bounds[0][0]&&lng<=bounds[1][0]);assert.ok(lat>=bounds[0][1]&&lat<=bounds[1][1]);
  }
  assert.ok(bounds[0][0]>120&&bounds[1][0]<140);assert.ok(bounds[0][1]>30&&bounds[1][1]<40);
});

const viewer={role:'resident',regionId:'east'};
const timing={eewReceivedAt:1000,firstReportedAt:2000,now:181000};
const sos={id:'a',s:'sos',name:'架空の山田',regionId:'east',address:'架空町123-1',location:[130,32],acquiredAt:1000,homeId:'h',bedroom:'寝室',memo:'救助依頼'};
const unknown={...sos,id:'b',s:'unanswered',name:'架空の森',location:null,acquiredAt:null};
const report={id:'r',title:'道路閉塞',category:'道路閉塞',description:'倒木',regionIds:['west'],assignees:[],status:'未対応',urgency:'高',result:'',reporter:{id:'secret-person',name:'架空の通報者',address:'架空町456-2',regionId:'west'},materials:[],history:[]};

test('three-minute start chooses the first trigger and later triggers do not extend it',()=>{
  assert.equal(disclosureStart(timing),1000);
  assert.equal(disclosureStart({firstReportedAt:0}),0);
  assert.equal(disclosureStart({eewReceivedAt:null,firstReportedAt:null}),null);
  const clock={eewReceivedAt:1000,firstReportedAt:2000};
  recordTrigger(clock,'eewReceivedAt',190000);recordTrigger(clock,'firstReportedAt',200000);
  assert.equal(disclosureStart(clock),1000);
  assert.equal(canSeePerson(unknown,viewer,{...clock,now:180999}),false);
  assert.equal(canSeePerson(unknown,viewer,{...clock,now:181000}),true);
});
test('SOS is immediate inside its town and unknown details wait exactly three minutes',()=>{
  assert.ok(projectPerson(sos,viewer,{...timing,now:1001}));
  assert.equal(projectPerson(unknown,viewer,{...timing,now:180999}),null);
  assert.equal(projectPerson(unknown,viewer,timing).name,'架空の森');
  assert.equal(projectPerson(unknown,viewer,{now:500000}),null);
});
test('other towns and municipal staff receive no individual safety record at any time',()=>{
  for(const role of ['resident','area','municipal']){
    const context={role,regionId:role==='municipal'?'east':'west'};
    for(const person of [sos,unknown,{...sos,s:'safe'},{...sos,s:'rescued'}])assert.equal(projectPerson(person,context,timing),null);
  }
  assert.ok(projectPerson(sos,{role:'area',regionId:'east'},timing));
});
test('known SOS projection contains neither residence address, home link nor bedroom',()=>{
  const projected=projectPerson(sos,viewer,timing);
  assert.equal(projected.name,sos.name);assert.deepEqual(projected.location,[130,32]);
  for(const key of ['address','homeId','bedroom'])assert.ok(!(key in projected));
  const hidden=JSON.stringify(projected);assert.ok(!hidden.includes('123-1'));assert.ok(!hidden.includes('寝室'));
  const permitted=projectPerson(unknown,viewer,timing);
  assert.equal(permitted.address,'架空町123-1');assert.equal(permitted.bedroom,'寝室');
});
test('non-SOS reports remain global and citizens see reporter address without identity',()=>{
  const projected=projectReport(report,viewer,[],timing);
  assert.equal(projected.description,'倒木');assert.deepEqual(projected.reporter,{address:'架空町456-2'});
  const encoded=JSON.stringify(projected);
  assert.ok(!encoded.includes('架空の通報者'));assert.ok(!encoded.includes('secret-person'));
  assert.equal(projectReport(report,{role:'area',regionId:'west'},[],timing).reporter.name,'架空の通報者');
  assert.equal(projectReport(report,{role:'area',regionId:'east'},[],timing).reporter.name,undefined);
  assert.equal(projectReport(report,{role:'municipal',regionId:'east'},[],timing).reporter.name,'架空の通報者');
});
test('SOS report, CSV and statistics obey the same rules without losing aggregate counts',()=>{
  const sosReport={...report,id:'sos-a',category:'SOS',title:sos.name,description:sos.memo,personId:sos.id};
  const foreign={role:'resident',regionId:'west'};
  assert.equal(projectReport(sosReport,foreign,[sos],timing),null);
  const csv=visibilityCsv([report,sosReport],foreign,[sos],timing);
  assert.ok(csv.includes('道路閉塞'));assert.ok(csv.includes('456-2'));
  assert.ok(!csv.includes('架空の山田'));assert.ok(!csv.includes('救助依頼'));assert.ok(!csv.includes('sos-a'));assert.ok(!csv.includes('架空の通報者'));
  const stats=safetyStatistics([sos,unknown,{...sos,id:'c',s:'safe',regionId:'west'}]);
  assert.deepEqual(stats.find(r=>r.regionId==='east').counts,{sos:1,unanswered:1,safe:0,rescued:0});
  assert.ok(!JSON.stringify(stats).includes('架空の山田'));assert.ok(!JSON.stringify(stats).includes('123-1'));
});
test('public CSV hides cancelled records and protects formulas in reporter fields',()=>{
  assert.ok(!visibilityCsv([{...report,cancelled:true}],viewer,[],timing).includes('倒木'));
  const csv=visibilityCsv([{...report,reporter:{...report.reporter,address:'=1+1'}}],viewer,[],timing);
  assert.ok(csv.includes("'=1+1"));assert.ok(csv.startsWith('\uFEFF'));
});
test('urgency icon distinguishes high, medium/low and unassessed',()=>{
  assert.equal(reportIcon('高'),'assets/report-emergency.png');
  assert.equal(reportIcon('中'),'assets/report-normal.png');
  assert.equal(reportIcon('低'),'assets/report-normal.png');
  assert.equal(reportIcon('未設定'),null);
});
