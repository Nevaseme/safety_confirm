import {CENTER,hasPosition,STATES} from './clustering.mjs?v=6';
import {REGIONS} from './operations-model.mjs?v=4';
import {reportIcon,TOWN_CENTERS} from './visibility-model.mjs?v=2';
import {townSummary,clusterScreenItems} from './map-overview-model.mjs?v=1';

const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon=name=>`<i class="ph-fill ph-${name}" aria-hidden="true"></i>`;
const labels={sos:'SOS',unanswered:'反応なし',safe:'無事',rescued:'救助済み'};
const towns=REGIONS.filter(r=>r.depth===2);
const anchors=TOWN_CENTERS;
const pointIcon=row=>{const src=reportIcon(row.urgency);return src?`<img src="${src}" alt="">`:icon('flag');};
const pointName=p=>p.s==='safe'?'無事の人':p.s==='rescued'?'救助済みの人':p.name;
const personIcon=p=>`<span class="symbol ${p.s}">${p.s==='sos'?'SOS':p.s==='unanswered'?'?':icon(p.s==='safe'?'check-fat':'user-check')}</span>${p.impact?'<span class="sensor"><img src="assets/impact-collision.svg" alt="衝撃"></span>':''}${p.enclosed?'<span class="sensor enclosed"><img src="assets/enclosed.png" alt="閉所"></span>':''}`;

export function installMapOverview(frame){
  const view=frame.visibility,ops=view.ops,map=frame.map;
  frame.phone.classList.add('map-overview-version');
  const markers=new Map();
  let returnKey,focusKey;
  const clear=()=>{returnKey=undefined;focusKey=undefined;for(const [key,marker] of markers){const element=marker.getElement();if(frame.returnFocus===element)returnKey=key;if(document.activeElement===element)focusKey=key;marker.remove();}markers.clear();};
  for(const marker of frame.markers.values())marker.marker.remove();frame.markers.clear();
  for(const marker of ops.reportMarkers.values())marker.remove();ops.reportMarkers.clear();
  for(const marker of view.statMarkers.values())marker.remove();view.statMarkers.clear();
  view.drawStatisticsMarkers=()=>{};
  view.cityButton.remove();

  // New-version-only fictional requests make the shortage view testable.
  const request=(id,name,unit,needed,arranged)=>({id,name,unit,needed,arranged,createdBy:'north'});
  ops.find('road-1').materials=[request('map-water-east','水','本',20,10),request('map-shovel-east','スコップ','本',5,2)];
  ops.find('building-1').materials=[request('map-sheet-central','ブルーシート','枚',4,1)];
  ops.find('road-2').materials=[request('map-water-west','水','本',12,4),request('map-blanket-west','毛布','枚',8,5)];
  ops.find('road-2').urgency='高';ops.find('road-2').reason='道路閉塞の解消を優先するため。';

  const controls=document.createElement('div');controls.className='map-hierarchy-controls';controls.setAttribute('aria-label','地図の表示単位');controls.innerHTML='<button data-map-mode="town">町内会</button><button data-map-mode="points">個別位置</button>';
  frame.phone.querySelector('.map-area').append(controls);
  let selectedTown=view.viewer.regionId;
  const mode=()=>map.getZoom()<16?'town':'points';
  const summary=id=>townSummary(id,view.rawPeople,ops.cases);
  const originalOpenSheet=frame.openSheet.bind(frame);
  frame.openSheet=(...args)=>{const returnFocus=frame.sheet.hidden?document.activeElement:frame.returnFocus;originalOpenSheet(...args);frame.returnFocus=returnFocus;};
  const openSheet=frame.openSheet.bind(frame);
  const setMarker=(key,point,element)=>{const marker=new maplibregl.Marker({element,anchor:'center'}).setLngLat(point).addTo(map);markers.set(key,marker);element.addEventListener('click',event=>event.stopPropagation());if(returnKey===key)frame.returnFocus=element;if(focusKey===key)element.focus({preventScroll:true});};
  const reportLocation=(row,i)=>{if(!row.location)row.location=[CENTER[0]+(i%2?.0005:-.0005),CENTER[1]+.0005*(Math.floor(i/2)+1)];return row.location;};

  function focusTown(id){
    selectedTown=id;frame.closeSheet();frame.showMap();
    const reports=ops.cases.filter(r=>!r.cancelled&&!r.personId&&r.regionIds.includes(id));
    const people=frame.people.filter(p=>p.regionId===id&&hasPosition(p));
    const center=reports[0]?.location||people[0]?.location||anchors[id];
    map.flyTo({center,zoom:16.8,duration:300});
  }
  function fitOverview(){
    map.fitBounds([[anchors.east[0],CENTER[1]+.001],[anchors.west[0],CENTER[1]+.003]],{padding:{top:80,right:56,bottom:70,left:56},maxZoom:15.9,duration:300});
  }
  function overview(){frame.closeSheet();frame.showMap();fitOverview();}
  function showTown(id){
    selectedTown=id;const item=summary(id);
    openSheet(`<h2>${esc(item.name)}</h2>`,`<dl class="town-safety-counts">${STATES.map(s=>`<div><dt>${labels[s]}</dt><dd>${item.safety[s]}<small>人</small></dd></div>`).join('')}</dl><div class="town-operation-summary"><p class="town-urgent"><span>${icon('warning')}高い緊急度の通報</span><strong>${item.highReports}<small>件</small></strong></p><p class="town-open-note">未対応・対応予定・対応中</p><p class="town-shortage-state">${icon('package')}資材：<strong>${item.needsMaterials?'不足あり':'不足の記録なし'}</strong></p></div><button class="ops-text-button" data-town-zoom>この町内会を拡大</button>`, '<button class="primary blue" data-town-details>詳細を見る</button>');
    frame.body.querySelector('[data-town-zoom]').onclick=()=>focusTown(id);
    frame.footer.querySelector('[data-town-details]').onclick=()=>showTownDetails(id);
  }
  function showTownDetails(id){
    const item=summary(id),rows=ops.visible(ops.cases.filter(r=>!r.cancelled&&r.regionIds.includes(id)));
    openSheet(`<h2>${esc(item.name)}の詳細</h2>`,`<h3>資材の不足分</h3>${item.shortages.length?`<dl class="town-materials">${item.shortages.map(m=>`<div><dt>${esc(m.name)}</dt><dd>${m.quantity}<small>${esc(m.unit)}</small></dd></div>`).join('')}</dl>`:'<p class="quiet">不足の記録はありません。</p>'}<h3>閲覧できる通報</h3>${ops.rowsHTML(rows)}${view.viewer.role==='municipal'||view.viewer.regionId!==id?'<p class="visibility-note">安否の個人情報は表示しません。</p>':''}`,'<button class="secondary full" data-town-back>町内会の要約に戻る</button>');
    frame.body.querySelectorAll('[data-case]').forEach(button=>button.onclick=()=>ops.openCase(button.dataset.case));
    frame.footer.querySelector('[data-town-back]').onclick=()=>showTown(id);
  }
  function showGroup(items){
    openSheet('<h2>この場所の情報</h2>',`<div class="map-nearby-list">${items.map(item=>{
      if(item.kind==='report'){const row=ops.find(item.id);return `<button class="person-row" data-group-report="${esc(row.id)}"><span class="map-list-symbol">${pointIcon(row)}</span><span class="row-content"><strong>${esc(row.title)}</strong><small>緊急度 ${esc(row.urgency)}・${esc(row.description)}</small></span>${icon('caret-right')}</button>`;}
      const p=frame.people.find(row=>row.id===item.id);return `<button class="person-row" data-group-person="${esc(p.id)}"><span class="map-list-symbol">${personIcon({...p,impact:false,enclosed:false})}</span><span class="row-content"><strong>${esc(pointName(p))}</strong><small>${labels[p.s]}</small></span>${icon('caret-right')}</button>`;
    }).join('')}</div>`);
    frame.body.querySelectorAll('[data-group-report]').forEach(b=>b.onclick=()=>ops.openCase(b.dataset.groupReport));
    frame.body.querySelectorAll('[data-group-person]').forEach(b=>b.onclick=()=>frame.openPerson(frame.personById(b.dataset.groupPerson)));
  }
  function render(){
    if(!map.getLayer('preview-building-rating'))return;
    clear();const currentMode=mode();frame.renderedZoom=Math.floor(map.getZoom());frame.phone.dataset.mapMode=currentMode;frame.phone.dataset.zoom=map.getZoom().toFixed(2);
    controls.querySelectorAll('button').forEach(b=>{b.setAttribute('aria-pressed',String(b.dataset.mapMode===currentMode));if(b.dataset.mapMode==='town')b.textContent=view.viewer.role==='municipal'?'市全体':'町内会';});
    if(currentMode==='town'){
      const townItems=towns.map(region=>{const at=map.project(anchors[region.id]);return {id:region.id,kind:'town',x:at.x,y:at.y,width:96,height:60};});
      for(const node of clusterScreenItems(townItems)){
        const button=document.createElement('button');
        if(node.items.length===1){const item=summary(node.items[0].id);button.className='bubble town-map-bubble';button.dataset.town=item.regionId;button.setAttribute('aria-label',`${item.name}の要約・SOS${item.safety.sos}人・高緊急通報${item.highReports}件`);button.innerHTML=`<strong>${esc(item.name)}</strong><span><b>SOS ${item.safety.sos}</b><b class="town-high">高 ${item.highReports}</b></span>`;button.onclick=()=>showTown(item.regionId);}
        else{button.className='map-mixed-group';button.style.width=node.width+'px';button.style.height=node.height+'px';button.dataset.townGroup=node.items.map(i=>i.id).join(',');button.innerHTML=`<strong>町内会 ${node.items.length}地域</strong><span>選択して要約を見る</span>`;button.onclick=()=>{openSheet('<h2>町内会を選ぶ</h2>',node.items.map(i=>{const item=summary(i.id);return `<button class="person-row" data-town-choice="${esc(i.id)}"><span class="row-content"><strong>${esc(item.name)}</strong><small>SOS ${item.safety.sos}人・高緊急通報 ${item.highReports}件</small></span>${icon('caret-right')}</button>`;}).join(''));frame.body.querySelectorAll('[data-town-choice]').forEach(b=>b.onclick=()=>showTown(b.dataset.townChoice));};}
        setMarker('town-'+node.items.map(i=>i.id).join('|'),map.unproject([node.x,node.y]),button);
      }
      return;
    }
    const items=[];
    for(const p of frame.people.filter(p=>hasPosition(p)&&(frame.showSafe||!['safe','rescued'].includes(p.s)))){const at=map.project(p.location),large=p.impact||p.enclosed;items.push({id:p.id,kind:p.s,x:at.x,y:at.y,width:large?72:44,height:large?64:44,person:p});}
    for(const [i,row] of ops.cases.filter(r=>!r.cancelled&&!r.personId).entries()){const at=map.project(reportLocation(row,i));items.push({id:row.id,kind:'report',x:at.x,y:at.y,width:44,height:44,row});}
    for(const node of clusterScreenItems(items)){
      const button=document.createElement('button');button.style.width=node.width+'px';button.style.height=node.height+'px';button.dataset.mapItems=node.items.map(i=>i.id).join(',');
      if(node.items.length>1){
        const second=node.sos?`SOS ${node.sos}`:node.unanswered?`反応なし ${node.unanswered}`:`安否 ${node.items.length-node.reports}`;
        button.className='map-mixed-group';button.innerHTML=`<strong>この場所 ${node.items.length}件</strong><span>${second}${node.reports?`　通報 ${node.reports}`:''}</span>`;button.setAttribute('aria-label',`${node.items.length}件の近接情報・SOS${node.sos}人・反応なし${node.unanswered}人・通報${node.reports}件・選択して開く`);button.onclick=()=>showGroup(node.items);
      }else{
        const item=node.items[0];
        if(item.kind==='report'){button.className='map-point-report';button.innerHTML=pointIcon(item.row);button.setAttribute('aria-label',`${item.row.category}・緊急度 ${item.row.urgency}の通報`);button.onclick=()=>ops.openCase(item.id);}
        else{button.className='map-point-person';button.innerHTML=personIcon(item.person);button.setAttribute('aria-label',`${pointName(item.person)}、${labels[item.person.s]}`);button.onclick=()=>frame.openPerson(item.person);}
      }
      setMarker(node.items.map(i=>i.id).join('|'),map.unproject([node.x,node.y]),button);
    }
  }
  controls.querySelector('[data-map-mode=town]').onclick=overview;
  controls.querySelector('[data-map-mode=points]').onclick=()=>focusTown(selectedTown||view.viewer.regionId);
  frame.phone.querySelector('.recenter').onclick=overview;
  frame.showMunicipalOverview=fitOverview;
  frame.render=render;ops.drawReports=render;
  const originalShowMap=frame.showMap.bind(frame);frame.showMap=()=>{originalShowMap();render();};
  map.on('zoomend',render);map.on('resize',render);
  frame.showMap();
  if(map.getLayer('preview-building-rating'))fitOverview();else map.on('load',fitOverview);
  render();
}
