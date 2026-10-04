import net from 'node:net';
import http from 'node:http';
import { isIP } from 'node:net';

const signature=Buffer.from('0d0a0d0a000d0a515549540a','hex');
export function canonicalIp(ip:string):string {
 if(isIP(ip)===4)return ip;
 if(isIP(ip)===6)return new URL(`http://[${ip}]/`).hostname.slice(1,-1);
 throw Error('INVALID_PEER_IP');
}
export function proxyPeer(buffer:Buffer):{ip:string,bytes:number}|null {
 if(buffer.length<16)return null;
 if(!buffer.subarray(0,12).equals(signature)||buffer[12]!==0x21)throw Error('PROXY_REQUIRED');
 const len=buffer.readUInt16BE(14);if(len>216)throw Error('PROXY_LIMIT');
 const family=buffer[13];if(family!==0x11&&family!==0x21)throw Error('PROXY_TCP_REQUIRED');
 if(len<(family===0x11?12:36))throw Error('PROXY_ADDRESS_REQUIRED');if(buffer.length<16+len)return null;
 const ip=family===0x11?[...buffer.subarray(16,20)].join('.'):[...Array(8)].map((_,i)=>buffer.readUInt16BE(16+i*2).toString(16)).join(':');
 return {ip:canonicalIp(ip),bytes:16+len};
}
/** Dedicated loopback ingress for TLS-terminated Tailscale Serve PROXY v2.
 * The original peer is taken from the transport prelude, never X-Forwarded-* headers.
 * App bearer authentication remains on the upstream bridge, for every protected route.
 */
export class TailscaleGateway {
 readonly diagnostic={accepted:0,deniedPeer:0,invalidProxy:0,http:0,ws:0,lastPeer:null as string|null};
 private readonly sockets=new Set<net.Socket>();
 private readonly allowed:Set<string>;
 readonly http=http.createServer((req,res)=>{
  this.diagnostic.http++;if(!this.valid(req)){res.writeHead(403);res.end();return;}
  const upstream=http.request({hostname:'127.0.0.1',port:this.bridgePort,path:req.url,method:req.method,headers:this.headers(req)},reply=>{res.writeHead(reply.statusCode??502,reply.headers);reply.pipe(res)});
  upstream.on('error',()=>{if(!res.headersSent)res.writeHead(502);res.end()});req.on('aborted',()=>upstream.destroy());req.pipe(upstream);
 });
 readonly ingress=net.createServer(socket=>{
  this.sockets.add(socket);socket.once('close',()=>this.sockets.delete(socket));let pending=Buffer.alloc(0);const timeout=setTimeout(()=>socket.destroy(),5000);
  const read=(data:Buffer)=>{try{pending=Buffer.concat([pending,data]);const p=proxyPeer(pending);if(!p){if(pending.length>232)throw Error('PROXY_LIMIT');return;}
   this.diagnostic.lastPeer=p.ip;if(!this.allowed.has(p.ip)){this.diagnostic.deniedPeer++;throw Error('PEER_NOT_ALLOWED')};this.diagnostic.accepted++;clearTimeout(timeout);socket.off('data',read);socket.pause();const rest=pending.subarray(p.bytes);if(rest.length)socket.unshift(rest);this.http.emit('connection',socket);socket.resume();
  }catch{if(this.diagnostic.lastPeer===null)this.diagnostic.invalidProxy++;clearTimeout(timeout);socket.destroy()}};
  socket.once('close',()=>clearTimeout(timeout));socket.on('error',()=>{});socket.on('data',read);
 });
 constructor(readonly bridgePort:number,readonly publicHost:string,readonly gatewayKey:string,peerIps:string[]){
  if(!/^[a-z0-9.-]+\.ts\.net(?::[0-9]+)?$/.test(publicHost))throw Error('INVALID_GATEWAY_HOST');if(!peerIps.length||peerIps.length>2)throw Error('SELECT_ONE_TABLET');this.allowed=new Set(peerIps.map(canonicalIp));
  this.http.on('upgrade',(req,socket,head)=>{
   this.diagnostic.ws++;if(!this.valid(req)||req.url!=='/v1/events'){socket.destroy();return;}
   const upstream=net.connect(this.bridgePort,'127.0.0.1');upstream.once('connect',()=>{const headers=this.headers(req);upstream.write(`${req.method} ${req.url} HTTP/1.1\r\n`+Object.entries(headers).map(([k,v])=>`${k}: ${Array.isArray(v)?v.join(', '):v}\r\n`).join('')+'\r\n');if(head.length)upstream.write(head);socket.pipe(upstream).pipe(socket)});upstream.on('error',()=>socket.destroy());socket.on('error',()=>upstream.destroy());socket.once('close',()=>upstream.destroy());
  });
 }
 private valid(req:http.IncomingMessage){return req.headers.host===this.publicHost&&req.headers.origin===undefined&&/^\/(?:healthz|v1\/(?:events|weather|desk-state|capabilities|preset-messages|messages|sync|media\/[a-f0-9]{64}))$/.test(req.url??'')}
 private headers(req:http.IncomingMessage){const result:{[key:string]:string|string[]}={};for(const [key,value] of Object.entries(req.headers)){if(value!==undefined&&!key.startsWith('x-forwarded-')&&!key.startsWith('tailscale-')&&!key.startsWith('x-bami-')&&key!=='forwarded')result[key]=value;}result['x-bami-gateway']='Bearer '+this.gatewayKey;return result}
 async start(port:number){await new Promise<void>((resolve,reject)=>{this.ingress.once('error',reject);this.ingress.listen(port,'127.0.0.1',()=>{this.ingress.off('error',reject);resolve()})});return (this.ingress.address() as net.AddressInfo).port}
 async stop(){for(const socket of this.sockets)socket.destroy();await new Promise<void>(r=>this.ingress.close(()=>r()));}
}
