# DriveLog

Pleos Connect에서 주행 중 표시한 순간을 주차 후 타임라인과 클립 초안으로 확인하는 Kotlin/Compose 앱입니다.

## 빌드 및 실행

Gradle 데몬에는 JDK 21을 사용합니다. Android SDK Platform 35와 Build Tools 34.0.0을 설치한 뒤 Android Studio로 이 폴더를 열거나 다음 명령을 실행하세요.

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. 테스트 기기는 [Pleos Connect v2.1.0 전용 이미지](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator) (Android 14/API 34, ARM64, 2560 × 1440)입니다. Device Manager에서 실행하고 APK를 설치한 뒤, 차량 홈이 준비되면 하단 앱 목록에서 **DRIVELOG**를 여세요.

## MVP 시연

초기 설정에서 **탑승자 동의 확인**, **자동 기록**, **AI 분석**을 켜고 저장하세요. 위치 연결은 꺼둔 상태에서도 순간을 표시할 수 있습니다. 아래 명령은 디버그 APK의 DriveLog 화면을 연 동안, 연결된 가상 기기 하나를 대상으로 실행합니다. 여러 기기가 연결돼 있으면 `adb -s <기기 ID>`를 사용하세요.

```sh
adb shell am broadcast --user current -a kr.drivelog.app.DEMO -p kr.drivelog.app --es event drive
adb shell am broadcast --user current -a kr.drivelog.app.DEMO -p kr.drivelog.app --es event mark --es request_id demo-1 --es instruction '웃는 데까지'
adb shell am broadcast --user current -a kr.drivelog.app.DEMO -p kr.drivelog.app --es event park
```

주차 홈으로 돌아오면 세션과 타임라인을 확인하세요. `drive`를 다시 보내도 진행 중인 세션을 유지합니다. 다른 요청 ID로 잠시 뒤 `mark`를 보내면 요청 시각과 탐색 범위가 달라집니다. 같은 ID로 재전송하면 한 번만 기록합니다. `occupants_changed` 이벤트는 기록을 일시 중지하며, 주차 중 동의를 확인한 뒤 계속할 수 있습니다. **세션 종료**를 확정하면 표시한 순간에 생성 중과 시연 완료 상태가 차례로 나타납니다. AI 분석을 꺼둔 경우에는 원본 대기로 표시됩니다.

설정과 타임라인 메타데이터는 기기에 저장됩니다. 주행 제한은 AAOS `CarUxRestrictions`에서 읽고, 시연 주행은 추가로 UI를 잠급니다. 플랫폼 제한을 확인하지 못하면 설정과 편집을 잠급니다. 시연 결과는 사진과 요청 시각 기준 탐색 범위로 구성됩니다. 실제 차량 자동 세션 신호, Gleo, 차량 위치, 원본 음성·영상, AI 추출과 영상 재생은 별도 연동이 필요합니다.

[DriveLog UI 기준](skills/drivelog-ui/SKILL.md) · [이전 목업 구현 및 검증 기록](docs/implementation.md)
