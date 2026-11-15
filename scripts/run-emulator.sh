#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
avd=${1:-Pleos_Connect_v2.1.0}
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-"$HOME/Library/Android/sdk"}}
avd_home=${ANDROID_AVD_HOME:-"${ANDROID_USER_HOME:-$HOME/.android}/avd"}
adb="$sdk/platform-tools/adb"
emulator="$sdk/emulator/emulator"
package=kr.drivelog.app

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

# Bound device calls even when the emulator is connected but unresponsive.
limited() {
    perl -e 'alarm shift; exec @ARGV or die $!' "$@"
}

[ -x "$adb" ] && [ -x "$emulator" ] || fail "Android SDK를 찾을 수 없어요. ANDROID_HOME을 확인하세요."
avd_path=$(sed -n 's/^path=//p' "$avd_home/$avd.ini")
[ -f "$avd_path/config.ini" ] || fail "Pleos 기기를 찾을 수 없어요: $avd"
grep -q '^image.sysdir.1=.*android-34/pleos_car_emulator/' "$avd_path/config.ini" ||
    fail "Device Manager에서 Pleos Connect 전용 API 34 이미지를 선택하세요."

cd "$root"
if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 21)
    export JAVA_HOME
fi
./gradlew :app:assembleDebug

serial=
for candidate in $(limited 10 "$adb" devices | awk '/^emulator-/ {print $1}'); do
    name=$(limited 5 "$adb" -s "$candidate" emu avd name 2>/dev/null | tr -d '\r' | head -n 1)
    if [ "$name" = "$avd" ]; then
        serial=$candidate
        break
    fi
done

stop_pleos() {
    limited 10 "$adb" -s "$serial" emu kill
    for attempt in $(seq 1 15); do
        if ! limited 5 "$adb" devices | awk -v target="$serial" '$1 == target {found=1} END {exit !found}'; then
            serial=
            return
        fi
        sleep 2
    done
    fail "Pleos 기기의 종료가 지연되고 있어요."
}

if [ -n "$serial" ]; then
    mode=$(sed -n 's/^hw.gpu.mode = //p' "$avd_path/hardware-qemu.ini" 2>/dev/null || :)
    state=$(limited 5 "$adb" -s "$serial" get-state 2>/dev/null || :)
    if [ "$mode" != host ] || [ "$state" != device ]; then
        stop_pleos
    elif [ "$(limited 5 "$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] &&
        ! limited 12 "$adb" -s "$serial" shell cmd activity get-current-user >/dev/null 2>&1; then
        printf '%s\n' "Pleos 시스템이 응답하지 않아 GPU 가속으로 다시 시작해요."
        stop_pleos
    fi
fi

if [ -z "$serial" ]; then
    log=$(mktemp "${TMPDIR:-/tmp}/drivelog-pleos.XXXXXX")
    printf 'Pleos 시작 로그: %s\n' "$log"
    nohup "$emulator" -avd "$avd" -gpu host -memory 4096 -no-snapshot >"$log" 2>&1 </dev/null &
    emulator_pid=$!
    for attempt in $(seq 1 25); do
        kill -0 "$emulator_pid" 2>/dev/null || fail "Pleos 시작 실패. 로그: $log"
        for candidate in $(limited 5 "$adb" devices | awk '/^emulator-/ {print $1}'); do
            name=$(limited 5 "$adb" -s "$candidate" emu avd name 2>/dev/null | tr -d '\r' | head -n 1)
            if [ "$name" = "$avd" ]; then
                serial=$candidate
                break 2
            fi
        done
        sleep 2
    done
    [ -n "$serial" ] || fail "Pleos 연결 시간이 초과됐어요. 로그: $log"
fi

boot=
for attempt in $(seq 1 24); do
    boot=$(limited 5 "$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    [ "$boot" != 1 ] || break
    sleep 3
done
[ "$boot" = 1 ] || fail "Pleos 부팅이 완료되지 않았어요: $serial"

for attempt in 1 2 3 4; do
    user_id=$(limited 8 "$adb" -s "$serial" shell cmd activity get-current-user 2>/dev/null | tr -d '\r')
    case "$user_id" in
        ''|*[!0-9]*) user_id=; sleep 3 ;;
        *) break ;;
    esac
done
[ -n "$user_id" ] || fail "현재 차량 사용자를 확인할 수 없어요: $serial"
limited 90 "$adb" -s "$serial" install -r --user "$user_id" app/build/outputs/apk/debug/app-debug.apk

for attempt in 1 2 3; do
    limited 20 "$adb" -s "$serial" shell am start -W --user "$user_id" -n "$package/.MainActivity" || :
    sleep 2
    if limited 10 "$adb" -s "$serial" shell dumpsys activity activities |
        grep -Eq 'topResumedActivity=.*kr\.drivelog\.app/'; then
        printf 'DriveLog 실행 확인 (%s, 사용자 %s)\n' "$serial" "$user_id"
        exit 0
    fi
done
fail "APK는 설치됐지만 DriveLog가 전면에 열린 상태를 확인하지 못했어요."
