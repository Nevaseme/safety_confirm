// Stable illustrative classes, not an assessment of actual building strength.
// Style expressions render the existing vector tiles without copying their geometry.
export const PREVIEW_RATING=['%',['floor',['/',['to-number',['coalesce',['id'],30],30],10]],4];
export function previewBuildingRating(id){
  const value=id==null?30:Number(id);
  return Math.floor((Number.isFinite(value)?value:30)/10)%4;
}
