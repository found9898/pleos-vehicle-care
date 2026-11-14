# 미리정비

현대자동차그룹 Pleos Connect(Android Automotive)용 소모품 관리 앱입니다. 차에서 직접 읽은 주행거리와 운행 신호로 엔진오일, 브레이크 패드, 타이어, 와이퍼, 냉방·난방, 실내 공기 필터 여섯 가지의 교체·점검 시점을 판단합니다.

취급설명서의 기간·거리 주기를 기본으로 두고, 차량 신호로 계산한 추정이 더 급할 때만 시점을 앞당깁니다. 추정이 설명서 주기를 늦추는 일은 없습니다.

## 소모품별 판단

| 소모품 | 차에서 읽는 값 | 추정 |
|---|---|---|
| 엔진오일 | 차속, 변속 P단, 외기 온도, 오일량 경고 | 오일 온도를 추정해 차가운 동안의 엔진 회전에 가중치를 주고(GM 특허 기준), 직분사 연료 희석을 따로 셉니다 |
| 브레이크 패드 | 차속, 페달, 브레이크액 경고 | 제동 구간의 운동에너지에서 주행저항과 회생제동 몫을 빼고, 앞 디스크 온도로 마모 배율을 줍니다. 정비 때 잰 두께가 있으면 그 값에서 다시 셉니다 |
| 타이어 | 차속, 조향각, 뒷바퀴 회전, 외기 온도 | UN 규정 117 보통 주행 대비 가감속 제곱 평균으로 마모 가중을 내고, 축별 홈 깊이를 위치 교환에 맞춰 추적합니다. 추정만으로는 교체가 아니라 깊이 확인을 요청합니다 |
| 와이퍼 | 와이퍼 스위치·작동 상태, 실내 마이크, 외기·실내 온도 | 블레이드 나이(더위, 햇볕 주차, 평균보다 많이 닦은 몫)와 작동 소리 변화로 판단합니다 |
| 냉방·난방 | 에어컨·송풍·내외기 상태, 실내·설정·외기 온도, 차속 | 점검 직후와 같은 조건의 냉방·난방 회복 속도를 비교합니다 |
| 실내 공기 필터 | 송풍 단계, 내외기 상태, 차속, 위치(선택) | 송풍량과 그달 시도별 미세먼지로 쌓인 먼지를 셉니다 |

모든 추정에는 근거 논문·규격과 모의 실험 검증이 있습니다. 회차별 설계와 검증 수치는 [docs/phase1-maintenance.md](docs/phase1-maintenance.md), 요약은 [docs/model-hardening-log.md](docs/model-hardening-log.md)에 있습니다. 수치별 출처와 등급은 `materials/04_research/<소모품>/model-inputs.md`에 있고, 받은 원문 파일은 저작권 때문에 저장소에 넣지 않았습니다.

지금 할 정비가 있으면 출발할 때 차량 음성(TextToSpeech SDK, 온디바이스)으로 한 문장을 알려 줍니다. 같은 내용은 하루에 한 번만 말하고 설정에서 끌 수 있습니다.

일반 서드파티 권한으로 읽을 수 있는 차량 신호와 읽을 수 없는 신호의 전체 목록은 [docs/vehicle-signal-access.md](docs/vehicle-signal-access.md)에 정리했습니다.

## APK

| 파일 | 위치 | 용도 |
|---|---|---|
| release APK | 저장소 [`apk/`](apk/) 폴더, [GitHub Releases](../../releases/latest) | 심사·설치용. 처음 실행하면 권한을 한 번에 받고 소모품별 마지막 정비 시기를 선택지로 고르는 첫 설정이 나옵니다. 설정 > 예시 데이터로 둘러보기로 시연 기록을 넣을 수 있습니다 |
| 시연용 debug APK | [GitHub Releases](../../releases/latest) | 시연 데이터를 한 번에 넣는 기능이 들어 있습니다 |

두 APK는 서명 키가 달라서 서로 덮어 설치되지 않습니다. 바꿔 설치할 때는 `adb uninstall dev.vehiclecare.app`으로 먼저 지워 주세요. 이때 앱에 저장한 정비 기록도 함께 지워집니다.

## 테스트 환경

| 항목 | 값 |
|---|---|
| 기기 | Pleos Connect 에뮬레이터 2.1.0 (Android Automotive 14, API 34, 2560×1440) |
| 사용자 | 에뮬레이터 기본 운전자 계정(user 10). `adb shell am get-current-user`로 확인합니다 |
| 패키지 | `dev.vehiclecare.app`, 최소 API 28 |
| 차량 신호 입력 | 에뮬레이터 Virtual Console(`adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`) |
| 외부 서버·계정 | 쓰지 않습니다. 모든 계산은 차 안에서 끝나고 기록은 앱 내부 저장소에만 남습니다 |

차량 정보·주행·공조·창문 권한은 설치할 때 자동으로 허용됩니다. 아래 셋은 실행 중에 사용자가 허용하는 권한입니다. 차속과 마이크는 소모품 상세 화면에서, 알림은 설정 화면에서 허용 버튼을 누르면 됩니다. 허용하지 않아도 앱은 동작하며 해당 신호를 쓰는 추정만 빠집니다.

| 권한 | 쓰는 곳 |
|---|---|
| 차량 속도(`android.car.permission.CAR_SPEED`) | 엔진오일·브레이크·타이어 추정의 차속과 바퀴 회전 |
| 알림(`android.permission.POST_NOTIFICATIONS`) | 교체 시점 알림 |
| 마이크(`android.permission.RECORD_AUDIO`) | 와이퍼 작동 소리 변화 |

화면을 거치지 않고 한 번에 허용하려면 다음 명령을 씁니다.

```sh
for p in android.car.permission.CAR_SPEED android.permission.POST_NOTIFICATIONS android.permission.RECORD_AUDIO; do
  adb shell pm grant --user 10 dev.vehiclecare.app $p
done
```

## 실행 방법

### APK로 바로 실행

```sh
adb install -r apk/미리정비_미리정비_release.apk
adb shell am start --user 10 -n dev.vehiclecare.app/.MainActivity
```

에뮬레이터 런처의 앱 목록에서 '미리정비'를 눌러도 됩니다. 첫 화면에서 소모품을 누르고 마지막 교체일과 주행거리를 넣으면 교체 시점 계산이 시작됩니다. Virtual Console에서 주행거리, 차속, 와이퍼, 공조 값을 바꾸면 추정이 그 신호를 따라 움직입니다.

### 시연 데이터로 보기

시연용 debug APK를 설치한 뒤 저장소에서 다음 명령을 실행합니다. 소모품 여섯 가지의 정비 기록과 몇 달 치 운행 신호가 들어가고, Virtual Console의 주행거리가 38,420km보다 작으면 38,420km로 맞추고, 이미 크면 그 값을 기준으로 기록을 옮깁니다. 날짜는 실행한 날부터 거꾸로 세므로 어느 날 넣어도 브레이크 패드 1개가 지금 할 때, 엔진오일·와이퍼 2개가 곧 할 때로 같게 나옵니다.

```sh
./scripts/dev.sh demo
```

### 소스에서 빌드

필요한 것: JDK 17, Android SDK(API 34), Pleos Connect 에뮬레이터 이미지 2.1.0. 설치 과정은 [docs/development-environment.md](docs/development-environment.md)에 있습니다.

```sh
./scripts/dev.sh doctor            # 도구와 기기 상태 확인
./scripts/dev.sh emulator          # 에뮬레이터 실행
./scripts/dev.sh run               # debug 빌드, 설치, 실행
./scripts/dev.sh demo              # 시연용 정비 기록과 신호 넣기(debug 빌드 전용)
./scripts/dev.sh test              # 단위 테스트 216개와 lint
./scripts/dev.sh release <팀명>    # 서명한 release APK를 apk/ 폴더에 만들기
```

release 서명 키는 저장소에 넣지 않았습니다. `.dev/keystore.properties`에 키 경로와 비밀번호가 있어야 `release` 명령이 동작합니다.

## 구성

- `android/` 앱 소스. 판단 로직은 `app/src/main/java/dev/vehiclecare/app/`의 `*Wear.kt`, `Assessment.kt`, `CareGuide.kt`이고, 모의 실험은 `app/src/test/`의 `*SimulationTest.kt`입니다.
- `docs/` 설계·검증 문서
- `planning/` 서비스 요구사항과 화면 기획
- `materials/04_research/` 소모품별 근거 수치 정리
- `materials/07_model_specs/specs.json` 차종별 주기·규격
- `scripts/` 개발 스크립트

정비 업체 이름, 별점, 가격은 시연용 예시이며 실제 업체가 아닙니다.
