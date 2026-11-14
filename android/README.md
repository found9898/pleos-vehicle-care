# Vehicle Care Android 개발 프로젝트

Pleos Connect 공식 시작 가이드를 기준으로 구성한 Kotlin 프로젝트입니다. 현재 앱은 여섯 관리 항목의 정비 날짜·누적 주행거리를 기록하고 다음 교체·점검 주기를 알려주는 1단계 서비스입니다.

- minSdk 28 / compileSdk 34 / targetSdk 34
- Vehicle SDK 2.3.2 및 공식 Pleos Maven 저장소
- Gradle Wrapper 8.13 / Android Gradle Plugin 8.13.2 / Kotlin 2.2.21
- 빌드 JDK 17
- 애플리케이션 ID: `dev.vehiclecare.app` — 개발용 임시 ID

Android Studio에서 이 `android` 폴더를 열면 됩니다. `local.properties`의 SDK 경로와 Gradle JVM 설정은 해당 Mac에 맞는 로컬 파일로 관리합니다.

저장소 루트에서 사용하는 명령:

```sh
./scripts/dev.sh doctor
./scripts/dev.sh build
./scripts/dev.sh test
./scripts/dev.sh integration
./scripts/dev.sh emulator
./scripts/dev.sh run
./scripts/dev.sh console
./scripts/dev.sh logs
```

`emulator`는 실행 상태를 유지하는 명령입니다. 별도 터미널에서 `run`, `console` 등을 실행하세요. 여러 에뮬레이터가 있으면 `ANDROID_SERIAL=emulator-5556`처럼 실행 대상을 지정할 수 있습니다.

상세 설정과 검증 기록은 [개발환경 안내](../docs/development-environment.md)를 참조하세요.

화면·계산·알림·저장 동작과 범위는 [1단계 구현 안내](../docs/phase1-maintenance.md)에 정리했습니다. 현재 주행거리는 Pleos 차량 데이터에서 자동 수신하고, 부품별 추천 주기를 기본 적용합니다. 실제 교체·점검을 했을 때만 작은 기록 버튼으로 갱신합니다. 기기 테스트는 실제 앱 데이터와 분리된 패키지에서 실행합니다. 그래픽·아이콘의 출처는 [자산 안내](THIRD_PARTY_NOTICES.md)를 참조하세요.
