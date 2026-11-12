# Kotlin 네이티브 프론트

## 기준

아이디어 기획서는 제품 기능의 기준, 피그마 메이크는 디자인 참고 자료입니다. 원본의 앱 본문 레이아웃·파란색·도로 이미지·Noto Sans KR/Manrope 폰트·카드·편집 화면을 Kotlin 네이티브 뷰로 구현합니다. Pleos 자체 차량 계기판·공조 바·시계를 앱에 복제하지 않습니다.

## 현재 구조

- MainActivity: 네이티브 TextView·EditText·SeekBar·ImageView·ProgressBar로 6개 화면 구성.
- DesignFrame: 가용 앱 영역에 맞춰 기준 좌표 조절 및 공통 UI 생성.
- DemoContent: 샘플 클립·지난 기록·시연 초기값·분석 단계.
- DesignAssets: 화면 재생성 시 재사용하는 폰트·이미지 캐시.
- DesignIcon/LogoMark: Android Canvas로 그리는 원본 스타일 아이콘.
- 제목은 SharedPreferences로 기기에 저장. 화면 상태는 구성 변경 시 Bundle로 복원.
- 정적 이미지와 폰트만 assets에 포함. 웹 런타임과 JavaScript·HTML·CSS 파일 없음.
- frontend 폴더·React·Vite·npm 빌드 작업·WebView 호스트 제거.

## 실제 동작과 시연 구분

화면 전환·제목 입력 및 제목 저장·슬라이더 조작은 네이티브에서 동작합니다. 기록 타이머·분석 진행률·쇼츠 정보는 로컬 샘플이며 실제 녹음/분석/영상 재생을 수행하지 않습니다. 저장 버튼은 제목만 저장합니다. 차량 SDK·주행 상태 연동과 정차 시 조작 제한은 후속 작업입니다.

## 빌드 환경

JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile/target API 35, min API 28. Android SDK와 에뮬레이터는 .local에 설치돼 있습니다. Node/npm 없이 빌드합니다.

## 검증

- 네이티브 :app:assembleDebug 성공.
- :app:lintDebug 오류·경고 없음.
- Pleos Connect v2.1.0 / Android 14 (API 34), arm64, 2560×1440, RAM 4GB에서 홈 화면 실행 확인.
- 에뮬레이터 스크린샷·UI XML은 .local/verification/native에 저장.


## 최종 실행 검증 (2026-11-12)

- 홈 → 기록 → 분석 완료 → 쇼츠 목록 → 상세 → 제목 입력 및 저장 → 홈 → 지난 기록 → 해당 드라이브 쇼츠 → 홈 검증 통과.
- EditText에 제목을 입력하고 실제 SharedPreferences 기록을 확인. 테스트 제목은 검증 후 제거하고 기존 값을 복원.
- 각 화면의 UI 계층이 Android 네이티브 뷰이며 WebView 노드가 없음을 확인.
- frontend 폴더 및 웹 산출물 디렉터리 부재 확인.
- APK 내부에 HTML·JS·CSS·TSX assets 없음. 인터넷 권한 없음.
- 네이티브 화면 스크린샷은 .local/verification/native의 home.png, recording.png, analyzing.png, shorts.png, detail.png, history.png.
- 입력·저장 검증은 Pleos 운전자 사용자(10)의 저장소에서 수행.


## 코드 정리

샘플 데이터·공통 UI·에셋 처리를 분리하고 Kotlin 포맷을 통일했습니다. 지난 기록의 여러 병렬 배열은 Drive 객체 목록으로 묶었습니다. 사용하지 않는 check 아이콘, 중복 홈 배경, 불필요한 화면 필드·반환값을 제거했습니다. 타이머는 기록 및 미완료 분석 화면이 표시되는 동안만 예약되며, 재렌더링 시 현재 기록 시간을 사용합니다. 제목 길이와 시연 초기값도 한 곳에서 관리합니다.

정리 후 APK 빌드·Lint(오류/경고 0)와 6개 화면 전환·제목 수정 및 저장 회귀 검증을 통과했습니다. Pleos 창 배치가 어긋난 경우 에뮬레이터 재시작 후 검증했습니다.


## 차량 시스템 화면 복구

부팅 직후 adb로 앱을 먼저 실행한 경우 DRIVELOG 작업이 fullscreen으로 생성돼 좌측 차량 화면을 덮는 상태를 확인했습니다. host GPU로 실행하고 Pleos 홈·DrivingView·내비게이션 준비 후 앱 목록에서 DRIVELOG를 열면 올바른 multi-window로 생성됩니다. 앱 경계 [853,90–2535,1300], 차량 시스템 경계 [0,0–853,1300] 및 3D 차량 표시를 함께 확인했습니다. 실행 스크립트는 에뮬레이터만 열고 앱 선택은 Pleos 런처에서 하도록 안내합니다.
