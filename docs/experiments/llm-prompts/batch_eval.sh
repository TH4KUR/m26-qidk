#!/bin/bash
# Runs E's DEV split on the kit once per prompt file, pulls the results and scores them.
# usage: SCRATCH=<dir for pulled runs> E_EVAL_DIR=<E's eval dir> ./batch_eval.sh prompt1.txt prompt2.txt ...
# needs: the debug app installed, gold_v1.jsonl already in the app's files/eval-in (see app/scripts), the kit idle.
# Held-out cases are never run (the app's eval runner filters split=dev). The app is force-stopped before every run:
# do NOT use the kit by hand while this runs.
set -u
: "${SCRATCH:?set SCRATCH to a writable folder}"
HERE="$(cd "$(dirname "$0")" && pwd)"
P=/data/data/com.megathon.incident/files
for f in "$@"; do
  n=$(basename "$f" .txt); n=${n//[^A-Za-z0-9_]/_}; run="b_$n"
  adb push "$f" /data/local/tmp/p.txt >/dev/null
  adb shell "cat /data/local/tmp/p.txt | run-as com.megathon.incident sh -c 'cat > files/eval-in/$n.txt'"
  adb shell am force-stop com.megathon.incident; adb logcat -c
  adb shell "am start -n com.megathon.incident/.MainActivity --es speech SHERPA_QNN --es llm GENIEX --es eval $P/eval-in/gold_v1.jsonl --es prompt $P/eval-in/$n.txt --es run $run" >/dev/null
  for i in $(seq 1 60); do adb logcat -d | grep -q -E "eval $run (done|failed)" && break; sleep 10; done
  mkdir -p "$SCRATCH/runs/$run"
  for x in raw.jsonl predictions.jsonl meta.json; do adb shell "run-as com.megathon.incident cat files/eval/$run/$x" > "$SCRATCH/runs/$run/$x"; done
  echo "##### $n  ($(wc -l < "$SCRATCH/runs/$run/raw.jsonl") model calls; a full dev run is about 40)"
  python3 -I "$HERE/score_run.py" "$SCRATCH/runs/$run" | grep -E "incident_type|severity \||location|date_time|equipment|immediate|\*\*mandatory|\*\*overall|turns:|per turn"
done
