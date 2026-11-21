#!/usr/bin/env bash
# Fails if R8 renamed one of this app's own classes in a release build.
#
# `app/src/main/keepRules/companion.keep` has `-keepnames class com.cooper.homelab.**`, so a release build's stack frames keep the
# `com.cooper.homelab.` prefix the ring log matches on. The minified debug build the device lane runs is not obfuscated, so a removed
# or narrowed rule would show only on a release user's Diagnostics page. This reads the release build's R8 mapping instead.
#
# Usage: check-r8-names.sh <mapping.txt> [<class> ...]
# A class line in the mapping is `original.Name -> final.Name:` at the start of a line (members are indented). Every sentinel must be
# there and must map to itself. With no classes named, the sentinels are three that survive shrinking and keep their package: the
# graph, the main view model and the server client. -keepnames does not stop R8 merging or generating classes, so this checks
# a short list and not every class. A sentinel missing from the mapping fails too: a renamed or removed class makes the list stale,
# and a check that finds nothing must not pass.
set -euo pipefail

mapping="${1:?mapping.txt}"
shift || true
if [ "$#" -gt 0 ]; then
  sentinels=("$@")
else
  sentinels=(
    com.cooper.homelab.ephyra.AppGraph
    com.cooper.homelab.ephyra.ui.JellyfinViewModel
    com.cooper.homelab.ephyra.server.JellyfinClient
  )
fi

[ -s "$mapping" ] || { echo "$mapping is missing or empty: the release build wrote no R8 mapping" >&2; exit 1; }

failed=0
for name in "${sentinels[@]}"; do
  # The class line only: it starts at column 0 as `name -> final:`, so a member line (indented) or a longer name never matches. An exact
  # string compare, so a dot or a dollar sign in a name is itself and not a pattern.
  final="$(awk -v n="$name" '$1 == n && $2 == "->" { print $3; exit }' "$mapping")"
  if [ -z "$final" ]; then
    echo "$name is not in the R8 mapping: the class was renamed, removed or shrunk away. Update the sentinel list in $(basename "$0")." >&2
    failed=1
    continue
  fi
  final="${final%:}"
  if [ "$final" != "$name" ]; then
    echo "R8 renamed $name to $final. Keep rules in app/src/main/keepRules/companion.keep must keep \`-keepnames class com.cooper.homelab.**\`; the ring log matches frames by that prefix." >&2
    failed=1
  fi
done

[ "$failed" -eq 0 ] && echo "check-r8-names: ${#sentinels[@]} classes keep their names"
exit "$failed"
