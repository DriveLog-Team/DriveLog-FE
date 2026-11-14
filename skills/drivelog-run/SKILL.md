---
name: drivelog-run
description: Run or rerun the current DriveLog debug build on the Mac Pleos Connect emulator. Use for 다시 실행해줘, 앱 띄워줘, and DriveLog installation, offline, or launch timeout recovery.
---

# DriveLog 실행

저장소 루트에서 `./scripts/run-emulator.sh`를 실행한다. 기본 대상은 이 Mac에 등록된 `Pleos_Connect_v2.1.0`이다. 이름이 다르면 첫 번째 인자로 해당 AVD 이름을 전달한다.

- Gradle로 현재 작업 내용을 빌드한다. 빌드가 실패하면 오류를 확인한다. 이전 APK로 대체하면 현재 작업을 실행했다는 인상을 줄 수 있으므로 사용자에게 빌드 문제를 알린다.
- 스크립트는 Pleos API 34 이미지와 GPU 가속을 확인한다. 정상 기기는 재사용하고, 해당 Pleos 기기가 꺼져 있거나 소프트웨어 렌더링 중이면 Mac GPU로 실행한다. 출력이 중간에 끊기면 스크립트 종료 상태와 출력된 로그 경로를 확인한다.
- 설치는 현재 차량 사용자에게 덮어쓰는 방식이다. 앱 데이터 삭제, 공장 초기화, 다른 기기 종료 없이 복구한다. 스크립트가 검증에 실패하면 `adb devices -l`, 해당 AVD의 시작 로그, 앱 실행 로그로 원인을 좁힌다. Pleos 홈이 아직 초기화 중이면 기다린 뒤 재시도한다.
- 성공은 설치 완료에 더해 DriveLog가 전면 앱으로 확인된 상태다. Pleos 런처가 화면을 가져가면 런처가 준비된 뒤 DriveLog를 다시 연다. 화면이 의도와 다르면 설치한 기기와 열린 창을 확인한다.
