import { randomBytes,timingSafeEqual } from 'node:crypto';
import { readFileSync,writeFileSync,mkdirSync,chmodSync,statSync } from 'node:fs';
import path from 'node:path';
export function newToken(){return randomBytes(32).toString('base64url');}
export function saveToken(file:string){mkdirSync(path.dirname(file),{recursive:true,mode:0o700});chmodSync(path.dirname(file),0o700);const token=newToken();writeFileSync(file,token+'\n',{mode:0o600});chmodSync(file,0o600);return token;}
export function loadToken(file:string){if(process.platform!=='win32'&&(statSync(file).mode&0o077))throw Error('TOKEN_FILE_PERMISSIONS');const token=readFileSync(file,'utf8').trim();if(!/^[A-Za-z0-9_-]{43}$/.test(token))throw Error('TOKEN_FORMAT');return token;}
export function authorized(header:string|undefined,token:string){const b=Buffer.from(header??''),a=Buffer.from('Bearer '+token);return a.length===b.length&&timingSafeEqual(a,b);}
