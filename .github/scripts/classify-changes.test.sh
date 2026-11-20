#!/usr/bin/env bash
# Tests for classify-changes.sh: each case is a diff and the three answers it must give.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
failed=0

# check <name> <expected "product screens release"> <path>...
check() {
  local name="$1" expected="$2"
  shift 2
  local file got
  file="$(mktemp)"
  printf '%s\0' "$@" > "$file"
  [ "$#" -eq 0 ] && : > "$file"
  got="$("$here/classify-changes.sh" "$file" 2>/dev/null | cut -d= -f2 | tr '\n' ' ' | sed 's/ $//')"
  rm -f "$file"
  if [ "$got" != "$expected" ]; then
    echo "FAIL $name: expected '$expected', got '$got'"
    failed=1
  fi
}

K=app/src/main/kotlin/com/cooper/homelab/ephyra
check "docs only"                  "false false false" CLAUDE.md docs/listing/privacy.md .ai/agents/pr-review-guide.md
check "scripts and tooling only"   "false false false" .github/scripts/classify-changes.sh tools/x.sh
check "the shard script runs screenshots" "true true false" .github/scripts/shard-screenshots.sh
check "workflows run the Gradle jobs, not R8" "true true false"    .github/workflows/ci.yml
check "empty diff runs everything" "true true true"
check "a screen"                   "true true false"   "$K/ui/SignedInHome.kt"
check "a frame"                    "true true false"   app/src/screenshotTestDebug/reference/x.png
check "server code can reach UI"   "true true false"   data/src/main/kotlin/com/cooper/homelab/ephyra/server/JellyfinClient.kt
check "a resource string"          "true true false"   app/src/main/res/values/strings.xml
check "tests only"                 "true false false"  app/src/test/kotlin/x/YTest.kt
check "the data module's tests"     "true false false"  data/src/test/kotlin/x/YTest.kt data/src/testFixtures/kotlin/x/Fake.kt
check "the exported Service"       "true false false"  "$K/service/JellyfinCompanionService.kt"
check "notifications"              "true true false"   "$K/notifications/NotificationPrefs.kt"
check "the bug report URL"         "true true false"   "$K/feedback/BugReport.kt"
check "hashes only"                "true false false"  translation-hashes.txt
check "the version catalog"        "true true true"    libs.versions.toml
check "a build file"               "true true true"    app/build.gradle.kts
check "a keep rule"                "true false true"   app/src/main/keepRules/app.pro
check "a pro file outside keepRules" "true true true" app/proguard-rules.pro
check "the release source set"     "true true false" app/src/release/kotlin/X.kt
check "assets"                     "true true false" app/src/main/assets/x.json
check "the manifest"               "true true false"    app/src/main/AndroidManifest.xml
check "the data module's manifest"  "true true false"    data/src/main/AndroidManifest.xml
check "the data module's build file" "true true true"   data/build.gradle.kts
check "the client module's tests"    "true false false"  client/src/test/kotlin/x/YTest.kt
check "the client module's build file" "true true true"   client/build.gradle.kts
check "the design system pin"      "true true true"    design-system
check "the homelab-core pin"      "true true true"    homelab-core
check "the homelab-player pin"    "true true true"    homelab-player
check "the companions pin"         "true false true"   binge-companions
# A rename shows as both paths (the diff runs with --no-renames), so a source file moved into docs is not docs-only.
check "a source file renamed into docs" "true true false" "$K/ui/SignedInHome.kt" docs/SignedInHome.md
check "a keep rule renamed into docs"   "true false true" app/src/main/keepRules/companion.keep docs/companion.md
# The diff is NUL-separated (git diff -z): a path with a non-ASCII character or a newline reaches the patterns as it is.
check "an accented keep rule"           "true false true"   $'app/src/main/keepRules/r\u00e8gles.keep'
check "an accented build file"          "true true true"    $'app/b\u00fcild.gradle.kts'
check "a docs file with a newline in its name" "false false false" $'docs/notes\napp/build.gradle.kts'
check "a keep rule with a newline in its name" "true false true"   $'app/src/main/keepRules/a\nb.keep'
check "docs with a screen"         "true true false"   CLAUDE.md "$K/ui/SignedInHome.kt"

check "a UI-only change skips R8" "true true false" "$K/ui/SignedInHome.kt" app/src/main/res/values/strings.xml
check ".gitmodules alone skips R8" "true true false" .gitmodules
check "a Gradle wrapper change"     "true true true"  gradle/wrapper/gradle-wrapper.properties
check "gradle.properties"           "true true true"  gradle.properties
check "settings.gradle.kts"         "true true true"  settings.gradle.kts
check "the binge-ci pin"            "true false true" binge-ci
check "a UI change with a build file" "true true true" "$K/ui/SignedInHome.kt" app/build.gradle.kts

[ "$failed" -eq 0 ] && echo "classify-changes: all cases pass"
exit "$failed"
