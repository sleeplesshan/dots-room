// Installed 1.96.5 Serve syntax: isolated TLS-terminated TCP + PROXY v2.
// No global reset, Funnel, account tokens, forwarding headers, or public bind.
const fs=require('node:fs'),path=require('node:path'),{execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..'),dir=path.join(root,'.state/runtime'),config=path.join(dir,'gateway.json'),marker=path.join(dir,'serve.json');
const cli=process.env.BAMI_TAILSCALE_CLI||'/usr/local/bin/tailscale';
function run(args){try{return execFileSync(cli,args,{encoding:'utf8',stdio:['ignore','pipe','pipe'],timeout:args[0]==='cert'?60000:15000}).trim()}catch(e){if((e.stderr??'').toString().includes('does not support getting TLS certs'))throw Error('TAILSCALE_HTTPS_ADMIN_REQUIRED');throw Error('TAILSCALE_COMMAND_FAILED_CHECK_CONNECTION_OR_ADMIN_APPROVAL')}}
function status(){return JSON.parse(run(['serve','status','--json']))}
function entry(s,port){return {TCP:s.TCP?.[String(port)]??null,Web:s.Web?.[String(port)]??null}}
function ports(s,port){return s.TCP?.[String(port)]||Object.keys(s.Web??{}).some(k=>k.endsWith(':'+port))}
function flags(c){return ['--tls-terminated-tcp='+c.publicPort,'--proxy-protocol=2']}
function probeCertificate(host){
 // The installed CLI rejects /dev/null once issuance succeeds. Use a private
 // temporary directory, never stdout, and remove both certificate files finally.
 const probe=fs.mkdtempSync(path.join(dir,'cert-probe-'));fs.chmodSync(probe,0o700);
 try{run(['cert','--cert-file='+path.join(probe,'certificate.pem'),'--key-file='+path.join(probe,'key.pem'),host]);for(const name of ['certificate.pem','key.pem'])fs.chmodSync(path.join(probe,name),0o600)}finally{fs.rmSync(probe,{recursive:true,force:true})}
}
async function main(){const mode=process.argv[2];if(!['prepare','setup','stop','status'].includes(mode))throw Error('PREPARE_SETUP_STOP_STATUS_REQUIRED');if(mode==='status'){console.log(run(['serve','status']));return;}
 if(mode==='stop'){if(!fs.existsSync(marker)){console.log('이 패키지가 만든 Serve 접속 지점이 없습니다.');return;}const m=JSON.parse(fs.readFileSync(marker));const now=status();if(!ports(now,m.config.publicPort)){fs.unlinkSync(marker);return;}if(JSON.stringify(entry(now,m.config.publicPort))!==JSON.stringify(m.entry))throw Error('SERVE_OWNERSHIP_CHANGED');try{run([...flags(m.config),'off'])}catch(error){if(ports(status(),m.config.publicPort))throw error};if(ports(status(),m.config.publicPort))throw Error('SERVE_STOP_NOT_CONFIRMED');fs.unlinkSync(marker);console.log('Dots Room Serve 접속 지점만 해제했습니다.');return;}
 const d=JSON.parse(run(['status','--json']));const peerId=process.argv[3];if(!peerId)throw Error('SELECTED_TABLET_TAILSCALE_ID_REQUIRED');const selected=Object.values(d.Peer??{}).find(p=>p.ID===peerId&&p.OS==='android');if(!selected)throw Error('SELECTED_TABLET_NOT_IN_TAILNET');const host=d.Self?.DNSName?.replace(/\.$/,'');if(!host?.endsWith('.ts.net'))throw Error('TAILSCALE_DNS_REQUIRED');const publicPort=Number(process.env.BAMI_PORT??8788),gatewayPort=Number(process.env.BAMI_GATEWAY_PORT??8789);if(![publicPort,gatewayPort].every(p=>Number.isInteger(p)&&p>=1024&&p<=65535&&![8765,8787].includes(p))||publicPort===gatewayPort)throw Error('NEW_PORT_REQUIRED');
 const c={host:host+':'+publicPort,publicPort,bridgePort:publicPort,port:gatewayPort,peerId,peerIps:selected.TailscaleIPs};
 if(!Array.isArray(c.peerIps)||c.peerIps.length<1||c.peerIps.length>2)throw Error('SELECTED_TABLET_IP_REQUIRED');fs.mkdirSync(dir,{recursive:true,mode:0o700});fs.chmodSync(dir,0o700);
 if(fs.existsSync(config)&&JSON.stringify(JSON.parse(fs.readFileSync(config)))!==JSON.stringify(c))throw Error('EXISTING_GATEWAY_CONFIG_DIFFERENT');
 if(mode==='prepare'){fs.writeFileSync(config,JSON.stringify(c,null,2)+'\n',{mode:0o600});console.log(`준비된 주소: https://${c.host}/ · Serve 설정은 아직 변경하지 않았습니다.`);return;}
 if(d.BackendState!=='Running'||!d.Self.Online)throw Error('MAC_TAILSCALE_OFFLINE');
 // Read every configured service first. Never overwrite other ports or named Services.
 const all=run(['serve','get-config','--all']);const before=status();probeCertificate(host);if(fs.existsSync(marker)){const m=JSON.parse(fs.readFileSync(marker));if(JSON.stringify(m.config)===JSON.stringify(c)&&JSON.stringify(m.entry)===JSON.stringify(entry(before,publicPort))){console.log('기존 Dots Room 접속 지점을 재사용합니다.');return;}throw Error('SERVE_OWNERSHIP_CHANGED')};if(ports(before,publicPort))throw Error('TAILSCALE_PORT_ALREADY_IN_USE');
 fs.writeFileSync(path.join(dir,'serve-before.json'),JSON.stringify({serve:before,all:JSON.parse(all)},null,2)+'\n',{mode:0o600});
 run(['ping','--c=1','--timeout=5s','--until-direct=false',c.peerIps[0]]);
 // Existing tailnet policy remains in force. Gateway additionally restricts transport source to this tablet.
 run(['serve','--bg',...flags(c),'tcp://127.0.0.1:'+gatewayPort]);const after=status();if(!ports(after,publicPort))throw Error('SERVE_NOT_CONFIRMED');fs.writeFileSync(config,JSON.stringify(c,null,2)+'\n',{mode:0o600});fs.writeFileSync(marker,JSON.stringify({config:c,entry:entry(after,publicPort)},null,2)+'\n',{mode:0o600});console.log(`Dots Room 사설 HTTPS/WSS: https://${c.host}/ · 선택 태블릿만 게이트웨이 통과`);
}
main().catch(e=>{console.error(/^[A-Z_0-9]+$/.test(e.message)?e.message:'TAILSCALE_SETUP_FAILED');process.exitCode=1});
