import unittest,tempfile,shutil,json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class CharacterPackTest(unittest.TestCase):
 def test_application_updates_every_scene_and_materializes_new_atlases(self):
  with tempfile.TemporaryDirectory(prefix='dots-pack-') as td:
   base=Path(td);shutil.copytree(ROOT/'assets',base/'assets');shutil.copytree(ROOT/'tools/assets',base/'tools/assets');pack=base/'custom-pack';shutil.copytree(ROOT/'assets/characters/bami',pack)
   m=json.loads((pack/'manifest.json').read_text());m['name']='fixture-character';(pack/'manifest.json').write_text(json.dumps(m))
   before=(base/'assets/registry.json').read_bytes()
   def run(*args):subprocess.run([sys.executable,str(base/'tools/assets/character-pack.py'),*args],check=True,capture_output=True)
   run('apply',str(pack),'--dry-run');self.assertEqual(before,(base/'assets/registry.json').read_bytes());run('apply',str(pack))
   for scene in ['home','office','subway-am','subway-pm']:
    updated=json.loads((base/'assets/scenes'/scene/'manifest.json').read_text());self.assertTrue(all(a['atlas'].startswith('character-packs/fixture-character/') for a in updated['animations']))
   subprocess.run([sys.executable,str(base/'tools/assets/prepare.py')],check=True,capture_output=True)
   updated=json.loads((base/'assets/scenes/home/manifest.json').read_text())
   for a in updated['animations']:self.assertTrue((base/'.cache/android-assets/bami'/a['atlas']).is_file())
 def test_outside_pack_path_is_rejected(self):
  with tempfile.TemporaryDirectory(prefix='dots-pack-') as td:
   pack=Path(td);m=json.loads((ROOT/'assets/characters/bami/manifest.json').read_text());m['animations'][0]['atlas']='../outside.png';(pack/'manifest.json').write_text(json.dumps(m));r=subprocess.run([sys.executable,str(ROOT/'tools/assets/character-pack.py'),'check',str(pack)],capture_output=True);self.assertNotEqual(r.returncode,0)
if __name__=='__main__':unittest.main()
