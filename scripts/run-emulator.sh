#!/bin/sh
set -eu

DRIVELOG_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
export ANDROID_HOME="$DRIVELOG_ROOT/.local/android-sdk"
export ANDROID_USER_HOME="$DRIVELOG_ROOT/.local/android-user"
export ANDROID_AVD_HOME="$DRIVELOG_ROOT/.local/avd"

if [ ! -x "$ANDROID_HOME/emulator/emulator" ]; then
    printf '%s\n' 'Android SDK가 없습니다. README의 개발 환경 설정을 확인하세요.' >&2
    exit 1
fi

if "$ANDROID_HOME/platform-tools/adb" -s emulator-5556 get-state >/dev/null 2>&1; then
    printf '%s\n' '에뮬레이터가 이미 실행 중입니다. Pleos 하단 앱 목록에서 DRIVELOG를 선택하세요.'
    exit 0
fi

printf '%s\n' '차량 시스템 화면이 나타나면 하단 앱 목록(격자 아이콘)에서 DRIVELOG를 선택하세요.'
exec "$ANDROID_HOME/emulator/emulator" \
    -avd DriveLogPleos \
    -no-audio \
    -no-snapshot \
    -gpu host \
    -port 5556 \
    -scale 0.5
