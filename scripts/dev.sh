#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$PROJECT_DIR/scripts/android-env.sh"
cd "$PROJECT_DIR"
mkdir -p .dev/logs
AVD_NAME="Pleos_Connect_2_1_0"
APP_ID="dev.vehiclecare.app"

case "${1:-doctor}" in
    doctor)
        java -version
        adb version
        emulator -list-avds
        adb devices
        ;;
    build)
        cd android
        ./gradlew assembleDebug lintDebug
        ;;
    test)
        cd android
        ./gradlew testDebugUnitTest lintDebug
        ;;
    integration)
        cd android
        ./gradlew -PcareIntegration=true connectedDebugAndroidTest
        ;;
    emulator)
        exec emulator -avd "$AVD_NAME" -no-snapshot -gpu host -memory 4096
        ;;
    console)
        adb -s "${ANDROID_SERIAL:-emulator-5554}" forward tcp:8088 tcp:8088
        open "http://127.0.0.1:8088/"
        ;;
    run)
        cd android
        ./gradlew assembleDebug
        adb -s "${ANDROID_SERIAL:-emulator-5554}" install -r app/build/outputs/apk/debug/app-debug.apk
        adb -s "${ANDROID_SERIAL:-emulator-5554}" shell am start -n "$APP_ID/.MainActivity"
        ;;
    release)
        # Submission APK: apk/<팀명>_미리정비_release.apk, signed with .dev/keystore.properties.
        team="${2:?Usage: $0 release <팀명>}"
        [ -f .dev/keystore.properties ] || { echo "Missing .dev/keystore.properties" >&2; exit 1; }
        (cd android && ./gradlew assembleRelease lintRelease)
        mkdir -p apk
        rm -f apk/*_release.apk
        cp android/app/build/outputs/apk/release/app-release.apk "apk/${team}_미리정비_release.apk"
        "$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)/apksigner" verify --print-certs "apk/${team}_미리정비_release.apk" | head -3
        ls -l apk
        ;;
    demo)
        # Debug build only: loads the demo service history and signals, and moves the console odometer to match.
        serial="${ANDROID_SERIAL:-emulator-5554}"
        user="$(adb -s "$serial" shell am get-current-user | tr -d '\r')"
        # The console refuses to lower the odometer; past 38,420 km the demo history shifts to the current reading instead.
        curl -s -m 5 -X PUT "http://127.0.0.1:8088/vehicle/odometer?traveled_distance_km=38420" >/dev/null || echo "Virtual Console not reachable; odometer left as is" >&2
        km="$(curl -s -m 5 "http://127.0.0.1:8088/vehicle/odometer" | sed -n 's/.*"traveled_distance_km":\([0-9]*\).*/\1/p')"
        adb -s "$serial" shell am force-stop --user "$user" "$APP_ID"
        adb -s "$serial" shell am broadcast --user "$user" -n "$APP_ID/.DemoSeedReceiver" --el odometer "${km:-0}"
        sleep 2
        adb -s "$serial" shell am force-stop --user "$user" "$APP_ID"
        adb -s "$serial" shell am start --user "$user" -n "$APP_ID/.MainActivity"
        ;;
    logs)
        exec adb -s "${ANDROID_SERIAL:-emulator-5554}" logcat -s AndroidRuntime:E
        ;;
    *)
        printf 'Usage: %s {doctor|build|test|integration|emulator|console|run|release|demo|logs}\n' "$0" >&2
        exit 2
        ;;
esac
