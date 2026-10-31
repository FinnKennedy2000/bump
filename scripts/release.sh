#!/usr/bin/env bash
# Cut a release: bump versionCode, build, publish app as release asset `bump.apk` tagged build-N.
#
# The release build is signed with the DEBUG keystore (see app/build.gradle.kts), so run this on
# the machine whose debug key signed the apps already installed; a different key cannot update them.
# `gh` must be logged in as the GitHub account that owns the repo (FinnKennedy2000).
# Usage: scripts/release.sh "release notes"
set -euo pipefail
cd "$(dirname "$0")/.."

[ -z "$(git status --porcelain)" ] || { echo "Working tree is dirty; commit or stash first." >&2; exit 1; }
echo "Reminder: gh must be logged in as the account that owns the repo (gh auth status)."

GRADLE=app/build.gradle.kts
old=$(sed -n 's/^ *versionCode = \([0-9]*\)$/\1/p' "$GRADLE")
[ -n "$old" ] || { echo "versionCode not found in $GRADLE" >&2; exit 1; }
new=$((old + 1))
sed -i "s/^\( *versionCode = \)$old\$/\1$new/" "$GRADLE"

./gradlew assembleRelease
tmp=$(mktemp -d)
cp app/build/outputs/apk/release/app-release.apk "$tmp/bump.apk"

gh release create "build-$new" "$tmp/bump.apk" --title "Build $new" --notes "${1:-Update}"
echo "Published build-$new. Commit the versionCode bump in $GRADLE."
