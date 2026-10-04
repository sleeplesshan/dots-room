import {it,expect} from 'vitest';
import {randomUUID} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {request} from 'node:http';
import {JSDOM} from 'jsdom';
import {BamiBridge} from '../bridge/src/server.js';
import {textCommandSchema,MAX_TEXT_LENGTH} from '../bridge/src/browser/commands.js';
import {newToken} from '../bridge/src/auth.js';
import {TARGET} from './target.js';
import {MediaStore} from '../bridge/src/media.js';
it('preserves UTF-8 code points split across HTTP body packets',async()=>{
 const text='한글 💙\n두 번째 줄',token=newToken();let received='';
 const b=new BamiBridge({source:{adapterId:'http-fragment-fixture',sourceMode:'mock',sourceSessionId:null},start(){},stop(){},async sendText(v:string){received=v;return {status:'confirmed'}}},token);const port=await b.start(0);
 try{
  const bytes=Buffer.from(JSON.stringify({commandId:randomUUID(),text}));const split=bytes.indexOf(Buffer.from('💙'))+1;
  const result=await new Promise<number>((resolve,reject)=>{const req=request(`http://127.0.0.1:${port}/v1/messages`,{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json','Content-Length':bytes.length}},res=>{res.resume();res.on('end',()=>resolve(res.statusCode!))});req.on('error',reject);req.write(bytes.subarray(0,split));setTimeout(()=>{req.write(bytes.subarray(split,split+2));setTimeout(()=>req.end(bytes.subarray(split+2)),20)},20)});
  expect(result).toBe(200);expect(received).toBe(text);
 }finally{await b.stop()}
});
it('carries long Korean/emoji free text through private IPC without character loss or duplicate bubbles',async()=>{
 const {BrowserDotsAdapter}=await import('../bridge/dist/adapters/browser-dots.js');const adapter=new BrowserDotsAdapter(new MediaStore(),process.cwd()+'/tests/fixtures/ego-reader.cjs');const rows:any[]=[];
 await adapter.start(e=>{if(e.type==='message.observed')rows.push(e.payload)});
 try{const until=Date.now()+5000;while(!adapter.diagnostic.ready&&Date.now()<until)await new Promise(r=>setTimeout(r,20));const text='💙'.repeat(1998)+'가\n';const r=await adapter.sendText(text);expect(r.status).toBe('confirmed');await adapter.refresh();const found=rows.filter(m=>m.messageId===r.messageId);expect(found).toHaveLength(1);expect(found[0].content).toBe(text);}finally{await adapter.stop()}
},15000);
it('counts Unicode code points, preserves multiline text and rejects extra fields',()=>{
 const commandId=randomUUID();
 expect(textCommandSchema.parse({commandId,text:'한글 💙\n둘째 줄'}).text).toBe('한글 💙\n둘째 줄');
 expect(textCommandSchema.safeParse({commandId,text:'💙'.repeat(MAX_TEXT_LENGTH)}).success).toBe(true);
 for(const text of ['', ' \n ', '💙'.repeat(MAX_TEXT_LENGTH+1)])expect(textCommandSchema.safeParse({commandId,text}).success).toBe(false);
 expect(textCommandSchema.safeParse({commandId,text:'hello',url:TARGET}).success).toBe(false);
});
it('authenticates free input, shares serialization with sync/presets and caches uncertain results',async()=>{
 let calls=0;let finish:(v:any)=>void=()=>{};
 const adapter={source:{adapterId:'free-fixture',sourceMode:'mock' as const,sourceSessionId:null},start(){},stop(){},sendText:async(text:string)=>{calls++;expect(text).toBe('한글 💙\n둘째 줄');return new Promise<any>(r=>finish=r)},async sendPreset(){return {status:'confirmed' as const}},async refresh(){return {observedAt:new Date().toISOString()}}};
 const token=newToken(),b=new BamiBridge(adapter,token),port=await b.start(0),root=`http://127.0.0.1:${port}`;
 const headers={Authorization:`Bearer ${token}`,'Content-Type':'application/json'};
 const post=(path:string,body:any)=>fetch(root+path,{method:'POST',headers,body:JSON.stringify(body)});
 try{
  expect((await fetch(root+'/v1/messages',{method:'POST',body:'{}'})).status).toBe(401);
  expect(await(await fetch(root+'/v1/capabilities',{headers})).json()).toMatchObject({textMessages:true,maxTextLength:2000});
  expect((await post('/v1/messages',{commandId:randomUUID(),text:'💙'.repeat(2001)})).status).toBe(400);
  const command={commandId:randomUUID(),text:'한글 💙\n둘째 줄'};const first=post('/v1/messages',command);
  while(calls===0)await new Promise(r=>setTimeout(r,10));
  expect((await post('/v1/sync',{commandId:randomUUID()})).status).toBe(409);
  expect((await post('/v1/preset-messages',{commandId:randomUUID(),presetId:'thanks'})).status).toBe(409);
  finish({status:'uncertain',code:'CONFIRMATION_TIMEOUT'});expect(await(await first).json()).toMatchObject({status:'uncertain'});
  expect(await(await post('/v1/messages',command)).json()).toMatchObject({status:'uncertain'});expect(calls).toBe(1);
  expect((await post('/v1/messages',{...command,text:'different'})).status).toBe(409);
 }finally{finish({status:'uncertain'});await b.stop()}
});
it('keeps free input disabled on old read-only adapters',async()=>{
 const token=newToken(),b=new BamiBridge({source:{adapterId:'old',sourceMode:'mock',sourceSessionId:null},start(){},stop(){}},token),port=await b.start(0),headers={Authorization:`Bearer ${token}`,'Content-Type':'application/json'};
 try{expect(await(await fetch(`http://127.0.0.1:${port}/v1/capabilities`,{headers})).json()).toMatchObject({textMessages:false,maxTextLength:2000});expect((await fetch(`http://127.0.0.1:${port}/v1/messages`,{method:'POST',headers,body:JSON.stringify({commandId:randomUUID(),text:'hi'})})).status).toBe(501)}finally{await b.stop()}
});
it('confirms actual DOM text only and preserves user drafts/control/address guards for free input',async()=>{
 const d=new JSDOM('<main><article data-message-id="before"><div class="message-surface">fixture</div></article></main><div contenteditable="true" role="textbox" aria-label="메시지"></div><button aria-label="보내기" disabled></button>',{url:TARGET,runScripts:'outside-only'}),w:any=d.window;
 w.eval('HTMLElement.prototype.__defineGetter__("innerText",function(){return this.textContent});HTMLElement.prototype.getClientRects=function(){return [{}]}');w.eval(readFileSync('bridge/src/browser/preset-sender.mjs','utf8').replace(/export /g,'')+';window.freeSend=sendText');let clicks=0;
 const editor=w.document.querySelector('[contenteditable]');
 const page={async evaluate(fn:any,arg:any){w.__arg=arg;return w.eval('('+fn.toString()+')(__arg)')},async fill(_:string,text:string){editor.textContent=text;w.document.querySelector('button').disabled=!text},async click(){clicks++;const a=w.document.createElement('article');a.dataset.messageId='dom-confirmed-'+clicks;a.className='self';const surface=w.document.createElement('div');surface.className='message-surface';surface.textContent=editor.textContent;a.append(surface);w.document.querySelector('main').append(a);editor.textContent=''}};
 try{
  const text='첫째 줄 💙\n둘째 줄';expect(await w.freeSend(page,TARGET,text,async()=>{})).toMatchObject({status:'confirmed',messageId:'dom-confirmed-1'});expect(w.document.querySelector('[data-message-id="dom-confirmed-1"]').textContent).toBe(text);
  editor.textContent='기존 초안';expect((await w.freeSend(page,TARGET,text,async()=>{})).code).toBe('DRAFT_EXISTS');expect(editor.textContent).toBe('기존 초안');editor.textContent='';
  expect((await w.freeSend(page,TARGET,text,async()=>{throw Error('USER_CONTROL')})).code).toBe('USER_CONTROL');
  w.history.pushState({},'', '/auth/login');expect((await w.freeSend(page,TARGET,text,async()=>{})).code).toBe('ADDRESS_CHANGED');expect(clicks).toBe(1);
 }finally{d.window.close()}
});
