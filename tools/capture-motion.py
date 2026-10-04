"""Capture actual native UI at fixed synthetic clock steps; emulator only."""
from pathlib import Path
import argparse,subprocess,time,io
from PIL import Image
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--out',type=Path,default=Path('docs/images/reply-motion.gif'));args=p.parse_args()
def adb(*a):return subprocess.check_output(['adb','-s',args.serial,*a])
if adb('shell','getprop','ro.hardware').strip() not in [b'ranchu',b'goldfish']:raise SystemExit('Emulator only')
adb('shell','wm','size','1920x1200')
adb('shell','settings','put','secure','immersive_mode_confirmations','confirmed')
adb('shell','settings','put','global','policy_control','immersive.full=dev.dots.room')
adb('shell','am','force-stop','dev.dots.room')
launch=adb('shell','am','start','-W','-f','0x10008000','-n','dev.dots.room/.ShowcaseActivity','--ez','animate','true')
if b'Status: ok' not in launch:raise SystemExit('Synthetic Activity did not launch')
for attempt in range(20):
 focus=adb('shell','dumpsys','window')
 if any(b'mCurrentFocus=' in line and b'dev.dots.room' in line for line in focus.splitlines()):break
 time.sleep(1)
else:raise SystemExit('Dots Room is not the focused window')
frames=[]
for elapsed in range(0,12000,200):
 adb('shell','am','broadcast','-p','dev.dots.room','-a','dev.dots.room.SHOWCASE_REPLY','--el','elapsed',str(elapsed))
 time.sleep(.08)
 with Image.open(io.BytesIO(adb('exec-out','screencap','-p'))) as frame:
  rgb=frame.convert('RGB')
  if frame.size!=(1920,1200) or len(rgb.resize((96,60)).getcolors(5760) or [])<64 or rgb.getpixel((1800,1000))!=(248,241,231):raise SystemExit('Native UI capture was blank, covered or wrong-sized')
  frames.append(rgb.resize((960,600),Image.Resampling.NEAREST))
cache=Path('.cache/showcase-motion');cache.mkdir(parents=True,exist_ok=True)
generated=cache/'reply-motion.gif'
frames[0].save(generated,save_all=True,append_images=frames[1:],duration=200,loop=0,disposal=2,optimize=True)
args.out.parent.mkdir(parents=True,exist_ok=True);args.out.write_bytes(generated.read_bytes())
print(f'Captured {len(frames)} native UI frames at fixed 200ms synthetic clock steps. Not an FPS measurement.')
