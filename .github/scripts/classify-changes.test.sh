#!/usr/bin/env bash
# Tests for classify-changes.sh: each case is a diff and the three answers it must give.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
failed=0

# check <name> <expected "product screens release"> <sensitive> <path>...
check() {
  local name="$1" expected="$2" sensitive="$3"
  shift 3
  local file got
  file="$(mktemp)"
  printf '%s\n' "$@" > "$file"
  [ "$#" -eq 0 ] && : > "$file"
  got="$(SENSITIVE="$sensitive" "$here/classify-changes.sh" "$file" 2>/dev/null | cut -d= -f2 | tr '\n' ' ' | sed 's/ $//')"
  rm -f "$file"
  if [ "$got" != "$expected" ]; then
    echo "FAIL $name: expected '$expected', got '$got'"
    failed=1
  fi
}

K=app/src/main/kotlin/com/cooper/binge/companion/jellyfin
check "docs only"                  "false false false" false CLAUDE.md docs/listing/privacy.md .ai/agents/pr-review-guide.md
check "scripts and tooling only"   "false false false" false .github/scripts/classify-changes.sh tools/x.sh
check "the shard script runs screenshots" "true true false" false .github/scripts/shard-screenshots.sh
check "workflows run everything"   "true true true"    false .github/workflows/ci.yml
check "empty diff runs everything" "true true true"    false
check "a screen"                   "true true false"   false "$K/ui/SignedInHome.kt"
check "a frame"                    "true true false"   false app/src/screenshotTestDebug/reference/x.png
check "server code can reach UI"   "true true false"   false "$K/server/JellyfinClient.kt"
check "a resource string"          "true true false"   false app/src/main/res/values/strings.xml
check "tests only"                 "true false false"  false app/src/test/kotlin/x/YTest.kt
check "the exported Service"       "true false false"  false "$K/service/JellyfinCompanionService.kt"
check "notifications"              "true true false"   false "$K/notifications/NotificationPrefs.kt"
check "the bug report URL"         "true true false"   false "$K/feedback/BugReport.kt"
check "hashes only"                "true false false"  false translation-hashes.txt
check "the version catalog"        "true true true"    false libs.versions.toml
check "a build file"               "true true true"    false app/build.gradle.kts
check "a keep rule"                "true false true"   false app/src/main/keepRules/app.pro
check "the manifest"               "true true true"    false app/src/main/AndroidManifest.xml
check "the design system pin"      "true true true"    false design-system
check "the companions pin"         "true false true"   false binge-companions
check "sensitive code"             "true true true"    true  "$K/notifications/NotificationPrefs.kt"
check "sensitive but docs only"    "false false false" true  CLAUDE.md
check "docs with a screen"         "true true false"   false CLAUDE.md "$K/ui/SignedInHome.kt"

[ "$failed" -eq 0 ] && echo "classify-changes: all cases pass"
exit "$failed"
