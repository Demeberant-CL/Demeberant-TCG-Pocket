"""Fail if the experiment changes an existing file or shares app identity."""
from pathlib import Path
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

BASE = '9da0780c1b0ac033144dfb8ead816a1720c55ef6'
APP_ID = 'cl.demeberant.pocketzone.experimental'
ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent

changes = subprocess.check_output(['git', 'diff', '--name-status', BASE, 'HEAD'], cwd=REPO, text=True)
for line in changes.splitlines():
    kind, path = line.split('\t', 1)
    assert path.startswith('experiments/pocket-zone/') or path == '.github/workflows/pocket-zone.yml', line
    assert kind in ('A', 'M'), line

manifest = ET.parse(ROOT / 'src/main/AndroidManifest.xml').getroot()
android = '{http://schemas.android.com/apk/res/android}'
permissions = [p.attrib[android + 'name'] for p in manifest.findall('uses-permission')]
assert permissions == ['android.permission.INTERNET'], permissions
assert not manifest.findall('queries')
application = manifest.find('application')
assert application is not None
assert application.get(android + 'allowBackup') == 'false'
assert application.get(android + 'usesCleartextTraffic') == 'false'
assert len(application.findall('activity')) == 1
assert not application.findall('provider')
assert not application.findall('service')
assert not manifest.get(android + 'sharedUserId')
gradle = (ROOT / 'build.gradle.kts').read_text()
assert re.search(r'applicationId\s*=\s*"' + re.escape(APP_ID) + '"', gradle)
original = (REPO / 'app/build.gradle.kts').read_text()
assert APP_ID not in original
source = (ROOT / 'src/main/java/cl/demeberant/pocketzone/MainActivity.java').read_text()
assert 'addJavascriptInterface' not in source
assert 'setAllowFileAccess(false)' in source
assert 'setAllowContentAccess(false)' in source
assert 'com.aistudio.tcgpocket2.kxmpzq' not in source

if '--apk-metadata' in sys.argv:
    metadata = json.loads((ROOT / 'build/outputs/apk/debug/output-metadata.json').read_text())
    assert metadata['applicationId'] == APP_ID, metadata
    print('APK:', metadata['applicationId'], [(e['versionName'], e['versionCode']) for e in metadata['elements']])
print('Isolation passed: the original app files and identity are preserved.')
