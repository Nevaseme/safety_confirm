import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

test('all offline assets and local module imports can be served by the experiment server',async()=>{
 const server=await readFile(new URL('../server.mjs',import.meta.url),'utf8');
 const sw=await readFile(new URL('../web/sw.js',import.meta.url),'utf8');
 const quoted=s=>[...s.matchAll(/'([^']+)'/g)].map(m=>m[1]);
 const served=new Set(quoted(server.match(/const allowed=new Map\(\[([^\]]+)\]/)[1]));
 const offline=quoted(sw.match(/const FILES=\[([^\]]+)\]/)[1]).map(p=>p==='/'?'index.html':p.slice(1));
 for(const name of offline){
  assert.ok(served.has(name),`not served: ${name}`);
  const content=await readFile(new URL('../web/'+name,import.meta.url),'utf8');
  if(name.endsWith('.mjs'))for(const m of content.matchAll(/(?:from\s*|import\s*)['"]\.\/([^'"]+)['"]/g)){
   assert.ok(served.has(m[1]),`not served: ${name} imports ${m[1]}`);
   assert.ok(offline.includes(m[1]),`not cached offline: ${m[1]}`);
  }
 }
});
