"""Check relative Markdown targets; external URLs require separate network read-back."""
from pathlib import Path
import re,sys
root=Path(__file__).resolve().parents[1];missing=[];count=0
for p in [*root.glob('*.md'),*root.glob('docs/*.md')]:
 for target in re.findall(r'\]\(([^)]+)\)',p.read_text()):
  target=target.split('#',1)[0]
  if not target or '://'in target:continue
  count+=1
  if not (p.parent/target).is_file():missing.append(f'{p.relative_to(root)}: {target}')
if missing:print('\n'.join(missing));sys.exit(1)
print(f'{count} local documentation targets exist.')
