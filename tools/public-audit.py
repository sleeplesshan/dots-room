"""Scan selected worktree, staged blobs and every reachable commit; never print matched values."""
from pathlib import Path
import re,subprocess,sys,json,os,io
from PIL import Image
root=Path(__file__).resolve().parents[1]
ignore={'.git','.cache','.state','.tools','.gradle','.kotlin','node_modules','build','dist','__pycache__','.venv'}
patterns={
 'absolute-user-path':rb'(?<![A-Za-z0-9])(?:/Users/|/home/|[A-Za-z]:\\Users\\)[A-Za-z0-9_.-]+',
 'credential':rb'(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|sk-(?:proj-)?[A-Za-z0-9_-]{24,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)',
 'personal-email':rb'[A-Za-z0-9._%+-]+@(?:naver|gmail|icloud|outlook|hotmail)\.com',
 'private-device':rb'\bT[0-9]{3}MAN[A-Z0-9]+\b',
}
forbidden={'.enc','.jks','.keystore','.pem','.key','.apk','.aab','.zip','.log'}
findings=[];checked=0

def check(name,data,where):
 global checked
 checked+=1;p=Path(name)
 if p.suffix in forbidden or p.name in ['config.local.json','local.properties','pairing.token'] or p.name.startswith('.env'):findings.append((where,name,'private-or-build-file'))
 for category,pattern in patterns.items():
  if re.search(pattern,data,re.I):findings.append((where,name,category))
 for target in re.findall(rb'https://chatgpt\.com/(?:dots/[a-f0-9-]+|space/page_[a-f0-9]+)',data):
  if target not in [b'https://chatgpt.com/dots/00000000-0000-4000-8000-000000000001',b'https://chatgpt.com/space/page_00000000000000000000000000000001']:
   findings.append((where,name,'non-synthetic-chat-page'))
 for address in re.findall(rb'[A-Za-z0-9.-]+\.ts\.net',data):
  if address.lstrip(b'.') not in [b'fixture.tailnet.ts.net',b'example.ts.net',b'example-host.example.ts.net',b'example-host.tailnet.ts.net',b'evil.ts.net',b'tailnet.ts.net',b'.ts.net']:
   findings.append((where,name,'non-example-tailnet'))
 if p.suffix.lower() in ['.png','.jpg','.jpeg','.webp','.gif']:
  try:
   with Image.open(io.BytesIO(data)) as im:
    if im.getexif() or any(k in im.info for k in ['exif','comment','Comment','Description','Software','Author','XML:com.adobe.xmp']):findings.append((where,name,'image-metadata'))
  except Exception:findings.append((where,name,'invalid-image'))

def git(*args):return subprocess.check_output(['git',*args],cwd=root,stderr=subprocess.DEVNULL)
for directory,dirs,files in os.walk(root):
 dirs[:]=[d for d in dirs if d not in ignore]
 for name in files:
  p=Path(directory)/name
  if p.is_symlink():findings.append(('worktree',str(p.relative_to(root)),'symlink'));continue
  check(str(p.relative_to(root)),p.read_bytes(),'worktree')
try:
 for name in git('diff','--cached','--name-only','--diff-filter=ACMR','-z').decode().split('\0'):
  if name:check(name,git('show',':'+name),'staged')
 for commit in git('rev-list','--all').decode().splitlines():
  author=git('show','-s','--format=%an%n%ae%n%cn%n%ce',commit).decode().splitlines()
  def public_identity(name,email):
   m=re.fullmatch(r'(?:[0-9]+\+)?([^@]+)@users\.noreply\.github\.com',email)
   return m is not None and (name==m.group(1)or(name=='GitHub'and m.group(1)=='web-flow'))
  if len(author)!=4 or not public_identity(author[0],author[1])or not public_identity(author[2],author[3]):findings.append(('history',commit[:8],'non-public-commit-identity'))
  for name in git('ls-tree','-r','--name-only','-z',commit).decode().split('\0'):
   if name:check(name,git('show',commit+':'+name),'history:'+commit[:8])
except subprocess.CalledProcessError:pass
if findings:
 for row in sorted(set(findings)):print(' | '.join(row))
 print('Public audit failed; matched values withheld.');sys.exit(1)
print(f'Public audit passed: {checked} worktree/staged/history blobs; metadata checked. Review licenses and synthetic screenshots separately.')
