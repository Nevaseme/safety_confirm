import {installMapOverview} from './map-overview-demo.mjs?v=4';
import {createTownFixtures} from './town-fixtures.mjs?v=3';
import {OperationsDemo} from './operations-demo.mjs?v=5';
import {REGIONS,regionName,filterCases,summarizeCases} from './operations-model.mjs?v=4';
import {STATES,NOW,hasPosition,buildIndex} from './clustering.mjs?v=7';
import {disclosureStart,recordTrigger,canSeePerson,projectPerson,projectReport,visibilityCsv,statisticsCsv,safetyStatistics,reportIcon,viewingScope,municipalMapBounds,TOWN_CENTERS} from './visibility-model.mjs?v=3';

const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon=name=>`<i class="ph-fill ph-${name}" aria-hidden="true"></i>`;
const labels={sos:'SOS',unanswered:'反応なし',safe:'無事',rescued:'救助済み'};
const roles={resident:'一般住民',area:'エリア管理者',municipal:'自治体職員'};
const leaves=REGIONS.filter(r=>r.depth===2);
const addresses={east:'木山123-1',central:'木山123-2',west:'木山124-1'};
const homeRegions={yamada:'east',sato:'central',tanaka:'west'};
const opts=(pairs,current)=>pairs.map(([value,label])=>`<option value="${esc(value)}" ${String(value)===String(current)?'selected':''}>${esc(label)}</option>`).join('');
const symbol=p=>`<span class="symbol ${p.s}">${p.s==='sos'?'SOS':p.s==='unanswered'?'?':icon(p.s==='safe'?'check-fat':'user-check')}</span>`;
const personLabel=p=>p.s==='safe'?'無事の人':p.s==='rescued'?'救助済みの人':p.name;
const reportSymbol=row=>{const src=reportIcon(row.urgency);return src?`<img class="visibility-report-icon" src="${src}" alt="緊急度 ${esc(row.urgency)}">`:icon('flag');};
const saveCsv=(csv,name)=>{const url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'})),link=document.createElement('a');link.href=url;link.download=name;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};

class VisibilityOperations extends OperationsDemo{
  constructor(frame,visibility,refreshAll){
    super(frame,{people:visibility.rawPeople,refreshAll,feedback:true,fourStage:true,assignmentWarning:true});
    this.visibility=visibility;visibility.ops=this;this.filter='all';
    this.frame.phone.parentElement.id=frame.config.id+'-demo';
    for(const row of this.cases){
      if(row.personId){row.regionIds=[visibility.rawPeople.find(p=>p.id===row.personId).regionId];continue;}
      const regionId=row.regionIds[0];row.reporter={name:regionId==='east'?'架空の高橋 一郎':regionId==='central'?'架空の中村 花子':'架空の小林 次郎',address:addresses[regionId],regionId};
      const fixture=frame.config.townFixtures?.reports[row.id];
      if(fixture){row.location=[...fixture.location];row.regionIds=[fixture.regionId];row.reporter.address=visibility.homes.find(h=>h.regionId===fixture.regionId).name;}
    }
    Object.assign(this.find('road-1'),{urgency:'高',reason:'倒木で救急車が通れません。'});
    Object.assign(this.find('building-1'),{urgency:'中',reason:'周辺を規制し、現地確認を待っています。'});
    Object.assign(this.find('road-2'),{urgency:'低',reason:'迂回路を確認しています。'});
    // The parent installs a person-detail link; replace it with the same disclosure gate as the list.
    frame.openPerson=person=>{if(!person||!visibility.allowed(person.id)){visibility.denied();return;}frame.openVisiblePerson(person);const row=this.cases.find(r=>r.personId===person.id);if(row){const button=document.createElement('button');button.className='secondary full ops-detail-link';button.textContent='担当・対応を見る';button.onclick=()=>this.openCase(row.id);frame.body.append(button);}};
    const menu=frame.showMenu.bind(frame);frame.showMenu=()=>{menu();for(const button of frame.body.querySelectorAll('button'))if(button.textContent.startsWith('操作する役割'))button.textContent='表示する立場・町内会・時間';if(frame.config.townFixtures)frame.body.querySelector('[data-menu=home]').onclick=()=>frame.showHome(frame.homeById(visibility.rawPeople.find(p=>p.id==='p5').homeId));};
    this.setRole();this.showQueue();
  }
  setRole(){this.role=this.visibility.viewer.role==='resident'?'MEMBER':this.visibility.viewer.role==='area'?'LEADER':'ADMIN';}
  syncSafety(){super.syncSafety();for(const row of this.cases)if(row.personId)row.regionIds=[this.people.find(p=>p.id===row.personId).regionId];this.visibility.refreshPeople();this.render();}
  visible(rows){return rows.filter(r=>projectReport(r,this.visibility.viewer,this.people,this.visibility.timing()));}
  render(){
    if(!this.visibility)return super.render();
    if(this.route.view==='summary')return this.renderSummary();
    if(this.route.view==='drill')return this.renderDrill();
    const all=this.cases;this.cases=this.visible(all);try{super.render();}finally{this.cases=all;}
  }
  rowsHTML(rows){
    if(!this.visibility)return super.rowsHTML(rows);
    const projected=rows.map(r=>projectReport(r,this.visibility.viewer,this.people,this.visibility.timing())).filter(Boolean);
    if(!projected.length)return '<p class="ops-empty" role="status">個別に表示できる通報はありません。</p>';
    const container=document.createElement('div');container.innerHTML=super.rowsHTML(projected);
    for(const button of container.querySelectorAll('[data-case]')){const row=projected.find(r=>r.id===button.dataset.case);if(!row.personId){const mark=button.querySelector('.ops-case-symbol');mark.innerHTML=reportSymbol(row);mark.classList.add('visibility-case-symbol');}}
    return container.innerHTML;
  }
  openCase(id){
    if(!this.visibility)return super.openCase(id);
    const row=this.find(id),publicRow=row&&projectReport(row,this.visibility.viewer,this.people,this.visibility.timing());
    if(!publicRow){this.visibility.denied();return;}
    super.openCase(id);
    if(publicRow.reporter){const note=document.createElement('p');note.className='visibility-reporter';note.textContent=`通報者：${publicRow.reporter.name?publicRow.reporter.name+' ／ ':''}${publicRow.reporter.address||'住所未登録'}`;this.frame.body.prepend(note);}
    if(!row.personId){const mark=document.createElement('span');mark.className='visibility-detail-icon';mark.innerHTML=reportSymbol(row);this.frame.head.querySelector('h2').prepend(mark);}
  }
  record(row,text){
    if(this.visibility&&!row.personId&&!row.reporter)row.reporter={name:'架空の自分',address:this.frame.config.townFixtures?this.visibility.homes.find(h=>h.regionId===this.visibility.viewer.regionId).name:addresses[this.visibility.viewer.regionId],regionId:this.visibility.viewer.regionId};
    super.record(row,text);this.visibility?.refreshPeople();
  }
  roleForm(){if(this.visibility)this.visibility.settings();else super.roleForm();}
  drawReports(){super.drawReports();if(!this.visibility)return;for(const [id,marker] of this.reportMarkers){const row=this.find(id);marker.getElement().innerHTML=reportSymbol(row);marker.getElement().setAttribute('aria-label',`${row.category}・緊急度 ${row.urgency}の通報`);}}
  renderSummary(){
    super.renderSummary();this.panel.querySelector('.ops-subhead').insertAdjacentHTML('afterend',this.visibility.statisticsHTML());
    this.panel.querySelector('.ops-summary-note').textContent='件数から、閲覧できる通報の詳細を開きます。安否の非公開対象は統計だけを表示します。';
    const button=document.createElement('button');button.className='ops-csv';button.textContent='安否の統計をCSVで書き出す';button.onclick=()=>saveCsv(statisticsCsv(this.people),'PlanD-demo-safety-statistics.csv');this.panel.append(button);
  }
  renderDrill(){
    const {regionId,type}=this.route,summary=summarizeCases(this.cases,regionId,type),raw=summary.ids.map(id=>this.find(id)),visible=this.visible(raw);
    this.panel.innerHTML=`<div class="ops-subhead"><button class="ops-back" data-back>地域集計に戻る</button><h2>${esc(regionId==='all'?'全地域':regionName(regionId))}</h2><p>${esc(type||'すべての種類')} ${summary.total}件</p>${visible.length<raw.length?'<p class="visibility-note">安否の個別情報は、閲覧できる対象だけを表示します。</p>':''}</div><div class="ops-case-list">${this.rowsHTML(visible)}</div><button class="ops-csv" data-csv>閲覧できる通報をCSVで書き出す</button>`;
    this.panel.querySelector('[data-back]').onclick=()=>this.showSummary();this.panel.querySelector('[data-csv]').onclick=()=>this.downloadCsv(raw);this.bindRows();
  }
  downloadCsv(rows){if(!this.visibility)return super.downloadCsv(rows);saveCsv(visibilityCsv(rows,this.visibility.viewer,this.people,this.visibility.timing()),'PlanD-demo-visible-reports.csv');this.frame.toast('閲覧できる通報をCSVに書き出しました');}
}

class VisibilityController{
  constructor(frame,people,homes){
    this.frame=frame;this.homes=homes;this.viewer={role:'resident',regionId:'east'};this.rawPeople=structuredClone(people);
    if(!frame.config.townFixtures){
      this.rawPeople.find(p=>p.id==='unknown1').homeId='yamada';
      this.rawPeople.find(p=>p.id==='p5').homeId='yamada';
      for(const p of this.rawPeople){p.regionId=homeRegions[p.homeId]||'east';p.address=homes.find(h=>h.id===p.homeId)?.name||'住所未登録';}
    }
    this.clock={eewReceivedAt:NOW,firstReportedAt:NOW+30000};this.elapsed=0;this.startedAt=Date.now();this.statMarkers=new Map();
    const toolbar=document.createElement('div');toolbar.className='visibility-toolbar';toolbar.innerHTML='<button data-view-settings></button><span data-demo-clock></span>';frame.phone.querySelector('.top').after(toolbar);this.toolbar=toolbar;toolbar.querySelector('button').onclick=()=>this.settings();
    frame.phone.classList.add('visibility-version');frame.visibility=this;
    const cityButton=document.createElement('button');cityButton.className='visibility-city-overview';cityButton.textContent='市全体';cityButton.hidden=true;cityButton.onclick=()=>this.showCityMap();frame.phone.querySelector('.map-area').append(cityButton);this.cityButton=cityButton;
    const recenter=frame.phone.querySelector('.recenter'),originalRecenter=recenter.onclick;recenter.onclick=()=>this.viewer.role==='municipal'?this.showCityMap():frame.config.townFixtures?this.showTownMap(this.viewer.regionId):originalRecenter();
    frame.map.on('load',()=>{if(this.viewer.role==='municipal'&&frame.page==='map')this.showCityMap();});
    this.timer=setInterval(()=>{this.updateToolbar();const ready=this.ready();if(ready!==this.lastReady){this.lastReady=ready;this.apply();}},1000);
  }
  timing(){return {...this.clock,now:NOW+this.elapsed*1000+Date.now()-this.startedAt};}
  ready(){const t=this.timing(),start=disclosureStart(t);return start!==null&&t.now>=start+180000;}
  allowed(id){const p=this.rawPeople.find(p=>p.id===id);return p&&canSeePerson(p,this.viewer,this.timing());}
  projectPeople(){return this.rawPeople.map(p=>projectPerson(p,this.viewer,this.timing())).filter(Boolean);}
  refreshPeople(){this.frame.config.people=this.projectPeople();this.frame.index=buildIndex(Supercluster,this.frame.people,this.frame.showSafe,this.frame.clusterEndZoom);this.frame.revision++;this.frame.render();this.frame.refreshList?.();}
  updateToolbar(){const sec=Math.max(0,Math.floor((this.timing().now-(disclosureStart(this.clock)??NOW))/1000));this.toolbar.querySelector('button').textContent=`${roles[this.viewer.role]}・${viewingScope(this.viewer).label}`;this.toolbar.querySelector('[data-demo-clock]').textContent=`経過 ${Math.floor(sec/60)}:${String(sec%60).padStart(2,'0')}`;}
  apply(){
    this.lastReady=this.ready();this.updateToolbar();this.ops?.setRole();this.refreshPeople();
    this.cityButton.hidden=this.viewer.role!=='municipal';this.frame.phone.querySelector('.recenter').setAttribute('aria-label',this.viewer.role==='municipal'?'市全体を表示':'表示の中心に戻る');
    const unknown=this.frame.people.filter(p=>!hasPosition(p)).length;this.frame.phone.querySelector('.unknown').innerHTML=`${icon('user')}位置不明 ${unknown}人${icon('caret-right')}`;
    this.ops?.render();this.ops?.drawReports();
  }
  statisticsHTML(){return `<section class="visibility-statistics"><h3>町内会別の安否統計</h3><div class="visibility-stat-scroll"><table><thead><tr><th>町内会</th>${STATES.map(s=>`<th>${labels[s]}</th>`).join('')}</tr></thead><tbody>${safetyStatistics(this.rawPeople).map(r=>`<tr><th>${esc(r.name)}</th>${STATES.map(s=>`<td>${r.counts[s]}</td>`).join('')}</tr>`).join('')}</tbody></table></div></section>`;}
  showTownMap(id){this.frame.closeSheet();this.frame.map.fitBounds(this.frame.config.townFixtures.bounds[id],{padding:60,maxZoom:16.8,duration:300});}
  showCityMap(){
    if(!this.frame.map.getLayer('preview-building-rating'))return;
    if(this.frame.showMunicipalOverview){this.frame.showMunicipalOverview();return;}
    this.frame.map.fitBounds(municipalMapBounds(this.ops?.cases||[],this.frame.config.townFixtures?.centers),{padding:{top:220,bottom:60,left:64,right:64},maxZoom:15.9,duration:300});
  }
  denied(){this.frame.openSheet('<h2>安否の統計</h2>',this.statisticsHTML()+'<p class="visibility-note">この立場・町内会・時間では、個人の詳細を表示しません。</p>');}
  settings(){
    const elapsed=Math.floor((this.timing().now-NOW)/1000);
    this.ops.form('表示条件を試す',`<p class="visibility-note">架空データの表示確認用です。</p><label class="ops-field">表示する立場<select name="viewer-role">${opts(Object.entries(roles),this.viewer.role)}</select></label><div data-resident-region><label class="ops-field">所属・担当する町内会<select name="viewer-region">${opts(leaves.map(r=>[r.id,r.name]),this.viewer.regionId)}</select></label></div><div class="visibility-city-scope" data-city-scope hidden><span>表示範囲</span><strong>架空市全域</strong><small>東町内会・中央町内会・西町内会</small></div><label class="ops-field">経過時間<select name="demo-elapsed">${opts([[0,'開始直後'],[179,'2分59秒'],[180,'3分00秒'],[240,'4分後']],elapsed<179?0:elapsed<180?179:elapsed<240?180:240)}</select></label><label class="ops-field">最初のきっかけ<select name="trigger">${opts([['eew','速報を受信 → 30秒後に最初の地震報告'],['report','最初の地震報告 → 30秒後に速報'],['eew-only','速報だけ'],['report-only','地震報告だけ']],this.scenario||'eew')}</select></label><p class="visibility-note">3分間は反応なしの個人情報を表示しません。3分後も、他の町内会・自治体職員には安否の統計だけを表示します。</p><button type="button" class="ops-text-button" data-later-trigger>追加の速報・地震報告を受ける</button>`,data=>{
      this.viewer={role:data.get('viewer-role'),regionId:data.get('viewer-region')||this.viewer.regionId};this.scenario=data.get('trigger');
      this.clock={eewReceivedAt:this.scenario==='report-only'?null:NOW+(this.scenario==='report'?30000:0),firstReportedAt:this.scenario==='eew-only'?null:NOW+(this.scenario==='eew'?30000:0)};
      this.elapsed=Number(data.get('demo-elapsed'));this.startedAt=Date.now();this.frame.closeSheet();this.apply();if(this.frame.page==='map'){if(this.viewer.role==='municipal')this.showCityMap();else if(this.frame.config.townFixtures)this.showTownMap(this.viewer.regionId);}
    },'表示を切り替える');
    this.frame.body.querySelector('[data-later-trigger]').onclick=()=>{const at=this.timing().now;recordTrigger(this.clock,'eewReceivedAt',at);recordTrigger(this.clock,'firstReportedAt',at);this.frame.toast('追加分を受信しました。3分の起点は変わりません。');};
    const role=this.frame.body.querySelector('[name=viewer-role]'),region=this.frame.body.querySelector('[name=viewer-region]');
    const updateScope=()=>{const municipal=role.value==='municipal';this.frame.body.querySelector('[data-resident-region]').hidden=municipal;region.disabled=municipal;this.frame.body.querySelector('[data-city-scope]').hidden=!municipal;};
    role.onchange=updateScope;updateScope();
  }
  drawStatisticsMarkers(){
    if(!this.frame.map.getLayer('preview-building-rating'))return;
    const stats=safetyStatistics(this.rawPeople),wanted=new Set();
    for(const region of stats){
      if(this.frame.config.townDisplayFixes&&this.viewer.role!=='municipal'&&region.regionId===this.viewer.regionId)continue;
      const hidden=this.rawPeople.filter(p=>p.regionId===region.regionId&&!this.allowed(p.id)&&(this.frame.showSafe||!['safe','rescued'].includes(p.s)));if(!hidden.length)continue;wanted.add(region.regionId);
      let marker=this.statMarkers.get(region.regionId);
      if(!marker){const button=document.createElement('button');button.className='visibility-stat-marker';button.onclick=event=>{event.stopPropagation();this.denied();};marker=new maplibregl.Marker({element:button,anchor:'bottom'}).setLngLat((this.frame.config.townFixtures?.centers||TOWN_CENTERS)[region.regionId]).addTo(this.frame.map);this.statMarkers.set(region.regionId,marker);}
      const counts=this.frame.config.townFixtures?STATES.map(s=>region.counts[s]):STATES.map(s=>hidden.filter(p=>p.s===s).length),button=marker.getElement();button.innerHTML=`<strong>${esc(region.name)}</strong>${STATES.map((s,j)=>counts[j]||this.frame.config.townFixtures?`<span>${labels[s]} ${counts[j]}人</span>`:'').join('')}`;button.setAttribute('aria-label',`${region.name}の安否統計・個人情報非表示`);
    }
    for(const [id,marker] of this.statMarkers)if(!wanted.has(id)){marker.remove();this.statMarkers.delete(id);}
  }
}

export function mountVisibilityDemo({ListMapFrame,createFrame,people,homes,zoom,refreshAll,overview=false,wideAreaClusters=false,correctedTowns=false,restoredPersonDetails=false,earthquakeTitle=false,namedResidents=false,townDisplayFixes=false}){
  class VisibilityFrame extends ListMapFrame{
    showMap(){const entering=this.page!=='map';super.showMap();if(entering&&this.visibility?.viewer.role==='municipal')this.visibility.showCityMap();}
    personById(id){return id==='p5'&&this.visibility?this.visibility.rawPeople.find(p=>p.id===id):super.personById(id);}
    refreshSource(){if(this.visibility)this.config.people=this.visibility.projectPeople();}
    render(){super.render();this.visibility?.drawStatisticsMarkers();}
    showSafety(){super.showSafety();if(this.visibility){const note=document.createElement('p');note.className='visibility-note';note.textContent='このデモで自分の安否を記録する利用者は、架空の東町内会に所属しています。';this.phone.querySelector('.safety-page h2').after(note);}}
    refreshList(){
      if(!this.visibility||this.page!=='people')return super.refreshList();
      if(this.visibility.viewer.role==='municipal'){this.peoplePage.innerHTML='<div class="people-heading"><h2 tabindex="-1">安否統計</h2></div>'+this.visibility.statisticsHTML()+'<p class="visibility-note">自治体職員には安否の統計だけを表示します。</p><button class="ops-csv" data-statistics-csv>安否の統計をCSVで書き出す</button>';this.peoplePage.querySelector('[data-statistics-csv]').onclick=()=>saveCsv(statisticsCsv(this.visibility.rawPeople),'PlanD-demo-safety-statistics.csv');return;}
      super.refreshList();this.peoplePage.querySelector('.people-heading p').textContent='個別に確認できる人';
      this.peoplePage.insertAdjacentHTML('afterbegin',this.visibility.statisticsHTML());
      if(this.visibility.viewer.role==='municipal')this.peoplePage.insertAdjacentHTML('beforeend','<p class="visibility-note">自治体職員には安否の統計だけを表示します。</p>');
      else if(!this.visibility.ready())this.peoplePage.insertAdjacentHTML('beforeend','<p class="visibility-note">3分間は反応なしの個人情報を表示しません。</p>');
    }
    openVisiblePerson(person){
      const p=this.people.find(row=>row.id===person.id);if(!p){this.visibility.denied();return;}
      const known=hasPosition(p);
      if(this.config.restoredPersonDetails){this.showPersonDetail(p,{showResidence:!known,actionPerson:this.visibility.rawPeople.find(row=>row.id===p.id)});return;}
      this.openSheet(`<h2>${esc(personLabel(p))}</h2><p class="ops-type-label">${labels[p.s]}・${esc(regionName(p.regionId))}</p>`,`<p>${known?'最後のスマホ位置を地図に表示しています。':'位置不明'}</p>${p.s==='sos'&&p.memo?`<h3>SOSメモ</h3><p>${esc(p.memo)}</p>`:''}${!known?`<h3>登録住居・寝室</h3><p>${esc(p.address||'住所未登録')}</p><p>${esc(p.bedroom||'寝室未登録')}</p><button class="ops-text-button" data-home>建物を見る</button>`:''}<details class="ops-history"><summary>救助・確認の記録 ${p.history?.length||0}件</summary>${this.historyHTML(p)}</details>`,['sos','unanswered'].includes(p.s)?'<button class="primary blue" data-rescue>救助済みにする</button>':p.s==='rescued'?'<button class="secondary full" data-correct>救助記録を訂正する</button>':'');
      this.footer.querySelector('[data-rescue]')?.addEventListener('click',()=>this.rescueForm(this.visibility.rawPeople.find(r=>r.id===p.id)));
      this.footer.querySelector('[data-correct]')?.addEventListener('click',()=>this.correctForm(this.visibility.rawPeople.find(r=>r.id===p.id)));
      this.body.querySelector('[data-home]')?.addEventListener('click',()=>this.showHome(this.homeById(p.homeId)));
    }
    rowsHTML(list){
      if(!this.visibility)return super.rowsHTML(list);
      return list.length?list.map(p=>`<button class="person-row" data-id="${esc(p.id)}">${symbol(p)}<span class="row-content"><strong>${esc(personLabel(p))}</strong><small>${hasPosition(p)?labels[p.s]:`位置不明・${esc(p.address||'住所未登録')}`}</small></span>${icon('caret-right')}</button>`).join(''):'<p class="empty-list">個別に表示できる人はいません。</p>';
    }
    showHome(home){
      if(!this.visibility)return super.showHome(home);
      const linked=this.people.filter(p=>p.homeId===home.id);
      const attributes=this.config.townDisplayFixes?`<div class="attrs"><span><small>建築年</small>${home.built}年</span><span><small>建材・構造</small>${esc(home.material)}</span><span><small>階数</small>${home.floors}階建</span></div>`:`<p>建材・構造：${esc(home.material)}／${home.floors}階建</p>`;
      this.openSheet(`<h2>建物の詳細</h2><p>${esc(home.name)}</p>`,`${attributes}<h3>個別に確認できる登録住民</h3>${this.rowsHTML(linked)}`);this.body.querySelectorAll('[data-id]').forEach(b=>b.onclick=()=>this.openPerson(this.personById(b.dataset.id)));
    }
    showLegend(){super.showLegend();this.body.insertAdjacentHTML('beforeend',`<h3>通報の緊急度</h3>${['高','中','低','未設定'].map(urgency=>`<div class="legend-row visibility-legend">${reportSymbol({urgency})}<span>${urgency==='未設定'?'未設定（判定前）':'緊急度 '+urgency}</span></div>`).join('')}`);}
  }
  const config={id:'response-visibility',zoom,enhanced:true,localSituation:true,positionControls:true,people:[],homes:structuredClone(homes),version:'ver 1.6.4',summary:'地域ごとの公開範囲設定と通報のアイコンを追加'};
  if(overview)Object.assign(config,{id:'response-map',version:'ver 1.6.5',summary:'ver 1.6.4の別案'});
  if(wideAreaClusters)Object.assign(config,{id:'response-neighborhood',version:'ver 1.6.6',summary:'クラスタは、さらに広域の表示から',clusterEndZoom:14,wideAreaClusters:true});
  if(correctedTowns){const fixture=createTownFixtures(people,homes,{observations:restoredPersonDetails,namedResidents});if(townDisplayFixes)Object.assign(fixture.people.find(p=>p.id==='p8'),{impact:true,enclosed:true});people=fixture.people;Object.assign(config,{id:'response-towns',version:'ver 1.6.7',summary:'町内会の人数・東西配置・拡大先を修正',townFixtures:fixture,homes:fixture.homes,center:fixture.centers.east});}
  if(restoredPersonDetails)Object.assign(config,{id:'response-details',version:'ver 1.6.8',summary:'公開範囲を保ち、人物詳細の表示を復元',restoredPersonDetails:true});
  if(earthquakeTitle)Object.assign(config,{id:'response-earthquake',version:'ver 1.6.9',summary:'タイトルを「地震発生」に変更',incidentTitle:'地震発生'});
  if(namedResidents)Object.assign(config,{id:'response-names',version:'ver 1.6.10',summary:'住民に自然な氏名を設定'});
  if(townDisplayFixes)Object.assign(config,{id:'response-town-display',version:'ver 1.6.11',summary:'自町内会の吹き出しを非表示、建築年と応答なしの観測例を追加',townDisplayFixes:true});
  const frame=new VisibilityFrame(config,createFrame(config)),visibility=new VisibilityController(frame,people,config.homes);
  new VisibilityOperations(frame,visibility,refreshAll);visibility.apply();if(overview)installMapOverview(frame);return frame;
}
