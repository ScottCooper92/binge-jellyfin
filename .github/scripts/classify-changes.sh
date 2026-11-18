#!/usr/bin/env bash
# Decides which CI jobs a diff can affect, from the changed paths, one per line, in the file given
# as $1. Prints `product=`, `screens=` and `release=`, each true or false, one per line.
#
# The design is Binge's change classifier, kept deliberately small. A job is skipped only when
# EVERY changed path is in a closed, finite set that provably cannot affect it. For `product` and
# `screens` a path in no set runs the job. `release` is the reverse, an allow-list: it runs only for a
# listed input, so a new R8 input (a source set, assets, a .pro file outside keepRules/) must be added
# to `release_input`. An empty list runs everything, and so does anything this script cannot read. The
# suite on main and the nightly never call this, so a wrong skip is found at the next merge.
#
#   product  the Gradle jobs: checks (lint, detekt, ktlint, assemble) and tests. False only when
#            every path is documentation or tooling nothing compiles or packages.
#   screens  the screenshot frames. False when no path can move a frame: tests, the exported
#            Service, the review launcher, keep rules and the like.
#   release  the R8 build. True only for what R8 reads: the version catalog, Gradle files, keep
#            rules and .pro files, the release source set, assets, the manifest, XML and raw resources, a submodule pin, the workflows
#            themselves, or (the $SENSITIVE env, set by the caller from the diff's content) code
#            that R8 can break without a file above changing: serialization, reflection.
#
# Never state a skip-set path here that a bot reads or that the build reads. `.github/workflows`
# is in no set: a change to the pipeline is validated by running the pipeline.
set -euo pipefail

list="${1:?usage: classify-changes.sh <file listing the changed paths>}"
sensitive="${SENSITIVE:-false}"

# Paths no Gradle task reads.
non_product() {
  case "$1" in
    # The screenshots job runs this script, so it is an input to the build.
    .github/scripts/shard-screenshots.sh) return 1 ;;
    *.md | LICENSE* | docs/* | .ai/* | tools/* | .gitignore | .github/ISSUE_TEMPLATE/* | .github/renovate.json5 | .github/scripts/*) return 0 ;;
  esac
  return 1
}

# Paths of product code that cannot move a screenshot frame.
no_frames() {
  case "$1" in
    app/src/test/* | app/src/androidTest/* | app/src/debug/keepRules/* | app/src/main/keepRules/*) return 0 ;;
    data/src/test/* | data/src/testFixtures/*) return 0 ;;
    app/src/main/kotlin/com/cooper/homelab/ephyra/service/*) return 0 ;;
    app/src/main/kotlin/com/cooper/homelab/ephyra/review/*) return 0 ;;
    translation-hashes.txt | detekt.yml | .editorconfig | binge-companions | binge-ci) return 0 ;;
  esac
  return 1
}

# Paths R8 reads.
release_input() {
  case "$1" in
    libs.versions.toml | *.gradle.kts | gradle/* | gradle.properties | .gitmodules) return 0 ;;
    app/src/main/keepRules/* | app/src/main/AndroidManifest.xml | app/src/main/res/xml/* | app/src/main/res/raw/*) return 0 ;;
    app/*.pro | app/proguard* | app/src/release/* | app/src/main/assets/*) return 0 ;;
    data/src/main/AndroidManifest.xml | data/src/main/res/* | data/src/main/assets/* | data/*.pro | data/proguard* | data/src/release/*) return 0 ;;
    design-system | binge-companions | binge-ci | homelab-core | .github/workflows/*) return 0 ;;
  esac
  return 1
}

paths=()
while IFS= read -r path; do
  [ -n "$path" ] && paths+=("$path")
done < "$list"

if [ "${#paths[@]}" -eq 0 ]; then
  echo "No changed paths to read: running everything." >&2
  printf 'product=true\nscreens=true\nrelease=true\n'
  exit 0
fi

product=false screens=false release=false
for path in "${paths[@]}"; do
  non_product "$path" && continue
  product=true
  no_frames "$path" || screens=true
  release_input "$path" && release=true
done
[ "$product" = true ] && [ "$sensitive" = true ] && release=true

printf 'product=%s\nscreens=%s\nrelease=%s\n' "$product" "$screens" "$release"
