export const STATES=['sos','unanswered','safe','rescued'];
export const CENTER=[130.8190,32.7904];
export const NOW=Date.parse('2026-10-21T15:00:00+09:00');
export const THREE_DAYS=3*86400000;
export const CLUSTER_END_ZOOM=16;
export function hasPosition(p){return Array.isArray(p.location)&&Number.isFinite(p.acquiredAt);}
export function buildIndex(Supercluster,people,showSafe=true){
  const points=people.filter(p=>hasPosition(p)&&(showSafe||!['safe','rescued'].includes(p.s))).map(p=>({type:'Feature',geometry:{type:'Point',coordinates:p.location},properties:{personId:p.id,state:p.s,...Object.fromEntries(STATES.map(s=>[s,Number(s===p.s)]))}}));
  return new Supercluster({radius:70,maxZoom:CLUSTER_END_ZOOM-1,minZoom:0,extent:512,map:p=>Object.fromEntries(STATES.map(s=>[s,p[s]])),reduce:(acc,p)=>STATES.forEach(s=>{acc[s]+=p[s];})}).load(points);
}
export function getNodes(index,bounds,zoom){return index.getClusters(bounds,Math.floor(zoom));}
export function displayCounts(feature){return STATES.map(s=>feature.properties[s]||0);}
export function clusterViewport(leaves,zoom,expansionZoom){
  const coordinates=leaves.map(f=>f.geometry.coordinates);
  const lngs=coordinates.map(c=>c[0]),lats=coordinates.map(c=>c[1]);
  return {bounds:[[Math.min(...lngs),Math.min(...lats)],[Math.max(...lngs),Math.max(...lats)]],options:{padding:{top:136,right:64,bottom:88,left:52},maxZoom:Math.min(CLUSTER_END_ZOOM+.6,Math.max(zoom+1.6,expansionZoom+.5)),duration:650}};
}
