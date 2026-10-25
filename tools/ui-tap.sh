#!/usr/bin/env bash
# Koppintás egy képernyőn látható szövegre (Compose is) uiautomator dump alapján.
# Használat: tools/ui-tap.sh "<szöveg>" [serial]
set -euo pipefail

TEXT="${1:?használat: ui-tap.sh <szöveg> [serial]}"
SERIAL="${2:-${DR_SERIAL:-}}"
DUMP="/tmp/opencode/ui-dump.xml"

ADB=(adb)
if [[ -n "${SERIAL}" ]]; then
  ADB+=(-s "${SERIAL}")
fi

"${ADB[@]}" shell uiautomator dump /sdcard/ui-dump.xml >/dev/null 2>&1
"${ADB[@]}" pull /sdcard/ui-dump.xml "${DUMP}" >/dev/null 2>&1

COORDS=$(python3 - "${DUMP}" "${TEXT}" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, needle = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
for node in root.iter("node"):
    label = (node.get("text") or "") + "|" + (node.get("content-desc") or "")
    if needle in label:
        bounds = node.get("bounds") or ""
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
)

if [[ -z "${COORDS}" ]]; then
  echo "nem találom a képernyőn: ${TEXT}" >&2
  exit 1
fi

"${ADB[@]}" shell input tap ${COORDS}
echo "tap ${COORDS}  (${TEXT})"
