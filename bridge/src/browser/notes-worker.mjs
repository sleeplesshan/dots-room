import {open} from 'node:fs/promises';import {createWriteStream,constants} from 'node:fs';import {once} from 'node:events';
// Selector was observed in the logged-in dedicated Page; no parent/sidebar/composer read.
export function installNotesReader(target){
 if(location.href!==target)return {error:'ADDRESS_CHANGED'};
 window.__bamiNotes?.stop();const editor=document.querySelector('[role="textbox"][aria-label="페이지 문서"]');const title=document.querySelector('textarea[aria-label="페이지 제목"]');
 if(!editor||!title||title.value!=='바미 업무 메모')return {error:'UNSUPPORTED_OR_LOGIN'};
 let changed=true;let last=0;const observer=new MutationObserver(()=>{changed=true;last=performance.now()});observer.observe(editor,{subtree:true,childList:true,characterData:true});
 const rows=()=>[...editor.querySelectorAll('[data-page-block-row]')].map(row=>({heading:row.querySelector('h1,h2,h3,h4,h5,h6')?.textContent?.trim()??null,text:(row.querySelector("[data-page-block-viewport]")??row).innerText}));
 window.__bamiNotes={stop:()=>observer.disconnect(),drain:()=>{if(location.href!==target)return {error:'ADDRESS_CHANGED'};if(!editor.isConnected||title.value!=='바미 업무 메모')return {reinstall:true};if(changed&&performance.now()-last>=200){changed=false;const content=rows();if(content.length===0){changed=true;return {pending:true}}return {rows:content}}return changed?{pending:true}:{};},read:rows};return {ready:true};
}
export async function runNotes(taskSpace,spaceId,pageId,stopPipe,dataPipe){
 const target='https://chatgpt.com/space/'+pageId;const output=createWriteStream(dataPipe);const write=async v=>{if(!output.write(JSON.stringify(v)+'\n'))await once(output,'drain')};const control=await open(stopPipe,constants.O_RDONLY|constants.O_NONBLOCK);const b=Buffer.alloc(64);let page;
 try{const task=await taskSpace(spaceId);if(task.ownership!=='agent')throw Error('USER_CONTROL');page=task.page('p1');if(await page.url()!==target)throw Error('ADDRESS_CHANGED');await page.waitForFunction(()=>!!document.querySelector('[role="textbox"][aria-label="페이지 문서"]'),undefined,{timeout:15000});let result=await page.evaluate(installNotesReader,target);if(result.error)throw Error(result.error);let beat=0;
 while(true){try{const r=await control.read(b,0,64,null);if(b.subarray(0,r.bytesRead).toString().includes('stop'))break;}catch(e){if(e.code!=='EAGAIN')throw e;}const current=await taskSpace(spaceId);if(current.ownership!=='agent')throw Error('USER_CONTROL');if(await page.url()!==target)throw Error('ADDRESS_CHANGED');let drain=await page.evaluate(()=>window.__bamiNotes?.drain()??{reinstall:true});if(drain.error)throw Error(drain.error);if(drain.reinstall){result=await page.evaluate(installNotesReader,target);if(result.error)throw Error(result.error);drain=await page.evaluate(()=>window.__bamiNotes.drain());}
 if(!drain.pending&&(drain.rows||Date.now()-beat>=5000)){const rows=drain.rows??await page.evaluate(()=>window.__bamiNotes.read());await write({kind:'notes',rows,observedAt:new Date().toISOString()});beat=Date.now();}await new Promise(r=>setTimeout(r,500));}
 await page.evaluate(()=>window.__bamiNotes?.stop());
 }catch(e){await write({kind:'error',code:['USER_CONTROL','ADDRESS_CHANGED','UNSUPPORTED_OR_LOGIN'].includes(e?.message)?e.message:'SPACE_UNAVAILABLE'});}finally{await control.close();await new Promise(r=>output.end(r));}
}
