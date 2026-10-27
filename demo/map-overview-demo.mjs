import {STATES} from './clustering.mjs?v=6';
import {TOWN_CENTERS} from './visibility-model.mjs?v=2';
import {townSummary} from './map-overview-model.mjs?v=2';

const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon=name=>`<i class="ph-fill ph-${name}" aria-hidden="true"></i>`;
const labels={sos:'SOS',unanswered:'反応なし',safe:'無事',rescued:'救助済み'};

// Keep the 1.6.4 map renderer and report markers; add only town detail behavior.
export function installMapOverview(frame){
  const view=frame.visibility,ops=view.ops;
  frame.phone.classList.add('map-overview-version');
  const request=(id,name,unit,needed,arranged)=>({id,name,unit,needed,arranged,createdBy:'north'});
  ops.find('road-1').materials=[request('map-water-east','水','本',20,10),request('map-shovel-east','スコップ','本',5,2)];
  ops.find('building-1').materials=[request('map-sheet-central','ブルーシート','枚',4,1)];
  ops.find('road-2').materials=[request('map-water-west','水','本',12,4),request('map-blanket-west','毛布','枚',8,5)];
  ops.find('road-2').urgency='高';ops.find('road-2').reason='道路閉塞の解消を優先するため。';

  const originalOpenSheet=frame.openSheet.bind(frame);
  frame.openSheet=(...args)=>{const returnFocus=frame.sheet.hidden?document.activeElement:frame.returnFocus;originalOpenSheet(...args);frame.returnFocus=returnFocus;};
  const summary=id=>townSummary(id,view.rawPeople,ops.cases);
  function focusTown(id){
    frame.closeSheet();frame.showMap();
    if(frame.config.townFixtures){view.showTownMap(id);return;}
    const report=ops.cases.find(r=>!r.cancelled&&!r.personId&&r.regionIds.includes(id));
    frame.map.flyTo({center:report?.location||TOWN_CENTERS[id],zoom:16.8,duration:300});
  }
  function showTown(id){
    const item=summary(id);
    frame.openSheet(`<h2>${esc(item.name)}</h2>`,`<dl class="town-safety-counts">${STATES.map(s=>`<div><dt>${labels[s]}</dt><dd>${item.safety[s]}<small>人</small></dd></div>`).join('')}</dl><div class="town-operation-summary"><p class="town-urgent"><span>${icon('warning')}高い緊急度の通報</span><strong>${item.highReports}<small>件</small></strong></p><p class="town-open-note">未対応・対応予定・対応中</p><p class="town-shortage-state">${icon('package')}資材：<strong>${item.needsMaterials?'不足あり':'不足の記録なし'}</strong></p></div><button class="ops-text-button" data-town-zoom>この町内会を拡大</button>`, '<button class="primary blue" data-town-details>詳細を見る</button>');
    frame.body.querySelector('[data-town-zoom]').onclick=()=>focusTown(id);
    frame.footer.querySelector('[data-town-details]').onclick=()=>showTownDetails(id);
  }
  function showTownDetails(id){
    const item=summary(id),rows=ops.visible(ops.cases.filter(r=>!r.cancelled&&r.regionIds.includes(id)));
    frame.openSheet(`<h2>${esc(item.name)}の詳細</h2>`,`<h3>資材の不足分</h3>${item.shortages.length?`<dl class="town-materials">${item.shortages.map(m=>`<div><dt>${esc(m.name)}</dt><dd>${m.quantity}<small>${esc(m.unit)}</small></dd></div>`).join('')}</dl>`:'<p class="quiet">不足の記録はありません。</p>'}<h3>閲覧できる通報</h3>${ops.rowsHTML(rows)}${view.viewer.role==='municipal'||view.viewer.regionId!==id?'<p class="visibility-note">安否の個人情報は表示しません。</p>':''}`,'<button class="secondary full" data-town-back>町内会の要約に戻る</button>');
    frame.body.querySelectorAll('[data-case]').forEach(button=>button.onclick=()=>ops.openCase(button.dataset.case));
    frame.footer.querySelector('[data-town-back]').onclick=()=>showTown(id);
  }
  const drawStatisticsMarkers=view.drawStatisticsMarkers.bind(view);
  view.drawStatisticsMarkers=()=>{
    drawStatisticsMarkers();
    for(const [id,marker] of view.statMarkers){
      const button=marker.getElement();button.dataset.town=id;button.setAttribute('aria-label',`${summary(id).name}の要約`);
      button.onclick=event=>{event.stopPropagation();showTown(id);};
    }
  };
  frame.showMap();ops.drawReports();
}
