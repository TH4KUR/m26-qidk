#!/usr/bin/env bash
# Give this app its own copy of an AI Hub language-model bundle that the GenieX demo app already downloaded.
# The demo's folder is private to the demo (a different uid), so this app cannot read it. A copy is made on the device,
# as root, without touching the original. About 3 GB for Qwen3-4B-Instruct-2507; check free space first.
#
#   llm_bundle.sh push [MODEL_DIR_NAME]     default: Qwen3-4B-Instruct-2507
set -euo pipefail
PKG=com.megathon.incident
SRC_APP=com.geniex.demo
NAME="${2:-Qwen3-4B-Instruct-2507}"
[ "${1:-}" = push ] || { sed -n '2,8p' "$0"; exit 2; }

adb root >/dev/null; adb wait-for-device
src="/data/data/$SRC_APP/files/geniex/models/qualcomm/$NAME"
dst_base="/data/data/$PKG/files/geniex/models/qualcomm"
adb shell "[ -f $src/geniex.json ]" || { echo "no such bundle in the demo app: $src"; exit 1; }
uid="$(adb shell stat -c %u /data/data/$PKG | tr -d '\r')"
need_kb="$(adb shell du -sk "$src" | cut -f1 | tr -d '\r')"
free_kb="$(adb shell df -k /data | tail -1 | awk '{print $4}' | tr -d '\r')"
[ "$free_kb" -gt $((need_kb + 2000000)) ] || { echo "not enough free space: need ${need_kb} KB plus margin, have ${free_kb} KB"; exit 1; }
adb shell "mkdir -p $dst_base && rm -rf $dst_base/$NAME.partial && cp -r $src $dst_base/$NAME.partial"
adb shell "rm -rf $dst_base/$NAME && mv $dst_base/$NAME.partial $dst_base/$NAME"
adb shell "chown -R $uid:$uid /data/data/$PKG/files/geniex && chmod -R u+rwX,go-rwx /data/data/$PKG/files/geniex && restorecon -R /data/data/$PKG/files/geniex"
echo "copied to $dst_base/$NAME:"; adb shell "ls -la $dst_base/$NAME | head -8; du -sh $dst_base/$NAME"
