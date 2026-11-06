#!/usr/bin/env bash
# Runs the plain-JVM tests. Needs org.json (JSON_JAR) and android.jar (ANDROID_SDK) like build.sh.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SDK=${ANDROID_SDK:-/root/android-sdk}
JSON_JAR=${JSON_JAR:-$HOME/.cache/dodiy-pm/json.jar}
[ -f "$JSON_JAR" ] || { mkdir -p "$(dirname "$JSON_JAR")"; curl -sSfL -o "$JSON_JAR" https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar; }
OUT=$ROOT/build/test
rm -rf "$OUT"; mkdir -p "$OUT"
CP="$JSON_JAR:$SDK/platforms/android-35/android.jar"
javac -nowarn -encoding UTF-8 -d "$OUT" -cp "$CP" \
  "$ROOT/test/android/graphics/Path.java" "$ROOT/test/com/dodiy/passwordmanager/VaultTest.java" \
  "$ROOT"/src/com/dodiy/passwordmanager/{Crypto,Entry,Person,Portfolio,VaultData,Field,FieldCatalog,Totp,Vault,PasswordGen,PathData,Session,BioKey}.java
java -cp "$OUT:$CP" com.dodiy.passwordmanager.VaultTest "$ROOT/assets/providers.txt"
