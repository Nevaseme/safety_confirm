// Bootstrap only: serves the public CA certificate, never keys, tokens, or records.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.dirname(fileURLToPath(import.meta.url));
const certificate=fs.readFileSync(path.join(root,'.local/PlanD-Lab-CA.cer'));
http.createServer((req,res)=>{
 if(req.method!=='GET'||req.url!=='/PlanD-Lab-CA.cer'){res.writeHead(404);res.end('PlanD: public experiment certificate only');return;}
 res.writeHead(200,{'Content-Type':'application/x-x509-ca-cert','Content-Disposition':'attachment; filename="PlanD-Lab-CA.cer"','Cache-Control':'no-store'});res.end(certificate);
}).listen(8080,'0.0.0.0',()=>console.log('PlanD public certificate download on :8080'));
