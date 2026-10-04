import { SourceAdapter } from './adapter.js';
import { Input } from '../protocol.js';
export class MockAdapter implements SourceAdapter {
 source={adapterId:'mock-v1',sourceMode:'mock' as const,sourceSessionId:'bami-demo'};private timer?:NodeJS.Timeout;
 start(emit:(e:Input)=>void){let tick=0,cycle=0;const run=()=>{const t=tick++%48;if(t===0){cycle++;if(cycle>1)emit({type:'task.remove',conversationId:'demo',taskId:'task-'+(cycle-1),payload:{}});emit({type:'source.status',payload:{state:'demo',lastObservedAt:new Date().toISOString(),detail:'데모 · 실제 dots 대화가 아니에요'}});}
 const conversationId='demo',taskId='task-'+cycle;const task=(status:string,phase:string,publicSummary:string)=>emit({type:'task.upsert',conversationId,taskId,payload:{status,phase,publicSummary,priority:50,updatedAt:new Date().toISOString()}});
 if(t===1)emit({type:'message.completed',conversationId,payload:{messageId:'user-'+cycle,revision:1,role:'user',content:'바미야, 오늘 할 일을 정리해 줘',completedAt:new Date().toISOString()}});
 if(t===2)task('running','reading','형아, 자료를 살펴보고 있어요');if(t===7)task('running','working','형아, 오늘 할 일을 정리하고 있어요');
 if(t===12){task('running','responding','형아, 정리한 내용을 알려 드릴게요');emit({type:'message.start',conversationId,taskId,payload:{messageId:'reply-'+cycle,revision:1,role:'assistant'}});}
 const chunks=['형아, ','오늘은 중요한 일부터 ','하나씩 해 보세요. 🌙\n','잠깐 쉬는 시간도 챙겨 주세요.'];
 if(t>=13&&t<=16)emit({type:'message.delta',conversationId,taskId,payload:{messageId:'reply-'+cycle,revision:1,role:'assistant',deltaIndex:t-13,text:chunks[t-13],isFinal:t===16}});
 if(t===17)emit({type:'message.completed',conversationId,taskId,payload:{messageId:'reply-'+cycle,revision:1,role:'assistant',content:chunks.join(''),completedAt:new Date().toISOString()}});
 if(t===18)task('waiting_user','none','형아, 확인을 기다리고 있어요');if(t===23)task('completed','none','형아, 정리를 마쳤어요');};run();this.timer=setInterval(run,700);}
 stop(){if(this.timer)clearInterval(this.timer);}
}
