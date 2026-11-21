#!/usr/bin/env bash
# Tests for check-r8-names.sh, on synthetic R8 mappings: no R8 run is needed.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
failed=0

A=com.cooper.homelab.ephyra.AppGraph
V=com.cooper.homelab.ephyra.ui.JellyfinViewModel
C=com.cooper.homelab.ephyra.server.JellyfinClient

mapping() { # a mapping with the three sentinels, each class line as given
  printf '# {"id":"com.android.tools.r8.mapping","version":"2.2"}\n'
  for line in "$@"; do printf '%s\n    1:1:void run():10:10 -> a\n' "$line"; done
}

mapping "$A -> $A:" "$V -> $V:" "$C -> $C:" > "$tmp/kept.txt"
"$here/check-r8-names.sh" "$tmp/kept.txt" >/dev/null || { echo "FAIL: kept names should pass"; failed=1; }

mapping "$A -> a.b:" "$V -> $V:" "$C -> $C:" > "$tmp/renamed.txt"
if "$here/check-r8-names.sh" "$tmp/renamed.txt" >/dev/null 2>&1; then
  echo "FAIL: a renamed class should fail"; failed=1
fi
out="$("$here/check-r8-names.sh" "$tmp/renamed.txt" 2>&1 || true)"
case "$out" in *"$A to a.b"*) ;; *) echo "FAIL: the message should name the class and what it became, got: $out"; failed=1 ;; esac

mapping "$A -> $A:" "$V -> $V:" > "$tmp/missing.txt"
if "$here/check-r8-names.sh" "$tmp/missing.txt" >/dev/null 2>&1; then
  echo "FAIL: a sentinel missing from the mapping should fail"; failed=1
fi

# The same name as a prefix of another class, or inside a member line, is not the class.
{
  mapping "$V -> $V:" "$C -> $C:"
  printf '%s$Inner -> a.c:\n    1:1:void m():1:1 -> b\n' "$A"
} > "$tmp/prefix.txt"
if "$here/check-r8-names.sh" "$tmp/prefix.txt" >/dev/null 2>&1; then
  echo "FAIL: a longer class name is not the sentinel"; failed=1
fi

# A dot in a name is a dot, not any character.
mapping "com.cooper.homelab.ephyraXAppGraph -> x:" "$V -> $V:" "$C -> $C:" > "$tmp/dot.txt"
if "$here/check-r8-names.sh" "$tmp/dot.txt" >/dev/null 2>&1; then
  echo "FAIL: dots in a sentinel must match literally"; failed=1
fi

: > "$tmp/empty.txt"
if "$here/check-r8-names.sh" "$tmp/empty.txt" >/dev/null 2>&1; then
  echo "FAIL: an empty mapping should fail"; failed=1
fi
if "$here/check-r8-names.sh" "$tmp/nope.txt" >/dev/null 2>&1; then
  echo "FAIL: a missing mapping should fail"; failed=1
fi

# Classes named on the command line replace the default list.
"$here/check-r8-names.sh" "$tmp/renamed.txt" "$V" >/dev/null || { echo "FAIL: named classes should replace the defaults"; failed=1; }

[ "$failed" -eq 0 ] && echo "check-r8-names: all cases pass"
exit "$failed"
