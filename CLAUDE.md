# NFC Copy App

Android NFC 카드 복사 앱. NFC 태그를 읽고 저장하며 HCE를 통해 에뮬레이션합니다.

## Build

```bash
PLATFORM=/usr/lib/android-sdk/platforms/android-23/android.jar
AAPT2=/usr/lib/android-sdk/build-tools/29.0.3/aapt2
DX=/usr/lib/android-sdk/build-tools/29.0.3/dx
BD=build

# Compile resources
$AAPT2 compile --dir app-java/res -o $BD/compiled/
$AAPT2 link -o $BD/app.unsigned.apk -I $PLATFORM --manifest app-java/AndroidManifest.xml -R $BD/compiled/*.flat --java $BD/gen --auto-add-overlay

# Compile Java
javac -source 1.8 -target 1.8 -bootclasspath $PLATFORM -classpath $PLATFORM -d $BD/classes $BD/gen/com/nfccopy/app/R.java app-java/src/com/nfccopy/app/*.java

# Package
$DX --dex --output=$BD/classes.dex $BD/classes/
cp $BD/app.unsigned.apk $BD/app.withclasses.apk
cd $BD && zip -j app.withclasses.apk classes.dex && cd ..
zipalign -f 4 $BD/app.withclasses.apk $BD/app.aligned.apk
apksigner sign --ks $BD/debug.keystore --ks-pass pass:android --key-pass pass:android --out output/nfc-copy-debug.apk $BD/app.aligned.apk
```

## Project Structure

- `app-java/src/com/nfccopy/app/` - Java 소스 코드
  - `MainActivity.java` - 메인 액티비티 (NFC 읽기, 카드 목록)
  - `CardDetailActivity.java` - 카드 상세 정보
  - `MyHostApduService.java` - HCE 에뮬레이션 서비스
  - `NfcCardStorage.java` - 카드 데이터 저장 (SharedPreferences)
  - `NfcCardData.java` - 카드 데이터 모델
- `app-java/res/` - 리소스 (레이아웃, 값, drawable)
- `app-java/AndroidManifest.xml` - 매니페스트
- `output/` - 빌드된 APK 출력
