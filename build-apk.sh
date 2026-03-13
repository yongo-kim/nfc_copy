#!/bin/bash
set -e

ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
AAPT2=/usr/lib/android-sdk/build-tools/debian/aapt2
AAPT=/usr/lib/android-sdk/build-tools/debian/aapt
DX=/usr/lib/android-sdk/build-tools/debian/dx
ZIPALIGN=/usr/lib/android-sdk/build-tools/debian/zipalign
APKSIGNER=/usr/lib/android-sdk/build-tools/debian/apksigner

SRC_DIR=app-java
BUILD_DIR=build
OUT_DIR=output

echo "=== NFC Copy APK Build ==="

# Clean
rm -rf $BUILD_DIR $OUT_DIR
mkdir -p $BUILD_DIR/compiled $BUILD_DIR/classes $BUILD_DIR/dex $OUT_DIR

# Step 1: Compile resources with aapt2
echo "[1/7] Compiling resources..."
find $SRC_DIR/res -type f -name "*.xml" -o -name "*.png" 2>/dev/null | while read f; do
    $AAPT2 compile "$f" -o $BUILD_DIR/compiled/ 2>/dev/null || true
done

# Step 2: Link resources
echo "[2/7] Linking resources..."
$AAPT2 link \
    -o $BUILD_DIR/app.unsigned.apk \
    --manifest $SRC_DIR/AndroidManifest.xml \
    -I $ANDROID_JAR \
    --java $BUILD_DIR/gen \
    --auto-add-overlay \
    $BUILD_DIR/compiled/*.flat

# Step 3: Compile Java source files
echo "[3/7] Compiling Java sources..."
find $BUILD_DIR/gen -name "*.java" > $BUILD_DIR/sources.txt
find $SRC_DIR/src -name "*.java" >> $BUILD_DIR/sources.txt

javac -source 1.8 -target 1.8 \
    -bootclasspath $ANDROID_JAR \
    -classpath $ANDROID_JAR \
    -d $BUILD_DIR/classes \
    @$BUILD_DIR/sources.txt \
    2>&1

# Step 4: Convert to DEX
echo "[4/7] Converting to DEX..."
$DX --dex --output=$BUILD_DIR/classes.dex $BUILD_DIR/classes/

# Step 5: Add DEX to APK
echo "[5/7] Adding DEX to APK..."
cp $BUILD_DIR/app.unsigned.apk $BUILD_DIR/app.withclasses.apk
cd $BUILD_DIR && zip -u app.withclasses.apk classes.dex && cd ..

# Step 6: Zipalign
echo "[6/7] Zipaligning..."
$ZIPALIGN -f 4 $BUILD_DIR/app.withclasses.apk $BUILD_DIR/app.aligned.apk

# Step 7: Sign APK
echo "[7/7] Signing APK..."
# Generate debug keystore if not exists
if [ ! -f $BUILD_DIR/debug.keystore ]; then
    keytool -genkey -v \
        -keystore $BUILD_DIR/debug.keystore \
        -storepass android \
        -alias androiddebugkey \
        -keypass android \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" \
        2>/dev/null
fi

$APKSIGNER sign \
    --ks $BUILD_DIR/debug.keystore \
    --ks-pass pass:android \
    --ks-key-alias androiddebugkey \
    --key-pass pass:android \
    --out $OUT_DIR/nfc-copy-debug.apk \
    $BUILD_DIR/app.aligned.apk

echo ""
echo "=== Build Complete ==="
echo "APK: $OUT_DIR/nfc-copy-debug.apk"
ls -lh $OUT_DIR/nfc-copy-debug.apk
