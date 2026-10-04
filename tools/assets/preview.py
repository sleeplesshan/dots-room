"""Create contact sheets and playback GIFs from an already validated character pack."""
from pathlib import Path
import argparse,json
from PIL import Image,ImageDraw
p=argparse.ArgumentParser();p.add_argument('pack',type=Path);p.add_argument('--out',type=Path,default=Path('.cache/character-preview'));a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
m=json.loads((a.pack/'manifest.json').read_text());cols=6;thumb=96;row=118
sheet=Image.new('RGB',(cols*thumb,((len(m['animations'])+cols-1)//cols)*row),'#f8f1e7');draw=ImageDraw.Draw(sheet)
for i,v in enumerate(m['animations']):
 im=Image.open(a.pack/v['atlas']).convert('RGBA');frames=[]
 for x,y,w,h in v['frameRects']:
  frame=im.crop((x,y,x+w,y+h));canvas=Image.new('RGBA',(w,h),'#f8f1e7');canvas.alpha_composite(frame);frames.append(canvas.convert('RGB'))
 x=i%cols*thumb;y=i//cols*row;sheet.paste(frames[0],(x,y));draw.text((x+3,y+98),v['assetId'][:18],fill='#332b33')
 frames[0].save(a.out/(v['assetId']+'.gif'),save_all=True,append_images=frames[1:],duration=v['frameDurationsMs'],loop=0 if v['loop'] else 1,disposal=2)
sheet.save(a.out/'contact-sheet.png')
print(f'Created {len(m["animations"])} previews and one contact sheet')
