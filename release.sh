#!/usr/bin/env bash
# Bumps BUILD_NUMBER (versionName 0.1.NNNN, versionCode NNNN), builds, and copies the APK to releases/.
set -euo pipefail
cd "$(dirname "$0")"
cur=$(grep '^BUILD_NUMBER=' version.properties | cut -d= -f2 | tr -d ' ')
next=$((10#$cur + 1))
sed -i "s/^BUILD_NUMBER=.*/BUILD_NUMBER=$next/" version.properties
./build.sh
out="${RELEASE_DIR:-/mnt/project-files/password-manager/releases}"
mkdir -p "$out"
name=$(printf "0.1.%04d" "$next")
cp build/DodiyPasswordManager.apk "$out/DodiyPasswordManager-$name.apk"
echo "Released $out/DodiyPasswordManager-$name.apk"
