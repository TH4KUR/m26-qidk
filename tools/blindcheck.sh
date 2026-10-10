#!/usr/bin/env bash
# Double-blind check for submission PDFs (organisers: no team name, member names or college names).
# Usage: tools/blindcheck.sh file.pdf [more.pdf ...]
# Needs pdftotext and pdfinfo (poppler-utils). Exit 0 = all PDFs passed, 1 = at least one failed.
# Edit tools/blindcheck.words (or set BLIND_WORDS=/path/to/file) to change the forbidden patterns.
set -u
here="$(cd "$(dirname "$0")" && pwd)"
words="${BLIND_WORDS:-$here/blindcheck.words}"
command -v pdftotext >/dev/null && command -v pdfinfo >/dev/null || { echo "need pdftotext and pdfinfo (poppler-utils)"; exit 2; }
[ -f "$words" ] || { echo "missing words file: $words"; exit 2; }
patterns="$(grep -v -E '^[[:space:]]*(#|$)' "$words")"
rc=0
for f in "$@"; do
  [ -f "$f" ] || { echo "FAIL  $f: file not found"; rc=1; continue; }
  text="$(pdftotext -layout "$f" - 2>/dev/null)"
  meta="$(pdfinfo "$f" 2>/dev/null | grep -E '^(Title|Author|Subject|Keywords|Creator|Producer):')"
  bad=0
  hits="$(printf '%s\n%s\n' "$text" "$meta" | grep -i -n -E -f <(printf '%s\n' "$patterns") || true)"
  if [ -n "$hits" ]; then echo "FAIL  $f: forbidden text found:"; printf '%s\n' "$hits" | head -10 | sed 's/^/        /'; bad=1; fi
  if ! printf '%s\n' "$text" | head -8 | grep -q -E 'Team 087'; then echo "FAIL  $f: 'Team 087' not found near the top"; bad=1; fi
  if [ "$bad" -eq 0 ]; then echo "PASS  $f"; else rc=1; fi
done
exit $rc
