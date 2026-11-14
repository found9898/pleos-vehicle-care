# 차량 센서 기반 관리 서비스 참고문헌

2026년 10월 26일 대화에서 검토한 주요 출처를 정리했습니다. 논문 초록·공개 자료에서 확인한 범위와 확보한 원문을 구분합니다. 논문 성능 수치가 Pleos 앱에서 재현된 것은 아닙니다.

| 주제 | 출처 | 확인한 범위 및 보관 상태 |
| --- | --- | --- |
| 레인센서·와이퍼 마모 | [METHOD AND DEVICE TO ASSESS THE WEAR OF A WINDSHIELD WIPER UNIT, EP2709879B1](https://data.epo.org/publication-server/rest/v1.2/patents/EP2709879NWB1/document.html) | [특허 HTML 원문](originals/wiper-wear-EP2709879.html). 광학 센서의 부분 관측 한계와 다른 추정 접근을 설명. 특허 자체가 실험 정확도 검증 논문은 아님 |
| 와이퍼 내구 기준 | [한국소비자원 자동차 와이퍼 블레이드 비교시험](https://www.consumer.go.kr/user/ftc/consumer/cnsmrBBS/80/selectInfoCmprDetail.do?cntntsId=00000205&searchGbn=REGIST_DT&infoId=A0000922) | 공개 페이지 확인. RS R 0031:2003 준용, 왕복 20만 회 = 1년 또는 2만 km 상당. 표준 원문 미확보 |
| 와이퍼 작동 속도 | [EU Regulation 1008/2010 Annex III](https://legislation.gov.uk/eur/2010/1008/annex/III/division/1) | 법령 원문 확인. 1.1.5 작동 속도 범위 |
| 와이퍼 소리 기반 마모 | [US10308225B2 Accelerometer-based vehicle wiper blade monitoring (Ford)](https://patents.google.com/patent/US10308225B2/en) | 특허 원문 확인. 1~10kHz 대역, 2.5kHz 끽 소리, 수치 임계값은 없음 |
| 레인센서 원리 | [HELLA Rain sensor & headlight sensor testing](https://www.hella.com/techworld/en/technical/car-electronics-and-electrics/check-change-rain-sensor/) | [제조사 HTML 원문](originals/rain-sensor-hella.html). 광학 측정 원리, 센서 영역 및 오염 적응 설명 |
| 엔진오일 열화 | [Machine Learning Based Indirect Engine Oil Deterioration Monitoring System, SAE 2026-26-0656](https://saemobilus.sae.org/papers/machine-learning-based-indirect-engine-oil-deterioration-monitoring-system-2026-26-0656) | 공개 초록 확인. RPM·토크·기어·출력·냉각수 온도·주행거리 등과 오일 지표를 사용하는 접근. 논문 PDF 미확보 |
| OBD 기반 오일 수명 | [Vehicular engine oil service life characterization using On-Board Diagnostic (OBD) sensor data, IEEE SENSORS 2014](https://dspace.mit.edu/entities/publication/0a34c3ad-9a9a-4d49-b6f4-ca030e33984a) | MIT 공개 초록 확인. 냉각수 온도 상승률의 대리 지표 가능성과 시험 조건 한계. PDF 다운로드는 HTTP 429로 실패해 링크만 보관 |
| 엔진오일 운행 조건 가중치 | [Schwartz 외, Automatic engine oil change indicator system, US 4,742,476 (GM, 1988)](https://patents.google.com/patent/US4742476A/en) | [USPTO PDF 원문](originals/gm-oil-life-US4742476.pdf) 확인. SAE 870403 실차 자료 기반. 온화한 장거리 16,000km, 영하 단거리(평균 4.0km) 2,400~3,680km, 장시간 공회전·고부하 5,000~8,000km. 가혹 조건 정의는 영하에서 4마일(6.4km) 미만 운행. 거리 기준 가중치는 교체 거리의 역수에 비례. 공회전을 거리로 바꾸는 값은 없음 |
| 엔진오일 가혹 조건 | [현대 2026 투싼 취급설명서 가혹 조건](https://ownersmanual.hyundai.com/full_webhelp/NX4/2026/ko_KR/id7b77f1c5ffe.html) | [원문 HTML](../06_maintenance_guides/tucson-2026-severe.html) 보관. 엔진 오일 및 필터 매 5,000km 또는 6개월. 짧은 거리 반복, 과다한 공회전, 정체, 잦은 정지·출발, 한랭 지역 등을 조건으로 들지만 거리·시간 수치는 없음 |
| 브레이크 패드 마모·잔여 수명 | [Prediction of brake pad wear and remaining useful life considering varying vehicle mass and an experimental holistic approach, Wear 552-553 (2024) 205433](https://doi.org/10.1016/j.wear.2024.205433) | CC-BY 원문 PDF 확보(사용자가 DTU Orbit에서 받음, `originals/jensen-2024-wear-205433.pdf`). 앱 마모 추정의 주 근거: k ≈ 1.22×10⁻⁹ mm/J, 1,200kg 차량 1만 200km·실측 31회 |
| 브레이크 패드 마모 | [Estimation of brake pad wear and remaining useful life from fused sensor system, statistical data processing, and passenger car longitudinal dynamics, Wear 538-539 (2024) 205220](https://doi.org/10.1016/j.wear.2023.205220) | CC-BY 원문 PDF 확보(`originals/jensen-2024-wear-205220.pdf`). 1,580kg 차량, k 0.75~1.74×10⁻⁹ mm/J, 공기저항 면적·구름저항 값 |
| 브레이크 패드 | [Estimation of passenger car brake pad temperatures and wear from on-road data and lumped multi-physical models, Wear 2025](https://www.sciencedirect.com/science/article/pii/S0043164825004077) | 공개 초록 확인. 열·마모 모델과 실측 검증. 게시자 페이지 직접 다운로드는 앞선 확인에서 403. 논문 PDF 미확보 |
| 타이어 마모 | [Estimating tyre wear for real-time applications: a cascaded Kalman filter approach, 2026](https://www.tandfonline.com/doi/full/10.1080/00423114.2026.2730699) | 공개 초록 확인. 차량 동역학 관측기 및 실측 마모 보정. 게시자 페이지 직접 다운로드는 앞선 확인에서 403. 논문 PDF 미확보 |
| 공조 고장 진단 | [Fault Detection and Isolation of automotive Air Conditioning systems using first principle models, 2015](https://www.sciencedirect.com/science/article/pii/S096706611500115X) | 공개 초록 확인. 냉매 사이클 모델을 활용한 고장 탐지·분리. 논문 PDF 미확보 |
| 냉매 누설률 | [Schwarz & Harnisch, Establishing the Leakage Rates of Mobile Air Conditioners, 2003](https://climate.ec.europa.eu/system/files/2016-11/leakage_rates_final_report_en.pdf) | EU 공개 원문 확인. 300대 실측, 평균 52.4 g/년(6.9%), 제조사별 5.3~10.6%/년 |
| 실내 필터 성능 | [Effects of Vehicle Cabin Filter Efficiency on Ultrafine Particle Concentration Ratios Measured In-Cabin and On-Roadway](https://www.tandfonline.com/doi/full/10.1080/02786826.2010.531792) | 공개 초록 확인. 풍량·사용 조건·필터 압력 손실과 여과 성능. 논문 PDF 미확보 |
| 차량 실내 공기질 | [Vehicle cabin air quality: influence of air recirculation on energy use, particles, and CO2, 2023](https://pmc.ncbi.nlm.nih.gov/articles/PMC10076388/) | [공개 HTML 원문](originals/cabin-air-quality-2023.html). 내외기 모드와 공기질·에너지 사용의 관계. 필터 500시간 외기 노화 = 교체 시점(약 1년), 걸러냄 효율 새 필터·노화 필터 표(부록 표 8) |

각 자료의 실제 다운로드 결과는 [download-status.json](download-status.json)에 기록했습니다. 접근 제한이나 유료 구독을 우회하지 않았습니다.
