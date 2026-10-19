// SPDX-License-Identifier: Apache-2.0
/* Original bounded Writer/WASM adapter. Engine payload is NOT bundled or downloaded by this file.
 * Main runtime owns FS; the LOK pthread owns every native call. Trusted documents/engine/embedding host only.
 * No claim of Android availability, hostile-document safety, all Office formats, hard cancellation or anonymity.
 */
(function(global) {
  'use strict';
  const MAX_BYTES=2*1024*1024, MAX_JOBS=64, TILE_WIDTH=256, TILE_HEIGHT=320;
  function need(value,message){if(!value)throw new Error(message);}
  function describe(id) {
    need(typeof id==='string' && /^[a-f0-9]{32}$/.test(id),'Invalid owned office job identity');
    const root='/tmp/osb-office-'+id;
    return Object.freeze({id,root,input:root+'/source.fodt',odt:root+'/copy.odt',pdf:root+'/copy.pdf',tileWidth:TILE_WIDTH,tileHeight:TILE_HEIGHT});
  }
  function job(value) {
    need(value && typeof value==='object','Missing office job');const expected=describe(value.id);
    for(const key of Object.keys(expected))need(value[key]===expected[key],'Office path/geometry/identity mismatch');
    return expected;
  }
  function bytes(value,max=MAX_BYTES) {
    need(value instanceof Uint8Array && value.byteLength>0 && value.byteLength<=max,'Office byte budget/type refused');
    return value.slice();
  }
  function dimensions(value) {need(Number.isSafeInteger(value) && value>0 && value<=1000000,'Document twip regime refused');return value;}

  class MainFiles {
    #fs; #active=null; #retired=new Set();
    constructor(fs) {
      need(fs && ['mkdir','writeFile','readFile','stat','unlink','rmdir'].every(k=>typeof fs[k]==='function'),'Actual main-runtime filesystem required');this.#fs=fs;
    }
    begin(input,id) {
      need(this.#active===null && this.#retired.size<MAX_JOBS,'Office session busy/exhausted');
      const request=describe(id);need(!this.#retired.has(id),'Retired office identity cannot be replayed');const owned=bytes(input);
      this.#fs.mkdir(request.root); // Exclusive owned namespace; never overwrite another job's source.
      try {this.#fs.writeFile(request.input,owned);need(this.#fs.stat(request.input).size===owned.length,'Source staging size mismatch');}
      catch(error) {try{this.#fs.unlink(request.input);}catch{}try{this.#fs.rmdir(request.root);}catch{}throw error;}
      this.#active=request;return request;
    }
    accept(result) {
      const active=this.#active;need(active!==null && result?.id===active.id,'Late/foreign office reply refused');
      const width=dimensions(result.documentWidthTwips),height=dimensions(result.documentHeightTwips);
      need(result.tileWidth===TILE_WIDTH && result.tileHeight===TILE_HEIGHT && (result.tileMode===0 || result.tileMode===1),'Wrong rendered tile shape/mode');
      need(Number.isSafeInteger(result.heapBytes) && result.heapBytes>0,'No actual runtime memory observation');
      const exact=TILE_WIDTH*TILE_HEIGHT*4;
      need(result.tile instanceof Uint8Array && result.tile.length===exact && result.detail instanceof Uint8Array && result.detail.length===exact,'Wrong native tile byte size');
      const region=[0,0,Math.min(width,TILE_WIDTH*15),Math.min(height,TILE_HEIGHT*15)];
      need(Array.isArray(result.detailRegionTwips) && result.detailRegionTwips.every((v,i)=>v===region[i]) && result.detailRegionTwips.length===4,'Wrong detail region');
      for(const path of [active.odt,active.pdf])need(this.#fs.stat(path).size>0 && this.#fs.stat(path).size<=MAX_BYTES,'Actual engine file size cap');
      const accepted=Object.freeze({id:active.id,odt:bytes(this.#fs.readFile(active.odt)),pdf:bytes(this.#fs.readFile(active.pdf)),
        tile:result.tile.slice(),detail:result.detail.slice(),documentWidthTwips:width,documentHeightTwips:height,
        tileWidth:TILE_WIDTH,tileHeight:TILE_HEIGHT,tileMode:result.tileMode,detailRegionTwips:Object.freeze(region),heapBytes:result.heapBytes});
      this.retire(active.id);return accepted;
    }
    retire(id) {
      need(this.#active!==null && this.#active.id===id,'No matching office job to retire');const current=this.#active;
      this.#active=null;this.#retired.add(id);
      // Virtual engine files only. This is not deletion/erasure of host/provider/source originals.
      for(const path of [current.input,current.odt,current.pdf])try{this.#fs.unlink(path);}catch{}
      try{this.#fs.rmdir(current.root);}catch{}
    }
    get activeId(){return this.#active?.id ?? null;}
  }

  class Worker {
    #module; #busy=false; #retired=new Set();
    constructor(module) {
      const names=['ccall','_malloc','_free','_lokjs_state','_lokjs_document_width','_lokjs_document_height','_lokjs_paint_tile','_lokjs_document_destroy','_lokjs_get_tile_mode','_lokjs_get_error','_lokjs_free','UTF8ToString'];
      need(module && names.every(n=>typeof module[n]==='function') && module.HEAPU8 instanceof Uint8Array,'Actual LOK runtime exports required');
      this.#module=module;
    }
    convert(requestValue) {
      const request=job(requestValue);need(!this.#busy && !this.#retired.has(request.id) && this.#retired.size<MAX_JOBS,'Office worker busy/replay/exhausted');
      const module=this.#module;need(module._lokjs_state()===2,'Actual LOK main loop is not running');
      this.#busy=true;let document=0,allocation=0;
      try {
        document=module.ccall('lokjs_document_load','number',['string','string'],['file://'+request.input,'']);
        if(!document) {
          const ptr=module._lokjs_get_error();const error=ptr?module.UTF8ToString(ptr):'no native detail';if(ptr)module._lokjs_free(ptr);
          throw new Error('Actual Writer load failed: '+error);
        }
        module.ccall('lokjs_initialize_for_rendering',null,['number','string'],[document,'{}']);
        const width=dimensions(module._lokjs_document_width(document)),height=dimensions(module._lokjs_document_height(document));
        const length=TILE_WIDTH*TILE_HEIGHT*4;allocation=module._malloc(length);need(allocation>0,'Actual tile allocation failed');
        module.HEAPU8.fill(0,allocation,allocation+length);
        module._lokjs_paint_tile(document,allocation,TILE_WIDTH,TILE_HEIGHT,0,0,width,height);
        const tile=module.HEAPU8.slice(allocation,allocation+length);
        for(const [path,format] of [[request.odt,'odt'],[request.pdf,'pdf']])
          need(module.ccall('lokjs_save_as','number',['number','string','string','string'],[document,'file://'+path,format,''])===1,'Actual Writer saveAs failed');
        const region=[0,0,Math.min(width,TILE_WIDTH*15),Math.min(height,TILE_HEIGHT*15)];
        module.HEAPU8.fill(0,allocation,allocation+length);
        module._lokjs_paint_tile(document,allocation,TILE_WIDTH,TILE_HEIGHT,...region);
        const detail=module.HEAPU8.slice(allocation,allocation+length),mode=module._lokjs_get_tile_mode(document);
        need(mode===0 || mode===1,'Unsupported native tile mode');
        return Object.freeze({id:request.id,documentWidthTwips:width,documentHeightTwips:height,tileWidth:TILE_WIDTH,tileHeight:TILE_HEIGHT,
          tileMode:mode,tile,detail,detailRegionTwips:Object.freeze(region),heapBytes:module.HEAPU8.byteLength});
      } finally {
        if(allocation)module._free(allocation);
        if(document)module._lokjs_document_destroy(document);
        this.#busy=false;this.#retired.add(request.id);
      }
    }
  }
  global.OsbLok=Object.freeze({describe,MainFiles,Worker,limits:Object.freeze({MAX_BYTES,MAX_JOBS,TILE_WIDTH,TILE_HEIGHT}),
    scope:'Experimental bounded Writer/WASM adapter only; engine payload, Android embedding, fidelity/security and licence gates are separate.'});
})(globalThis);
