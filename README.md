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

## 연결된 드라이브

주차 홈의 **연결된 드라이브**에서 팀 BFF의 HTTPS 주소와 로그인 세션 토큰을 입력하세요. BFF는 로그인한 사용자를 확인하고 해당 사용자의 개인 토큰을 서버에서 선택해 아래 경로를 전달해야 합니다. 운영자·팀 토큰은 앱에 입력하거나 빌드에 포함하지 않습니다. 디버그 APK에서는 운영 주소에 발급받은 테스트 계정의 **개인 토큰**으로도 확인할 수 있습니다. 앱은 세션 토큰을 메모리에만 유지합니다.

| 기능 | 경로 |
| --- | --- |
| 연결 확인 | `GET /api/status` |
| 내 운행과 타임라인 | `GET /api/private-trips`, `GET /api/private-trips/{id}/timeline` |
| 음성·동승자 표시 시연 | `PUT /api/private-trips/{id}/voice-marks/{markId}`, `PUT /api/private-trips/{id}/marks/{markId}` |
| 완성된 순간 미리보기 | `GET /api/private-trips/{id}/marks/{markId}/clip` |
| 보관 목록·영상·휴대폰 QR | `GET /api/library`, `GET /api/library/{id}/clip`, `POST /api/library/{id}/handoffs` |

서버에 등록된 운행을 선택하면 운행 시작 시각과 서버의 시간순 타임라인을 표시합니다. 동의가 적용된 완성 클립만 미리볼 수 있으며, 보관함의 저장본에서 휴대폰 저장 QR을 열 수 있습니다. 주행 제한 또는 상태 불명 시에는 서버 화면을 닫습니다.

디버그 입력의 `server_voice_mark`와 `server_passenger_mark`는 현재 선택한 **서버 운행**에 표시를 보냅니다. 선택적으로 새 UUID를 `request_id`로 넘기고, 음성 시연에서는 `instruction`으로 명령을 지정할 수 있습니다. 응답을 받지 못하면 **표시 다시 보내기**가 같은 ID와 본문을 사용합니다. 전송 대기는 앱 실행 중 유지됩니다. 로컬 `mark`, `passenger_mark` 이벤트는 시연 타임라인에만 기록됩니다.

차량 앱의 연결 범위는 이미 등록된 개인 운행과 보관함입니다. 계정 발급, 운영자 출발·도착 등록, 탑승자·보호자 동의, 원본 업로드, 편집 작업과 확정은 팀 서버 흐름에서 처리해야 합니다. 휴대폰 전용 앱과 실제 Gleo 입력은 별도 연동입니다.
