# DRIVELOG

피그마 디자인을 참고해 Kotlin으로 구현한 Pleos 차량 앱 프론트엔드입니다.

홈의 **주행 타임라인 연결**에서 배정받은 주행 ID와 개인 토큰으로 접속합니다. 개발 연동 화면은 토큰을 메모리에만 보관합니다. 차량의 음성 인식기가 전달한 저장 명령은 등록 성공 후 TTS로 응답합니다. 정차 등록 후에는 시간순 표시와 공유가 허용된 영상을 조회합니다. 동승자 폰의 버튼과 BFF 연결 예시는 [passenger-web](passenger-web/README.md)에 있습니다.

## 휴대폰에서 Instagram 스토리 공유

Android 9 이상에서 `./gradlew :phone:assembleDebug`로 빌드한 `phone/build/outputs/apk/debug/phone-debug.apk`와 Instagram을 설치합니다. DriveLog 스토리 앱에서 프로젝트의 공개 Meta 앱 ID를 한 번 설정합니다. 빌드 시 `-PdriveLogMetaAppId=숫자ID`로 기본값을 지정할 수도 있습니다.

차량 또는 개인 보관함의 QR을 Android Chrome으로 스캔한 뒤 **인스타 스토리에 공유**를 누릅니다. 앱이 선택한 MP4를 받아 Instagram 작성기에 읽기 권한을 넘깁니다. 최종 게시 버튼은 사용자가 누릅니다. 전송 중 네트워크가 끊겨 QR이 소진됐으면 차량이나 보관함에서 새 QR을 발급합니다.

## 구조

- `app/src/main/java/kr/drivelog/app/MainActivity.kt`: 홈·기록·분석·쇼츠 목록·상세·지난 기록과 화면 상태.
- `app/src/main/java/kr/drivelog/app/Design.kt`: 공통 네이티브 UI·색상·벡터 아이콘.
- `app/src/main/java/kr/drivelog/app/DesignAssets.kt`: 폰트와 이미지 로딩·캐시.
- `app/src/main/java/kr/drivelog/app/DemoContent.kt`: 타입으로 묶은 로컬 샘플 클립·지난 기록.
- `app/src/main/assets`: 원본 도로 사진·폰트·폰트 라이선스.

화면과 입력창은 Android 네이티브 뷰입니다. React, TypeScript, WebView, frontend 폴더, npm 빌드 단계는 제거했습니다. 피그마 원본은 Downloads에 그대로 보존돼 있으며 앱 실행에 필요하지 않습니다.

## 현재 기능

홈 → 기록 시연 → 분석 시연 → 추천 쇼츠 → 상세, 지난 드라이브 탐색. 제목 입력, 재생 위치 슬라이더, 재생 버튼의 시각 상태 전환. 수정한 제목은 기기에 저장하며 앱 재실행 후에도 반영됩니다.

차량 연결·실제 녹음·AI 분석·영상 재생·영상 파일 생성은 미구현입니다. 타이머·분석 진행률·도로 이미지·클립 정보는 시연용 데이터입니다. 저장 버튼은 제목만 저장하며 영상은 만들지 않습니다. 정차 여부도 자동 확인하지 않습니다.

Pleos 시스템이 제공하는 좌측 차량 정보·하단 공조 UI는 앱에 중복 구현하지 않습니다.

## 빌드

JDK 17, Android SDK Platform 35, Build Tools 34가 필요합니다. Node.js와 npm은 필요하지 않습니다.

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

현재 Mac에서:

```sh
cd /Users/moonsi/Desktop/moonsi/DriveLog-FE
JAVA_HOME=/Users/moonsi/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home GRADLE_USER_HOME="$PWD/.local/gradle" ./gradlew :app:assembleDebug :app:lintDebug
```

`local.properties`에서 `.local/android-sdk`를 지정합니다. SDK·캐시·로컬 설정은 Git에서 제외합니다.

APK: `app/build/outputs/apk/debug/app-debug.apk` (테스트용 서명).

## 실행

```sh
cd /Users/moonsi/Desktop/moonsi/DriveLog-FE
./scripts/run-emulator.sh
```

Pleos의 왼쪽 3D 차량 화면이 나타날 때까지 기다린 다음, 하단 격자 아이콘(앱 목록)에서 DRIVELOG를 선택합니다. Pleos 런처가 오른쪽 앱 영역을 지정합니다. 스크립트는 차량 3D 화면을 위해 host GPU 모드를 사용하고 에뮬레이터만 실행합니다.

새 APK를 설치할 때는 부팅 후 다른 터미널에서:

```sh
.local/android-sdk/platform-tools/adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
```

설치 후에도 Pleos 앱 목록에서 DRIVELOG를 엽니다. `sys.boot_completed` 직후 앱을 adb로 바로 실행하면, Pleos 창 관리 준비 전에 전체 화면으로 뜰 수 있습니다.

[검증 기록](docs/implementation.md)
