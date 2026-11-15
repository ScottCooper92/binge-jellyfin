#!/usr/bin/env bash
# Tests for shard-screenshots.sh: every class lands in exactly one shard, and the shards are balanced.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
failed=0

mkdir -p "$tmp/tests" "$tmp/refs"
# Class, frames: one heavy class, a few middling, several light, and one with no frames yet.
for spec in Big:40 MidA:12 MidB:11 MidC:10 SmallA:3 SmallB:2 SmallC:2 SmallD:1 New:0; do
  name="${spec%%:*}ScreenshotTest" n="${spec##*:}"
  printf 'package x\nclass %s {\n}\n' "$name" > "$tmp/tests/$name.kt"
  mkdir -p "$tmp/refs/ui/$name"
  for ((i = 0; i < n; i++)); do : > "$tmp/refs/ui/$name/f$i.png"; done
done

all="$(for i in 0 1 2; do "$here/shard-screenshots.sh" 3 "$i" "$tmp/tests" "$tmp/refs" | grep -v '^--tests$'; done | sort)"
expected="$(printf '*.%sScreenshotTest\n' Big MidA MidB MidC SmallA SmallB SmallC SmallD New | sort)"
if [ "$all" != "$expected" ]; then
  echo "FAIL: the shards together are not every class exactly once"; echo "$all"; failed=1
fi

# The heavy class is alone on a shard, and the rest are spread: no shard is empty.
for i in 0 1 2; do
  n="$("$here/shard-screenshots.sh" 3 "$i" "$tmp/tests" "$tmp/refs" | grep -vc '^--tests$' || true)"
  [ "$n" -ge 1 ] || { echo "FAIL: shard $i is empty"; failed=1; }
done
first="$("$here/shard-screenshots.sh" 3 0 "$tmp/tests" "$tmp/refs" | grep -v '^--tests$' | head -1)"
[ "$first" = "*.BigScreenshotTest" ] || { echo "FAIL: the heaviest class should open shard 0, got $first"; failed=1; }

# One shard is the whole suite.
one="$("$here/shard-screenshots.sh" 1 0 "$tmp/tests" "$tmp/refs" | grep -vc '^--tests$')"
[ "$one" -eq 9 ] || { echo "FAIL: one shard should hold all 9 classes, got $one"; failed=1; }

# No classes at all is an error, not an empty run that passes.
mkdir -p "$tmp/none"
if "$here/shard-screenshots.sh" 3 0 "$tmp/none" "$tmp/refs" >/dev/null 2>&1; then
  echo "FAIL: no classes should fail"; failed=1
fi

# A frame the shards would miss fails the script rather than dropping out of the gate.
mkdir -p "$tmp/mod" "$tmp/top" "$tmp/stray"
printf 'package x\ninternal class ModScreenshotTest {\n  @PreviewTest fun a() {}\n}\n' > "$tmp/mod/M.kt"
mod="$("$here/shard-screenshots.sh" 1 0 "$tmp/mod" "$tmp/refs" | grep -v '^--tests$')"
[ "$mod" = "*.ModScreenshotTest" ] || { echo "FAIL: a class with a modifier should be found, got $mod"; failed=1; }
printf 'package x\nclass AScreenshotTest {\n}\n@PreviewTest fun c() {}\n' > "$tmp/top/T.kt"
if "$here/shard-screenshots.sh" 3 0 "$tmp/top" "$tmp/refs" >/dev/null 2>&1; then
  echo "FAIL: a top-level @PreviewTest should fail"; failed=1
fi
printf 'package x\nclass AScreenshotTest {\n}\n' > "$tmp/stray/A.kt"
printf 'package x\nclass Other {\n  @PreviewTest fun b() {}\n}\n' > "$tmp/stray/B.kt"
if "$here/shard-screenshots.sh" 3 0 "$tmp/stray" "$tmp/refs" >/dev/null 2>&1; then
  echo "FAIL: a @PreviewTest in a file with no *ScreenshotTest class should fail"; failed=1
fi

[ "$failed" -eq 0 ] && echo "shard-screenshots: all cases pass"
exit "$failed"
