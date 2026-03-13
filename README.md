# NFC Copy

Android NFC 카드 리더 및 에뮬레이터 앱입니다. NFC 태그/카드의 데이터를 읽어 저장하고, 저장된 카드를 HCE(Host Card Emulation)로 에뮬레이션할 수 있습니다.

## 주요 기능

### NFC 카드 읽기
- NfcA, NfcB, IsoDep, MifareClassic, MifareUltralight, NDEF 등 다양한 NFC 기술 지원
- UID, ATQA, SAK, 섹터/블록 데이터 등 카드의 상세 정보 읽기
- NDEF 메시지 레코드 파싱

### 카드 저장 및 관리
- 읽은 카드 데이터를 기기에 저장 (SharedPreferences)
- 카드 목록에서 저장된 카드 확인
- 카드 이름 지정 및 삭제
- 카드 상세 정보 화면에서 전체 데이터 확인

### NFC 카드 에뮬레이션 (HCE)
- 저장된 카드의 UID를 이용한 카드 에뮬레이션
- Host APDU Service 기반 에뮬레이션
- 에뮬레이션 시작/중지 토글

## 사용법

### 1. 카드 읽기
1. 앱을 실행합니다
2. 화면 우하단의 **+ 버튼**을 누릅니다
3. NFC 카드/태그를 기기 뒷면에 대면 자동으로 읽습니다
4. 카드 이름을 입력하고 **저장**을 누릅니다

### 2. 카드 상세 정보 확인
1. 저장된 카드 목록에서 카드를 탭합니다
2. UID, 기술 종류, ATQA, SAK, 원시 데이터 등을 확인할 수 있습니다

### 3. 카드 에뮬레이션
1. 카드 목록에서 원하는 카드를 **길게 누릅니다**
2. **에뮬레이션 시작/중지**를 선택합니다
3. 에뮬레이션 중인 카드는 목록에 `[에뮬레이션 중]`으로 표시됩니다
4. 카드 상세 화면에서도 에뮬레이션 버튼으로 토글할 수 있습니다

### 4. 카드 삭제
1. 카드 목록에서 카드를 **길게 누릅니다**
2. **삭제**를 선택합니다

## 요구 사항

- Android 5.0 (API 21) 이상
- NFC 하드웨어 (카드 읽기용)
- HCE 지원 (카드 에뮬레이션용)

## 빌드

### APK 직접 설치
`output/nfc-copy-debug.apk` 파일을 기기에 설치합니다.

### 소스에서 빌드
```bash
# Android SDK Build Tools 필요
PLATFORM=$ANDROID_HOME/platforms/android-23/android.jar
AAPT2=$ANDROID_HOME/build-tools/29.0.3/aapt2
DX=$ANDROID_HOME/build-tools/29.0.3/dx

# 리소스 컴파일 및 링크
$AAPT2 compile --dir app-java/res -o build/compiled/
$AAPT2 link -o build/app.unsigned.apk -I $PLATFORM \
  --manifest app-java/AndroidManifest.xml \
  -R build/compiled/*.flat --java build/gen --auto-add-overlay

# Java 컴파일 및 DEX 변환
javac -source 1.8 -target 1.8 -bootclasspath $PLATFORM \
  -classpath $PLATFORM -d build/classes \
  build/gen/com/nfccopy/app/R.java app-java/src/com/nfccopy/app/*.java
$DX --dex --output=build/classes.dex build/classes/

# APK 패키징 및 서명
cp build/app.unsigned.apk build/app.withclasses.apk
cd build && zip -j app.withclasses.apk classes.dex && cd ..
zipalign -f 4 build/app.withclasses.apk build/app.aligned.apk
apksigner sign --ks build/debug.keystore \
  --ks-pass pass:android --key-pass pass:android \
  --out output/nfc-copy-debug.apk build/app.aligned.apk
```

## 프로젝트 구조

```
nfc_copy/
├── app-java/                    # Java 소스 (빌드 대상)
│   ├── AndroidManifest.xml
│   ├── src/com/nfccopy/app/
│   │   ├── MainActivity.java        # 메인 화면, NFC 읽기
│   │   ├── CardDetailActivity.java  # 카드 상세 정보
│   │   └── CardEmulationService.java # HCE 에뮬레이션 서비스
│   └── res/
│       ├── layout/                  # UI 레이아웃
│       ├── drawable/                # FAB 배경
│       ├── values/                  # 문자열 리소스
│       └── xml/                     # NFC 필터, APDU 설정
├── app/                         # Kotlin 모듈 (참고용)
├── build/                       # 빌드 산출물
├── output/
│   └── nfc-copy-debug.apk      # 설치용 APK
└── README.md
```

## 지원 NFC 기술

| 기술 | 설명 |
|------|------|
| NfcA | ISO 14443-3A (Type A) |
| NfcB | ISO 14443-3B (Type B) |
| IsoDep | ISO 14443-4 |
| MifareClassic | MIFARE Classic (섹터/블록 읽기) |
| MifareUltralight | MIFARE Ultralight (페이지 읽기) |
| NDEF | NFC Data Exchange Format |
| NdefFormatable | NDEF 포맷 가능 태그 |
| NfcF | JIS 6319-4 (FeliCa) |
| NfcV | ISO 15693 (Vicinity) |

## 참고 사항

- HCE 에뮬레이션은 UID 기반이며, 보안 키가 필요한 카드의 완전한 복제는 불가능합니다
- MifareClassic 읽기 시 기본 키(FFFFFFFFFFFF)로 인증을 시도합니다
- 일부 기기에서는 HCE 기능이 제한될 수 있습니다
