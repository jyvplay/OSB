// SPDX-License-Identifier: Apache-2.0
// Actual pinned office engine in a real Chromium worker. No Android/fidelity/security acceptance inferred.
import fs from 'node:fs/promises';
import path from 'node:path';
import http from 'node:http';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const here=path.dirname(fileURLToPath(import.meta.url));
const artifactRoot=path.resolve(process.argv[2]||'');
const output=path.resolve(process.argv[3]||'');
if(!process.argv[2] || !process.argv[3] || !process.env.CHROME_PATH) throw new Error('Actual artifact directory, output directory and installed CHROME_PATH are required');
await fs.mkdir(output,{recursive:true});
const intake=JSON.parse(await fs.readFile(path.join(artifactRoot,'../intake.json'),'utf8'));
const files=new Map();
for(const name of ['lokjs.js','lokjs.wasm','soffice.data','soffice.data.js.metadata']) {
  const data=await fs.readFile(path.join(artifactRoot,name));
  if(crypto.createHash('sha256').update(data).digest('hex')!==intake.members[name]?.sha256) throw new Error('Artifact changed after intake: '+name);
  files.set('/'+name,data);
}
const workerTemplate=await fs.readFile(path.join(here,'probe-worker.js'),'utf8');
const fixture='<?xml version="1.0" encoding="UTF-8"?>'+
  '<office:document xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" '+
  'office:mimetype="application/vnd.oasis.opendocument.text" office:version="1.3">'+
  '<office:body><office:text><text:p>OSB R25 actual LibreOfficeKit canary</text:p><text:p>Controlled local fixture only; no user file or remote service.</text:p></office:text></office:body></office:document>';

const logs=[], blocked=[];
let resolveResult, rejectResult;
const result=new Promise((resolve,reject)=>{resolveResult=resolve;rejectResult=reject;});
result.catch(()=>{}); // Ownership stays with the awaited result; avoid an early unhandled rejection during browser startup.
let origin;
const server=http.createServer(async(req,res)=>{
  try {
    res.setHeader('Cross-Origin-Opener-Policy','same-origin');
    res.setHeader('Cross-Origin-Embedder-Policy','require-corp');
    res.setHeader('Cross-Origin-Resource-Policy','same-origin');
    res.setHeader('Cache-Control','no-store');
    res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' 'wasm-unsafe-eval'; worker-src 'self' blob:; connect-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; object-src 'none'");
    const url=new URL(req.url,origin);
    if(req.method==='POST' && url.pathname==='/r25-result') {
      let size=0;const pieces=[];
      for await(const piece of req){size+=piece.length;if(size>3*1024*1024)throw new Error('Result byte budget exceeded');pieces.push(piece);}
      const value=JSON.parse(Buffer.concat(pieces).toString('utf8'));
      res.writeHead(200,{'Content-Type':'text/plain'});res.end('received');resolveResult(value);return;
    }
    if(req.method!=='GET') {res.writeHead(405);res.end();return;}
    if(url.pathname==='/') {
      res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});
      res.end(`<!doctype html><meta charset="utf-8"><title>Actual LOK WASM experiment</title><body><p>Controlled real-engine experiment, not an Android UI.</p><script>
        var Module={uno_scripts:[location.origin+'/probe-worker.js'],locateFile:(name)=>location.origin+'/'+name,
          onRuntimeInitialized:()=>{Module.FS.writeFile('/tmp/r25-canary.fodt',new TextEncoder().encode(${JSON.stringify(fixture)}));window.r25SourceBytes=Module.FS.stat('/tmp/r25-canary.fodt').size;},
          print:(text)=>console.log('engine:'+text),printErr:(text)=>console.error('engine:'+text),
          onAbort:(reason)=>fetch('/r25-result',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({origin:'engine-abort',error:String(reason)})})};
        </script><script src="/lokjs.js"></script></body>`);return;
    }
    if(url.pathname==='/probe-worker.js') {
      res.writeHead(200,{'Content-Type':'application/javascript'});
      res.end(workerTemplate.replace('__R25_REPORT_URL__',JSON.stringify(origin+'/r25-result')));return;
    }
    const data=files.get(url.pathname);
    if(!data) {res.writeHead(404);res.end();return;}
    res.writeHead(200,{'Content-Type':url.pathname.endsWith('.wasm')?'application/wasm':url.pathname.endsWith('.js')?'application/javascript':url.pathname.endsWith('.metadata')?'application/json':'application/octet-stream','Content-Length':data.length});res.end(data);
  } catch(error) {res.writeHead(400);res.end('Rejected');rejectResult(error);}
});
await new Promise((resolve)=>server.listen(0,'0.0.0.0',resolve));
origin='http://127.0.0.1:'+server.address().port; // Headless browser is on THIS runner. Client assets use same-origin relative URLs.
let browser;let final={sourceCandidate:process.env.GITHUB_SHA||null,archiveSha256:intake.archive_sha256,engineExecuted:false,integratedAndroid:false,legalAccepted:false};
const timer=setTimeout(()=>rejectResult(new Error('No actual engine result within180s')),180000);
try {
  browser=await chromium.launch({executablePath:process.env.CHROME_PATH,headless:true,chromiumSandbox:true,
    args:['--disable-background-networking','--disable-component-update','--disable-sync','--js-flags=--max-old-space-size=2048']});
  final.browserVersion=browser.version();
  const context=await browser.newContext({serviceWorkers:'block',acceptDownloads:false});
  await context.route('**/*',async(route)=>{
    const u=route.request().url();
    if(!u.startsWith(origin+'/')) {if(blocked.length<40)blocked.push(u.slice(0,300));await route.abort();}
    else await route.continue();
  });
  await context.routeWebSocket('**/*',(socket)=>socket.close());
  const page=await context.newPage();
  page.on('console',(message)=>{if(logs.length<200)logs.push(message.type()+': '+message.text().slice(0,1000));});
  page.on('pageerror',(error)=>{if(logs.length<200)logs.push('pageerror: '+String(error).slice(0,1000));});
  await page.goto(origin,{waitUntil:'domcontentloaded',timeout:30000});
  final.browserCapabilities=await page.evaluate(()=>({crossOriginIsolated,sharedArrayBuffer:typeof SharedArrayBuffer==='function',wasm:typeof WebAssembly==='object'}));
  if(!final.browserCapabilities.crossOriginIsolated || !final.browserCapabilities.sharedArrayBuffer)throw new Error('Actual browser shared-memory precondition unavailable');
  const r=await result;
  final.mainFsSourceBytes=await page.evaluate(()=>window.r25SourceBytes||0);
  if(r.error){final.engineStages=r.stages||null;throw new Error('Engine failed: '+r.error+' '+(r.stack||''));}
  if(r.origin!=='real-lok-wasm-worker' || r.state!==2 || !Number.isInteger(r.documentWidthTwips) || !Number.isInteger(r.documentHeightTwips) || r.documentWidthTwips<=0 || r.documentHeightTwips<=0)throw new Error('Invalid actual engine observation');
  const saved=await page.evaluate(()=>{
    const encode=(data)=>{if(data.length>2*1024*1024)throw new Error('Main FS output cap');let s='';for(let i=0;i<data.length;i+=16384)s+=String.fromCharCode(...data.subarray(i,i+16384));return btoa(s);};
    return {odtBase64:encode(Module.FS.readFile('/tmp/r25-result.odt')),pdfBase64:encode(Module.FS.readFile('/tmp/r25-result.pdf'))};
  });
  Object.assign(r,saved);
  for(const [field,file] of [['odtBase64','output.odt'],['pdfBase64','output.pdf'],['tileBase64','tile.raw']]){
    if(typeof r[field]!=='string' || r[field].length>3*1024*1024 || !/^[A-Za-z0-9+/]*={0,2}$/.test(r[field]))throw new Error('Output framing refused');
    const data=Buffer.from(r[field],'base64');if(data.length>2*1024*1024)throw new Error('Output cap exceeded');
    await fs.writeFile(path.join(output,file),data);delete r[field];
  }
  if(blocked.length)throw new Error('Engine attempted an unadmitted external browser request');
  final={...final,engineExecuted:true,observation:r,
    scope:'Actual controlled Writer fixture load/tile/saveAs in desktop Chromium WASM ONLY. Independent output verification still required. NOT Android, general Office fidelity, hostile-document safety, upstream reproducibility or license closure.'};
} catch(error) {
  final.error=String(error);process.exitCode=1;
} finally {
  clearTimeout(timer);
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
  final.console=logs;final.blockedRequests=blocked;
  await fs.writeFile(path.join(output,'runtime.json'),JSON.stringify(final,null,2)+'\n');
  console.log(JSON.stringify(final));
}
