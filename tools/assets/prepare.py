"""Materialize canonical assets and alpha fixtures into ignored build cache."""
from pathlib import Path
import json,shutil,struct
from PIL import Image
root=Path(__file__).resolve().parents[2]
registry=json.loads((root/'assets/registry.json').read_text())
out=root/'.cache/android-assets/bami'
shutil.rmtree(out,ignore_errors=True);out.mkdir(parents=True)
for logical,relative in registry.items():
 source=(root/relative).resolve();target=(out/logical).resolve()
 if not source.is_relative_to(root/'assets') or not target.is_relative_to(out):raise ValueError('Asset path escapes registry root')
 target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
 if target.suffix=='.png':
  im=Image.open(target).convert('RGBA');alpha=root/'.cache/layer-alpha'/f'{logical}.alpha';alpha.parent.mkdir(parents=True,exist_ok=True)
  alpha.write_bytes(struct.pack('>II',im.width,im.height)+im.getchannel('A').tobytes())
print(f'Prepared {len(registry)} registered assets in build cache')
