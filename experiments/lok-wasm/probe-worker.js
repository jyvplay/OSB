// SPDX-License-Identifier: Apache-2.0
// Loaded through the actual engine's UNO script hook, on its LOK-owning pthread.
(() => {
  const reportUrl = __R25_REPORT_URL__;
  const marker = 'OSB R25 actual LibreOfficeKit canary';
  function encoded(bytes) {
    if (bytes.length > 2 * 1024 * 1024) throw new Error('Engine output outside experiment byte budget');
    let s=''; for(let i=0;i<bytes.length;i+=16384) s+=String.fromCharCode(...bytes.subarray(i,i+16384));
    return btoa(s);
  }
  async function report(value) {
    try { await fetch(reportUrl, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(value)}); }
    catch(error) { console.error('R25 engine result delivery failed', String(error)); }
  }
  Module.lokReady = () => setTimeout(async () => {
    let doc=0, pixels=0;
    const stages={mainLoopReady:false,sourceWritten:false,documentLoaded:false};
    try {
      const state=Module._lokjs_state(); stages.nativeState=state; if(state!==2) throw new Error('Actual LOK main loop is not running: '+state);
      stages.mainLoopReady=true;
      // Filesystem I/O belongs to the browser main runtime; C syscalls are proxied there.
      // The original FODT fixture is staged by Module.onRuntimeInitialized before this LOK thread starts.
      doc=Module.ccall('lokjs_document_load','number',['string','string'],['file:///tmp/r25-canary.fodt','']);
      if(!doc) {
        const pointer=Module._lokjs_get_error();
        const detail=pointer?Module.UTF8ToString(pointer):'no native error string';
        if(pointer)Module._lokjs_free(pointer);
        throw new Error('Actual LibreOffice document load returned no document: '+detail);
      }
      stages.documentLoaded=true;
      Module.ccall('lokjs_initialize_for_rendering',null,['number','string'],[doc,'{}']);
      const width=Module._lokjs_document_width(doc), height=Module._lokjs_document_height(doc);
      if(!(width>0 && height>0 && width<=1000000 && height<=1000000)) throw new Error('Document size outside trusted-fixture regime');
      const tileWidth=256, tileHeight=320, length=tileWidth*tileHeight*4;
      pixels=Module._malloc(length); if(!pixels) throw new Error('Native tile allocation failed');
      Module.HEAPU8.fill(0,pixels,pixels+length);
      Module._lokjs_paint_tile(doc,pixels,tileWidth,tileHeight,0,0,width,height);
      const tile=Module.HEAPU8.slice(pixels,pixels+length);
      const odtOk=Module.ccall('lokjs_save_as','number',['number','string','string','string'],[doc,'file:///tmp/r25-result.odt','odt','']);
      const pdfOk=Module.ccall('lokjs_save_as','number',['number','string','string','string'],[doc,'file:///tmp/r25-result.pdf','pdf','']);
      if(!odtOk || !pdfOk) throw new Error('Actual saveAs reported failure');
      await report({origin:'real-lok-wasm-worker',state,documentWidthTwips:width,documentHeightTwips:height,
        tileWidth,tileHeight,tileMode:Module._lokjs_get_tile_mode(doc),heapBytes:Module.HEAPU8.byteLength,
        tileBase64:encoded(tile),outputsSaved:true});
    } catch(error) { await report({origin:'real-lok-wasm-worker',stages,error:String(error),stack:String(error?.stack||'').slice(0,4000)}); }
    finally {
      if(pixels) Module._free(pixels);
      if(doc) Module._lokjs_document_destroy(doc);
    }
  },0);
})();
