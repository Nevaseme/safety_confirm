import {STATES,CENTER,NOW,CLUSTER_END_ZOOM,hasPosition,buildIndex,getNodes,displayCounts,clusterViewport} from './clustering.mjs?v=5';
import {people,homes,personLabel} from './data.mjs?v=7';
import {PREVIEW_RATING,previewBuildingRating} from './map-style.mjs';
const labels={sos:'SOS',unanswered:'応答なし',safe:'無事',rescued:'救助済み'};
const stateText=p=>!hasPosition(p)&&p.s==='unanswered'?'安否未受信':labels[p.s];
const query=new URLSearchParams(location.search),frames=[];
const icon=(name)=>`<i class="ph-${['list','x','caret-right','caret-down','crosshair','gear-six'].includes(name)?'bold':'fill'} ph-${name}" aria-hidden="true"></i>`;
const escape=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const symbol=state=>`<span class="symbol ${state}" aria-hidden="true">${state==='sos'?'SOS':state==='unanswered'?'?':icon(state==='rescued'?'user-check':'check-fat')}</span>`;
const time=stamp=>new Intl.DateTimeFormat('ja-JP',{timeZone:'Asia/Tokyo',hour:'2-digit',minute:'2-digit',hour12:false}).format(stamp);
const age=p=>p.acquiredAt?`${Math.round((NOW-p.acquiredAt)/60000)}分前`:'';
const sensor=(name,large=false)=>`<span class="${large?'obs-icon':'sensor'} ${name==='enclosed'?'enclosed':''}"><img src="assets/${name==='enclosed'?'enclosed.png':'impact.svg'}" alt="${name==='enclosed'?'閉所':'衝撃'}"></span>`;
const personById=id=>people.find(p=>p.id===id);
const homeById=id=>homes.find(h=>h.id===id);
const config={id:'near',zoom:query.get('view')==='wide'?14.2:16.8};

function createFrame(config){
  const section=document.createElement('section');section.className='view';
  section.innerHTML=`<article class="phone" id="${config.id}-screen">
  <header class="top"><button class="plain menu" aria-label="メニュー">${icon('list')}</button><span class="title">地震発生</span><span class="date">10月21日</span></header>
  <div class="tools"><label class="toggle-label">無事を表示<button class="switch" role="switch" aria-label="無事を表示" aria-checked="true"></button></label><button class="unknown">${icon('user')}位置不明 2人${icon('caret-right')}</button></div>
  <div class="map-area"><div class="map" id="${config.id}-map"></div><div class="loading">地図を読み込んでいます</div>
  <button class="legend-trigger">${icon('stack')}凡例${icon('caret-right')}</button><button class="recenter" aria-label="表示の中心に戻る">${icon('crosshair')}</button></div>
  <nav class="nav" aria-label="メイン"><button class="active" data-page="map">${icon('map-trifold')}地図</button><button data-page="safety">${icon('user')}安否</button></nav>
  <section class="safety-page" hidden></section>
  <section class="sheet" hidden role="dialog" aria-modal="false" aria-label="詳細"><div class="handle"></div><div class="sheet-head"></div><div class="sheet-body"></div><div class="sheet-footer"></div></section>
  <div class="toast" role="status" hidden></div></article>`;
  document.querySelector('#board').append(section);
  return section.querySelector('.phone');
}
function refreshAll(){for(const frame of frames){frame.index=buildIndex(Supercluster,people,frame.showSafe);frame.revision++;frame.render();}}

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
        const el=entry?.marker.getElement()||document.createElement('button');el.type='button';el.className=bubble?'bubble':'individual';
        if(bubble){
          el.innerHTML=counts.map((n,i)=>n?`<span class="bubble-row">${symbol(STATES[i])}<strong>${n}<small>人</small></strong></span>`:'').join('');
          el.setAttribute('aria-label',counts.map((n,i)=>n?`${labels[STATES[i]]}${n}人`:'').filter(Boolean).join('、')+(isCluster?'、拡大して見る':`、${personLabel(p)}`));
          el.dataset.total=String(counts.reduce((a,c)=>a+c,0));
          el.dataset.memberIds=members;
          el.onclick=event=>{event.stopPropagation();this.closeSheet();if(isCluster){const view=clusterViewport(leaves,this.map.getZoom(),this.index.getClusterExpansionZoom(props.cluster_id));this.map.fitBounds(view.bounds,view.options);}else this.openMapPerson(p);};
        }else{
          el.innerHTML=symbol(p.s)+(NOW-p.acquiredAt>5*60000?`<span class="age">${age(p)}</span>`:'')+(p.impact?sensor('impact'):'')+(p.enclosed?sensor('enclosed'):'');
          el.setAttribute('aria-label',`${personLabel(p)}、${labels[p.s]}${p.impact?'、衝撃あり':''}${p.enclosed?'、閉所あり':''}`);el.dataset.personId=p.id;el.dataset.memberIds=p.id;
          el.onclick=event=>{event.stopPropagation();this.openMapPerson(p);};
        }
        const marker=entry?.marker||new maplibregl.Marker({element:el,anchor:bubble?'bottom':'center',offset:bubble?[0,-5]:[0,0]}).setLngLat(feature.geometry.coordinates).addTo(this.map);
        entry={marker,signature,bubble};this.markers.set(key,entry);
      }
      const position=entry.marker.getLngLat(),[lng,lat]=feature.geometry.coordinates;
      if(position.lng!==lng||position.lat!==lat)entry.marker.setLngLat(feature.geometry.coordinates);
      entry.marker.getElement().classList.toggle('selected',!bubble&&this.selected===p?.id);
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
    let location=hasPosition(p)?`${time(p.acquiredAt)}取得・${age(p)}・精度 約${p.accuracy}m`:p.acquiredAt?'3日以上更新なし':'未受信';
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
  showUnknown(){this.showPeople(people.filter(p=>!hasPosition(p)),'位置不明 2人');}
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
frames.push(new MapFrame(config,createFrame(config)));
