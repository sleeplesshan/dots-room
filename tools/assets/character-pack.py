"""Validate or apply a complete 96px character pack. Never accesses a PET installation."""
from pathlib import Path
import argparse,json,shutil
from PIL import Image
root=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('action',choices=['check','apply']);parser.add_argument('pack',type=Path);parser.add_argument('--dry-run',action='store_true');args=parser.parse_args()
folder=args.pack.resolve();pack=json.loads((folder/'manifest.json').read_text());base=json.loads((root/'assets/characters/bami/manifest.json').read_text());required={a['assetId']for a in base['animations']}
assert pack['version']==1 and pack['cellSize']==96
assert set(a['assetId']for a in pack['animations'])==required,'A complete pack must supply all default animation IDs'
assert len(pack['animations'])==len(required)
frames=0;atlases={}
for a in pack['animations']:
 p=(folder/a['atlas']).resolve();assert p.is_relative_to(folder),'Atlas path outside pack'
 if a['atlas'] not in atlases:
  with Image.open(p) as opened:atlases[a['atlas']]=opened.copy()
 im=atlases[a['atlas']];assert im.mode=='RGBA'
 assert len(a['frameRects'])==len(a['frameDurationsMs']) and len(a['frameRects'])>0
 assert all(isinstance(v,int)and v>0 for v in a['frameDurationsMs'])
 for anchor in ['footAnchor','interactionAnchor']:assert len(a[anchor])==2 and all(0<=v<96 for v in a[anchor])
 for x,y,w,h in a['frameRects']:
  assert all(type(v)is int for v in [x,y,w,h])and w==h==96 and x>=0 and y>=0 and x+w<=im.width and y+h<=im.height
  f=im.crop((x,y,x+w,y+h));assert f.getbbox() and f.getchannel('A').getextrema()[0]==0,'Transparent margin required';frames+=1
if args.action=='apply':
 name=pack['name'];assert name and name.isascii()and all(c.isalnum()or c in '-_'for c in name)
 registry=json.loads((root/'assets/registry.json').read_text());animations=[]
 for a in pack['animations']:
  a=dict(a);logical='character-packs/'+name+'/'+a['atlas'];registry[logical]='assets/characters/'+name+'/'+a['atlas'];a['atlas']=logical;animations.append(a)
 targets=[root/v for k,v in registry.items()if k.endswith('.json')and not k.startswith('character-packs/')]
 updates=[]
 for p in targets:
  m=json.loads(p.read_text())
  if 'animations'in m:m['animations']=animations;updates.append((p,m))
 if not args.dry_run:
  destination=root/'assets/characters'/name
  if destination.resolve()!=folder:
   for atlas in atlases:
    target=destination/atlas;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(folder/atlas,target)
   destination.mkdir(parents=True,exist_ok=True);shutil.copy2(folder/'manifest.json',destination/'manifest.json')
  for p,m in updates:p.write_text(json.dumps(m,ensure_ascii=False,indent=2)+'\n')
  (root/'assets/registry.json').write_text(json.dumps(registry,ensure_ascii=False,indent=2)+'\n')
 print('Dry-run passed'if args.dry_run else'Pack applied; run npm run assets:prepare before rebuilding')
print(f'Validated {len(pack["animations"])} animations, {frames} frames, {len(atlases)} RGBA atlases')
