import { sendPreset,sendText } from './preset-sender.mjs';
import { installReader,captureImage } from './dom-reader.mjs';
import { randomUUID,createHash } from 'node:crypto';
import { StringDecoder } from 'node:string_decoder';
import { once } from 'node:events';
import { createWriteStream,constants } from 'node:fs';
import { open } from 'node:fs/promises';
export async function run(taskSpace,stopPipe,dataPipe,spaceId=null,target){
 if(typeof target!=='string'||!/^https:\/\/chatgpt\.com\/dots\/[a-f0-9-]{36}$/.test(target))throw Error('DOTS_URL_REQUIRED_OR_INVALID');
 const workerId=randomUUID();let detached=false;let stopped=false;const control=await open(stopPipe,constants.O_RDONLY|constants.O_NONBLOCK);const stopBuffer=Buffer.alloc(4096);const controlDecoder=new StringDecoder('utf8');let commandBuffer='';const commands=[];const checkStop=async()=>{try{const result=await control.read(stopBuffer,0,4096,null);commandBuffer+=controlDecoder.write(stopBuffer.subarray(0,result.bytesRead));if(Buffer.byteLength(commandBuffer)>32768)throw Error('CONTROL_LIMIT');let end;while((end=commandBuffer.indexOf('\n'))>=0){const line=commandBuffer.slice(0,end);commandBuffer=commandBuffer.slice(end+1);if(line==='stop')stopped=true;else if(line==='detach'){detached=true;stopped=true;}else{const c=JSON.parse(line);if(c.workerId===workerId&&typeof c.id==='string'&&['sync','send','text'].includes(c.kind))commands.push(c);}}}catch(e){if(e.code!=='EAGAIN')stopped=true;}};
 const output=createWriteStream(dataPipe);const write=async value=>{if(!output.write(JSON.stringify(value)+'\n'))await once(output,'drain');};
 let task,page,normal=false;
 const loadSpace=async value=>{try{return await taskSpace(value);}catch{throw Error('SPACE_UNAVAILABLE');}};
 try{task=await loadSpace(spaceId??'바미 브라우저 대화 표시');if(task.ownership!=='agent')throw Error('USER_CONTROL');await write({kind:'ready',spaceId:task.spaceId,workerId});page=task.page('p1');
 if(spaceId===null)await page.goto(target);else if(await page.url()!==target)throw Error('ADDRESS_CHANGED');await page.waitForFunction(()=>document.querySelector('article[data-message-id] .message-surface'),undefined,{timeout:15000}).catch(()=>{throw Error('UNSUPPORTED_OR_LOGIN');});
 const guard=async()=>{const current=await loadSpace(task.spaceId);if(current.ownership!=='agent')throw Error('USER_CONTROL');if(await page.url()!==target)throw Error('ADDRESS_CHANGED');};
 let result=await page.evaluate(installReader,target);if(result.error)throw Error(result.error);let beat=0,lastControlCheck=0;
 while(!stopped){await checkStop();if(stopped)break;if(Date.now()-lastControlCheck>=1000){const current=await loadSpace(task.spaceId);if(current.ownership!=='agent')throw Error('USER_CONTROL');lastControlCheck=Date.now();}if(await page.url()!==target)throw Error('ADDRESS_CHANGED');const exists=await page.evaluate(()=>!!window.__bamiReader);if(!exists){await page.waitForFunction(()=>document.querySelector('article[data-message-id] .message-surface'),undefined,{timeout:15000}).catch(()=>{throw Error('UNSUPPORTED_OR_LOGIN');});if(await page.url()!==target)throw Error('ADDRESS_CHANGED');result=await page.evaluate(installReader,target);if(result.error)throw Error(result.error);}
 const command=commands.shift();if(command){await guard();if(command.kind==='sync')await page.evaluate(()=>window.__bamiReader.refresh());else{const result=command.kind==='text'?await sendText(page,target,command.text,guard):await sendPreset(page,target,command.presetId,guard);await write({kind:'command',id:command.id,result});}}
 const batch=await page.evaluate(()=>window.__bamiReader.drain());if(batch.error)throw Error(batch.error);
 for(const row of batch.rows){const images=[];for(const image of row.images){const pixel=await page.evaluate(captureImage,{id:row.id,index:image.index});let assetId=null,width=null,height=null;if(pixel.status==='available'){const bytes=Buffer.from(pixel.data,'base64');if(bytes.length>2*1024*1024){pixel.status='error';pixel.error='IMAGE_TOO_LARGE';}else{assetId=createHash('sha256').update(bytes).digest('hex');width=bytes.readUInt32BE(16);height=bytes.readUInt32BE(20);await write({kind:'media',assetId,data:pixel.data});}}images.push({assetId,status:pixel.status,width,height,alt:image.alt,error:pixel.error??null});}await write({kind:'row',row:{...row,images}});}
 if(command?.kind==='sync')await write({kind:'command',id:command.id,result:{observedAt:new Date().toISOString()}});
 if(Date.now()-beat>=5000){await write({kind:'heartbeat'});beat=Date.now();}await new Promise(r=>setTimeout(r,100));}
 normal=true;await page.evaluate(()=>window.__bamiReader?.stop());
 }catch(error){
 const permanent=['USER_CONTROL','SPACE_UNAVAILABLE','ADDRESS_CHANGED','UNSUPPORTED_OR_LOGIN','STRUCTURE_CHANGED'];
 const code=permanent.includes(error?.message)?error.message:'BROWSER_TRANSPORT_ERROR';
 await write({kind:'error',code});
 }
 finally{if(normal&&!detached){await task.finish({keep:[]});await write({kind:"stopped",finished:true});}if(normal&&detached)await write({kind:'stopped',finished:true,detached:true});await control.close();await new Promise(resolve=>output.end(resolve));}
}
