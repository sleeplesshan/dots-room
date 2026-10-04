/** Recover only a crashed worker in its existing agent space. Control/navigation errors stop. */
export class BrowserRecovery {
 spaceId:number|null=null;attempts=0;blocked=false;reason:string|null=null;startedAt=0;
 ready(spaceId:number,now:number){this.spaceId=spaceId;this.startedAt=now;}
 heartbeat(now:number){if(now-this.startedAt>=30000)this.attempts=0;}
 error(code:string){this.reason=['USER_CONTROL','ADDRESS_CHANGED','UNSUPPORTED_OR_LOGIN','STRUCTURE_CHANGED','SPACE_UNAVAILABLE','BROWSER_TRANSPORT_ERROR'].includes(code)?code:'SOURCE_ERROR';if(code!=='BROWSER_TRANSPORT_ERROR')this.blocked=true;}
 delay():number|null{if(this.blocked||this.spaceId===null||this.attempts>=5)return null;return Math.min(30000,1000*2**this.attempts++);}
}
