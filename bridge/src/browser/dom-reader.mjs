// This function runs inside the Page. No network, cookies or application internals.
export function installReader(target) {
 if(location.href!==target)return {error:'ADDRESS_CHANGED'};
 const root=document.querySelector('main');const rows=[...document.querySelectorAll('article[data-message-id]')];
 if(!root||!rows.length||rows.some(r=>!r.classList.contains('message-row'))||!rows.some(r=>r.querySelector('.message-surface')))return {error:'UNSUPPORTED_OR_LOGIN'};
 window.__bamiReader?.stop();
 const dirty=new Set(rows.filter(r=>r.querySelector('.message-surface')).slice(-50).map(r=>r.dataset.messageId));let ready=true,timer=null,first=0;
 const surfaces=r=>[...r.querySelectorAll('.message-surface')].filter(b=>!b.parentElement?.closest('.message-surface'));
 const bodyImages=r=>[...new Set(surfaces(r).flatMap(b=>[...b.querySelectorAll('img')]))];
 // Background tabs can throttle timers and image load callbacks. Keep fingerprints inside
 // the Page; private image URLs never leave this closure. No server requests are made.
 const signature=r=>JSON.stringify([r.dataset.messageId,r.classList.contains('self'),surfaces(r).map(b=>b.innerText),r.querySelector('time[datetime]')?.getAttribute('datetime'),bodyImages(r).map(i=>[i.alt,i.currentSrc,i.getAttribute('src'),i.complete,i.naturalWidth,i.naturalHeight])]);
 const signatures=new WeakMap(rows.map(r=>[r,signature(r)]));let observedBody=document.body;
 const mark=()=>{first ||= Date.now();clearTimeout(timer);timer=setTimeout(()=>{ready=true;first=0;},Math.min(50,Math.max(0,200-(Date.now()-first))));};
 const touch=node=>{const el=node.nodeType===1?node:node.parentElement;const row=el?.closest('article[data-message-id]');if(row){dirty.add(row.dataset.messageId);mark();}el?.querySelectorAll?.('article[data-message-id]').forEach(r=>{dirty.add(r.dataset.messageId);mark();});};
 const observer=new MutationObserver(changes=>changes.forEach(c=>{touch(c.target);c.addedNodes.forEach(touch);}));const observe=()=>observer.observe(observedBody,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['src','srcset','alt','class','datetime','data-message-id']});observe();
 const imageEvent=e=>touch(e.target);document.addEventListener('load',imageEvent,true);document.addEventListener('error',imageEvent,true);
 window.__bamiReader={refresh(){observer.disconnect();observedBody=document.body;observe();for(const r of [...document.querySelectorAll('article[data-message-id]')].filter(r=>surfaces(r).length).slice(-50))dirty.add(r.dataset.messageId);ready=true;first=0;},stop(){observer.disconnect();clearTimeout(timer);document.removeEventListener('load',imageEvent,true);document.removeEventListener('error',imageEvent,true);},drain(){if(location.href!==target)return {error:'ADDRESS_CHANGED'};if(!document.querySelector('main article[data-message-id] .message-surface'))return {error:'UNSUPPORTED_OR_LOGIN'};
  if(observedBody!==document.body){observer.disconnect();observedBody=document.body;observe();}
  const currentRows=[...document.querySelectorAll('article[data-message-id]')];
  if(currentRows.some(r=>!r.classList.contains('message-row')))return {error:'STRUCTURE_CHANGED'};
  for(const r of currentRows){const value=signature(r);if(signatures.get(r)!==value){signatures.set(r,value);dirty.add(r.dataset.messageId);mark();}}
  // Enforce the 200ms bound from the worker's queue drain even if setTimeout is suspended.
  if(!ready&&first&&Date.now()-first>=200){clearTimeout(timer);ready=true;first=0;}
  if(!ready)return {rows:[]};ready=false;const out=[];for(const id of dirty){const r=currentRows.find(r=>r.dataset.messageId===id);if(!r)continue;
  // This page uses bare UUIDs for optimistic user rows and a CalpicoMessage ID after acknowledgement.
  // Preserve final DOM IDs; do not emit the temporary preview as a second logical message.
  if(r.classList.contains('self')&&/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(id))continue;
  const bodies=surfaces(r);if(!bodies.length)continue;const time=r.querySelector('time[datetime]')?.getAttribute('datetime');out.push({id,role:r.classList.contains('self')?'user':'assistant',content:bodies.map(b=>b.innerText).filter(text=>text.length>0).join('\n\n'),sourceTimestamp:time&&time.length<=30&&/^\d{4}-\d{2}-\d{2}T.*Z$/.test(time)&&Number.isFinite(Date.parse(time))?time:null,images:bodyImages(r).slice(0,8).map((img,index)=>({index,alt:(img.alt||'').slice(0,1000)}))});}dirty.clear();return {rows:out};}};
 return {ok:true};
}
export function captureImage({id,index}) {
 const row=[...document.querySelectorAll('article[data-message-id]')].find(r=>r.dataset.messageId===id),bodies=[...(row?.querySelectorAll('.message-surface')??[])].filter(b=>!b.parentElement?.closest('.message-surface')),img=[...new Set(bodies.flatMap(b=>[...b.querySelectorAll('img')]))][index];
 if(!img)return {status:'error',error:'IMAGE_REMOVED'};if(!img.complete)return {status:'pending'};if(!img.naturalWidth||!img.naturalHeight)return {status:'error',error:'IMAGE_LOAD_FAILED'};
 try{const scale=Math.min(1,1024/Math.max(img.naturalWidth,img.naturalHeight)),canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(img.naturalWidth*scale));canvas.height=Math.max(1,Math.round(img.naturalHeight*scale));canvas.getContext('2d').drawImage(img,0,0,canvas.width,canvas.height);const data=canvas.toDataURL('image/png').split(',')[1];if(data.length>Math.ceil(2*1024*1024/3)*4)return {status:'error',error:'IMAGE_TOO_LARGE'};return {status:'available',data};}catch{return {status:'error',error:'IMAGE_PIXELS_UNREADABLE'};}
}
