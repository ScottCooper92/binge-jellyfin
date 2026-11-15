#!/usr/bin/env bash
# Splits the screenshot test classes into N shards, so each shard renders in a JVM of its own.
#
# The render holds every frame's native memory until its JVM exits, so one JVM for the whole
# suite grows with the suite: past what a hosted runner has, the job stalls and is cancelled at
# its time limit with no log. Each shard renders a share and exits.
#
# Usage: shard-screenshots.sh <shards> <index> [<test dir> [<reference dir>]]
# Prints the `--tests` arguments for shard <index> (0-based), one per line. A class is a file in
# the test dir with a `class …ScreenshotTest`; its weight is how many reference PNGs it has
# (a class with none yet counts 1). Heaviest classes are placed first, each on the lightest shard,
# ties by name, so the split is stable and every class lands in exactly one shard.
set -euo pipefail

shards="${1:?shards}"
index="${2:?index}"
tests="${3:-app/src/screenshotTest}"
refs="${4:-app/src/screenshotTestDebug/reference}"

classes="$(grep -rhoE '^class [A-Za-z0-9_]*ScreenshotTest' "$tests" | awk '{print $2}' | sort -u)"
[ -n "$classes" ] || { echo "no screenshot test classes under $tests" >&2; exit 1; }

weighted=""
while IFS= read -r c; do
  n="$(find "$refs" -type d -name "$c" -exec find {} -name '*.png' \; 2>/dev/null | wc -l | tr -d ' ')"
  [ "$n" -gt 0 ] || n=1
  weighted+="$n $c"$'\n'
done <<< "$classes"

printf '%s' "$weighted" | sort -k1,1nr -k2,2 | awk -v shards="$shards" -v want="$index" '
  BEGIN { for (i = 0; i < shards; i++) load[i] = 0 }
  {
    best = 0
    for (i = 1; i < shards; i++) if (load[i] < load[best]) best = i
    load[best] += $1
    if (best == want) printf "--tests\n*.%s\n", $2
  }'
