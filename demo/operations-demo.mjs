import {CATEGORIES,WORK_STATUSES,REGIONS,MEMBERS,seedCases,filterCases,summarizeCases,caseCategory,regionName,memberName,caseTitle,casesCsv,saveMaterial,cancelReport,restoreReport,workSummary,updateWorkProgress,startWork,isAssignedToOthers} from './operations-model.mjs?v=4';

const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon=name=>`<i class="ph-fill ph-${name}" aria-hidden="true"></i>`;
const options=(values,current)=>values.map(([value,label])=>`<option value="${esc(value)}" ${value===current?'selected':''}>${esc(label)}</option>`).join('');
const field=(name,label,value='',type='text')=>`<label class="ops-field">${label}<input name="${name}" type="${type}" value="${esc(value)}" ${type==='number'?'min="0" step="any"':''} required></label>`;
const select=(name,label,values,current)=>`<label class="ops-field">${label}<select name="${name}">${options(values,current)}</select></label>`;
const leaves=()=>[['','地域未設定'],...REGIONS.filter(r=>r.depth===2).map(r=>[r.id,r.name])];
const caseIcon=row=>row.category==='SOS'?'<span class="ops-case-symbol sos">SOS</span>':`<span class="ops-case-symbol report">${icon(caseCategory(row.category)==='道路閉塞'?'road-horizon':caseCategory(row.category)==='建物倒壊'?'house-line':'flag')}</span>`;

export class OperationsDemo{
  constructor(frame,{people,refreshAll,feedback=false,fourStage=false,assignmentWarning=false}){
    this.frame=frame;this.people=people;this.refreshAll=refreshAll;
    this.feedback=feedback;this.fourStage=fourStage;this.assignmentWarning=assignmentWarning;this.picking=false;
    this.cases=seedCases(people,{fourStage});this.filter='none';this.activity='活動可能';this.role='ADMIN';this.route={view:'queue'};this.reportMarkers=new Map();
    const phone=frame.phone;phone.classList.add('operations');phone.parentElement.id=assignmentWarning?'response-assignment-demo':fourStage?'response-stages-demo':feedback?'response-feedback-demo':'response-demo';
    this.panel=document.createElement('section');this.panel.className='response-page';this.panel.setAttribute('aria-label','対応');this.panel.hidden=true;phone.append(this.panel);
    this.summaryButton=document.createElement('button');this.summaryButton.className='ops-region-button';this.summaryButton.textContent='地域集計';this.summaryButton.onclick=()=>this.showSummary();phone.querySelector('.top').append(this.summaryButton);
    const nav=document.createElement('button');nav.dataset.page='response';nav.innerHTML=icon('users-three')+'対応';nav.onclick=()=>this.showQueue();phone.querySelector('[data-page=safety]').before(nav);
    const setPage=frame.setPage.bind(frame);
    frame.setPage=page=>{if(this.feedback&&page!=='map')this.stopMapPick();setPage(page);this.panel.hidden=page!=='response';this.summaryButton.hidden=page!=='response';phone.querySelector('.title').textContent=frame.config.incidentTitle||(page==='response'?'対応':'訓練');phone.querySelectorAll('.nav button').forEach(b=>b.setAttribute('aria-current',b.dataset.page===page?'page':'false'));};
    const menu=frame.showMenu.bind(frame);
    frame.showMenu=()=>{if(this.feedback)this.stopMapPick();menu();const button=document.createElement('button');button.className='person-row';button.innerHTML=icon('users-three')+'<span class="row-content"><strong>対応・通報</strong></span>'+icon('caret-right');button.onclick=()=>this.showQueue();frame.body.prepend(button);if(this.feedback){const archived=document.createElement('button');archived.className='person-row';archived.textContent=`取り消した通報 ${this.cases.filter(r=>r.cancelled).length}件`;archived.onclick=()=>this.enter({view:'cancelled'});frame.body.append(archived);}const role=document.createElement('button');role.className='person-row';role.textContent=`操作する役割（デモ）：${this.role==='ADMIN'?'管理者':this.role==='LEADER'?'班長':'メンバー'}`;role.onclick=()=>this.roleForm();frame.body.append(role);};
    const personDetail=frame.openPerson.bind(frame);
    frame.openPerson=person=>{personDetail(person);const row=this.cases.find(r=>r.personId===person.id);if(row){const button=document.createElement('button');button.className='secondary full ops-detail-link';button.textContent='担当・対応を見る';button.onclick=()=>this.openCase(row.id);frame.body.append(button);}};
    const safety=frame.showSafety.bind(frame);
    frame.showSafety=()=>{safety();phone.querySelectorAll('[data-save]').forEach(button=>{const save=button.onclick;button.onclick=event=>{save(event);this.syncSafety();};});};
    phone.querySelector('.incident-date').textContent=frame.config.incidentTitle?'10月26日 14:32':'訓練　10月26日 14:32';clearInterval(frame.clockTimer);
    frame.map.on('load',()=>this.drawReports());
    if(this.feedback)this.installFeedback();
    this.showQueue();
  }
  get coordinator(){return this.role!=='MEMBER';}
  title(row){return caseTitle(row,this.people);}
  find(id){return this.cases.find(r=>r.id===id);}
  syncSafety(){
    for(const person of this.people.filter(p=>p.s==='sos'&&p.id==='p5')){
      let row=this.cases.find(r=>r.personId===person.id);
      if(!row){row={id:`sos-${person.id}`,personId:person.id,title:person.name||'自分',category:'SOS',regionIds:[],assignees:[],status:'未対応',result:'',urgency:'未設定',reason:'',createdBy:'self',materials:[],history:[]};this.cases.push(row);}
      row.description=person.memo||'助けを求めています。';row.updatedAt='10月26日 15:00';
    }
    this.render();
  }
  record(row,text){row.updatedAt='10月26日 15:00';row.history.push({text,author:'自分',at:row.updatedAt});this.render();this.drawReports();}
  enter(route){this.frame.closeSheet();this.route=route;this.frame.setPage('response');this.render();this.panel.scrollTop=0;}
  showQueue(){this.enter({view:'queue'});}
  showSummary(){this.enter({view:'summary'});}
  showDrill(regionId,type=null){this.enter({view:'drill',regionId,type});}
  rowsHTML(rows){return rows.length?rows.map(row=>{const state=workSummary(row);return `<button class="ops-case-row" data-case="${esc(row.id)}">${caseIcon(row)}<span class="ops-case-content"><span class="ops-case-title">${esc(this.title(row))}</span>${row.urgency!=='未設定'?`<span class="ops-urgency ${row.urgency==='高'?'high':''}">緊急度 ${esc(row.urgency)}</span>`:''}<span class="ops-description">${esc(row.description)}</span>${this.feedback?`<span class="ops-work-state"><span class="ops-case-status">${esc(state.status)}</span><span class="ops-assignment">${esc(state.assignment)}</span></span>`:`<span class="ops-case-status">${row.assignees.length?esc(row.status)+'　'+esc(row.assignees.map(memberName).join('・')):'担当者無し'}</span>`}<span class="ops-case-meta">${esc(row.updatedAt)}　${esc(row.regionIds.map(regionName).join('・')||'地域未設定')}</span></span>${icon('caret-right')}</button>`;}).join(''):'<p class="ops-empty" role="status">該当する案件はありません。</p>';}
  bindRows(){this.panel.querySelectorAll('[data-case]').forEach(b=>b.onclick=()=>this.openCase(b.dataset.case));}
  render(){
    this.panel.dataset.view=this.route.view;
    if(this.route.view==='cancelled'){this.renderCancelled();return;}
    if(this.route.view==='summary'){this.renderSummary();return;}
    if(this.route.view==='drill'){this.renderDrill();return;}
    const counts={none:filterCases(this.cases,'none').length,mine:filterCases(this.cases,'mine').length,all:filterCases(this.cases).length};
    this.panel.innerHTML=`<button class="ops-availability" data-activity><span>自分の活動</span><strong>${icon('circle')} ${esc(this.activity)}</strong>${icon('caret-right')}</button><div class="ops-filters" aria-label="担当で絞り込み">${[['none','担当者無し'],['mine','自分の担当'],['all','すべて']].map(([value,label])=>`<button data-work-filter="${value}" aria-pressed="${this.filter===value}">${label} <span>${counts[value]}</span></button>`).join('')}</div><div class="ops-case-list">${this.rowsHTML(filterCases(this.cases,this.filter))}</div><div class="ops-actions"><button class="primary blue" data-add>${icon('plus')}通報を追加</button><button class="ops-csv" data-csv>CSVを書き出す</button></div>`;
    this.panel.querySelector('[data-activity]').onclick=()=>this.activityForm();
    this.panel.querySelectorAll('[data-work-filter]').forEach(b=>b.onclick=()=>{this.filter=b.dataset.workFilter;this.render();this.panel.querySelector(`[data-work-filter="${this.filter}"]`).focus({preventScroll:true});});
    this.panel.querySelector('[data-add]').onclick=()=>this.reportForm();
    this.panel.querySelector('[data-csv]').onclick=()=>this.downloadCsv(this.cases);
    this.bindRows();
  }
  renderSummary(){
    const rows=[{id:'all',name:'全地域',depth:0},...REGIONS,{id:'unassigned',name:'地域未設定',depth:0}];
    this.panel.innerHTML=`<div class="ops-subhead"><button class="ops-back" data-back>${icon('caret-left')}対応に戻る</button><h2>地域集計</h2><p>10月26日の訓練</p></div><div class="ops-table-wrap"><table class="ops-summary"><caption class="visually-hidden">地域別の通報総数と種類別件数</caption><thead><tr><th scope="col">地域</th><th scope="col">合計</th><th scope="col">SOS</th><th scope="col">道路<br>閉塞</th><th scope="col">建物<br>倒壊</th><th scope="col">その他</th></tr></thead><tbody>${rows.map(region=>{const summary=summarizeCases(this.cases,region.id);return `<tr class="ops-depth-${region.depth}"><th scope="row">${esc(region.name)}</th>${[null,...CATEGORIES].map(type=>{const n=type?summary.counts[type]:summary.total;return `<td><button data-region="${region.id}" data-type="${type||''}" aria-label="${esc(region.name)} ${type||'合計'} ${n}件">${n}</button></td>`;}).join('')}</tr>`;}).join('')}</tbody></table></div><p class="ops-summary-note">件数を押すと通報一覧を開きます。</p><button class="ops-csv" data-csv>通報明細をCSVで書き出す</button>`;
    this.panel.querySelector('[data-back]').onclick=()=>this.showQueue();
    this.panel.querySelectorAll('[data-region]').forEach(b=>b.onclick=()=>this.showDrill(b.dataset.region,b.dataset.type||null));
    this.panel.querySelector('[data-csv]').onclick=()=>this.downloadCsv(this.cases);
  }
  renderDrill(){
    const {regionId,type}=this.route;const summary=summarizeCases(this.cases,regionId,type);const rows=summary.ids.map(id=>this.find(id));
    this.panel.innerHTML=`<div class="ops-subhead"><button class="ops-back" data-back>${icon('caret-left')}地域集計に戻る</button><h2>${esc(regionId==='all'?'全地域':regionName(regionId))}</h2><p>${esc(type||'すべての種類')} ${summary.total}件</p></div><div class="ops-case-list">${this.rowsHTML(rows)}</div><button class="ops-csv" data-csv>この一覧をCSVで書き出す</button>`;
    this.panel.querySelector('[data-back]').onclick=()=>this.showSummary();this.panel.querySelector('[data-csv]').onclick=()=>this.downloadCsv(rows);this.bindRows();
  }
  form(title,html,submit,label='保存する'){
    const formId=this.frame.config.id+'-form';
    this.frame.openSheet(`<h2>${esc(title)}</h2>`,`<form class="ops-form" id="${formId}">${html}<p class="ops-error" role="alert" hidden></p></form>`,`<button class="primary blue" type="submit" form="${formId}">${esc(label)}</button>`);
    this.frame.body.querySelector('form').onsubmit=event=>{event.preventDefault();const form=event.currentTarget;try{submit(new FormData(form));}catch(error){const feedback=form.querySelector('.ops-error');feedback.textContent=error.message;feedback.hidden=false;}};
  }
  showAssignmentNotice(row){
    if(!this.assignmentWarning||!row||!isAssignedToOthers(row))return;
    const notice=document.createElement('p');notice.className='ops-assignment-warning';notice.setAttribute('role','note');notice.textContent='あなたはこの通報の担当ではありません';this.frame.head.append(notice);
  }
  editForm(row,...args){this.form(...args);this.showAssignmentNotice(row);}
  activityForm(){this.form('自分の活動',select('activity','活動状態',['活動可能','対応中','活動不可','未設定'].map(v=>[v,v]),this.activity),data=>{this.activity=data.get('activity');this.render();this.frame.closeSheet();this.frame.toast('活動状態を保存しました');});}
  roleForm(){this.form('操作する役割（デモ）',select('role','役割',[['MEMBER','メンバー'],['LEADER','班長'],['ADMIN','管理者']],this.role),data=>{this.role=data.get('role');this.frame.closeSheet();this.frame.toast('デモの役割を変更しました');});}
  canEdit(row){return !(this.feedback&&row.personId)&&(this.coordinator||row.createdBy==='self');}
  reportForm(row=null,location=null){
    if(row&&!this.canEdit(row)){this.frame.toast('作成者・班長・管理者が訂正できます');return;}
    this.editForm(row,row?'通報を訂正':'通報を追加',field('category','種類（自由入力）',row?.category||'道路閉塞')+`<datalist id="response-types">${['道路閉塞','建物倒壊','その他'].map(v=>`<option value="${v}"></option>`).join('')}</datalist>`+`<label class="ops-field">状況<textarea name="description" maxlength="500" required>${esc(row?.description||'')}</textarea></label>`+select('region','地域',leaves(),row?.regionIds[0]||''),data=>{
      const category=data.get('category').trim();const description=data.get('description').trim();if(!category||!description)throw Error('種類と状況を入力してください');
      if(category==='SOS'&&row?.category!=='SOS')throw Error('自分のSOSは「安否」画面から登録してください');
      const next=row||{id:crypto.randomUUID(),assignees:[],status:'未対応',result:'',urgency:'未設定',reason:'',createdBy:'self',materials:[],history:[]};
      Object.assign(next,{category,title:next.personId?next.title:category,description,regionIds:data.get('region')?[data.get('region')]:[]});
      if(!row&&location)next.location=[...location];
      if(!row)this.cases.push(next);this.record(next,row?'通報を訂正':'通報を追加');this.openCase(next.id);this.frame.toast(row?'通報を訂正しました':'通報を追加しました');
    },row?'訂正を保存':'通報を保存');
    this.frame.body.querySelector('[name=category]').setAttribute('list','response-types');
    if(row?.category==='SOS')this.frame.body.querySelector('[name=category]').readOnly=true;
    if(location){const point=document.createElement('p');point.className='ops-selected-point';point.textContent=`選択した地点：緯度 ${location[1].toFixed(5)}・経度 ${location[0].toFixed(5)}`;this.frame.body.querySelector('form').prepend(point);}
    if(this.feedback&&row&&!row.personId){const cancel=document.createElement('button');cancel.type='button';cancel.className='ops-cancel-report';cancel.textContent='この通報を取り消す';cancel.onclick=()=>this.cancelForm(row);this.frame.body.querySelector('form').append(cancel);}
  }
  openCase(id){
    const row=this.find(id);if(!row)return;
    if(this.feedback&&row.cancelled){this.openCancelledCase(row);return;}
    const assigned=row.assignees.includes('self');
    const canStart=this.fourStage&&['未対応','対応予定'].includes(row.status);
    const actions=this.fourStage
      ? (row.status!=='対応済み'&&(canStart||!assigned)?'<button class="primary blue" data-start>対応を始める</button>':'')+(assigned?'<button class="secondary full" data-release>自分の担当を外す</button>':'')
      : (assigned?'<button class="secondary full" data-release>自分の担当を外す</button>':'<button class="primary blue" data-start>対応を始める</button>');
    const materials=row.materials.map(m=>`<button class="ops-material-row" data-material="${esc(m.id)}"><span><strong>${esc(m.name)}</strong><small>必要 ${m.needed}${esc(m.unit)}・手配済み ${m.arranged}${esc(m.unit)}</small></span><b>不足 ${Math.max(0,m.needed-m.arranged)}${esc(m.unit)}</b>${icon('caret-right')}</button>`).join('');
    this.frame.openSheet(`<h2>${esc(this.title(row))}</h2><p class="ops-type-label">${esc(caseCategory(row.category))}　${esc(row.regionIds.map(regionName).join('・')||'地域未設定')}</p>`,`<p class="ops-detail-description">${esc(row.description)}</p><dl class="ops-facts"><div><dt>担当</dt><dd>${esc(row.assignees.map(memberName).join('・')||'担当者無し')}</dd></div><div><dt>対応状況</dt><dd>${esc(row.status)}</dd></div><div><dt>緊急度</dt><dd>${esc(row.urgency)}</dd></div></dl>${row.result?`<h3>対応結果</h3><p>${esc(row.result)}</p>`:''}${row.reason?`<p class="quiet">緊急度の理由：${esc(row.reason)}</p>`:''}<div class="ops-detail-buttons"><button class="secondary" data-assignment>担当を変更</button><button class="secondary" data-progress>${this.fourStage?'対応状況を変更':'対応結果を記録'}</button>${this.coordinator?'<button class="secondary" data-priority>緊急度を変更</button>':''}${this.canEdit(row)?'<button class="secondary" data-edit>通報を訂正</button>':''}<button class="secondary" data-region-edit>地域を変更</button></div><h3>必要資材</h3>${materials||'<p class="quiet">資材要請はありません。</p>'}<button class="ops-text-button" data-new-material>資材を要請する</button><details class="ops-history"><summary>対応履歴 ${row.history.length}件</summary>${row.history.slice().reverse().map(h=>`<p><strong>${esc(h.text)}</strong><small>${esc(h.at)}　${esc(h.author)}</small></p>`).join('')||'<p>追加の記録はありません。</p>'}</details>`,actions);
    this.showAssignmentNotice(row);
    const body=this.frame.body;body.querySelector('[data-assignment]').onclick=()=>this.assignmentForm(row);body.querySelector('[data-progress]').onclick=()=>this.progressForm(row);body.querySelector('[data-priority]')?.addEventListener('click',()=>this.priorityForm(row));body.querySelector('[data-edit]')?.addEventListener('click',()=>this.reportForm(row));body.querySelector('[data-region-edit]').onclick=()=>this.regionForm(row);body.querySelector('[data-new-material]').onclick=()=>this.materialForm(row);body.querySelectorAll('[data-material]').forEach(b=>b.onclick=()=>this.materialForm(row,row.materials.find(m=>m.id===b.dataset.material)));
    this.frame.footer.querySelector('[data-start]')?.addEventListener('click',()=>{if(this.fourStage)startWork(row);else{row.assignees.push('self');if(row.status==='未対応')row.status='対応中';}this.record(row,'自分が対応を開始');this.openCase(id);this.frame.toast('担当に加わりました');});
    this.frame.footer.querySelector('[data-release]')?.addEventListener('click',()=>{row.assignees=row.assignees.filter(v=>v!=='self');this.record(row,'自分の担当を解除');this.openCase(id);});
  }
  assignmentForm(row){this.editForm(row,'担当を変更',`<div class="ops-checks">${MEMBERS.map(m=>`<label><input type="checkbox" name="member" value="${m.id}" ${row.assignees.includes(m.id)?'checked':''} ${!this.coordinator&&m.id!=='self'?'disabled':''}>${esc(m.name)}</label>`).join('')}</div>`,data=>{const chosen=data.getAll('member');row.assignees=this.coordinator?chosen:[...row.assignees.filter(v=>v!=='self'),...chosen.filter(v=>v==='self')];this.record(row,'担当を変更');this.openCase(row.id);});}
  progressForm(row){this.editForm(row,this.fourStage?'対応状況を変更':'対応結果を記録',select('status','対応状況',(this.fourStage?WORK_STATUSES:['未対応','対応中','対応済み']).map(v=>[v,v]),row.status)+(this.fourStage?'<p class="quiet">「対応予定」は引き受け済み・未着手です。</p>':'')+`<label class="ops-field">対応結果<textarea name="result" maxlength="500">${esc(row.result)}</textarea></label><p class="quiet">本人の「救助済み」とは別の記録です。</p>`,data=>{const status=data.get('status'),result=data.get('result').trim();if(this.fourStage)updateWorkProgress(row,status,result);else{if(status==='対応済み'&&!result)throw Error('対応済みにする場合は結果を入力してください');row.status=status;row.result=result;}this.record(row,`${status}：${result||'状況を更新'}`);this.openCase(row.id);});}
  priorityForm(row){if(!this.coordinator)return;this.editForm(row,'緊急度を変更',select('urgency','緊急度',['高','中','低','未設定'].map(v=>[v,v]),row.urgency)+`<label class="ops-field">理由<textarea name="reason" maxlength="500">${esc(row.reason)}</textarea></label>`,data=>{const urgency=data.get('urgency'),reason=data.get('reason').trim();if(urgency!=='未設定'&&!reason)throw Error('緊急度の理由を入力してください');row.urgency=urgency;row.reason=reason;this.record(row,`緊急度 ${urgency}：${reason}`);this.openCase(row.id);});}
  regionForm(row){this.editForm(row,'地域を変更',select('region','地域',leaves(),row.regionIds[0]||''),data=>{row.regionIds=data.get('region')?[data.get('region')]:[];this.record(row,'地域を変更');this.openCase(row.id);});}
  materialForm(row,material=null){
    if(material&&!this.coordinator&&material.createdBy!=='self'){this.frame.toast('作成者・班長・管理者が訂正できます');return;}
    this.editForm(row,material?'資材要請を訂正':'資材を要請',field('name','品名',material?.name||'')+field('unit','単位',material?.unit||'')+`<div class="ops-quantity">${field('needed','必要数',material?.needed??'','number')}${field('arranged','手配済み数',material?.arranged??0,'number')}</div>`,data=>{const value={id:material?.id||crypto.randomUUID(),name:data.get('name').trim(),unit:data.get('unit').trim(),needed:Number(data.get('needed')),arranged:Number(data.get('arranged')),createdBy:material?.createdBy||'self'};saveMaterial(row,value);this.record(row,`${value.name}：必要${value.needed}${value.unit}・手配済み${value.arranged}${value.unit}`);this.openCase(row.id);});
    if(material)this.frame.body.querySelector('[name=unit]').readOnly=true;
  }
  downloadCsv(rows){const url=URL.createObjectURL(new Blob([casesCsv(rows,this.people)],{type:'text/csv;charset=utf-8'}));const link=document.createElement('a');link.href=url;link.download='PlanD-demo-2026-10-26.csv';document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);this.frame.toast('CSVを書き出しました');}
  drawReports(){
    const center=this.frame.map.getCenter();
    const liveIds=new Set(this.cases.filter(r=>!r.personId&&!r.cancelled).map(r=>r.id));
    for(const [id,marker] of this.reportMarkers)if(!liveIds.has(id)){marker.remove();this.reportMarkers.delete(id);}
    for(const [i,row] of this.cases.filter(r=>!r.personId).entries()){
      if(row.cancelled)continue;
      const marker=this.reportMarkers.get(row.id);
      if(marker){const button=marker.getElement();button.setAttribute('aria-label',`${row.category}の通報`);button.innerHTML=icon(caseCategory(row.category)==='建物倒壊'?'house-line':'flag');continue;}
      const point=row.location||[center.lng+(.0005*(i%2?1:-1)),center.lat+.0005*(Math.floor(i/2)+1)];row.location=point;
      const button=document.createElement('button');button.className='ops-map-report';button.dataset.caseId=row.id;button.dataset.longitude=point[0];button.dataset.latitude=point[1];button.setAttribute('aria-label',`${row.category}の通報`);button.innerHTML=icon(caseCategory(row.category)==='建物倒壊'?'house-line':'flag');button.onclick=event=>{event.stopPropagation();this.openCase(row.id);};
      this.reportMarkers.set(row.id,new maplibregl.Marker({element:button}).setLngLat(point).addTo(this.frame.map));
    }
  }
  installFeedback(){
    const {frame}=this;frame.phone.classList.add('feedback-version');
    frame.toast=text=>{
      frame.phone.querySelector('.toast').hidden=true;clearTimeout(frame.toastTimer);clearTimeout(this.feedbackTimer);frame.phone.querySelectorAll('.ops-inline-status').forEach(el=>el.remove());
      const status=document.createElement('p');status.className='ops-inline-status';status.setAttribute('role','status');status.textContent=text;
      if(!frame.sheet.hidden)frame.head.append(status);
      else if(!this.panel.hidden)this.panel.prepend(status);
      else if(frame.page==='safety')frame.phone.querySelector('.safety-page').prepend(status);
      else frame.phone.querySelector('.tools').after(status);
      this.feedbackTimer=setTimeout(()=>status.remove(),2600);
    };
    const add=document.createElement('button');add.className='ops-add-on-map';add.innerHTML=icon('plus')+'通報を追加';add.onclick=()=>this.beginMapPick();frame.phone.querySelector('.map-area').append(add);
    const selectBuilding=frame.selectBuilding.bind(frame);
    frame.selectBuilding=event=>{if(!this.picking){selectBuilding(event);return;}const point=[event.lngLat.lng,event.lngLat.lat];this.stopMapPick();this.reportForm(null,point);};
  }
  beginMapPick(){
    this.stopMapPick();this.frame.showMap();this.picking=true;this.frame.phone.classList.add('ops-picking');
    const banner=document.createElement('div');banner.className='ops-pick-banner';banner.innerHTML='<span role="status">通報する地点をタップ</span><button>やめる</button>';banner.querySelector('button').onclick=()=>this.stopMapPick();this.frame.phone.querySelector('.map-area').append(banner);this.pickBanner=banner;
  }
  stopMapPick(){this.picking=false;this.pickBanner?.remove();this.frame.phone.classList.remove('ops-picking');}
  cancelForm(row){
    if(!this.canEdit(row))return;
    this.editForm(row,'通報を取り消す',`<p>${esc(this.title(row))}</p><p class="quiet">一覧・地域集計から外します。通報と履歴は残ります。</p><label class="ops-field">取り消す理由<textarea name="reason" maxlength="500" required></textarea></label>`,data=>{cancelReport(row,data.get('reason'));this.render();this.drawReports();this.frame.closeSheet();this.frame.toast('通報を取り消しました');},'取り消す');
  }
  renderCancelled(){
    const rows=this.cases.filter(r=>r.cancelled);this.panel.innerHTML=`<div class="ops-subhead"><button class="ops-back" data-back>${icon('caret-left')}対応に戻る</button><h2>取り消した通報</h2><p>${rows.length}件</p></div><div class="ops-case-list">${this.rowsHTML(rows)}</div>`;this.panel.querySelector('[data-back]').onclick=()=>this.showQueue();this.bindRows();
  }
  openCancelledCase(row){
    this.frame.openSheet(`<h2>${esc(this.title(row))}</h2><p class="ops-type-label">取り消し済み</p>`,`<p>${esc(row.description)}</p><h3>取り消す理由</h3><p>${esc(row.cancelReason)}</p><h3>対応履歴</h3>${row.history.map(h=>`<p class="ops-cancel-history"><strong>${esc(h.text)}</strong><small>${esc(h.at)}　${esc(h.author)}</small></p>`).join('')}`,this.canEdit(row)?'<button class="primary blue" data-restore>取り消しを戻す</button>':'');
    this.showAssignmentNotice(row);
    this.frame.footer.querySelector('[data-restore]')?.addEventListener('click',()=>{restoreReport(row);this.render();this.drawReports();this.openCase(row.id);this.frame.toast('取り消しを戻しました');});
  }
}
