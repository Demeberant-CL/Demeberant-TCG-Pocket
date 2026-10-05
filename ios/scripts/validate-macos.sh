#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p validation
exec > >(tee validation/xcodebuild.log) 2>&1
if ! command -v xcodebuild >/dev/null; then
  echo 'BLOCKED: Apple Xcode is required. No iOS build/test was executed.'
  exit 2
fi
xcodebuild -version
xcodebuild -showsdks
sw_vers
python3 scripts/generate_project.py
xcodebuild -list -project Pocket.xcodeproj
xcrun simctl list devices available -j > validation/simulators.json
simulator_id=$(python3 - <<'PY'
import json,re
j=json.load(open('validation/simulators.json'))
candidates=[]
for runtime,rows in j['devices'].items():
    version=re.search(r'iOS-(\d+)-(\d+)',runtime)
    if version and int(version[1])>=17:
        for r in rows:
            if r.get('isAvailable') and r['name'].startswith('iPhone'):
                candidates.append((int(version[1]),int(version[2]),r['name'],r['udid']))
assert candidates,'No available iPhone simulator with iOS 17+; inspect simulators.json'
print(sorted(candidates)[-1][-1])
PY
)
printf 'Simulator UDID: %s\n' "$simulator_id"
xcodebuild -project Pocket.xcodeproj -scheme Pocket -destination 'generic/platform=iOS Simulator' -derivedDataPath validation/DerivedData CODE_SIGNING_ALLOWED=NO build
xcodebuild -project Pocket.xcodeproj -scheme Pocket -destination "platform=iOS Simulator,id=$simulator_id" -derivedDataPath validation/DerivedData -resultBundlePath validation/PocketTests.xcresult CODE_SIGNING_ALLOWED=NO test

xcrun xcresulttool get test-results summary --path validation/PocketTests.xcresult > validation/test-summary.json
xcrun xcresulttool export attachments --path validation/PocketTests.xcresult --output-path validation/screenshots
