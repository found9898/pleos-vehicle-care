# Pleos Connect 개발환경

구성일: 2026년 10월 26일 · 대상: Apple Silicon Mac · 프로젝트: `android/`

## 설치 및 프로젝트 설정

| 항목 | 구성 |
| --- | --- |
| Android Studio | 2026.2.1.8 Rabbit 1, `/Applications/Android Studio.app` |
| 빌드 JDK | Amazon Corretto 17.0.19, 기존 설치 재사용 |
| Android SDK | `~/Library/Android/sdk` |
| Command-line Tools | 22.0, build 15859902 |
| Android SDK Platform | API 34, revision 3 |
| Build Tools | 35.0.0 |
| Platform Tools | 37.0.1 |
| Android Emulator | 37.2.12 |
| Pleos Connect 이미지 | v2.1.0, API 34, arm64-v8a, repository revision 2 |
| AVD | `Pleos_Connect_2_1_0`, 공식 Pleos 하드웨어 프로필 |
| AVD 실행 설정 | RAM 4GB, Cold boot, GPU host |
| Gradle / AGP / Kotlin | 8.13 / 8.13.2 / 2.2.21 |
| 앱 SDK | minSdk 28 / compileSdk 34 / targetSdk 34 |
| Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` |

Android Studio의 JDK 25는 IDE 실행에 사용합니다. Gradle 8.13 빌드에는 JDK 17을 사용하도록 프로젝트 스크립트와 로컬 Gradle JVM 설정을 구성했습니다. 전역 쉘 설정 파일을 변경하지 않았습니다.

## 사용 방법

Android Studio에서 저장소의 `android` 폴더를 엽니다. Gradle JVM은 `GRADLE_LOCAL_JAVA_HOME`에 연결한 JDK 17을 사용하도록 로컬 설정을 만들었습니다. 다른 Mac에서는 JDK 17과 SDK 경로를 해당 환경에 맞게 지정합니다.

저장소 루트에서 다음 명령을 사용합니다.

```sh
# 설치 도구와 기기 상태
./scripts/dev.sh doctor

# APK 빌드 및 Android lint
./scripts/dev.sh build

# 에뮬레이터 실행: 이 터미널은 실행 상태를 유지
./scripts/dev.sh emulator

# 별도 터미널: APK 설치 및 개발용 앱 실행
./scripts/dev.sh run

# Virtual Console 포트 연결 및 브라우저 열기
./scripts/dev.sh console

# 개발용 앱의 SDK 조회 로그
./scripts/dev.sh logs
```

`adb`, `emulator`, `sdkmanager` 등을 직접 사용하려면 다음을 실행합니다.

```sh
source scripts/android-env.sh
adb devices
```

Virtual Console은 에뮬레이터 부팅 후 `adb forward tcp:8088 tcp:8088`로 연결하며 주소는 `http://127.0.0.1:8088/`입니다. 포트가 다른 용도로 사용 중이면 해당 프로세스를 종료하지 말고 다른 로컬 포트를 사용합니다.

브라우저에서 콘솔을 열고 `DDS ONLINE` 상태를 확인해야 차량 시뮬레이션이 활성화됩니다. HTTP 페이지 조회만으로는 활성화되지 않으며, 브라우저 연결이 없을 때 상태 변경 요청이 `emulator_inactive`로 실패할 수 있습니다.

기기가 여러 대이면 `ANDROID_SERIAL`로 대상 에뮬레이터를 지정합니다. 기본 대상은 `emulator-5554`입니다.

## 첫 SDK 호출

아래는 초기 개발환경 검증 기록이다. 현재 화면은 [1단계 주기 관리 서비스](phase1-maintenance.md)로 교체했으며 도어 조회와 `CAR_DOORS` 권한은 제거했다. Vehicle SDK 의존성과 초기 연동 검증 기록은 유지한다.

앱의 `MainActivity`는 공식 시작 가이드의 다음 과정을 구현합니다.

1. `Vehicle` 생성 및 `initialize()` 호출
2. `CAR_DOORS` 권한으로 운전석 도어 위치 조회
3. 비동기 성공·실패 응답을 화면과 `PleosProbe` 로그로 확인
4. 화면 종료 시 진행 중인 콜백을 고려해 SDK 연결 해제

Virtual Console에서 운전석 도어 상태를 바꾸고 앱의 다시 조회 버튼을 누르면 값을 비교할 수 있습니다. 이 프로젝트에는 서버 음성 SDK나 외부 AI API를 연결하지 않았으며, 이 로컬 Vehicle State 검증에는 클라이언트 시크릿을 사용하지 않습니다. Pleos Playground 프로젝트 생성·SDK 승인·마켓 등록은 해당 계정이 필요한 단계에서 진행합니다.

## 이미지 설치 방식과 출처

공식 Mac 이미지 URL과 하드웨어 프로필을 사용했습니다. 다운로드한 ZIP의 SHA-1을 공식 repository XML과 대조했습니다.

- 저장소: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
- 패키지: `system-images;android-34;pleos_car_emulator;arm64-v8a`
- ZIP 크기: 4,669,762,816 bytes
- 공식 SHA-1: `24d28eb2f18eba5e87c861a7eb2bdccebb049419`

시스템 이미지의 논리적 크기가 17GiB이므로, `scripts/install-pleos-image.py`에서 0으로 채워진 영역을 sparse file로 보관해 디스크 사용을 줄였습니다. 이미지 내용은 변경하지 않았습니다. SDK 등록 메타데이터는 최신 SDK 도구가 읽을 수 있는 XML schema로 변환했고, 공식 라이선스 본문·패키지 ID·버전·하드웨어 프로필을 보존했습니다.

SDK 저장소는 `~/.android/repositories.cfg`에 추가했습니다. Android Studio SDK Manager에서 업데이트 사이트가 보이지 않으면 위 공식 XML URL을 `Pleos Connect System Image`라는 이름으로 추가하면 됩니다.

## 검증 기록

| 항목 | 상태 |
| --- | --- |
| 공식 이미지 체크섬 | 일치 |
| SDK 패키지 및 Pleos AVD 등록 | 완료 |
| APK 빌드 | 성공 |
| Android lint | 오류 0개, 경고 5개로 통과 |
| 에뮬레이터 부팅 | `sys.boot_completed=1`, API 34 |
| Vehicle SDK 실제 응답 | 콘솔의 도어 닫힘→열림→닫힘에 응답 `0→1→0` 확인 |
| Virtual Console 연결 | HTTP 200, 브라우저 연결 및 DDS bridge 활성화 확인 |
| Android Studio 프로젝트 | `VehicleCare` 열기 및 Gradle sync 성공. Corretto 17 선택 확인 |

로컬 실행 로그와 설치 메타데이터는 `.dev/`에 저장하며 Git 추적 대상에서 제외합니다. APK는 `android/app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

SDK 연동 검증 시각은 2026년 10월 26일 15:49 KST이며 상세 결과는 `.dev/verification.json`에 기록했습니다. IDE 로그에서는 15:58 KST에 프로젝트 import와 Gradle sync 성공을 확인했습니다. 초기 화면 기록의 일부 구간은 캡처 예산·창 전환으로 연속 관측하지 못했으며, IDE 완료 판정은 현재 화면 상태와 IDE 동기화 로그를 기준으로 했습니다.

lint 경고는 최신 타깃 API·compileSdk·Gradle 버전 권장, 백업 규칙, 앱 아이콘 관련입니다. API 34와 빌드 도구 조합은 Pleos 개발 환경에 맞춰 고정했습니다. 앱 아이콘과 최종 데이터 백업 정책은 서비스 구현 단계에서 정리합니다.

설치가 끝난 약 4.7GB 이미지 ZIP과 Command-line Tools ZIP은 로컬 다운로드 캐시에서 정리했습니다. SDK에 설치한 이미지와 체크섬·출처 기록은 유지합니다.

## 참고한 공식 가이드

- [Pleos 에뮬레이터 설치](../materials/02_pleos_docs/pages/docs__connect__guide__getting-started__application-development__setup-connect-sdk-emulator.md)
- [Cold boot 및 에뮬레이터 설정](../materials/02_pleos_docs/pages/docs__connect__guide__getting-started__application-development__configure-pleos-connect-emulator.md)
- [Virtual Console 설정](../materials/02_pleos_docs/pages/docs__connect__guide__getting-started__application-development__use-vehicle-emulator.md)
- [첫 앱과 SDK 호출](../materials/02_pleos_docs/pages/docs__pleos-only__getting-started.md)
- [Vehicle SDK 2.3.2](../materials/02_pleos_docs/pages/docs__pleos-only__connect-sdk-pleos__Vehicle__project-setup.md)
- [AGP 8.13 호환성](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
