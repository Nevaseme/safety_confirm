import {STATES,CENTER,NOW,CLUSTER_END_ZOOM,hasPosition,buildIndex,getNodes,displayCounts,clusterViewport} from './clustering.mjs?v=6';
import {people,homes,personLabel} from './data.mjs?v=8';
import {PREVIEW_RATING,previewBuildingRating} from './map-style.mjs';
import {elapsedLabel,scopedPeople,filterPeople,listCounts} from './people-view.mjs?v=2';
import {OperationsDemo} from './operations-demo.mjs?v=4';
const labels={sos:'SOS',unanswered:'応答なし',safe:'無事',rescued:'救助済み'};
const stateText=p=>!hasPosition(p)&&p.s==='unanswered'?'安否未受信':labels[p.s];
const query=new URLSearchParams(location.search),frames=[];
const icon=(name)=>`<i class="ph-${['list','x','caret-right','caret-down','crosshair','gear-six'].includes(name)?'bold':'fill'} ph-${name}" aria-hidden="true"></i>`;
const escape=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const symbol=state=>`<span class="symbol ${state}" aria-hidden="true">${state==='sos'?'SOS':state==='unanswered'?'?':icon(state==='rescued'?'user-check':'check-fat')}</span>`;
const time=stamp=>new Intl.DateTimeFormat('ja-JP',{timeZone:'Asia/Tokyo',hour:'2-digit',minute:'2-digit',hour12:false}).format(stamp);
const age=p=>p.acquiredAt?`${Math.max(0,Math.round((NOW-p.acquiredAt)/60000))}分前`:'';
const sensor=(name,large=false,enhanced=false)=>`<span class="${large?'obs-icon':'sensor'} ${name==='enclosed'?'enclosed':''}"><img src="assets/${name==='enclosed'?'enclosed.png':enhanced?'impact-collision.svg':'impact.svg'}" alt="${name==='enclosed'?'閉所':'衝撃'}"></span>`;
const personById=id=>people.find(p=>p.id===id);
const homeById=id=>homes.find(h=>h.id===id);
const config={zoom:query.get('view')==='wide'?14.2:16.8};

function createFrame(config){
  const section=document.createElement('section');section.className='view';
  section.innerHTML=`<header class="comparison-caption"><h1 class="comparison-label">${escape(config.version)}</h1><p class="comparison-summary">${escape(config.summary)}</p></header><article class="phone ${config.enhanced?'enhanced':''}" id="${config.id}-screen">
  <header class="top"><button class="plain menu" aria-label="メニュー">${icon('list')}</button>${config.enhanced?'<div class="incident-heading"><span class="title">地震発生</span><span class="incident-date">10月21日 14:32 ・ <span data-elapsed>28分前</span></span></div>':'<span class="title">地震発生</span><span class="date">10月21日</span>'}</header>
  <div class="tools"><label class="toggle-label">無事を表示<button class="switch" role="switch" aria-label="無事を表示" aria-checked="true"></button></label><button class="unknown">${icon('user')}位置不明 ${people.filter(p=>!hasPosition(p)).length}人${icon('caret-right')}</button></div>
  <div class="map-area"><div class="map" id="${config.id}-map"></div><div class="loading">地図を読み込んでいます</div>
  <button class="legend-trigger">${icon('stack')}凡例${icon('caret-right')}</button><button class="recenter" aria-label="表示の中心に戻る">${icon('crosshair')}</button></div>
  <nav class="nav" aria-label="メイン"><button class="active" data-page="map">${icon('map-trifold')}地図</button>${config.enhanced?`<button data-page="people">${icon('list-bullets')}一覧</button>`:''}<button data-page="safety">${icon('user')}安否</button></nav>
  ${config.enhanced?'<section class="people-page" aria-label="メンバー一覧" hidden></section>':''}
  <section class="safety-page" hidden></section>
  <section class="sheet" hidden role="dialog" aria-modal="false" aria-label="詳細"><div class="handle"></div><div class="sheet-head"></div><div class="sheet-body"></div><div class="sheet-footer"></div></section>
  <div class="toast" role="status" hidden></div></article>`;
  document.querySelector('#board').append(section);
  return section.querySelector('.phone');
}
function refreshAll(){for(const frame of frames){frame.index=buildIndex(Supercluster,people,frame.showSafe);frame.revision++;frame.render();frame.refreshList?.();}}

class MapFrame{
  constructor(config,phone){
    this.config=config;this.phone=phone;this.showSafe=true;this.index=buildIndex(Supercluster,people);this.markers=new Map();this.revision=0;this.selected=null;
    this.sheet=phone.querySelector('.sheet');this.head=phone.querySelector('.sheet-head');this.body=phone.querySelector('.sheet-body');this.footer=phone.querySelector('.sheet-footer');
    this.map=new maplibregl.Map({container:config.id+'-map',style:'https://tiles.openfreemap.org/styles/positron',center:CENTER,zoom:config.zoom,maxZoom:21,pitch:0,bearing:0,dragRotate:false,attributionControl:false,locale:{'NavigationControl.ZoomIn':'拡大','NavigationControl.ZoomOut':'縮小'}});
    this.map.addControl(new maplibregl.AttributionControl({compact:false}),'bottom-right');
    this.map.addControl(new maplibregl.ScaleControl({maxWidth:65,unit:'metric'}),'bottom-left');
    this.map.addControl(new maplibregl.NavigationControl({showCompass:false}),'top-right');
    this.map.on('load',()=>this.setupMap());
    // MapLibre moves existing markers. Rebuild cluster membership only at a zoom boundary or after panning.
    this.map.on('zoom',()=>{if(Math.floor(this.map.getZoom())!==this.renderedZoom&&!this.pendingFrame){this.pendingFrame=requestAnimationFrame(()=>{this.pendingFrame=null;this.render();});}});
    this.map.on('moveend',()=>this.render());
    this.map.on('click',event=>this.selectBuilding(event));
    phone.querySelector('.switch').onclick=event=>{this.showSafe=!this.showSafe;event.currentTarget.setAttribute('aria-checked',String(this.showSafe));this.index=buildIndex(Supercluster,people,this.showSafe);this.revision++;this.render();};
    phone.querySelector('.legend-trigger').onclick=()=>this.showLegend();
    phone.querySelector('.unknown').onclick=()=>this.showUnknown();
    phone.querySelector('.menu').onclick=()=>this.showMenu();
    phone.querySelector('.recenter').onclick=()=>{this.closeSheet();this.map.flyTo({center:CENTER,zoom:config.zoom});};
    phone.querySelector('[data-page=safety]').onclick=()=>this.showSafety();
    phone.querySelector('[data-page=map]').onclick=()=>this.showMap();
    phone.addEventListener('keydown',event=>{if(event.key==='Escape')this.closeSheet();});
  }
  openSheet(title,html,footer=''){
    this.returnFocus=document.activeElement;
    this.sheet.hidden=false;this.sheet.setAttribute('aria-label',title.replace(/<[^>]*>/g,''));
    this.head.innerHTML=`<button class="sheet-close" aria-label="詳細を閉じる">${icon('x')}</button>${title}`;
    this.body.innerHTML=html;this.footer.innerHTML=footer;this.footer.hidden=!footer;this.body.scrollTop=0;
    this.head.querySelector('button').onclick=()=>this.closeSheet();this.head.tabIndex=-1;this.head.focus({preventScroll:true});
  }
  closeSheet(){this.sheet.hidden=true;this.selected=null;this.render();if(this.returnFocus?.isConnected)this.returnFocus.focus({preventScroll:true});}
  toast(text){const el=this.phone.querySelector('.toast');el.textContent=text;el.hidden=false;clearTimeout(this.toastTimer);this.toastTimer=setTimeout(()=>el.hidden=true,2600);}
  render(){
    if(!this.map.getLayer('preview-building-rating'))return;
    const b=this.map.getBounds(),z=this.map.getZoom();this.renderedZoom=Math.floor(z);
    const nodes=getNodes(this.index,[b.getWest(),b.getSouth(),b.getEast(),b.getNorth()],z),shown=new Set();let clusters=0,singles=0;
    for(const feature of nodes){
      const props=feature.properties,isCluster=Boolean(props.cluster),p=isCluster?null:personById(props.personId),bubble=isCluster||z<CLUSTER_END_ZOOM;
      const key=isCluster?`cluster-${props.cluster_id}`:`person-${props.personId}`;shown.add(key);
      const counts=displayCounts(feature),leaves=isCluster?this.index.getLeaves(props.cluster_id,Infinity):[feature];
      const members=leaves.map(f=>f.properties.personId).join(',');
      const signature=`${bubble}-${counts.join(',')}-${members}-${p?.s}-${p?.impact}-${p?.enclosed}-${p?.acquiredAt}`;
      if(isCluster)clusters++;else singles++;
      let entry=this.markers.get(key);
      if(entry?.signature!==signature){
        if(entry&&entry.bubble!==bubble){entry.marker.remove();entry=null;}
        const el=entry?.marker.getElement()||document.createElement(bubble&&this.config.enhanced?'div':'button');if(el.tagName==='BUTTON')el.type='button';el.className=bubble?'bubble':'individual';
        if(bubble){
          el.innerHTML=counts.map((n,i)=>n?`<span class="bubble-row">${symbol(STATES[i])}<strong>${n}<small>人</small></strong></span>`:'').join('');
          el.setAttribute('aria-label',counts.map((n,i)=>n?`${labels[STATES[i]]}${n}人`:'').filter(Boolean).join('、')+(isCluster?'、拡大して見る':`、${personLabel(p)}`));
          el.dataset.total=String(counts.reduce((a,c)=>a+c,0));
          el.dataset.memberIds=members;
          if(this.config.enhanced){
            const rows=el.innerHTML,description=el.getAttribute('aria-label');
            el.removeAttribute('aria-label');
            el.innerHTML=`<button class="bubble-main" aria-label="${escape(description)}">${rows}</button><button class="cluster-list" aria-label="このエリアの${leaves.length}人を一覧で見る">${icon('list-bullets')}一覧${icon('caret-right')}</button>`;
          }
          el.onclick=event=>{event.stopPropagation();if(this.config.enhanced&&event.target.closest('.cluster-list')){this.showArea(members.split(','),key,feature.geometry.coordinates);return;}this.closeSheet();if(isCluster){const view=clusterViewport(leaves,this.map.getZoom(),this.index.getClusterExpansionZoom(props.cluster_id));this.map.fitBounds(view.bounds,view.options);}else this.openMapPerson(p);};
        }else{
          el.innerHTML=symbol(p.s)+(NOW-p.acquiredAt>5*60000?`<span class="age">${age(p)}</span>`:'')+(p.impact?sensor('impact',false,this.config.enhanced):'')+(p.enclosed?sensor('enclosed'):'');
          el.setAttribute('aria-label',`${personLabel(p)}、${labels[p.s]}${p.impact?'、衝撃あり':''}${p.enclosed?'、閉所あり':''}`);el.dataset.personId=p.id;el.dataset.memberIds=p.id;
          el.onclick=event=>{event.stopPropagation();this.openMapPerson(p);};
        }
        const marker=entry?.marker||new maplibregl.Marker({element:el,anchor:bubble?'bottom':'center',offset:bubble?[0,-5]:[0,0]}).setLngLat(feature.geometry.coordinates).addTo(this.map);
        entry={marker,signature,bubble};this.markers.set(key,entry);
      }
      const position=entry.marker.getLngLat(),[lng,lat]=feature.geometry.coordinates;
      if(position.lng!==lng||position.lat!==lat)entry.marker.setLngLat(feature.geometry.coordinates);
      entry.marker.getElement().classList.toggle('selected',bubble?this.areaKey===key:this.selected===p?.id);
    }
    for(const [key,entry] of this.markers)if(!shown.has(key)){entry.marker.remove();this.markers.delete(key);}
    this.phone.dataset.zoom=z.toFixed(2);this.phone.dataset.clusters=String(clusters);this.phone.dataset.singles=String(singles);
    this.phone.dataset.visiblePopulation=String(nodes.reduce((n,f)=>n+(f.properties.point_count||1),0));
  }
  openMapPerson(p){
    const samePosition=people.filter(other=>hasPosition(other)&&(this.showSafe||!['safe','rescued'].includes(other.s))&&other.location.every((coordinate,i)=>coordinate===p.location[i]));
    if(samePosition.length>1)this.showPeople(samePosition);else this.openPerson(p);
  }
  openPerson(p){
    this.selected=p.id;this.render();
    const home=homeById(p.homeId),needsHelp=['sos','unanswered'].includes(p.s);
    let location=hasPosition(p)?`${time(p.acquiredAt)}取得・${age(p)}・精度 約${p.accuracy}m`:'未受信';
    const title=`<div class="person-title">${symbol(p.s)}<div><h2>${escape(personLabel(p))}</h2><div class="person-state ${p.s}">${stateText(p)}${p.s==='sos'?`・${p.answeredAt}の回答`:''}</div></div></div><p class="location-line">${icon('map-pin')}最後のスマホ位置　${location}</p>`;
    let body=p.s==='sos'&&p.memo?`<h3>SOSメモ</h3><p class="sos-note">${escape(p.memo)}</p>`:'';
    if(needsHelp){
      body+=`<h3>普段の住居・寝室</h3><div class="home-row"><strong>${home.name}</strong><button class="link-button" data-action="home">建物を見る ${icon('caret-right')}</button></div><div class="room">${icon('bed')}<span>${escape(p.bedroom)}</span></div><div class="attrs"><span><small>築年数</small>築${2026-home.built}年</span><span><small>建材・構造</small>${home.material}</span><span><small>階数</small>${home.floors}階建</span></div>`;
      if(p.impact||p.enclosed)body+=`<h3>スマホの観測</h3><div class="observations">${p.impact?`<div class="observation">${sensor('impact',true)}<div><b>衝撃あり</b><small>10/21 ${p.impactAt}</small></div></div>`:''}${p.enclosed?`<div class="observation">${sensor('enclosed',true)}<div><b>閉所を検知</b><small>10/21 ${p.enclosedAt}</small></div></div>`:''}</div>`;
    }
    body+=`<button class="history-summary" aria-expanded="${p.s==='rescued'}">救助・確認の記録 ${p.history.length}件 ${icon('caret-down')}</button><div class="history" ${p.s==='rescued'?'':'hidden'}>${this.historyHTML(p)}</div>`;
    const footer=needsHelp?`<button class="primary" data-action="rescue">${icon('user-check')}救助済みにする</button>`:p.s==='rescued'?'<button class="secondary full" data-action="correct">救助記録を訂正する</button>':'';
    this.openSheet(title,body,footer);this.sheet.setAttribute('aria-label',`${personLabel(p)}の詳細`);
    this.body.querySelector('[data-action=home]')?.addEventListener('click',()=>this.showHome(home,p));
    this.body.querySelector('.history-summary').onclick=event=>{const list=this.body.querySelector('.history');list.hidden=!list.hidden;event.currentTarget.setAttribute('aria-expanded',String(!list.hidden));};
    this.footer.querySelector('[data-action=rescue]')?.addEventListener('click',()=>this.rescueForm(p));
    this.footer.querySelector('[data-action=correct]')?.addEventListener('click',()=>this.correctForm(p));
  }
  historyHTML(p){return p.history.length?p.history.slice().reverse().map(h=>`<div class="history-entry"><strong>${h.action}</strong><small>10/21 ${h.time}　記録者：${h.author}</small><p>${escape(h.text)}</p></div>`).join(''):'<p class="quiet">記録はまだありません。</p>';}
  rescueForm(p){
    this.openSheet(`<h2>救助の記録</h2>`,`<div class="legend-row">${symbol(p.s)}<strong>${escape(personLabel(p))}</strong></div><label class="form-label" for="${this.config.id}-rescue-note">対応メモ（任意）</label><textarea id="${this.config.id}-rescue-note" placeholder="例：玄関前で本人を確認。屋外へ移動済み。"></textarea><p class="quiet">記録者：自分　10月21日 15:00</p>`,`<button class="primary" data-action="save">${icon('check-fat')}救助済みで保存</button>`);
    this.footer.querySelector('[data-action=save]').onclick=()=>{const note=this.body.querySelector('textarea').value.trim();p.priorSafety=p.s;p.s='rescued';p.history.push({time:'15:00',author:'自分',text:note||'本人の救助を確認。',action:'救助済み'});refreshAll();this.openPerson(p);this.toast('救助済みを記録しました');};
  }
  correctForm(p){
    this.openSheet('<h2>救助記録を訂正</h2>',`<p>この人の救助済みの記録を取り消し、${labels[p.priorSafety||'sos']}へ戻します。</p><label class="form-label" for="${this.config.id}-correction">訂正の内容</label><textarea id="${this.config.id}-correction" placeholder="例：別の人と取り違えたため"></textarea>`,`<button class="primary blue" data-action="correct-save">訂正を保存</button>`);
    this.footer.querySelector('[data-action=correct-save]').onclick=()=>{p.s=p.priorSafety||'sos';p.history.push({time:'15:00',author:'自分',text:this.body.querySelector('textarea').value.trim()||'救助済みの記録を取り消し。',action:'救助記録の訂正'});refreshAll();this.openPerson(p);this.toast('訂正を記録しました');};
  }
  showPeople(list,title='この場所にいる人'){
    this.openSheet(`<h2>${title}</h2>`,list.map(p=>`<button class="person-row" data-id="${p.id}">${symbol(p.s)}<span class="row-content"><strong>${escape(personLabel(p))}</strong><small>${hasPosition(p)?`${stateText(p)}・位置 ${age(p)}`:`普段の住居：${escape(homeById(p.homeId).name)}<br>寝室：${escape(p.bedroom)}`}</small></span>${icon('caret-right')}</button>`).join(''));
    this.body.querySelectorAll('[data-id]').forEach(button=>button.onclick=()=>this.openPerson(personById(button.dataset.id)));
  }
  showUnknown(){const unknown=people.filter(p=>!hasPosition(p));this.showPeople(unknown,`位置不明 ${unknown.length}人`);}
  showHome(home,backPerson=null){
    const linked=people.filter(p=>p.homeId===home.id),body=`<div class="attrs"><span><small>建築年</small>${home.built}年</span><span><small>建材・構造</small>${home.material}</span><span><small>階数</small>${home.floors}階建</span></div><h3>この建物の登録住民 ${linked.length}人</h3>${linked.map(p=>`<button class="person-row" data-id="${p.id}">${symbol(p.s)}<span class="row-content"><strong>${personLabel(p)}</strong><small>${stateText(p)}・${hasPosition(p)?`最後の位置 ${age(p)}`:'位置不明'}</small></span>${icon('caret-right')}</button>`).join('')}`;
    this.openSheet(`<h2>${home.name}</h2>`,body,backPerson?'<button class="secondary full" data-action="back">人の詳細へ戻る</button>':'');
    this.body.querySelectorAll('[data-id]').forEach(button=>button.onclick=()=>{const p=personById(button.dataset.id);if(hasPosition(p))this.map.flyTo({center:p.location,zoom:17});this.openPerson(p);});
    this.footer.querySelector('[data-action=back]')?.addEventListener('click',()=>this.openPerson(backPerson));
  }
  selectBuilding(event){
    if(!this.map.getLayer('preview-building-rating'))return;
    const feature=this.map.queryRenderedFeatures(event.point,{layers:['preview-building-rating']})[0];if(!feature)return;
    const coords=feature.geometry.type==='Polygon'?feature.geometry.coordinates[0]:feature.geometry.coordinates[0][0];
    const center=coords.reduce((sum,c)=>[sum[0]+c[0]/coords.length,sum[1]+c[1]/coords.length],[0,0]);
    const home=homes.find(h=>Math.hypot((center[0]-h.location[0])*.84,center[1]-h.location[1])<.00035);
    const rating=previewBuildingRating(feature.id);
    if(home)this.showHome(home);else this.openSheet('<h2>建物の詳細</h2>',`<h3>建物の強さ</h3><div class="legend-row"><span class="patch" style="background:${['#FBE8C5','#F2AE5D','#C5672E','#d9dde2'][rating]}"></span>${['強い側','中間','弱い側','不明'][rating]}</div><h3>登録住民</h3><p>この建物にはまだ住民の登録がありません。</p>`);
  }
  showLegend(){
    this.openSheet('<h2>凡例</h2>',`<h3>スマホの安否</h3>${STATES.map(s=>`<div class="legend-row">${symbol(s)}${labels[s]}</div>`).join('')}<h3>スマホの観測</h3><div class="legend-sensors"><div>${sensor('impact',true)}衝撃</div><div>${sensor('enclosed',true)}閉所</div></div><h3>建物の強さ</h3>${['強い側','中間','弱い側','不明'].map((label,i)=>`<div class="legend-row"><span class="patch ${i===3?'legend-unknown':''}" style="${i<3?'background:'+['#FBE8C5','#F2AE5D','#C5672E'][i]:''}"></span>${label}</div>`).join('')}`);
  }
  showMenu(){this.openSheet('<h2>メニュー</h2>',`<button class="person-row" data-menu="home">${icon('house')}<span class="row-content"><strong>自分の住居・寝室</strong></span>${icon('caret-right')}</button><button class="person-row" data-menu="group">${icon('users')}<span class="row-content"><strong>グループ</strong><small>木山地区</small></span>${icon('caret-right')}</button><button class="person-row" data-menu="map">${icon('download-simple')}<span class="row-content"><strong>地図を保存</strong></span>${icon('caret-right')}</button><button class="person-row" data-menu="settings">${icon('gear-six')}<span class="row-content"><strong>設定</strong></span>${icon('caret-right')}</button>`);this.body.querySelectorAll('[data-menu]').forEach(b=>b.onclick=()=>b.dataset.menu==='home'?this.showHome(homeById('sato')):this.toast('このプレビューでは地図と詳細を確認できます'));}
  showMap(){this.phone.querySelector('.safety-page').hidden=true;this.phone.querySelectorAll('.nav button').forEach(b=>b.classList.toggle('active',b.dataset.page==='map'));this.closeSheet();}
  showSafety(){
    this.closeSheet();this.phone.querySelectorAll('.nav button').forEach(b=>b.classList.toggle('active',b.dataset.page==='safety'));
    const self=personById('p5'),page=this.phone.querySelector('.safety-page');page.hidden=false;let selected=self.s==='sos'?'sos':'safe';
    page.innerHTML=`<h2>自分の安否</h2><p>10月21日の地震</p><div class="choice-buttons"><button data-state="safe">${symbol('safe')}無事</button><button data-state="sos">${symbol('sos')}SOS</button></div><div class="memo-field"><label class="form-label" for="${this.config.id}-memo">SOSメモ</label><textarea id="${this.config.id}-memo" placeholder="今いる場所や、助けてほしいこと">${escape(self.memo)}</textarea></div><button class="primary blue" data-save="safety">安否を更新する</button>`;
    const choose=state=>{selected=state;page.querySelectorAll('[data-state]').forEach(b=>b.classList.toggle('chosen',b.dataset.state===state));page.querySelector('.memo-field').hidden=state!=='sos';};choose(selected);
    page.querySelectorAll('[data-state]').forEach(b=>b.onclick=()=>choose(b.dataset.state));page.querySelector('[data-save]').onclick=()=>{self.s=selected;self.memo=page.querySelector('textarea').value;refreshAll();this.showMap();this.toast('安否を更新しました');};
  }
  setupMap(){
    const pattern=new Uint8Array(8*8*4);for(let y=0;y<8;y++)for(let x=0;x<8;x++){const j=(y*8+x)*4,line=(x+y)%8<2;pattern[j]=line?148:218;pattern[j+1]=line?154:221;pattern[j+2]=line?164:225;pattern[j+3]=170;}
    this.map.addImage('unknown-hatch',{width:8,height:8,data:pattern});
    const buildings=this.map.getStyle().layers.find(l=>l.id==='building');
    const shared={source:buildings.source,'source-layer':buildings['source-layer'],minzoom:buildings.minzoom??12};
    if(buildings.filter)shared.filter=buildings.filter;
    const before=this.map.getStyle().layers.find(l=>l.type==='symbol')?.id;
    this.map.addLayer({id:'preview-building-rating',type:'fill',...shared,paint:{'fill-color':['match',PREVIEW_RATING,0,'#FBE8C5',1,'#F2AE5D',2,'#C5672E','#D4D7DC'],'fill-opacity':.58,'fill-outline-color':'rgba(133,109,85,0.28)'}},before);
    this.map.addLayer({id:'preview-building-unknown',type:'fill',...shared,filter:buildings.filter?['all',buildings.filter,['==',PREVIEW_RATING,3]]:['==',PREVIEW_RATING,3],paint:{'fill-pattern':'unknown-hatch','fill-opacity':.60}},before);
    this.phone.querySelector('.loading').hidden=true;this.phone.dataset.ready='true';this.render();
    if(query.get('detail')==='sos')this.openPerson(personById('p0'));
  }
}
class ListMapFrame extends MapFrame{
  showMenu(){
    super.showMenu();
    if(this.config.positionControls){
      const button=document.createElement('button');button.className='person-row';
      button.innerHTML=`${icon('crosshair')}<span class="row-content"><strong>位置記録</strong></span>${icon('caret-right')}`;
      button.onclick=()=>this.showPosition();this.body.prepend(button);
    }
  }
  showPosition(){
    const self=personById('p5');
    this.openSheet('<h2>位置記録</h2>',`<h3>${this.positionActive?'位置記録中':'位置記録は停止中'}</h3><p>${this.positionMessage||'架空の位置で操作を確認できます。'}</p><h3>最後に保存した位置</h3><p>${time(self.acquiredAt)}取得 ・ 精度 約${self.accuracy}m</p><p>緯度 ${self.location[1].toFixed(5)} ・ 経度 ${self.location[0].toFixed(5)}</p>`,this.positionActive?'<button class="primary blue" data-position="refresh">今の位置を取得</button><button class="secondary full safety-submit" data-position="stop">位置記録を停止</button>':'<button class="primary blue" data-position="start">位置記録を開始</button>');
    this.footer.querySelectorAll('[data-position]').forEach(button=>button.onclick=()=>{
      if(button.dataset.position==='stop'){this.positionActive=false;this.positionMessage='位置記録を停止しました';}
      else{this.positionActive=true;self.location=[self.location[0]+.00003,self.location[1]+.00002];self.acquiredAt=NOW;self.accuracy=8;this.positionMessage='位置を保存しました';refreshAll();}
      this.showPosition();this.toast(this.positionMessage);
    });
  }
  constructor(config,phone){
    super(config,phone);
    this.page='map';this.listFilter='all';this.areaIds=null;this.areaKey=null;this.areaOpen=false;
    this.peoplePage=phone.querySelector('.people-page');
    phone.querySelector('[data-page=people]').onclick=()=>this.showList();
    phone.querySelector('.recenter').onclick=()=>{this.showMap();this.map.flyTo({center:CENTER,zoom:config.zoom});};
    const openedAt=Date.now();
    const updateClock=()=>{const clock=phone.querySelector('[data-elapsed]');if(clock)clock.textContent=elapsedLabel(NOW+Date.now()-openedAt);};
    updateClock();
    this.clockTimer=setInterval(()=>{if(!document.hidden)updateClock();},60000);
    document.addEventListener('visibilitychange',()=>{if(!document.hidden)updateClock();});
  }
  render(){
    super.render();
    // Use the supplied source asset in the new version, including details and legend.
    this.phone.querySelectorAll('img[src="assets/impact.svg"]').forEach(img=>img.src='assets/impact-collision.svg');
  }
  setPage(page){
    this.page=page;
    this.peoplePage.hidden=page!=='people';
    this.phone.querySelector('.safety-page').hidden=page!=='safety';
    this.phone.querySelector('.tools').inert=page!=='map';
    this.phone.querySelector('.map-area').inert=page!=='map';
    this.phone.querySelectorAll('.nav button').forEach(b=>b.classList.toggle('active',b.dataset.page===page));
  }
  showMap(){this.returnArea=null;this.closeSheet();this.setPage('map');this.map.resize();}
  showSafety(){
    this.returnArea=null;this.setPage('safety');
    if(!this.config.localSituation){super.showSafety();return;}
    this.closeSheet();
    const self=personById('p5'),page=this.phone.querySelector('.safety-page');
    const previousAnswer=self.s==='rescued'?self.priorSafety:self.s;
    let selected=previousAnswer==='sos'?'sos':'safe';
    page.innerHTML=`<h2>自分の安否</h2><p>10月21日の地震</p><div class="choice-buttons"><button data-state="safe">${symbol('safe')}無事</button><button data-state="sos">${symbol('sos')}SOS</button></div><div class="memo-field"><label class="form-label" for="${this.config.id}-memo">SOSメモ</label><textarea id="${this.config.id}-memo" maxlength="500" aria-describedby="${this.config.id}-count" placeholder="今いる場所や、助けてほしいこと">${escape(self.memo)}</textarea><p class="memo-count" id="${this.config.id}-count"></p><button class="secondary full" data-save="memo">メモだけ保存する</button></div><button class="primary blue safety-submit" data-save="safety">安否を更新する</button>`;
    const input=page.querySelector('textarea'),memoSave=page.querySelector('[data-save=memo]');
    const update=()=>{
      page.querySelector('.memo-count').textContent=`${input.value.length} / 500文字`;
      memoSave.hidden=previousAnswer!=='sos';
      memoSave.disabled=input.value===self.memo||input.value.length>500;
      page.querySelector('[data-save=safety]').disabled=selected==='sos'&&input.value.length>500;
    };
    const choose=state=>{
      selected=state;
      page.querySelectorAll('[data-state]').forEach(b=>{b.classList.toggle('chosen',b.dataset.state===state);b.setAttribute('aria-pressed',String(b.dataset.state===state));});
      page.querySelector('.memo-field').hidden=state!=='sos';update();
    };
    page.querySelectorAll('[data-state]').forEach(b=>b.onclick=()=>choose(b.dataset.state));
    input.oninput=update;choose(selected);
    memoSave.onclick=()=>{
      if(memoSave.disabled)return;
      self.memo=input.value;refreshAll();update();this.toast('メモを保存しました');
    };
    page.querySelector('[data-save=safety]').onclick=()=>{
      if(selected==='sos'&&input.value.length>500)return;
      self.s=selected;self.memo=selected==='sos'?input.value:'';self.answeredAt='15:00';
      refreshAll();this.showMap();this.toast('安否を更新しました');
    };
  }
  showList(filter='all'){
    this.returnArea=null;this.closeSheet();this.listFilter=filter;this.setPage('people');this.refreshList();
    this.peoplePage.querySelector('h2').focus({preventScroll:true});
  }
  showUnknown(){this.showList('unknown');}
  filtersHTML(list,selected){
    const counts=listCounts(list);
    return `<div class="people-filters" aria-label="安否で絞り込み">${[['all','全員'],['sos','SOS'],['unanswered','応答なし'],['safe','無事・救助済み']].map(([key,label])=>`<button data-filter="${key}" aria-pressed="${key===selected}" class="filter-${key}">${label} <span>${counts[key]}</span></button>`).join('')}</div>`;
  }
  rowsHTML(list){
    if(!list.length)return '<p class="empty-list" role="status">該当する人はいません</p>';
    return list.map(p=>{
      const known=hasPosition(p),home=homeById(p.homeId);
      return `<button class="person-row" data-id="${escape(p.id)}">${known?symbol(p.s):`<span class="unknown-symbol">${icon('user')}</span>`}<span class="row-content"><strong>${escape(personLabel(p))}</strong><small>${known?`位置更新 ${age(p)}`:`普段の住居：${escape(home.name)}<br>寝室：${escape(p.bedroom)}`}</small></span>${known?`<span class="row-state ${p.s}">${labels[p.s]}</span>`:''}${icon('caret-right')}</button>`;
    }).join('');
  }
  bindList(container,area=false){
    container.querySelectorAll('[data-filter]').forEach(button=>button.onclick=()=>{
      const filter=button.dataset.filter;
      if(area){this.areaFilter=filter;this.paintArea();}else{this.listFilter=filter;this.refreshList();}
      container.querySelector(`[data-filter="${filter}"]`)?.focus({preventScroll:true});
    });
    container.querySelectorAll('[data-id]').forEach(button=>button.onclick=()=>this.openPerson(personById(button.dataset.id)));
  }
  refreshList(){
    if(this.page!=='people')return;
    const scroll=this.peoplePage.querySelector('.people-rows')?.scrollTop||0;
    const unknown=listCounts(people).unknown;
    this.peoplePage.innerHTML=`<div class="people-heading"><h2 tabindex="-1">メンバー一覧</h2><p>グループ全体${this.listFilter==='unknown'?' ・ 位置不明':''}</p></div>${this.filtersHTML(people,this.listFilter)}<button class="unknown-list-link" aria-pressed="${this.listFilter==='unknown'}">${icon('user')}位置不明 ${unknown}人を見る${icon('caret-right')}</button><div class="people-rows">${this.rowsHTML(filterPeople(people,this.listFilter))}</div>`;
    this.peoplePage.querySelector('.unknown-list-link').onclick=()=>{this.listFilter='unknown';this.refreshList();this.peoplePage.querySelector('.unknown-list-link').focus();};
    this.bindList(this.peoplePage);
    this.peoplePage.querySelector('.people-rows').scrollTop=scroll;
  }
  showArea(ids,key=null,coordinates=null){
    this.returnArea=null;this.closeSheet();this.setPage('map');
    this.areaIds=[...ids];this.areaKey=key;this.areaFilter='all';this.paintArea();this.render();
    if(coordinates)this.map.easeTo({center:coordinates,offset:[0,-this.map.getContainer().clientHeight*.16],duration:350});
  }
  paintArea(){
    const list=scopedPeople(people,this.areaIds);
    super.openSheet(`<h2>このエリアの${list.length}人</h2><button class="all-group-link">グループ全体を見る ${icon('caret-right')}</button>`,`${this.filtersHTML(list,this.areaFilter)}<div class="area-rows">${this.rowsHTML(filterPeople(list,this.areaFilter))}</div>`);
    this.sheet.classList.add('area-sheet');this.areaOpen=true;
    this.head.querySelector('.all-group-link').onclick=()=>this.showList();
    this.bindList(this.body,true);
  }
  openSheet(title,html,footer=''){
    this.sheet.classList.remove('area-sheet');this.areaOpen=false;
    super.openSheet(title,html,footer);
    this.phone.querySelectorAll('img[src="assets/impact.svg"]').forEach(img=>img.src='assets/impact-collision.svg');
  }
  closeSheet(){
    const back=this.returnArea;
    this.returnArea=null;this.areaOpen=false;this.areaKey=null;
    this.sheet.classList.remove('area-sheet');
    super.closeSheet();
    if(back){this.areaIds=back.ids;this.areaKey=back.key;this.areaFilter=back.filter;this.paintArea();this.render();}
  }
  openPerson(p){
    if(this.areaOpen)this.returnArea={ids:[...this.areaIds],key:this.areaKey,filter:this.areaFilter};
    if(!hasPosition(p)){
      const home=homeById(p.homeId);
      this.openSheet(`<h2>${escape(personLabel(p))}</h2><p class="location-line">位置不明${p.acquiredAt?' ・ 3日以上更新なし':''}</p>`,`<h3>普段の住居・寝室</h3><div class="home-row"><strong>${escape(home.name)}</strong><button class="link-button" data-home>建物を見る ${icon('caret-right')}</button></div><div class="room">${icon('bed')}<span>${escape(p.bedroom)}</span></div>`);
      this.body.querySelector('[data-home]').onclick=()=>this.showHome(home,p);
    }else super.openPerson(p);
    if(this.returnArea)this.head.querySelector('.sheet-close').setAttribute('aria-label','エリアの一覧に戻る');
  }
  showPeople(list){this.showArea(list.map(p=>p.id));}
  showHome(home,backPerson=null){
    super.showHome(home,backPerson);
    this.body.querySelectorAll('[data-id]').forEach(row=>{
      const p=personById(row.dataset.id);
      if(!hasPosition(p)){
        row.querySelector('.symbol').outerHTML=`<span class="unknown-symbol">${icon('user')}</span>`;
        row.querySelector('small').textContent='位置不明';
      }
    });
  }
}
const latestConfig={id:'latest',zoom:config.zoom,enhanced:true,localSituation:true,version:'1.5.4 反映案',summary:'地図・一覧・安否の画面見本。今回の追加：SOSメモの500文字制限と「メモだけ保存」。'};
frames.push(new ListMapFrame(latestConfig,createFrame(latestConfig)));
const responseConfig={id:'response',zoom:config.zoom,enhanced:true,localSituation:true,positionControls:true,version:'Web案 1.6.0 対応・地域集計',summary:'担当・通報・活動・資材・CSVと、地域別の総数／SOS・道路閉塞・建物倒壊・その他の集計を追加。架空データの操作デモで、Androidの画面は未実装です。'};
const responseFrame=new ListMapFrame(responseConfig,createFrame(responseConfig));
frames.push(responseFrame);
new OperationsDemo(responseFrame,{people,refreshAll});
const feedbackConfig={id:'response-feedback',zoom:config.zoom,enhanced:true,localSituation:true,positionControls:true,version:'Web案 1.6.1 操作改善',summary:'完了通知の重なりを解消し、状態と担当を分けて表示。通報の取り消し・履歴確認と地図からの地点指定を追加したWeb案です（Android画面は未実装）。'};
const feedbackFrame=new ListMapFrame(feedbackConfig,createFrame(feedbackConfig));
frames.push(feedbackFrame);
new OperationsDemo(feedbackFrame,{people,refreshAll,feedback:true});
if(query.get('demo')==='response'||location.hash==='#response-demo')requestAnimationFrame(()=>responseFrame.phone.parentElement.scrollIntoView({inline:'start',block:'start'}));
if(query.get('demo')==='feedback'||location.hash==='#response-feedback-demo')requestAnimationFrame(()=>feedbackFrame.phone.parentElement.scrollIntoView({inline:'start',block:'start'}));

const stagesConfig={id:'response-stages',zoom:config.zoom,enhanced:true,localSituation:true,positionControls:true,version:'Web案 1.6.2 対応状況を4段階に',summary:'未対応・対応予定・対応中・対応済みの4段階に変更。1.6.1の操作改善を引き継いだ架空データのWeb案です（Android画面は未実装）。'};
const stagesFrame=new ListMapFrame(stagesConfig,createFrame(stagesConfig));
frames.push(stagesFrame);
new OperationsDemo(stagesFrame,{people,refreshAll,feedback:true,fourStage:true});
if(query.get('demo')==='stages'||location.hash==='#response-stages-demo')requestAnimationFrame(()=>stagesFrame.phone.parentElement.scrollIntoView({inline:'start',block:'start'}));

const assignmentConfig={id:'response-assignment',zoom:config.zoom,enhanced:true,localSituation:true,positionControls:true,version:'Web案 1.6.3 担当外の注意表示',summary:'他の担当者が付いていて自分が担当していない通報の詳細・変更画面に注意を表示。注意を表示したまま変更できるWeb案です（Android画面は未実装）。'};
const assignmentFrame=new ListMapFrame(assignmentConfig,createFrame(assignmentConfig));
frames.push(assignmentFrame);
new OperationsDemo(assignmentFrame,{people,refreshAll,feedback:true,fourStage:true,assignmentWarning:true});
if(query.get('demo')==='assignment'||location.hash==='#response-assignment-demo')requestAnimationFrame(()=>assignmentFrame.phone.parentElement.scrollIntoView({inline:'start',block:'start'}));
