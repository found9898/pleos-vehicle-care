# 차량 신호 접근 범위 (Pleos 속성 기준)

조사일 2026-11-02. Pleos Vehicle SDK 2.3.2(`vehicle-data` 1.1.11)와 Pleos Connect 에뮬레이터(사용자 10, AAOS Android 13 기반)에서 확인했다.

## 확인 방법

- 대상: Pleos `PleosPropertyIds`에 정의된 427개와 AAOS `VehiclePropertyIds` 208개를 합친 이름 전체.
- AAOS 속성: 디버그 빌드의 `PropertyProbeReceiver`가 모든 속성을 `CarPropertyManager.getProperty`로 직접 읽어 결과와 예외 메시지(필요 권한)를 기록했다. 앱이 지금 가진 권한으로 한 번, 일반 앱이 받을 수 있는 권한(normal·dangerous)을 모두 선언하고 승인한 상태로 한 번 읽었다. 시스템 전용 권한(signature|privileged)은 선언해도 승인되지 않는 것을 같은 실행에서 확인했다.
- 권한 등급: 에뮬레이터 `dumpsys package permission`의 protection level.
- Pleos 전용 속성: SDK 구현 소스(`api/implement/*Impl.kt`) 530개 메서드가 요구하는 권한과 읽는 `PleosPropertyIds`를 대조했다. 문서에 `@hide`·`@suppress`로 표시되었거나 `PRIVATE_*` 권한을 요구하는 API는 일반 앱이 쓸 수 없는 것으로 분류했다. SDK 문서는 `PRIVATE_*` 권한을 signature|privileged로 적고 있으나 에뮬레이터에서는 normal로 등록되어 있어, 실차 기준인 문서를 따랐다.
- 다시 돌리는 법: `adb shell am broadcast --user 10 -n dev.vehiclecare.app/.PropertyProbeReceiver` 후 `adb logcat -s PropProbe`.

## 요약

| 구분 | 개수 | 뜻 |
|---|---|---|
| 지금 앱이 읽는 신호 | 21 | 차속, 휠 틱, 외기 온도, 변속 레버, 누적 주행거리, 브레이크 페달, 공조 5종, 와이퍼 상태·스위치, 엔진 오일량 경고, 워셔액 부족, 차종 정보 |
| 지금 바로 읽을 수 있지만 쓰지 않는 신호 | 114 | Pleos 공개 API 또는 이미 가진 AAOS 권한으로 읽힌다 |
| 일반 권한만 선언하면 읽을 수 있는 신호 | 19 | `CAR_POWERTRAIN`, `CAR_ENERGY`, `READ_CAR_DISPLAY_UNITS` 등 |
| 일반 앱은 읽을 수 없는 신호 | 240 | 시스템 전용 권한, Pleos 비공개 API, 또는 Pleos SDK에 읽는 API가 없음 |
| 에뮬레이터에 없어 실측하지 못한 신호 | 33 | 에뮬레이터가 전기차라 엔진 계열 속성이 없다. 권한 등급으로 판단을 덧붙였다 |

## 소모품 판정에 영향을 주는데 읽을 수 없는 신호

일반 앱이 읽을 수 없어서 지금은 다른 신호로 추정하는 항목이다. 제출물의 고도화 대상(플랫폼 권한 개방 요청)으로 쓴다.

| 소모품 | 읽을 수 없는 신호 | 막힌 이유 | 있으면 무엇이 좋아지나 | 지금의 대체 방법 |
|---|---|---|---|---|
| 엔진오일 | `ENGINE_OIL_TEMP` 오일 온도 | AAOS `CAR_ENGINE_DETAILED`(signature|privileged), Pleos API 없음 | 연료 희석·수분 응축이 일어나는 저온 운행 시간을 직접 잰다. 오일 수명 계산의 가장 큰 오차 요인이 사라진다 | 차속·주행 시간·외기 온도로 엔진 열 상태를 추정 |
| 엔진오일 | `ENGINE_RPM` 엔진 회전수 | 위와 같음 | 제조사 오일 수명 알고리즘이 쓰는 엔진 회전 누적을 계산할 수 있다. 공회전 시간과 하이브리드의 엔진 정지 구간을 구분한다 | 차속 0 구간을 공회전으로 봄. 하이브리드는 엔진이 꺼져 있어도 공회전으로 셈 |
| 엔진오일 | `ENGINE_COOLANT_TEMP` 냉각수 온도 | 위와 같음 | 예열 완료 시점을 안다 | 같은 열 모델 |
| 엔진오일 | `ENGINE_STATE`(Pleos) 엔진 작동 상태 | Pleos SDK에 API 없음 | 하이브리드의 엔진 가동 시간을 직접 센다 | 동력원별 고정 가정 |
| 타이어 | `TIRE_PRESSURE` 바퀴별 공기압 | AAOS `CAR_TIRES`(signature|privileged), Pleos `getTirePressure`는 `@hide`·`PRIVATE_CAR_SAFETY` | 공기압 부족은 편마모·마모 속도에 직접 영향을 준다. 월 점검을 실제 공기압으로 대체할 수 있다 | 월 1회 점검 알림. `CRITICALLY_LOW_TIRE_PRESSURE`는 공개 API로 읽히지만 경고가 아니라 바퀴별 저압 기준값(kPa)이라, 실제 공기압 없이는 쓸 데가 없다(11월 3일 SDK 확인) |
| 타이어 | `RECOMMENDED_TIRE_PRESSURE`, `OPTIMAL_TIRE_PRESSURE`, `TIRE_PRESSURE_MONITORING_SYSTEM_STATE`(Pleos) | Pleos SDK에 API 없음 | 차량에 맞는 적정 공기압과 TPMS 상태 | 취급설명서 값(차종 목록) |
| 타이어·브레이크 | `ABS_ACTIVE`, `TRACTION_CONTROL_ACTIVE` | AAOS `CAR_DYNAMICS_STATE`(signature|privileged), Pleos API 없음 | 급제동·미끄러짐 순간을 직접 잡아 마모 가중치에 쓴다 | 차속 변화율로 감가속 추정 |
| 브레이크 | 브레이크 패드 마모 센서 | AAOS·Pleos 모두 해당 속성이 없음 | 패드 잔량을 직접 안다 | 제동 에너지 누적으로 추정 |
| 브레이크 | `BRAKE_MALFUNCTION`, `PARKING_BRAKE_MALFUNCTION`, `ELECTRONIC_PARKING_BRAKE_STATE`(Pleos) | Pleos SDK에 API 없음 | 제동 계통 경고를 정비 알림과 연결 | 없음 |
| 브레이크·타이어 | `VEHICLE_CURB_WEIGHT` 공차 중량, `TRAILER_PRESENT` 견인 여부 | AAOS `PRIVILEGED_CAR_INFO`(signature|privileged) | 제동 에너지와 타이어 하중 계산에 실제 중량·견인 여부를 넣는다 | 차종별 공차 중량 고정값 |
| 와이퍼 | `WINDSHIELD_WIPERS_PERIOD` 작동 주기 | AAOS `READ_WINDSHIELD_WIPERS`(signature|privileged) | 닦은 횟수를 실제 주기로 센다 | Pleos 공개 API의 작동 단계(간헐·저속·고속)로 횟수 범위를 추정 |
| 와이퍼 | `WASHER_STATE`, `WASHER_SWITCH`, `WASHER_FLUID_SPRAY_STATE`(Pleos) | Pleos SDK에 API 없음 | 워셔액 분사 뒤 닦음(마른 닦음과 구분)을 안다 | 없음 |
| 냉방·난방 | `HVAC_ACTUAL_FAN_SPEED_RPM` 실제 송풍기 회전수 | AAOS `CONTROL_CAR_CLIMATE`(signature|privileged) | 같은 설정에서 실제 풍량 저하(필터 막힘)를 직접 잰다 | 설정 단계만 앎 |
| 실내 필터 | `INSIDE_AIR_QUALITY_LEVEL` 실내 공기질, `HVAC_AUTO_RECIRC_UPON_AIR_POLLUTION`(Pleos) | Pleos SDK에 API 없음 | 외기 오염도에 따라 필터 부하를 가중한다 | 외기 유입 시간만 셈 |
| 공통 | `INFO_VIN` 차대번호 | AAOS `CAR_IDENTIFICATION`(signature|privileged). Pleos `getVINInfo`는 에뮬레이터에서 "invalid" | 트림·타이어 규격·정확한 엔진을 확정한다 | 차종 이름과 연료 종류로 추정, 엔진이 갈리면 짧은 주기 쪽 |
| 공통 | `PERF_ODOMETER` 직접 읽기 | AAOS `CAR_MILEAGE`(signature|privileged) | 없음. Pleos `Odometer` 공개 API로 같은 값을 읽는다 | Pleos API 사용 중 |

## 읽을 수 있지만 아직 쓰지 않는 신호 중 쓸모 있는 것

다음 고도화 회차에서 반영을 검토한다.

| 신호 | 경로 | 쓸 곳 |
|---|---|---|
| `PERF_STEERING_ANGLE` 조향각 | Pleos `SteeringWheel.getCurrentSteeringWheelAngle`(공개, `CAR_DRIVE`) | 타이어 횡하중을 휠 틱 차이 대신 조향각과 차속으로 계산 |
| `CRITICALLY_LOW_TIRE_PRESSURE` 저압 기준값 | Pleos `Tire.getLowTirePressure`(공개, `CAR_SAFETY`) | 쓰지 않음. 경고가 아니라 기준값(kPa)이고 실제 공기압은 시스템 전용이다 |
| `BRAKE_FLUID_LEVEL` 브레이크액 부족 | Pleos `Safety.getBrakeFluidLevelState`(공개) | 11월 3일 반영. 부족·매우 부족이면 브레이크 바로 도래. 에뮬레이터는 값을 주지 않는다 |
| `TRANSMISSION_OIL_LEVEL` 변속기 오일량 | Pleos `Safety.getTransmissionOilLevelState`(공개) | 새 관리 항목 후보 |
| `INSTANTANEOUS_FUEL_ECONOMY` 순간 연비, `CUMULATIVE_FUEL_ECONOMY` | Pleos `Fuel`(공개) | 엔진 부하(오일 열화)와 공회전 연료 소모 추정 |
| `TRIP_DRIVING_TIME`, `TRIP_DRIVING_DISTANCE` | Pleos `Odometer`(공개) | 앱이 놓친 운행의 길이를 차량 값으로 보충 |
| `DRIVING_MODE` 주행 모드 | Pleos `DrivingMode`(공개) | 11월 3일 반영. 모드별 주행거리를 세어 브레이크·타이어 측정값 창에 스포츠 모드 비율로 보여 준다. 크기 근거가 없어 판정에는 넣지 않는다 |
| `HVAC_AIR_CLEANER` 공기청정 모드 | Pleos `HVAC`(공개) | 실내 필터 부하 |
| `PARKING_BRAKE_ON`, `CURRENT_GEAR`, `IGNITION_STATE` | AAOS `CAR_POWERTRAIN`(normal, 선언만 필요) | 11월 3일 `IGNITION_STATE` 반영. 시동을 끈 시각을 주차 시작으로 써서 난방 냉간 시동·햇볕 주차 판정의 정차 시간을 잰다. 신호가 없으면(에뮬레이터) P단 진입 시각을 그대로 쓴다 |
| `FUEL_LEVEL` 연료량 | AAOS `CAR_ENERGY`(dangerous, 사용자 승인) | 주유 간 연료 소모로 엔진 가동량 추정 |

## 제출물에 쓸 문장 (초안)

이 앱은 일반 서드파티 권한만으로 동작한다. 엔진 오일 온도·회전수·냉각수 온도, 바퀴별 타이어 공기압, ABS·TCS 작동, 실제 송풍기 회전수, 와이퍼 작동 주기, 차대번호는 Pleos 속성 목록에 있지만 시스템 전용 권한이 필요하거나 공개 SDK API가 없어 읽지 못한다. 앱은 이 값들을 차속·외기 온도·주행 시간·조작 기록으로 추정하고, 추정 범위를 화면에 함께 보여 준다. 플랫폼이 이 신호들을 읽기 전용으로 열어 주면 같은 화면 구조에서 추정을 측정으로 바꿀 수 있고, 이것을 다음 단계 고도화 대상으로 둔다.

## 부록: 전체 목록

### 1. 지금 앱이 읽는 신호 (21개)

| 속성 | 경로·이유 | Pleos SDK API |
|---|---|---|
| `BRAKE_PEDAL_STATUS` | Pleos 공개 API | Brake.getCurrentBrakePedalStatus, Brake.registerBrakePedalStatus, Brake.unregisterBrakePed |
| `ENGINE_OIL_LEVEL` | Pleos 공개 API | Safety.getEngineOilLevelState, Safety.registerEngineOilLevelState, Safety.unregisterEngine |
| `ENV_OUTSIDE_TEMPERATURE` | AAOS 직접 |  |
| `GEAR_SELECTION` | Pleos 공개 API | Gear.getCurrentGearSelectionState, Gear.registerGearSelection |
| `HVAC_AC_ON` | Pleos 공개 API | HVAC.getHvacAcState, HVAC.registerHvacAcState, HVAC.setHvacAc, HVAC.unregisterHvacAcState |
| `HVAC_FAN_SPEED` | Pleos 공개 API | HVAC.getHvacFanSpeed, HVAC.registerHvacFanSpeed, HVAC.setHvacFanSpeed, HVAC.unregisterHvac |
| `HVAC_POWER_ON` | Pleos 공개 API | HVAC.getHvacPowerState, HVAC.registerHvacPowerState, HVAC.setHvacPower, HVAC.unregisterHva |
| `HVAC_RECIRC_ON` | Pleos 공개 API | HVAC.getHvacRecirculationState, HVAC.registerHvacRecirculationState, HVAC.setHvacRecircula |
| `HVAC_TEMPERATURE_CURRENT` | Pleos 공개 API | HVAC.getHvacTemperatureCurrent, HVAC.onFailed, HVAC.onHvacTemperatureUpdated, HVAC.registe |
| `HVAC_TEMPERATURE_SET` | Pleos 공개 API | HVAC.getHvacTemperature, HVAC.onFailed, HVAC.onHvacTemperatureUpdated, HVAC.registerHvacTe |
| `INFO_EV_BATTERY_CAPACITY` | Pleos 공개 API | EvBattery.getEvBatteryCapacity |
| `INFO_EXTERIOR_DIMENSIONS` | AAOS 직접 |  |
| `INFO_FUEL_TYPE` | AAOS 직접 |  |
| `INFO_MAKE` | AAOS 직접 |  |
| `INFO_MODEL` | AAOS 직접 |  |
| `INFO_MODEL_YEAR` | AAOS 직접 |  |
| `PERF_ODOMETER` | Pleos 공개 API | Odometer.getCurrentDistanceDriven, Odometer.registerDistanceDriven, Odometer.unregisterDis |
| `PERF_VEHICLE_SPEED` | AAOS 직접 |  |
| `WHEEL_TICK` | AAOS 직접 |  |
| `WINDSHIELD_WIPERS_STATE` | Pleos 공개 API | Wiper.getCurrentWiperState, Wiper.registerWiperState, Wiper.unregisterWiperState |
| `WINDSHIELD_WIPERS_SWITCH` | Pleos 공개 API | Wiper.getCurrentWiperSwitch, Wiper.setWiperSwitch |

### 2. 지금 바로 읽을 수 있지만 쓰지 않는 신호 (114개)

| 속성 | 경로·이유 | Pleos SDK API |
|---|---|---|
| `AMBIENT_COLOR_AUTO_BY_DRIVE_MODE_ENABLED` | Pleos 공개 API | Light.getAmbientColorAutoByDriveModeEnabled, Light.registerAmbientColorAutoByDriveModeEnab |
| `BRAKE_FLUID_LEVEL` | Pleos 공개 API | Safety.getBrakeFluidLevelState, Safety.registerBrakeFluidLevelState, Safety.unregisterBrak |
| `CABIN_LIGHTS_STATE` | Pleos 공개 API | Light.getCurrentCabinLightState, Light.registerCabinLightState, Light.unregisterCabinLight |
| `CABIN_LIGHTS_SWITCH` | Pleos 공개 API | Light.getCabinLightSwitch, Light.setCabinLightSwitch |
| `CRITICALLY_LOW_TIRE_PRESSURE` | Pleos 공개 API | Tire.getLowTirePressure |
| `CRUISE_CONTROL_STATE` | Pleos 공개 API | DrivingMode.getCruiseControlState, DrivingMode.registerCruiseControlState, DrivingMode.unr |
| `CRUISE_CONTROL_TARGET_SPEED` | Pleos 공개 API | DrivingMode.getCruiseControlTargetSpeed, DrivingMode.registerCruiseControlTargetSpeed, Dri |
| `CUMULATIVE_EV_EFFICIENCY` | Pleos 공개 API | EvBattery.getCumulativeEvEfficiency, EvBattery.registerCumulativeEvEfficiency, EvBattery.u |
| `CUMULATIVE_FUEL_ECONOMY` | Pleos 공개 API | Fuel.getCumulativeFuelEconomy, Fuel.registerCumulativeFuelEconomy, Fuel.unregisterCumulati |
| `DOOR_LOCK` | Pleos 공개 API | Door.getDoorLockState, Door.registerDoorLockState, Door.setDoorLock, Door.unregisterDoorLo |
| `DOOR_POS` | Pleos 공개 API | Door.getCurrentDoorPosition, Door.registerDoorPosition, Door.setDoorPosition, Door.unregis |
| `DRIVING_MODE` | Pleos 공개 API | DrivingMode.getCurrentDrivingModeState, DrivingMode.registerDrivingModeState, DrivingMode. |
| `ELECTRONIC_TOLL_COLLECTION_CARD_STATUS` | AAOS 직접 |  |
| `ELECTRONIC_TOLL_COLLECTION_CARD_TYPE` | AAOS 직접 |  |
| `ETCS_VOICE_GUIDANCE_VOLUME` | Pleos 공개 API | Toll.getCurrentEtcsVoiceGuidanceVolume, Toll.registerEtcsVoiceGuidanceVolume, Toll.setEtcs |
| `EV_BATTERY_LEVEL` | Pleos 공개 API | EvBattery.getEvBatteryLevel, EvBattery.registerEvBatteryLevel, EvBattery.unregisterEvBatte |
| `EV_BATTERY_SOC` | Pleos 공개 API | EvBattery.getEvBatteryStateOfCharge, EvBattery.registerEvBatteryStateOfCharge, EvBattery.u |
| `EV_CHARGE_CURRENT_DRAW_LIMIT` | Pleos 공개 API | EvBattery.getEvChargeCurrentDrawLimit, EvBattery.setEvChargeCurrentDrawLimit |
| `EV_CHARGE_PERCENT_LIMIT` | Pleos 공개 API | EvBattery.setEvChargePercentLimit |
| `EV_CHARGE_PORT_OPEN` | Pleos 공개 API | EvBattery.isEvChargePortOpen, EvBattery.registerEvChargePortOpen, EvBattery.setEvChargePor |
| `EV_CHARGE_SWITCH` | Pleos 공개 API | EvBattery.getEvChargeSwitchState, EvBattery.setEvChargeSwitch |
| `FRONT_FOG_LIGHTS_STATE` | Pleos 공개 API | Light.getCurrentFrontFogLightState, Light.registerFrontFogLightState, Light.unregisterFron |
| `FRONT_FOG_LIGHTS_SWITCH` | Pleos 공개 API | Light.getFrontFogLightSwitch, Light.setFrontFogLightSwitch |
| `FUEL_PERCENT` | Pleos 공개 API | Fuel.getFuelPercent, Fuel.registerFuelPercent, Fuel.unregisterFuelPercent |
| `GENERAL_SAFETY_REGULATION_COMPLIANCE` | AAOS 직접 |  |
| `GLOVE_BOX_LOCKED` | Pleos 공개 API | Door.getCurrentGloveBoxLockState, Door.registerGloveBoxLockState, Door.setGloveBoxLock, Do |
| `HIGH_BEAM_LIGHTS_STATE` | Pleos 공개 API | Light.getCurrentHighBeamLightState, Light.registerHighBeamLightState, Light.unregisterHigh |
| `HIGH_BEAM_LIGHTS_SWITCH` | Pleos 공개 API | Light.getHighBeamLightSwitch, Light.setHighBeamLightSwitch |
| `HVAC_AIR_CLEANER` | Pleos 공개 API | HVAC.getHvacAirCleanerState, HVAC.registerHvacAirCleanerState, HVAC.setHvacAirCleaner, HVA |
| `HVAC_AUTO_ON` | Pleos 공개 API | HVAC.getHvacAutoState, HVAC.registerHvacAutoState, HVAC.setHvacAuto, HVAC.unregisterHvacAu |
| `HVAC_AUTO_RECIRC_ON` | Pleos 공개 API | HVAC.getHvacAutoRecirculationState, HVAC.registerHvacAutoRecirculationState, HVAC.setHvacA |
| `HVAC_DEFROSTER` | Pleos 공개 API | HVAC.getHvacDefrosterState, HVAC.registerHvacDefrosterState, HVAC.setHvacDefroster, HVAC.u |
| `HVAC_DUAL_ON` | Pleos 공개 API | HVAC.getHvacDualState, HVAC.registerHvacDualState, HVAC.setHvacDual, HVAC.unregisterHvacDu |
| `HVAC_FAN_DIRECTION` | Pleos 공개 API | HVAC.getHvacFanDirection, HVAC.setHvacFanDirection |
| `HVAC_MAX_AC_ON` | Pleos 공개 API | HVAC.getHvacMaxAcState, HVAC.registerHvacMaxAcState, HVAC.setHvacMaxAc, HVAC.unregisterHva |
| `HVAC_MAX_DEFROST_ON` | Pleos 공개 API | HVAC.getHvacMaxDefrostState, HVAC.registerHvacMaxDefrostState, HVAC.setHvacMaxDefrost, HVA |
| `HVAC_SEAT_TEMPERATURE` | Pleos 공개 API | Seat.getHvacSeatTemperature, Seat.registerHvacSeatTemperature, Seat.setHvacSeatTemperature |
| `HVAC_SEAT_VENTILATION` | Pleos 공개 API | Seat.getHvacSeatVentilationState, Seat.registerHvacSeatVentilation, Seat.setHvacSeatVentil |
| `HVAC_SIDE_MIRROR_HEAT` | Pleos 공개 API | SideMirror.getHvacSideMirrorHeatState, SideMirror.registerHvacSideMirrorHeatState, SideMir |
| `HVAC_STEERING_WHEEL_HEAT` | Pleos 공개 API | SteeringWheel.getHvacSteeringWheelHeatState, SteeringWheel.registerHvacSteeringWheelHeatSt |
| `HVAC_TEMPERATURE_DISPLAY_UNITS` | Pleos 공개 API | Display.getCurrentTemperatureDisplayUnit, Display.registerTemperatureDisplayUnit, Display. |
| `HVAC_TEMPERATURE_VALUE_SUGGESTION` | Pleos 공개 API |  |
| `INFO_CRN` | Pleos 공개 API | CarInfo.getCRNInfo |
| `INFO_DRIVER_SEAT` | AAOS 직접 |  |
| `INFO_EV_CONNECTOR_TYPE` | AAOS 직접 |  |
| `INFO_EV_PORT_LOCATION` | AAOS 직접 |  |
| `INFO_FUEL_CAPACITY` | AAOS 직접 |  |
| `INFO_FUEL_DOOR_LOCATION` | AAOS 직접 |  |
| `INFO_MULTI_EV_PORT_LOCATIONS` | AAOS 직접 |  |
| `INFO_VIN` | Pleos 공개 API | CarInfo.getVINInfo |
| `INSTANTANEOUS_EV_EFFICIENCY` | Pleos 공개 API | EvBattery.getInstantaneousEvEfficiency, EvBattery.registerInstantaneousEvEfficiency, EvBat |
| `INSTANTANEOUS_FUEL_ECONOMY` | Pleos 공개 API | Fuel.getInstantaneousFuelEconomy, Fuel.registerInstantaneousFuelEconomy, Fuel.unregisterIn |
| `INTERIOR_LIGHTS_AMBIENT_BRIGHTNESS` | Pleos 공개 API | Light.getAmbientLightBrightness, Light.registerAmbientLightBrightnessListener, Light.setAm |
| `INTERIOR_LIGHTS_AMBIENT_BRIGHTNESS_COLOR` | Pleos 공개 API | Light.getAmbientLightBrightnessColor, Light.registerAmbientLightBrightnessColorListener, L |
| `INTERIOR_LIGHTS_AMBIENT_STATE` | Pleos 공개 API | Light.getAmbientLightState, Light.registerAmbientLightStateListener, Light.unregisterAmbie |
| `INTERIOR_LIGHTS_AMBIENT_SWITCH` | Pleos 공개 API | Light.getAmbientLightSwitch, Light.setAmbientLightSwitch |
| `LOW_BEAM_LIGHTS_STATE` | Pleos 공개 API | Light.getCurrentLowBeamLightState, Light.registerLowBeamLightState, Light.unregisterLowBea |
| `LOW_BEAM_LIGHTS_SWITCH` | Pleos 공개 API | Light.getLowBeamLightSwitch, Light.setLowBeamLightSwitch |
| `NIGHT_MODE` | AAOS 직접 |  |
| `PERF_STEERING_ANGLE` | Pleos 공개 API | SteeringWheel.getCurrentSteeringWheelAngle, SteeringWheel.registerSteeringWheelAngle, Stee |
| `PERF_VEHICLE_SPEED_DISPLAY` | AAOS 직접 |  |
| `POWER_DOWN_WARNING_INDICATOR` | Pleos 공개 API | Safety.hasPowerDownWarningIndicator, Safety.registerPowerDownWarningIndicator, Safety.unre |
| `RANGE_REMAINING` | Pleos 공개 API | Odometer.getRangeRemainingDistance, Odometer.registerRangeRemainingListener, Odometer.unre |
| `READING_LIGHTS_STATE` | Pleos 공개 API | Light.getCurrentReadingLightState, Light.registerReadingLightState, Light.unregisterReadin |
| `READING_LIGHTS_SWITCH` | Pleos 공개 API | Light.getReadingLightSwitch, Light.setReadingLightSwitch |
| `REAR_FOG_LIGHTS_STATE` | Pleos 공개 API | Light.getCurrentRearFogLightState, Light.registerRearFogLightState, Light.unregisterRearFo |
| `REAR_FOG_LIGHTS_SWITCH` | Pleos 공개 API | Light.getRearFogLightSwitch, Light.setRearFogLightSwitch |
| `SEAT_AIRBAGS_DEPLOYED` | Pleos 공개 API | Safety.hasSeatAirbagBeenDeployed, Safety.registerSeatAirbagDeployed, Safety.unregisterSeat |
| `SEAT_AIRBAG_ENABLED` | Pleos 공개 API | Safety.hasSeatAirbagEnabled, Safety.registerSeatAirbagEnabled, Safety.unregisterSeatAirbag |
| `SEAT_BACKREST_ANGLE_1_MOVE` | Pleos 공개 API | Seat.moveSeatBackrestAngle |
| `SEAT_BACKREST_ANGLE_1_POS` | Pleos 공개 API | Seat.getCurrentSeatBackrestAnglePosition, Seat.registerSeatBackrestAnglePosition, Seat.unr |
| `SEAT_BELT_BUCKLED` | Pleos 공개 API | Seat.getCurrentSeatBeltBuckledState, Seat.registerSeatBeltBuckledState, Seat.unregisterSea |
| `SEAT_BELT_HEIGHT_MOVE` | Pleos 공개 API | Seat.moveSeatBeltHeight |
| `SEAT_BELT_HEIGHT_POS` | Pleos 공개 API | Seat.getCurrentSeatBeltHeightPosition, Seat.registerSeatBeltHeightPosition, Seat.unregiste |
| `SEAT_BELT_NOT_BUCKLED_WARNING` | Pleos 공개 API | Safety.hasSeatBeltNotBuckledWarning, Safety.registerSeatBeltNotBuckledWarning, Safety.unre |
| `SEAT_DEPTH_MOVE` | Pleos 공개 API | Seat.moveSeatDepth |
| `SEAT_DEPTH_POS` | Pleos 공개 API | Seat.getCurrentSeatDepthPosition, Seat.registerSeatDepthPosition, Seat.unregisterSeatDepth |
| `SEAT_FORE_AFT_MOVE` | Pleos 공개 API | Seat.moveSeatForeAft |
| `SEAT_FORE_AFT_POS` | Pleos 공개 API | Seat.getCurrentSeatForeAftPosition, Seat.registerSeatForeAftPosition, Seat.unregisterSeatF |
| `SEAT_HEADREST_ANGLE_MOVE` | Pleos 공개 API | Seat.moveSeatHeadrestAngle |
| `SEAT_HEADREST_ANGLE_POS` | Pleos 공개 API | Seat.getCurrentSeatHeadrestAnglePosition, Seat.registerSeatHeadrestAnglePosition, Seat.unr |
| `SEAT_HEADREST_FORE_AFT_MOVE` | Pleos 공개 API | Seat.moveSeatHeadrestForeAft |
| `SEAT_HEADREST_FORE_AFT_POS` | Pleos 공개 API | Seat.getCurrentSeatHeadrestForeAftPosition, Seat.registerSeatHeadrestForeAftPosition, Seat |
| `SEAT_HEADREST_HEIGHT_MOVE` | Pleos 공개 API | Seat.moveSeatHeadrestHeight |
| `SEAT_HEADREST_HEIGHT_POS_V2` | Pleos 공개 API | Seat.getCurrentSeatHeadrestHeightAftPosition, Seat.registerSeatHeadrestHeightAftPosition,  |
| `SEAT_HEIGHT_MOVE` | Pleos 공개 API | Seat.moveSeatHeight |
| `SEAT_HEIGHT_POS` | Pleos 공개 API | Seat.getCurrentSeatHeightPosition, Seat.registerSeatHeightPosition, Seat.unregisterSeatHei |
| `SEAT_LUMBAR_FORE_AFT_MOVE` | Pleos 공개 API | Seat.moveSeatLumbarForeAft |
| `SEAT_LUMBAR_FORE_AFT_POS` | Pleos 공개 API | Seat.getCurrentSeatLumbarForeAftPosition, Seat.registerSeatLumbarForeAftPosition, Seat.unr |
| `SEAT_LUMBAR_SIDE_SUPPORT_MOVE` | Pleos 공개 API | Seat.moveSeatLumbarSideSupport |
| `SEAT_LUMBAR_SIDE_SUPPORT_POS` | Pleos 공개 API | Seat.getCurrentSeatLumbarSideSupportPosition, Seat.registerSeatLumbarSideSupportPosition,  |
| `SEAT_MEMORY_SELECT` | Pleos 공개 API | Seat.selectSeatMemory |
| `SEAT_OCCUPANCY` | Pleos 공개 API | Seat.getCurrentSeatOccupancy, Seat.registerSeatOccupancy, Seat.unregisterSeatOccupancy |
| `SEAT_TILT_MOVE` | Pleos 공개 API | Seat.moveSeatTilt |
| `SEAT_TILT_POS` | Pleos 공개 API | Seat.getCurrentSeatTiltPosition, Seat.registerSeatTiltPosition, Seat.unregisterSeatTiltPos |
| `SINCE_CHARGE_EV_EFFICIENCY` | Pleos 공개 API | EvBattery.getSinceChargeEvEfficiency, EvBattery.registerSinceChargeEvEfficiency, EvBattery |
| `SINCE_REFUEL_FUEL_ECONOMY` | Pleos 공개 API | Fuel.getSinceChargeFuelEconomy, Fuel.registerSinceChargeFuelEconomy, Fuel.unregisterSinceC |
| `SMART_GLASS_CONFIG` | Pleos 공개 API | Window.getSmartGlassConfig |
| `SMART_GLASS_LEVEL` | Pleos 공개 API | Window.getCurrentSmartGlassLevel, Window.registerSmartGlassLevel, Window.setSmartGlassLeve |
| `SMART_GLASS_ON` | Pleos 공개 API | Window.getCurrentSmartGlassState, Window.registerSmartGlassState, Window.setSmartGlassStat |
| `SMART_GLASS_ZONE_ON` | Pleos 공개 API | Window.setSmartGlassZoneState |
| `TAIL_LAMP_LIGHTS_STATE` | Pleos 공개 API | Light.getCurrentTailLampLightState, Light.registerTailLampLightState, Light.unregisterTail |
| `TAIL_LAMP_LIGHTS_SWITCH` | Pleos 공개 API | Light.getTailLampLightSwitch, Light.setTailLampLightSwitch |
| `TRANSMISSION_OIL_LEVEL` | Pleos 공개 API | Safety.getTransmissionOilLevelState, Safety.registerTransmissionOilLevelState, Safety.unre |
| `TRIP_DRIVING_DISTANCE` | Pleos 공개 API | Odometer.onFailed, Odometer.onTripDrivingDistanceUpdated |
| `TRIP_DRIVING_TIME` | Pleos 공개 API | Odometer.onFailed, Odometer.onTripDrivingTimeUpdated |
| `TRIP_EV_EFFICIENCY` | Pleos 공개 API | EvBattery.onFailed, EvBattery.onTripEvEfficiencyUpdated |
| `TURN_SIGNAL_STATE` | Pleos 공개 API | TurnSignal.getCurrentTurnSignalState, TurnSignal.registerTurnSignalState, TurnSignal.unreg |
| `VENDOR_GEAR_SELECTION` | Pleos 공개 API | Gear.getCurrentGearSelectionState, Gear.registerGearSelection, Gear.unregisterGearSelectio |
| `WASHER_FLUID_LOW` | Pleos 공개 API | Safety.hasLowWasherFluidWarning, Safety.registerLowWasherFluidWaringState, Safety.unregist |
| `WINDOW_BLIND_MOVE` | Pleos 공개 API | Window.moveWindowBlind |
| `WINDOW_BLIND_POS` | Pleos 공개 API | Window.getCurrentWindowBlindPosition, Window.registerWindowBlindPosition, Window.unregiste |
| `WINDOW_LOCK` | Pleos 공개 API | Window.getCurrentWindowLockState, Window.registerWindowLockState, Window.setWindowLock, Wi |
| `WINDOW_POS` | Pleos 공개 API | Window.getCurrentWindowPosition, Window.registerWindowPosition, Window.setWindowPosition,  |

### 3. 일반 권한을 선언하면 읽을 수 있는 신호 (19개)

| 속성 | 경로·이유 | Pleos SDK API |
|---|---|---|
| `CURRENT_GEAR` | AAOS 일반 권한 선언 필요: CAR_POWERTRAIN(normal) |  |
| `DISTANCE_DISPLAY_UNITS` | AAOS 일반 권한 선언 필요: READ_CAR_DISPLAY_UNITS(normal) |  |
| `EV_BATTERY_DISPLAY_UNITS` | AAOS 일반 권한 선언 필요: READ_CAR_DISPLAY_UNITS(normal) |  |
| `EV_BATTERY_INSTANTANEOUS_CHARGE_RATE` | AAOS 일반 권한 선언 필요: CAR_ENERGY(dangerous) |  |
| `EV_BRAKE_REGENERATION_LEVEL` | AAOS 일반 권한 선언 필요: CAR_POWERTRAIN(normal) |  |
| `EV_CHARGE_PORT_CONNECTED` | AAOS 일반 권한 선언 필요: CAR_ENERGY_PORTS(normal) |  |
| `EV_CHARGE_STATE` | AAOS 일반 권한 선언 필요: CAR_ENERGY(dangerous) |  |
| `EV_CHARGE_TIME_REMAINING` | AAOS 일반 권한 선언 필요: CAR_ENERGY(dangerous) |  |
| `EV_CURRENT_BATTERY_CAPACITY` | AAOS 일반 권한 선언 필요: CAR_ENERGY(dangerous) |  |
| `EV_REGENERATIVE_BRAKING_STATE` | AAOS 일반 권한 선언 필요: CAR_ENERGY(dangerous) |  |
| `EV_STOPPING_MODE` | AAOS 일반 권한 선언 필요: CAR_POWERTRAIN(normal) |  |
| `FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME` | AAOS 일반 권한 선언 필요: READ_CAR_DISPLAY_UNITS(normal) |  |
| `FUEL_LEVEL` | AAOS 일반 권한 선언 필요: CAR_ENERGY(dangerous) |  |
| `FUEL_LEVEL_LOW` | AAOS 일반 권한 선언 필요: CAR_ENERGY(dangerous) |  |
| `FUEL_VOLUME_DISPLAY_UNITS` | AAOS 일반 권한 선언 필요: READ_CAR_DISPLAY_UNITS(normal) |  |
| `PARKING_BRAKE_AUTO_APPLY` | AAOS 일반 권한 선언 필요: CAR_POWERTRAIN(normal) |  |
| `PARKING_BRAKE_ON` | AAOS 일반 권한 선언 필요: CAR_POWERTRAIN(normal) |  |
| `TIRE_PRESSURE_DISPLAY_UNITS` | AAOS 일반 권한 선언 필요: READ_CAR_DISPLAY_UNITS(normal) |  |
| `VEHICLE_SPEED_DISPLAY_UNITS` | AAOS 일반 권한 선언 필요: READ_CAR_DISPLAY_UNITS(normal) |  |

### 4. 일반 앱은 읽을 수 없는 신호 (240개)

| 속성 | 경로·이유 | Pleos SDK API |
|---|---|---|
| `ABS_ACTIVE` | 시스템 전용 권한: CAR_DYNAMICS_STATE(signature|privileged) · Pleos SDK에 API 없음 |  |
| `ABS_COLLISION` | Pleos SDK에 API 없음 |  |
| `ADAPTIVE_CRUISE_CONTROL_LEAD_VEHICLE_MEASURED_DISTANCE` | 시스템 전용 권한: READ_ADAS_STATES(signature|privileged) · Pleos SDK에 API 없음 |  |
| `ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP` | 시스템 전용 권한: READ_ADAS_STATES(signature|privileged) · Pleos SDK에 API 없음 |  |
| `ADAPTIVE_FRONT_LIGHTING_SYSTEM_STATE` | Pleos SDK에 API 없음 |  |
| `AD_COLLISION_WARNING_LEVEL` | Pleos SDK에 API 없음 |  |
| `AD_DISTANCE_LEVEL` | Pleos SDK에 API 없음 |  |
| `AD_FEEDBACK` | Pleos SDK에 API 없음 |  |
| `AD_LANE_CHANGE_STATE` | Pleos SDK에 API 없음 |  |
| `AD_PLANNING_MODE` | Pleos SDK에 API 없음 |  |
| `AD_SPEED_LIMIT_FOLLOW_ENABLED` | Pleos SDK에 API 없음 |  |
| `AD_SPEED_LIMIT_FOLLOW_STATE` | Pleos SDK에 API 없음 |  |
| `AD_TAKE_OVER_REQUEST_STATE` | Pleos SDK에 API 없음 |  |
| `AD_TARGET_SPEED_RELATIVE_OFFSET` | Pleos SDK에 API 없음 |  |
| `AFE_EXIT_WARNING_STATE` | Pleos SDK에 API 없음 |  |
| `AFS_COLLISION` | Pleos SDK에 API 없음 |  |
| `ANTI_LOCK_BRAKING_SYSTEM_STATE` | Pleos SDK에 API 없음 |  |
| `AUTOMATIC_EMERGENCY_BRAKING_ENABLED` | 시스템 전용 권한: READ_ADAS_SETTINGS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `AUTOMATIC_EMERGENCY_BRAKING_STATE` | 시스템 전용 권한: READ_ADAS_STATES(signature|privileged) · Pleos SDK에 API 없음 |  |
| `AUTO_EMERGENCY_SIGNAL_LIGHTS_SWITCH` | Pleos SDK에 API 없음 |  |
| `AUTO_HIGH_BEAM_LIGHTS_COLLISION` | Pleos SDK에 API 없음 |  |
| `AUTO_HIGH_BEAM_LIGHTS_STATE` | Pleos SDK에 API 없음 |  |
| `AUTO_HIGH_BEAM_LIGHTS_SWITCH` | Pleos SDK에 API 없음 |  |
| `AUTO_HOLD_ENABLED` | Pleos SDK에 API 없음 |  |
| `AUTO_HOLD_STATE` | Pleos SDK에 API 없음 |  |
| `AUTO_PARKING_STATE` | Pleos SDK에 API 없음 |  |
| `AUTO_TURNSIGNAL_LIGHTS_SWITCH` | Pleos SDK에 API 없음 |  |
| `BLACKBOX_MODE_ENABLED` | Pleos SDK에 API 없음 |  |
| `BLIND_SPOT_COLLISION_AVOIDANCE_ASSIST_ENABLED` | Pleos SDK에 API 없음 |  |
| `BLIND_SPOT_WARNING_ENABLED` | 시스템 전용 권한: READ_ADAS_SETTINGS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `BLIND_SPOT_WARNING_STATE` | 시스템 전용 권한: READ_ADAS_STATES(signature|privileged) · Pleos SDK에 API 없음 |  |
| `BRAKE_MALFUNCTION` | Pleos SDK에 API 없음 |  |
| `BUILTIN_CAM_MODE` | Pleos SDK에 API 없음 |  |
| `BUILTIN_CAM_VIEW_MODE` | Pleos SDK에 API 없음 |  |
| `CLUSTER_DISPLAY_STATE` | Pleos SDK에 API 없음 |  |
| `CLUSTER_NAVIGATION_STATE` | Pleos SDK에 API 없음 |  |
| `CLUSTER_REPORT_STATE` | Pleos SDK에 API 없음 |  |
| `CLUSTER_REQUEST_DISPLAY` | Pleos SDK에 API 없음 |  |
| `CLUSTER_SWITCH_UI` | Pleos SDK에 API 없음 |  |
| `CREATE_USER` | Pleos SDK에 API 없음 |  |
| `CRUISE_CONTROL_ENABLED` | 시스템 전용 권한: READ_ADAS_SETTINGS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `CRUISE_CONTROL_TYPE` | 시스템 전용 권한: READ_ADAS_STATES(signature|privileged) · Pleos SDK에 API 없음 |  |
| `CURRENT_EV_EFFICIENCY` | Pleos SDK에 API 없음 |  |
| `CURRENT_FUEL_ECONOMY` | Pleos SDK에 API 없음 |  |
| `CURRENT_POWER_POLICY` | Pleos SDK에 API 없음 |  |
| `DIESEL_GLOW_PLUG_ACTIVE` | Pleos SDK에 API 없음 |  |
| `DMS_DISTRACTION_WARNING` | Pleos SDK에 API 없음 |  |
| `DMS_DROWSINESS_WARNING` | Pleos SDK에 API 없음 |  |
| `DMS_MOBILEPHONE_USAGE_DETECTED` | Pleos SDK에 API 없음 |  |
| `DOOR_APPROACH_UNLOCK` | Pleos SDK에 API 없음 |  |
| `DOOR_AUTO_MODE` | Pleos SDK에 API 없음 |  |
| `DOOR_CHILD_LOCK_ENABLED` | 시스템 전용 권한: CONTROL_CAR_DOORS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `DOOR_LIGHTS_STATE` | Pleos SDK에 API 없음 |  |
| `DOOR_LIGHTS_SWITCH` | Pleos SDK에 API 없음 |  |
| `DOOR_MOVE` | 시스템 전용 권한: CONTROL_CAR_DOORS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `DOOR_WALK_AWAY_LOCK` | Pleos SDK에 API 없음 |  |
| `DOOR_WALK_AWAY_LOCK_ENABLED` | Pleos SDK에 API 없음 |  |
| `DPF_GPF_SYSTEM_STATE` | Pleos SDK에 API 없음 |  |
| `DRIVER_MONITORING_SYSTEM_ENABLED` | Pleos SDK에 API 없음 |  |
| `DRIVE_ACCELERATION_LEVEL` | Pleos SDK에 API 없음 |  |
| `DRIVE_STEERING_WEIGHT_LEVEL` | Pleos SDK에 API 없음 |  |
| `DRIVING_SOUND_EXTERNAL` | Pleos SDK에 API 없음 |  |
| `DRIVING_SOUND_EXTERNAL_TONE` | Pleos SDK에 API 없음 |  |
| `DRIVING_SOUND_INTERNAL` | Pleos SDK에 API 없음 |  |
| `DRIVING_SOUND_INTERNAL_TONE` | Pleos SDK에 API 없음 |  |
| `DRIVING_SOUND_MODE` | Pleos SDK에 API 없음 |  |
| `ELECTRONIC_PARKING_BRAKE_STATE` | Pleos SDK에 API 없음 |  |
| `ELECTRONIC_STABILITY_CONTROL_ENABLED` | Pleos SDK에 API 없음 |  |
| `ELECTRONIC_STABILITY_CONTROL_STATE` | Pleos SDK에 API 없음 |  |
| `EMERGENCY_CALL_MALFUNCTION` | Pleos SDK에 API 없음 |  |
| `EMERGENCY_LANE_KEEP_ASSIST_ENABLED` | 시스템 전용 권한: READ_ADAS_SETTINGS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `EMERGENCY_LANE_KEEP_ASSIST_STATE` | 시스템 전용 권한: READ_ADAS_STATES(signature|privileged) · Pleos SDK에 API 없음 |  |
| `ENGINE_STATE` | Pleos SDK에 API 없음 |  |
| `ESCORT_LIGHTS_ENABLED` | Pleos SDK에 API 없음 |  |
| `ESC_COLLISION` | Pleos SDK에 API 없음 |  |
| `ESC_OFF_WARNING` | Pleos SDK에 API 없음 |  |
| `EV_BATTERY_ECONOMY_LEVEL` | Pleos SDK에 API 없음 |  |
| `EV_BATTERY_LOW_TEMPERATURE_WARNING` | Pleos SDK에 API 없음 |  |
| `EV_BATTERY_LOW_VOLTAGE_WARNING` | Pleos SDK에 API 없음 |  |
| `EV_BATTERY_MANAGEMENT_SYSTEM_VERSION` | Pleos SDK에 API 없음 |  |
| `EV_BATTERY_MIN_TEMPERATURE` | Pleos SDK에 API 없음 |  |
| `EV_BATTERY_WINTER_MODE` | Pleos SDK에 API 없음 |  |
| `EV_CHARGE_CURRENT` | Pleos SDK에 API 없음 |  |
| `EV_CHARGE_INLET_LOCK` | Pleos SDK에 API 없음 |  |
| `EV_CHARGE_INLET_LOCK_MODE` | Pleos SDK에 API 없음 |  |
| `EV_CHARGE_PORT_LOCK` | Pleos SDK에 API 없음 |  |
| `EV_CHARGE_VOLTAGE` | Pleos SDK에 API 없음 |  |
| `EV_CHARGING_MODE` | Pleos SDK에 API 없음 |  |
| `EV_CHARGING_SPEED` | Pleos SDK에 API 없음 |  |
| `EV_CUMULATIVE_CHARGE_ENERGY` | Pleos SDK에 API 없음 |  |
| `EV_POWER_STATE` | Pleos SDK에 API 없음 |  |
| `EV_PRE_HEATING_STATE` | Pleos SDK에 API 없음 |  |
| `EV_REGENERATIVE_BRAKING_COLLISION` | Pleos SDK에 API 없음 |  |
| `EV_SYSTEM_STATE` | Pleos SDK에 API 없음 |  |
| `EXTERIOR_LIGHTS_MODE` | Pleos SDK에 API 없음 |  |
| `EXTERIOR_LIGHTS_SYSTEM_STATE` | Pleos SDK에 API 없음 |  |
| `FORWARD_COLLISION_WARNING_ENABLED` | 시스템 전용 권한: READ_ADAS_SETTINGS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `FORWARD_COLLISION_WARNING_STATE` | 시스템 전용 권한: READ_ADAS_STATES(signature|privileged) · Pleos SDK에 API 없음 |  |
| `GLOVE_BOX_DOOR_POS` | 시스템 전용 권한: CONTROL_GLOVE_BOX(signature|privileged) · Pleos SDK에 API 없음 |  |
| `GLOVE_BOX_LOCK` | Pleos SDK에 API 없음 |  |
| `HANDS_FREE_SMART_TRUNK_ENABLED` | Pleos SDK에 API 없음 |  |
| `HANDS_ON_DETECTION_DRIVER_STATE` | 시스템 전용 권한: READ_DRIVER_MONITORING_STATES(signature|privileged) · Pleos SDK에 API 없음 |  |
| `HANDS_ON_DETECTION_ENABLED` | 시스템 전용 권한: READ_DRIVER_MONITORING_SETTINGS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `HANDS_ON_DETECTION_WARNING` | 시스템 전용 권한: READ_DRIVER_MONITORING_STATES(signature|privileged) · Pleos SDK에 API 없음 |  |
| `HAZARD_LIGHTS_STATE` | 시스템 전용 권한: CAR_EXTERIOR_LIGHTS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `HAZARD_LIGHTS_SWITCH` | 시스템 전용 권한: CONTROL_CAR_EXTERIOR_LIGHTS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `HEADLIGHTS_STATE` | 시스템 전용 권한: CAR_EXTERIOR_LIGHTS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `HEADLIGHTS_SWITCH` | 시스템 전용 권한: CONTROL_CAR_EXTERIOR_LIGHTS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `HEAD_LAMP_COLLISION` | Pleos SDK에 API 없음 |  |
| `HUD_DISPLAY_BRIGHTNESS` | Pleos SDK에 API 없음 |  |
| `HUD_DISPLAY_INFO` | Pleos SDK에 API 없음 |  |
| `HUD_MODE` | Pleos SDK에 API 없음 |  |
| `HVAC_ACTUAL_FAN_SPEED_RPM` | 시스템 전용 권한: CONTROL_CAR_CLIMATE(signature|privileged) · Pleos SDK에 API 없음 |  |
| `HVAC_AIR_CONDITIONER_AUTO_DRY_MODE` | Pleos SDK에 API 없음 |  |
| `HVAC_AIR_VENT_ANGLE_POS` | Pleos SDK에 API 없음 |  |
| `HVAC_AIR_VENT_MODE` | Pleos SDK에 API 없음 |  |
| `HVAC_AUTO_LEVEL` | Pleos SDK에 API 없음 |  |
| `HVAC_AUTO_RECIRC_IN_TUNNEL` | Pleos SDK에 API 없음 |  |
| `HVAC_AUTO_RECIRC_UPON_AIR_POLLUTION` | Pleos SDK에 API 없음 |  |
| `HVAC_AUTO_RECIRC_UPON_WASHER_FLUID` | Pleos SDK에 API 없음 |  |
| `HVAC_CABIN_OVERHEAT_PROTECTION_MODE` | Pleos SDK에 API 없음 |  |
| `HVAC_ELECTRIC_DEFROSTER_ON` | 시스템 전용 권한: CONTROL_CAR_CLIMATE(signature|privileged) · Pleos SDK에 API 없음 |  |
| `HVAC_FAN_DIRECTION_AVAILABLE` | 시스템 전용 권한: CONTROL_CAR_CLIMATE(signature|privileged) · Pleos SDK에 API 없음 |  |
| `HVAC_REAR_CLIMATE_CONTROL_LOCK` | Pleos SDK에 API 없음 |  |
| `HYDROGEN_LEAKAGE_SYSTEM_STATE` | Pleos SDK에 API 없음 |  |
| `IMMOBILIZER_WARNING` | Pleos SDK에 API 없음 |  |
| `INFO_RELEASE_COUNTRY` | Pleos SDK에 API 없음 |  |
| `INFO_SERVICE_ENVIRONMENT` | Pleos SDK에 API 없음 |  |
| `INFO_USER_PROFILE` | Pleos SDK에 API 없음 |  |
| `INFO_VEHICLE_CELLULAR_SIGNAL_STRENGTH` | Pleos SDK에 API 없음 |  |
| `INITIAL_USER_INFO` | Pleos SDK에 API 없음 |  |
| `INSIDE_AIR_QUALITY_LEVEL` | Pleos SDK에 API 없음 |  |
| `INTERIOR_LIGHTS_AMBIENT_BRIGHTNESS_VISUALIZER` | Pleos SDK에 API 없음 |  |
| `INTERIOR_LIGHTS_DOME_BRIGHTNESS` | Pleos SDK에 API 없음 |  |
| `INTERIOR_LIGHTS_DOOR_BRIGHTNESS` | Pleos SDK에 API 없음 |  |
| `LANE_CENTERING_ASSIST_ENABLED` | 시스템 전용 권한: READ_ADAS_SETTINGS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `LANE_CENTERING_ASSIST_STATE` | 시스템 전용 권한: READ_ADAS_STATES(signature|privileged) · Pleos SDK에 API 없음 |  |
| `LANE_CHANGE_ASSIST_ENABLED` | Pleos SDK에 API 없음 |  |
| `LANE_CHANGE_ASSIST_TYPE` | Pleos SDK에 API 없음 |  |
| `LANE_CHANGE_STATE` | Pleos SDK에 API 없음 |  |
| `LANE_DEPARTURE_WARNING_ENABLED` | 시스템 전용 권한: READ_ADAS_SETTINGS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `LANE_DEPARTURE_WARNING_STATE` | 시스템 전용 권한: READ_ADAS_STATES(signature|privileged) · Pleos SDK에 API 없음 |  |
| `LANE_KEEP_ASSIST_ENABLED` | 시스템 전용 권한: READ_ADAS_SETTINGS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `LANE_KEEP_ASSIST_STATE` | 시스템 전용 권한: READ_ADAS_STATES(signature|privileged) · Pleos SDK에 API 없음 |  |
| `LIGHTS_FAULT_WARNING` | Pleos SDK에 API 없음 |  |
| `LIGHTS_SHOW_STATE` | Pleos SDK에 API 없음 |  |
| `LIGHTS_SHOW_SWITCH` | Pleos SDK에 API 없음 |  |
| `LIGHTS_WALK_AWAY_OFF` | Pleos SDK에 API 없음 |  |
| `LIGHTS_WALK_AWAY_OFF_ENABLED` | Pleos SDK에 API 없음 |  |
| `LIGHT_SHOW_ACTION` | Pleos SDK에 API 없음 |  |
| `LIGHT_SHOW_STATE` | Pleos SDK에 API 없음 |  |
| `LIGHT_SHOW_SWITCH` | Pleos SDK에 API 없음 |  |
| `LOCATION_CHARACTERIZATION` | 시스템 전용 권한: ACCESS_FINE_LOCATION(dangerous|instant) · Pleos SDK에 API 없음 |  |
| `MIRROR_AUTO_FOLD_ENABLED` | 시스템 전용 권한: CONTROL_CAR_MIRRORS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `MIRROR_AUTO_TILT_ENABLED` | 시스템 전용 권한: CONTROL_CAR_MIRRORS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `MIRROR_FOLD` | 시스템 전용 권한: CONTROL_CAR_MIRRORS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `MIRROR_Y_MOVE` | 시스템 전용 권한: CONTROL_CAR_MIRRORS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `MIRROR_Y_POS` | 시스템 전용 권한: CONTROL_CAR_MIRRORS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `MIRROR_Z_MOVE` | 시스템 전용 권한: CONTROL_CAR_MIRRORS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `MIRROR_Z_POS` | 시스템 전용 권한: CONTROL_CAR_MIRRORS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `MOTOR_POWER_STEERING_SYSTEM_STATE` | Pleos SDK에 API 없음 |  |
| `NFC_KEY_AUTH_STATUS` | Pleos SDK에 API 없음 |  |
| `OPTIMAL_TIRE_PRESSURE` | Pleos SDK에 API 없음 |  |
| `PARKING_BRAKE_AUTO_HOLD` | Pleos SDK에 API 없음 |  |
| `PARKING_BRAKE_AUTO_HOLD_STATE` | Pleos SDK에 API 없음 |  |
| `PARKING_BRAKE_MALFUNCTION` | Pleos SDK에 API 없음 |  |
| `PASSENGER_SEAT_AIRBAG_OFF` | Pleos SDK에 API 없음 |  |
| `PEPS_CONNECTED_USER_IDENTIFICATION` | Pleos SDK에 API 없음 |  |
| `PEPS_PAIRED_USER_IDENTIFICATION` | Pleos SDK에 API 없음 |  |
| `PEPS_USER_PAIRING_REQ` | Pleos SDK에 API 없음 |  |
| `PEPS_USER_STATE` | Pleos SDK에 API 없음 |  |
| `PERF_REAR_STEERING_ANGLE` | 시스템 전용 권한: READ_CAR_STEERING(signature|privileged) · Pleos SDK에 API 없음 |  |
| `PLAYING_AUDIO_SOUND_ATTR` | Pleos SDK에 API 없음 |  |
| `POWER_POLICY_GROUP_REQ` | Pleos SDK에 API 없음 |  |
| `POWER_POLICY_REQ` | Pleos SDK에 API 없음 |  |
| `REAR_VIEW_MIRROR_PRESENCE` | Pleos SDK에 API 없음 |  |
| `RECOMMENDED_TIRE_PRESSURE` | Pleos SDK에 API 없음 |  |
| `REGENERATIVE_BRAKING_SYSTEM_STATE` | Pleos SDK에 API 없음 |  |
| `REMOTE_PARKING_STATE` | Pleos SDK에 API 없음 |  |
| `REMOVE_USER` | Pleos SDK에 API 없음 |  |
| `SAFE_DRIVING_MODE_ENABLED` | Pleos SDK에 API 없음 |  |
| `SAFE_DRIVING_MODE_STATE` | Pleos SDK에 API 없음 |  |
| `SAFE_EXIT_ASSIST_ENABLED` | Pleos SDK에 API 없음 |  |
| `SAFE_EXIT_ASSIST_STATE` | Pleos SDK에 API 없음 |  |
| `SAFE_EXIT_WARNING_ENABLED` | Pleos SDK에 API 없음 |  |
| `SEAT_CENTER_CONSOLE_MOVE` | Pleos SDK에 API 없음 |  |
| `SEAT_CENTER_CONSOLE_POS` | Pleos SDK에 API 없음 |  |
| `SEAT_CUSHION_SIDE_SUPPORT_MOVE` | 시스템 전용 권한: CONTROL_CAR_SEATS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `SEAT_CUSHION_SIDE_SUPPORT_POS` | 시스템 전용 권한: CONTROL_CAR_SEATS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `SEAT_EASY_ACCESS_ENABLED` | 시스템 전용 권한: CONTROL_CAR_SEATS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `SEAT_FOOTWELL_LIGHTS_STATE` | 시스템 전용 권한: READ_CAR_INTERIOR_LIGHTS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `SEAT_FOOTWELL_LIGHTS_SWITCH` | 시스템 전용 권한: CONTROL_CAR_INTERIOR_LIGHTS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `SEAT_LUMBAR_VERTICAL_MOVE` | 시스템 전용 권한: CONTROL_CAR_SEATS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `SEAT_LUMBAR_VERTICAL_POS` | 시스템 전용 권한: CONTROL_CAR_SEATS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `SEAT_RELAX_SELECT` | Pleos SDK에 API 없음 |  |
| `SEAT_WALK_IN_POS` | 시스템 전용 권한: CONTROL_CAR_SEATS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `SENTRY_MODE` | Pleos SDK에 API 없음 |  |
| `SENTRY_MODE_STATE` | Pleos SDK에 API 없음 |  |
| `STEERING_CONTROL_INDICATOR_STATE` | Pleos SDK에 API 없음 |  |
| `STEERING_WHEEL_DEPTH_MOVE` | 시스템 전용 권한: CONTROL_STEERING_WHEEL(signature|privileged) · Pleos SDK에 API 없음 |  |
| `STEERING_WHEEL_DEPTH_POS` | 시스템 전용 권한: CONTROL_STEERING_WHEEL(signature|privileged) · Pleos SDK에 API 없음 |  |
| `STEERING_WHEEL_HEIGHT_MOVE` | 시스템 전용 권한: CONTROL_STEERING_WHEEL(signature|privileged) · Pleos SDK에 API 없음 |  |
| `STEERING_WHEEL_HEIGHT_POS` | 시스템 전용 권한: CONTROL_STEERING_WHEEL(signature|privileged) · Pleos SDK에 API 없음 |  |
| `STEERING_WHEEL_LIGHTS_STATE` | 시스템 전용 권한: READ_CAR_INTERIOR_LIGHTS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `STOP_LIGHTS_STATE` | Pleos SDK에 API 없음 |  |
| `SUNROOF_SUNSHADE_MOVE` | Pleos SDK에 API 없음 |  |
| `SUNROOF_SUNSHADE_POS` | Pleos SDK에 API 없음 |  |
| `SWITCH_USER` | Pleos SDK에 API 없음 |  |
| `TELLTALES` | Pleos 비공개 API(@hide·PRIVATE 권한) | Safety.getTelltalesInfo, Safety.registerTelltales, Safety.unregisterTelltales |
| `TIRE_PRESSURE_MONITORING_SYSTEM_STATE` | Pleos SDK에 API 없음 |  |
| `TRACTION_CONTROL_ACTIVE` | 시스템 전용 권한: CAR_DYNAMICS_STATE(signature|privileged) · Pleos SDK에 API 없음 |  |
| `TRAILER_PRESENT` | 시스템 전용 권한: PRIVILEGED_CAR_INFO(signature|privileged) · Pleos SDK에 API 없음 |  |
| `TRUNK_MOTION_OPEN_MODE` | Pleos SDK에 API 없음 |  |
| `TURN_SIGNAL_LIGHTS_STATE` | Pleos SDK에 API 없음 |  |
| `TURN_SIGNAL_MODE` | Pleos SDK에 API 없음 |  |
| `USER_IDENTIFICATION_ASSOCIATION` | Pleos SDK에 API 없음 |  |
| `USER_IDENTIFICATION_KEY` | Pleos SDK에 API 없음 |  |
| `VEHICLE_CURB_WEIGHT` | 시스템 전용 권한: PRIVILEGED_CAR_INFO(signature|privileged) · Pleos SDK에 API 없음 |  |
| `VEHICLE_IN_ENTERTAINMENT_MODE` | Pleos SDK에 API 없음 |  |
| `VEHICLE_PRESET_MODE` | Pleos SDK에 API 없음 |  |
| `VENDOR_SEAT_BACKREST_ANGLE_MOVE` | Pleos SDK에 API 없음 |  |
| `VENDOR_SEAT_BACKREST_ANGLE_POS` | Pleos SDK에 API 없음 |  |
| `VHAL_HEARTBEAT` | Pleos SDK에 API 없음 |  |
| `WASHER_FLUID_SPRAY_STATE` | Pleos SDK에 API 없음 |  |
| `WASHER_STATE` | Pleos SDK에 API 없음 |  |
| `WASHER_SWITCH` | Pleos SDK에 API 없음 |  |
| `WATCHDOG_ALIVE` | Pleos SDK에 API 없음 |  |
| `WATCHDOG_TERMINATED_PROCESS` | Pleos SDK에 API 없음 |  |
| `WELCOME_LIGHTS_DOOR_UNLOCK_ENABLED` | Pleos SDK에 API 없음 |  |
| `WELCOME_LIGHTS_DRIVER_APPROACH_ENABLED` | Pleos SDK에 API 없음 |  |
| `WELCOME_LIGHTS_ENABLED` | Pleos SDK에 API 없음 |  |
| `WELCOME_LIGHTS_SWITCH` | Pleos SDK에 API 없음 |  |
| `WELCOME_SIDE_MIRROR` | Pleos SDK에 API 없음 |  |
| `WELCOME_SIDE_MIRROR_ENABLED` | Pleos SDK에 API 없음 |  |
| `WHEEL_SLIP_START` | Pleos SDK에 API 없음 |  |
| `WHEEL_SLIP_START_ENABLED` | Pleos SDK에 API 없음 |  |
| `WINDOW_MOVE` | 시스템 전용 권한: CONTROL_CAR_WINDOWS(signature|privileged) · Pleos SDK에 API 없음 |  |
| `WINDOW_ROLLER_SHADE_MOVE` | Pleos SDK에 API 없음 |  |
| `WINDOW_ROLLER_SHADE_POS` | Pleos SDK에 API 없음 |  |
| `WINDSHIELD_WIPERS_PERIOD` | 시스템 전용 권한: READ_WINDSHIELD_WIPERS(signature|privileged) · Pleos SDK에 API 없음 |  |

### 5. 에뮬레이터에 없어 판단하지 못한 신호 (33개)

| 속성 | 경로·이유 | Pleos SDK API |
|---|---|---|
| `AP_POWER_BOOTUP_REASON` | 에뮬레이터 VHAL 미지원 |  |
| `AP_POWER_STATE_REPORT` | 에뮬레이터 VHAL 미지원 |  |
| `AP_POWER_STATE_REQ` | 에뮬레이터 VHAL 미지원 |  |
| `CRUISE_CONTROL_COMMAND` | 에뮬레이터 VHAL 미지원 |  |
| `DISPLAY_BRIGHTNESS` | 에뮬레이터 VHAL 미지원 |  |
| `ENGINE_COOLANT_TEMP` | 에뮬레이터 VHAL 미지원 · 필요 권한 CAR_ENGINE_DETAILED(signature|privileged)라 실차에서도 일반 앱 불가 |  |
| `ENGINE_IDLE_AUTO_STOP_ENABLED` | 에뮬레이터 VHAL 미지원 |  |
| `ENGINE_OIL_TEMP` | 에뮬레이터 VHAL 미지원 · 필요 권한 CAR_ENGINE_DETAILED(signature|privileged)라 실차에서도 일반 앱 불가 |  |
| `ENGINE_RPM` | 에뮬레이터 VHAL 미지원 · 필요 권한 CAR_ENGINE_DETAILED(signature|privileged)라 실차에서도 일반 앱 불가 |  |
| `EPOCH_TIME` | 에뮬레이터 VHAL 미지원 |  |
| `FOG_LIGHTS_STATE` | 에뮬레이터 VHAL 미지원 |  |
| `FOG_LIGHTS_SWITCH` | 에뮬레이터 VHAL 미지원 |  |
| `FUEL_DOOR_OPEN` | 에뮬레이터 VHAL 미지원 |  |
| `HW_KEY_INPUT` | 에뮬레이터 VHAL 미지원 |  |
| `IGNITION_STATE` | 에뮬레이터 VHAL 미지원 · 필요 권한 CAR_POWERTRAIN(normal)이라 실차에서는 읽을 수 있을 것으로 봄 |  |
| `INVALID` | 에뮬레이터 VHAL 미지원 |  |
| `LANE_CENTERING_ASSIST_COMMAND` | 에뮬레이터 VHAL 미지원 |  |
| `MIRROR_LOCK` | 에뮬레이터 VHAL 미지원 |  |
| `OBD2_FREEZE_FRAME` | 에뮬레이터 VHAL 미지원 |  |
| `OBD2_FREEZE_FRAME_CLEAR` | 에뮬레이터 VHAL 미지원 |  |
| `OBD2_FREEZE_FRAME_INFO` | 에뮬레이터 VHAL 미지원 |  |
| `OBD2_LIVE_FRAME` | 에뮬레이터 VHAL 미지원 |  |
| `PER_DISPLAY_BRIGHTNESS` | 에뮬레이터 VHAL 미지원 |  |
| `SEAT_BACKREST_ANGLE_2_MOVE` | 에뮬레이터 VHAL 미지원 |  |
| `SEAT_BACKREST_ANGLE_2_POS` | 에뮬레이터 VHAL 미지원 |  |
| `SEAT_HEADREST_HEIGHT_POS` | 에뮬레이터 VHAL 미지원 |  |
| `SEAT_MEMORY_SET` | 에뮬레이터 VHAL 미지원 |  |
| `STEERING_WHEEL_EASY_ACCESS_ENABLED` | 에뮬레이터 VHAL 미지원 |  |
| `STEERING_WHEEL_LIGHTS_SWITCH` | 에뮬레이터 VHAL 미지원 |  |
| `STEERING_WHEEL_LOCKED` | 에뮬레이터 VHAL 미지원 |  |
| `STEERING_WHEEL_THEFT_LOCK_ENABLED` | 에뮬레이터 VHAL 미지원 |  |
| `TIRE_PRESSURE` | 에뮬레이터 VHAL 미지원 · AAOS 권한 CAR_TIRES(signature|privileged), Pleos API는 @hide·PRIVATE_CAR_SAFETY라 실차에서도 일반 앱 불가 | Tire.getTirePressure, Tire.registerTirePressure, Tire.unregisterTirePressure |
| `VEHICLE_MAP_SERVICE` | 에뮬레이터 VHAL 미지원 |  |
