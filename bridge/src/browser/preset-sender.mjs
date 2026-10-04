export const presets={briefing:'지금 뭐하고 있어? 브리핑해줘',thanks:'고마워',stop:'멈춰',later:'알겠어. 이따 확인해 볼게',continue:'계속해줘'};
// Read only visible DOM. Do not use application internals, server APIs or hidden submission hooks.
export function editorState(target){
 if(location.href!==target)return {error:'ADDRESS_CHANGED'};
 if(!document.querySelector('main article[data-message-id] .message-surface'))return {error:'UNSUPPORTED_OR_LOGIN'};
 const visible=e=>!!e&&e.getClientRects().length>0;
 const editors=[...document.querySelectorAll('[contenteditable="true"][role="textbox"][aria-label="메시지"]')].filter(visible);
 const buttons=[...document.querySelectorAll('button[aria-label="보내기"]')].filter(visible);
 if(editors.length!==1||buttons.length!==1)return {error:'UNSUPPORTED_EDITOR'};
 return {draft:editors[0].innerText.trim(),disabled:buttons[0].disabled,ids:[...document.querySelectorAll('article[data-message-id]')].map(e=>e.dataset.messageId)};
}
export function sentMessage({target,ids,text}){
 if(location.href!==target)return {error:'ADDRESS_CHANGED'};
 if(!document.querySelector('main article[data-message-id] .message-surface'))return {error:'UNSUPPORTED_OR_LOGIN'};
 const rows=[...document.querySelectorAll('article[data-message-id].self')];
 const row=rows.find(r=>!/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(r.dataset.messageId)&&!ids.includes(r.dataset.messageId)&&[...r.querySelectorAll('.message-surface')].filter(b=>!b.parentElement?.closest('.message-surface')).map(b=>b.innerText).filter(t=>t.length>0).join('\n\n').trim()===text);
 return row?{messageId:row.dataset.messageId}:{};
}
export async function sendPreset(page,target,presetId,guard){
 const text=presets[presetId];if(!text)return {status:'failed',code:'INVALID_PRESET'};
 return sendText(page,target,text,guard);
}
export async function sendText(page,target,text,guard){
 if(typeof text!=='string'||!text.trim()||Array.from(text).length>2000)return {status:'failed',code:'INVALID_TEXT'};
 let submitted=false;
 try{
  await guard();const initial=await page.evaluate(editorState,target);if(initial.error)return {status:'failed',code:initial.error};if(initial.draft)return {status:'failed',code:'DRAFT_EXISTS'};
  await page.fill('loc=css:[contenteditable="true"][role="textbox"][aria-label="메시지"]',text);
  await guard();const filled=await page.evaluate(editorState,target);if(filled.error)return {status:'failed',code:filled.error};if(filled.draft!==text.trim())return {status:'failed',code:'DRAFT_CHANGED'};
  if(filled.disabled)return {status:'failed',code:'SEND_DISABLED'};
  // Mark uncertain BEFORE dispatch: a lost action receipt may still have sent the message.
  submitted=true;await page.click('loc=css:button[aria-label="보내기"]');
  const deadline=Date.now()+15000;
  while(Date.now()<deadline){await guard();const result=await page.evaluate(sentMessage,{target,ids:filled.ids,text:text.trim()});if(result.error)throw Error(result.error);if(result.messageId)return {status:'confirmed',messageId:result.messageId};await new Promise(r=>setTimeout(r,100));}
  return {status:'uncertain',code:'CONFIRMATION_TIMEOUT'};
 }catch(error){const safe=['USER_CONTROL','ADDRESS_CHANGED','SPACE_UNAVAILABLE','UNSUPPORTED_OR_LOGIN'];return {status:submitted?'uncertain':'failed',code:safe.includes(error?.message)?error.message:'BROWSER_TRANSPORT_ERROR'};}
}
