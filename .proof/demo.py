import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

def run(argv, expected=0):
    result = subprocess.run(argv, cwd=ROOT, capture_output=True, text=True, timeout=30)
    if result.returncode != expected:
        raise RuntimeError(result.stderr + result.stdout)
    return result.stdout

with tempfile.TemporaryDirectory() as temp:
    path=Path(temp)/'repairs.log'
    argv=['java','RepairLog',str(path)]
    ticket=json.loads(run(argv+['add','zone','3','repair']))['tickets'][0]
    run(argv+['assign',ticket['id'],'1','operator'])
    stale=subprocess.run(argv+['transition',ticket['id'],'1','IN_PROGRESS','start'],cwd=ROOT,capture_output=True,text=True)
    assert stale.returncode!=0
    report=json.loads(run(argv+['transition',ticket['id'],'2','IN_PROGRESS','start']))
    assert report['tickets'][0]['revision']==3
    print(json.dumps({'report':report,'stale_revision_rejected':True},sort_keys=True))
