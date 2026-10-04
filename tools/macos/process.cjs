// Only processes launched by THIS package are eligible for termination.
const fs=require('node:fs'),path=require('node:path'),net=require('node:net');
const {spawn,execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..'),runtime=path.join(root,'.state/runtime'),marker=path.join(runtime,'bridge.json');
const state=path.join(require('node:os').homedir(),'.local/state/dots-room');
const cli=path.join(root,'bridge/dist/cli.js');
function fingerprint(pid){try{return execFileSync('/bin/ps',['-p',String(pid),'-o','lstart=,command='],{encoding:'utf8'}).trim()}catch{return ''}}
function own(m){return m.root===root&&Number.isSafeInteger(m.pid)&&fingerprint(m.pid)===m.fingerprint&&m.fingerprint.includes(cli)}
async function free(port){await new Promise((r,j)=>{const s=net.createServer();s.once('error',()=>j(Error('PORT_OCCUPIED_'+port)));s.listen(port,'127.0.0.1',()=>s.close(r))})}
async function main(){const mode=process.argv[2];if(mode==='stop'){if(!fs.existsSync(marker)){console.log('이 패키지가 실행한 새 브리지가 없습니다.');return;}const m=JSON.parse(fs.readFileSync(marker));if(!fingerprint(m.pid)){fs.unlinkSync(marker);return;}if(!own(m))throw Error('PROCESS_OWNERSHIP_CHANGED');process.kill(m.pid,'SIGTERM');const end=Date.now()+15000;while(fingerprint(m.pid)&&Date.now()<end)await new Promise(r=>setTimeout(r,100));if(fingerprint(m.pid))throw Error('BRIDGE_STOP_TIMEOUT');fs.unlinkSync(marker);console.log('새 무선 브리지만 종료했습니다.');return;}
 if(mode!=='start')throw Error('START_OR_STOP_REQUIRED');if(fs.existsSync(marker)){const m=JSON.parse(fs.readFileSync(marker));if(fingerprint(m.pid)){if(!own(m))throw Error('PROCESS_OWNERSHIP_CHANGED');console.log('이 패키지의 새 브리지가 실행 중입니다.');return;}fs.unlinkSync(marker)}
 const port=Number(process.env.BAMI_PORT??8788);if(!Number.isInteger(port)||port<1024||port>65535||[8765,8787].includes(port))throw Error('NEW_PORT_REQUIRED');await free(port);
 const fixture=process.argv[4]==='--fixture';if(process.argv[4]&&!fixture)throw Error('UNKNOWN_ARGUMENT');if(fixture&&process.argv[3]!=='mock')throw Error('SYNTHETIC_FIXTURE_ONLY');const token=fixture?path.join(root,'.cache/demo/token'):path.join(state,'pairing.token');if(!fs.existsSync(token))throw Error(fixture?'FIXTURE_TOKEN_REQUIRED':'PAIRING_APPROVAL_REQUIRED');
 fs.mkdirSync(runtime,{recursive:true,mode:0o700});fs.chmodSync(runtime,0o700);const gateway=path.join(runtime,'gateway.json');if(fs.existsSync(gateway)){const g=JSON.parse(fs.readFileSync(gateway));if(g.bridgePort!==port||!Number.isInteger(g.port)||g.port===port||[8765,8787].includes(g.port))throw Error('GATEWAY_PORT_MISMATCH');await free(g.port)}
 const adapter=process.argv[3]??'browser-dots';if(!['browser-dots','mock','dots'].includes(adapter))throw Error('ADAPTER_REQUIRED');
 const env={...process.env,BAMI_BROWSER_SPACE_FILE:path.join(state,'browser-space-id'),BAMI_KEEP_BROWSER_PAGE_ON_STOP:'1'};delete env.BAMI_BROWSER_SPACE_ID;const localSpace=path.join(runtime,'browser-space-id');if(fs.existsSync(localSpace))env.BAMI_BROWSER_SPACE_ID=fs.readFileSync(localSpace,'utf8').trim();if(fs.existsSync(env.BAMI_BROWSER_SPACE_FILE))env.BAMI_BROWSER_SPACE_ID=fs.readFileSync(env.BAMI_BROWSER_SPACE_FILE,'utf8').trim();if(fs.existsSync(gateway))env.BAMI_GATEWAY_CONFIG=gateway;

 const child=spawn(process.execPath,[cli,'--host','127.0.0.1','--port',String(port),'--adapter',adapter,'--token-file',token],{cwd:root,env,detached:true,stdio:'ignore'});child.unref();await new Promise((r,j)=>{child.once('spawn',r);child.once('error',()=>j(Error('BRIDGE_SPAWN_FAILED')))});
 const started=fingerprint(child.pid);if(!started.includes(cli))throw Error('BRIDGE_START_FAILED');const record={root,pid:child.pid,fingerprint:started,port,adapter};fs.writeFileSync(marker,JSON.stringify(record,null,2)+'\n',{mode:0o600});
 const end=Date.now()+15000;let ready=false;while(Date.now()<end&&own(record)){try{const res=await fetch(`http://127.0.0.1:${port}/healthz`,{signal:AbortSignal.timeout(1000)});if(res.ok){ready=true;break}}catch{}await new Promise(r=>setTimeout(r,100))};if(!ready){if(own(record))process.kill(child.pid,'SIGTERM');throw Error('BRIDGE_START_FAILED')};console.log(`새 브리지 실행: 127.0.0.1:${port} · ${adapter} · Dots Room 전용`);
}
main().catch(e=>{console.error(/^[A-Z_0-9]+$/.test(e.message)?e.message:'WIRELESS_PROCESS_FAILED');process.exitCode=1});
