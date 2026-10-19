// SPDX-License-Identifier: Apache-2.0
// Separately served consumer of the production OSB adapter, on the real LOK-owning worker.
importScripts(__R25_ADAPTER_URL__);
(() => {
  const reportUrl=__R25_REPORT_URL__, id=__R25_JOB_ID__;
  let checks=0;
  function refuse(action){let rejected=false;try{action();}catch{rejected=true;}if(!rejected)throw new Error('Office protocol attack accepted');checks++;}
  function encoded(data){if(data.length>2*1024*1024)throw new Error('Output cap');let s='';for(let i=0;i<data.length;i+=16384)s+=String.fromCharCode(...data.subarray(i,i+16384));return btoa(s);}
  async function publish(data){await fetch(reportUrl,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});}
  Module.lokReady=()=>setTimeout(async()=>{
    const stages={nativeState:Module._lokjs_state(),mainLoopReady:true};
    try {
      const worker=new OsbLok.Worker(Module);const request=OsbLok.describe(id);
      refuse(()=>OsbLok.describe('../unowned'));
      refuse(()=>worker.convert({...request,input:'/tmp/unowned.fodt'}));
      refuse(()=>worker.convert({...request,tileWidth:4096}));
      const actual=worker.convert(request);
      refuse(()=>worker.convert(request));
      const value={...actual,origin:'real-lok-wasm-worker',state:2,workerProtocolChecks:checks,
        tileBase64:encoded(actual.tile),detailBase64:encoded(actual.detail)};
      delete value.tile;delete value.detail;
      await publish(value);
    }catch(error){await publish({origin:'real-lok-wasm-worker',stages,error:String(error),stack:String(error?.stack||'').slice(0,3000)});}
  },0);
})();
