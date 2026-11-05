#!/usr/bin/env bash
# Builds and signs build/DodiyPasswordManager.apk using the current BUILD_NUMBER (no bump).
# Toolchain: javac (JDK 17+), Debian android-sdk-build-tools (aapt2, zipalign, apksigner), dalvik-exchange (dx).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT"
SDK=${ANDROID_SDK:-/root/android-sdk}
BT=${BUILD_TOOLS:-/usr/lib/android-sdk/build-tools/29.0.3}
COMPILE_JAR=$SDK/platforms/android-35/android.jar   # Java APIs
LINK_JAR=$SDK/platforms/android-33/android.jar      # resources (aapt2 29 can't read the 35 table)
SIGN=${PM_SIGNING_PROPS:-/mnt/project-files/password-manager/signing/keystore.properties}
N=$(grep '^BUILD_NUMBER=' version.properties | cut -d= -f2 | tr -d ' ')
N=$((10#$N)); [ "$N" -ge 1 ] || { echo "BUILD_NUMBER must be >= 1 (run release.sh)"; exit 1; }
VN=$(printf "0.1.%04d" "$N")
prop() { grep "^$1=" "$SIGN" | cut -d= -f2-; }

OUT=$ROOT/build
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/gen" "$OUT/dex"
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -I "$LINK_JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 28 --target-sdk-version 34 \
  --version-code "$N" --version-name "$VN" \
  --java "$OUT/gen" -o "$OUT/unsigned.apk" "$OUT/res.zip" -A assets
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$COMPILE_JAR" \
  -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java')
dalvik-exchange --dex --min-sdk-version=28 --output="$OUT/dex/classes.dex" "$OUT/classes"
(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$(dirname "$SIGN")/$(prop storeFile)" --ks-type PKCS12 \
  --ks-pass "pass:$(prop storePassword)" --ks-key-alias "$(prop keyAlias)" --key-pass "pass:$(prop keyPassword)" \
  --out "$OUT/DodiyPasswordManager.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/DodiyPasswordManager.apk"
echo "Built $OUT/DodiyPasswordManager.apk  versionName $VN  versionCode $N"
