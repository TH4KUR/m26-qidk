#!/usr/bin/env bash
# Mirror a clean, COMMITTED snapshot of the code from this private dev repo into a clone of the submission repo.
# Usage:  tools/mirror_to_submission.sh <submission-clone-dir> [--apply]
# Default is a DRY RUN. The script never commits and never pushes: review the target, then commit and push yourself.
# Needs: git, rsync, grep. Reads forbidden patterns from tools/blindcheck.words (one extended regex per line).
set -euo pipefail

src="$(cd "$(dirname "$0")/.." && pwd)"
target="${1:-}"; mode="${2:---dry-run}"
[ -n "$target" ] || { sed -n '2,5p' "$0"; exit 2; }
[ "$mode" = "--apply" ] || [ "$mode" = "--dry-run" ] || { echo "second argument must be --apply or --dry-run"; exit 2; }
target="$(cd "$target" && pwd)" || exit 2
[ "$target" != "$src" ] || { echo "refusing: target is the dev repo itself"; exit 2; }
command -v rsync >/dev/null || { echo "rsync is required"; exit 2; }

fail() { echo "FAIL: $*"; exit 1; }

# 1. target must be the submission repo, on main
git -C "$target" rev-parse --git-dir >/dev/null 2>&1 || fail "target is not a git repository"
url="$(git -C "$target" remote get-url origin 2>/dev/null || true)"
case "$url" in *megathon26-readyornot*) ;; *) fail "target's origin is '$url', expected the megathon26-readyornot submission repo";; esac
[ "$(git -C "$target" branch --show-current)" = "main" ] || fail "target is not on branch main"

# 2. patterns (names, college, email, ...)
words="$src/tools/blindcheck.words"
[ -f "$words" ] || fail "missing $words"
patterns="$(grep -v -E '^[[:space:]]*(#|$)' "$words")"
[ -n "$patterns" ] || fail "no patterns in $words"

# 3. the commit identity used in the target must not match a pattern either
ident="$(git -C "$target" config user.name 2>/dev/null || true) <$(git -C "$target" config user.email 2>/dev/null || true)>"
idpat="$(printf '%s\n' "$patterns" | grep -v '@' || true)"   # name/college patterns only; the generic email regex would flag every identity
if [ -n "$idpat" ] && printf '%s\n' "$ident" | grep -q -i -E -f <(printf '%s\n' "$idpat"); then fail "git identity in the target ($ident) matches a forbidden pattern; set a neutral one with git config in the target"; fi

# 4. what to mirror (only what exists in the committed HEAD of the dev repo)
dirs=(app core gradle tools)
files=(settings.gradle.kts build.gradle.kts gradle.properties gradlew gradlew.bat)
present=()
for p in "${dirs[@]}" "${files[@]}"; do git -C "$src" cat-file -e "HEAD:$p" 2>/dev/null && present+=("$p"); done
if [ "${#present[@]}" -eq 0 ]; then echo "Nothing to mirror yet (none of: ${dirs[*]} ${files[*]} exist in HEAD). Target untouched."; exit 0; fi
echo "dev repo: branch $(git -C "$src" branch --show-current) @ $(git -C "$src" rev-parse --short HEAD) (committed content only)"
if ! git -C "$src" diff --quiet HEAD -- "${present[@]}"; then echo "WARNING: uncommitted changes in mirrored paths are IGNORED (only HEAD is exported)"; fi

# 5. export a snapshot, then strip what must never be published
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
git -C "$src" archive HEAD -- "${present[@]}" | tar -x -C "$tmp"
rm -f "$tmp"/tools/blindcheck.words "$tmp"/tools/blindcheck.sh "$tmp"/tools/mirror_to_submission.sh
find "$tmp" -type f \( -iname '*.wav' -o -iname '*.mp3' -o -iname '*.m4a' -o -iname '*.flac' -o -iname '*.gguf' -o -iname '*.onnx' -o -iname '*.bin' -o -iname '*.so' -o -iname '*.zip' -o -iname '*.tar.gz' -o -iname '*.pdf' -o -iname '*.lock' \) -print -delete | sed 's/^/  excluded: /' || true

# 6. scan contents and file names for forbidden words
bad=0
hits="$(grep -r -I -i -n -E -f <(printf '%s\n' "$patterns") "$tmp" 2>/dev/null | sed "s|$tmp/||" || true)"
[ -z "$hits" ] || { echo "FAIL: forbidden text in the snapshot:"; printf '%s\n' "$hits" | head -20 | sed 's/^/    /'; bad=1; }
nhits="$(cd "$tmp" && find . -print | grep -i -E -f <(printf '%s\n' "$patterns") || true)"
[ -z "$nhits" ] || { echo "FAIL: forbidden text in file names:"; printf '%s\n' "$nhits" | head -10 | sed 's/^/    /'; bad=1; }
[ "$bad" -eq 0 ] || { echo "Nothing was copied. Fix the findings (in the dev repo, commit) and run again."; exit 1; }
echo "scan: clean ($(find "$tmp" -type f | wc -l) files)"

# 7. copy: directories are synced (with --delete) ONLY inside the mirrored dirs; root files are copied; README, LICENSE, deliverables/ are never touched
flags=(-a); [ "$mode" = "--dry-run" ] && flags+=(-n -i)
# rsync output is captured first: piping it into `head` would SIGPIPE rsync (pipefail) and leave a half-copied snapshot
for d in "${dirs[@]}"; do [ -d "$tmp/$d" ] && { mkdir -p "$target/$d"; out="$(rsync "${flags[@]}" --delete "$tmp/$d/" "$target/$d/")"; printf '%s\n' "$out" | sed "s|^|  $d: |" | head -40 || true; }; done
for f in "${files[@]}"; do [ -f "$tmp/$f" ] && rsync "${flags[@]}" "$tmp/$f" "$target/$f" | sed "s|^|  $f: |"; done
if [ "$mode" = "--dry-run" ]; then echo "DRY RUN only. Re-run with --apply to copy."; else
  echo "Copied. Now review and publish yourself:"; echo "  cd $target && git status && git add -A && git commit -m '...' && git push origin main"
  echo "  (README.md, LICENSE and deliverables/ were not touched)"; fi
