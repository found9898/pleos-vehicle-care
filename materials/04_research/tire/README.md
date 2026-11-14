# 타이어 마모 모델 근거 자료

접근일은 전부 2026-11-02. 파일 접두어는 주제다. 모델에 넣을 수치와 등급은 `model-inputs.md`에 있다.

| 접두어 | 주제 |
|---|---|
| A | 마모 법칙, 앱이 인용하는 SUV 2대 연구 |
| B | 도로 마모율, 트레드 깊이 감소, R117 마모 시험, 하중·EV |
| C | 구동 방식별 앞뒤 분담 |
| D | 공기압, 누설, TPMS |
| E | 노화 |
| F | 신품 깊이, OE 타이어, 한계 깊이 |
| G | 조향비, 자전거 모델, 자연 주행 횡가속 |
| H | 특허 |

다운로드 파일은 하나도 실행하지 않았다. 텍스트 추출과 계산은 이 폴더 밖 스크립트(`scratchpad/tirescripts/`, `python -I`)로 했고 추출 텍스트는 `scratchpad/tire_txt/`에 있다. 현대 취급설명서 타이어 페이지는 이미 `../../07_model_specs/ownersmanual/<차종>_2026/tire-*.html`에 있으므로 다시 받지 않았다. 접근 제한이나 유료 구독을 우회하지 않았다.

신뢰도 구분: 동료심사, 학위논문, 규제(UN·미국 연방관보·NHTSA·인도 AIS·한국 법령), 시험기관(ADAC), OEM(현대 문서), 타이어사, 산업(시험 회사), 특허, 2차(소매점, 제3자 호스팅, 재인용).

## A. 마모 법칙

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| A_Giechaskiel2024_Lubricants12-338_tyre_wear_2SUV.pdf | https://mdpi-res.com/d_attachment/lubricants/lubricants-12-00338/article_deploy/lubricants-12-00338.pdf | 앱이 인용하는 연구. FWD SUV 2대(1,494 / 1,430 kg), 225/60R18, 하중지수 51%, 2.4 bar. 구간 22 ~ 123 mg/km, 20 °C 보정 63 / 60 mg/km. 기온 2.7 %/°C(9 ~ 29 °C). √(σx²+σy²)에 원점 통과 기울기 60, 70 (R² 0.38, 0.70). 트레드 도심 1.6 ~ 2.3, 고속도로 0.3 ~ 0.4, 평균 0.8 ~ 1.4 mm/10,000 km. 질량 기준 앞 몫 약 65%. 표 9에 R117 범위 0.35 ~ 0.55 / 0.83 ~ 1.03 기재 | 동료심사, OA |
| A_arXiv2503.19494_Rubber_wear_history_mechanisms_review.pdf | https://arxiv.org/pdf/2503.19494 | Grosch·Schallamach 법칙 V/L = K·μF₀/U(마찰 에너지 비례). 압력 지수 n > 1 보고와 0.43 MPa까지 선형이라는 저자 실험 | 프리프린트 리뷰(Persson 그룹, Linglong 소속 포함) |
| A_Lubricants2021_9-113_block_level_tread_wear_frictional_energy.pdf | https://mdpi-res.com/d_attachment/lubricants/lubricants-09-00113/article_deploy/lubricants-09-00113.pdf | 블록 단위 마찰에너지율 기반 마모 모델(Archard·Schallamach 확장), 선형·비선형 관계 비교 | 동료심사, OA. 앱 계수로는 쓰지 않음 |

## B. 도로 마모율, R117, 하중·EV

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| B_Giechaskiel2025_Vehicles7-91_C1_tyre_wear_convoy_tread_depth.pdf | https://mdpi-res.com/d_attachment/vehicles/vehicles-07-00091/article_deploy/vehicles-07-00091.pdf | R117 Annex 10 컨보이(DEKRA), 15종, 8,000 ~ 10,000 km, σ 0.44 ~ 0.45 / 0.94 ~ 0.96. 여름 앞 1.47, 뒤 0.64 mm/10,000 km, 겨울 1.73 / 0.73. 54 ~ 180 mg/km, 35 ~ 85 mg/km/t. EV 43.3 대 ICE 42.0 ~ 45.4 mg/km/t. 하중 거의 선형. 앞뒤 약 2.4. g/mm 측정 191 ~ 380(표 A2). 10 °C 낮아지면 약 −14 mg/km/t | 동료심사, OA (JRC) |
| B_Giechaskiel2025_Vehicles7-29_tread_depth_service_life.pdf | https://mdpi-res.com/d_attachment/vehicles/vehicles-07-00029/article_deploy/vehicles-07-00029.pdf | 앞 1 ~ 1.2, 뒤 0.5 ~ 0.6, 네 바퀴 0.78 ~ 1.16 mm/10,000 km. 250 g/mm(205/55R16). ADAC 40,000 ± 10,000 km, FWD 앞 37,000 / 뒤 64,000 km. 마일 ≈ 100 × UTQG. 앞뒤 2.0(여름) 2.6(겨울). 정렬 불량 약 3 mm/10,000 km. 두 경로 1.6배. 부록 A 식(지름 감소 표기 오류로 보임, model-inputs a-5) | 동료심사, OA (JRC) |
| B_Giechaskiel2024_Sustainability16-522_tyre_wear_review.pdf | https://mdpi-res.com/d_attachment/sustainability/sustainability-16-00522/article_deploy/sustainability-16-00522.pdf | 110 mg/km/대(n = 303), 68 mg/km/t. 수명 중 질량 10%(0.6 ~ 1.5 kg). FWD 앞 몫 57 ~ 85%(문헌별), RWD 뒤 71%. 정렬 불량 193 대 86 mg/km. 국내 시험 가솔린 158 / 디젤 168 / EV 202 mg/km, 20% 무거운 EV +25 ~ 30%. 횡가속 영향 "very high" | 동료심사 리뷰, OA |
| B_UNECE_R117_Rev6_Amend2_2025_Annex10_abrasion_g2503171.pdf | https://documents.un.org/doc/undoc/gen/g25/031/71/pdf/g2503171.pdf | 1.6.13.3.1 σ_long 0.45 ± 10%, σ_lat 0.93 ± 10%. 고속도로형 >35% 거리 0.10 ~ 0.45 / 0.15 ~ 1.00, 도심형 >25% 0.45 ~ 0.90 / 0.40 ~ 1.20. 속도 분포, 하중 67 ± 7%, FWD 56/44, RWD·AWD 50/50, 토·캠버 0 ± 0.1°. 부록 1 가속도 계산(Butterworth 1 Hz, 이동평균 1 s / 2 s, 7 km/h 미만 제외, 1 m 거리 기준). 기준 타이어 25 ~ 75 mg/km/t | 규제(UN, 2025-01-10 발효 통합본) |
| B_Kole2017_IJERPH14-1265_tyre_wear_emissions.pdf | https://mdpi-res.com/d_attachment/ijerph/ijerph-14-01265/article_deploy/ijerph-14-01265.pdf | 밀도 1.15 ~ 1.18. 네덜란드 도심 132, 시골 85, 고속도로 104 mg/km. 10 kg 타이어가 50,000 km에 약 1.5 kg 손실. EV 16 ~ 23% 무거움, 선형 가정 약 20% 증가 | 동료심사, OA |
| B_Krishnappa_CMU_UTC2018_tire_tread_inspection_final_report.pdf | https://ppms.cit.cmu.edu/media/project_files/16_-_UTC_Tire_Tread_Final_Report.pdf | 펜실베이니아 검사 약 115만 건. 0.3 ~ 0.4/32" per 1,000 mi(1.48 ~ 1.97 mm/10,000 km), 요약의 예비값 0.2/32" | 연구 보고서(US DOT UTC) |
| B_ADAC_Reifenabrieb_Mikroplastik_page.html | https://www.adac.de/rund-ums-fahrzeug/ausstattung-technik-zubehoer/reifen/reifenkauf/reifenabrieb-mikroplastik/ | 160종, 브랜드 평균 52 ~ 82 mg/km/t(Hankook 62, Kumho 70). 수명 중 최대 1 kg. 무게와 "weitgehend linear". 15,000 km 시험 | 시험기관 |
| B_EmissionsAnalytics2022_tires_webinar_Apr2022.pdf | https://www.emissionsanalytics.com/s/Emissions_Analytics_tires_webinar_20_April_2022.pdf | 신품 73 mg/km에서 로그 감소, 수명 평균 37 mg/km. +500 kg에 +21% | 산업(시험 회사) |
| B_EmissionsAnalytics2023_AutomotiveTireTechnology_Jan2023.pdf | https://www.emissionsanalytics.com/s/Emissions-Analytics-Automotive-Tire-Technology-January-2023-v2.pdf | 18종, 고속도로 90%, 평균 67 mg/km(38 ~ 161), 약 10배 차 | 산업 |
| B_Pohrt2019_FUMechEng17-17_tire_wear_hot_spots.pdf | https://casopisi.junis.ni.ac.rs/index.php/FUMechEng/article/download/4780/2923 | 마모 ∝ 힘²(종·횡), K_lat ≈ 7 K_long(Veith 인용), 노면에 따라 지수 3 이상. 도심 5% 거리가 63% 마모. 뉘르부르크링 10배 초과. 겨울 1.6 ~ 1.7배(Le Maître), 1 °C당 1 ~ 4% 증가, 운전자 6.2배, 신품 +10%, 1.4 kg / 50,000 km | 동료심사 리뷰 |
| B_Schlaefle2023_Atmosphere14-1780_long_lat_forces_TRPM.pdf | https://mdpi-res.com/d_attachment/atmosphere/atmosphere-14-01780/article_deploy/atmosphere-14-01780.pdf | 드럼 255/40R20. PM이 힘의 4차식, 횡력이 종력보다 약 4배. 자유 구름 대비 최대 28배(종), 109배(횡) | 동료심사, OA, PM 기준 |
| B_Schlaefle2024_Atmosphere15-502_load_pressure_speed_TRPM.pdf | https://mdpi-res.com/d_attachment/atmosphere/atmosphere-15-00502/article_deploy/atmosphere-15-00502.pdf | 하중 +25%에 PM +14 ~ 69%. 하중·공기압에 선형, 속도 효과 불분명 | 동료심사, OA, PM 기준 |

초록만 확인(파일 없음): Liu 2022 Sci Total Environ 842:156950 (택시 76대, 하이브리드 72 대 ICE 53 mg/km, 겨울 타이어 160, 앞이 뒤의 1.7배), Lee·Ju·Kim 2020 Waste Manag 108:154 (국내 승용 45 ~ 57, 평균 51.1 mg/km), Woo 2022 Sci Total Environ 842:156961 (초록은 PM10만, 마모 수치는 리뷰 재인용), Das 2024 J. Eng. 10.1155/2024/5723254 (토인 0 ~ 4.2°, 수명 최대 98% 감소, 원문 403).

## C. 구동 방식별 분담

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| C_EmissionsAnalytics2021_whats_in_a_tyre_RWD_rear_share.html | https://www.emissionsanalytics.com/news/whats-in-a-tyre | 2012 Mercedes C클래스 RWD, 8종, 1,000마일 이상, 고속도로 약 90%. 평균 64 mg/km, 뒤 71%, 브랜드 간 2.3배 | 산업 |

## D. 공기압, 누설, TPMS

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| D_FR_2002-06-05_TPMS_final_rule_phase1.pdf | https://www.govinfo.gov/content/pkg/FR-2002-06-05/pdf/02-13915.pdf | 월 약 1 psi 손실, 10 °F당 1 psi. Goodyear 35에서 17 psi로 수명 68%, 1 psi당 1.78%, 평균 45,000 mi. RMA 월 1 ~ 2 psi. 느린 누설 85%. 월 1회 점검 29% | 규제(미국 연방관보) |
| D_FR_2005-04-08_FMVSS138_final_rule.pdf | https://www.govinfo.gov/content/pkg/FR-2005-04-08/pdf/05-6741.pdf | 25% 저압 경고, 20분 이내. 설명서 문구 "매월 냉간 점검". 1.78 %/psi 재인용, TPMS로 740 ~ 900 mi 증가 | 규제 |
| D_NHTSA_FMVSS138_FRIA_2005.pdf | https://www.nhtsa.gov/sites/nhtsa.gov/files/fmvss/TPMS-2005-FMVSS-No138.pdf | 월 1 psi, 25% 문턱이면 승용 평균 7.5개월마다 경고. 승용 26%가 25% 이상 저압 타이어 보유, 평균 6.8 psi | 규제 분석(NHTSA) |
| D_NHTSA_DOT-HS-809-317_TPSS_vehicle_observation.pdf | https://crashstats.nhtsa.dot.gov/Api/Public/ViewPublication/809317 | 11,530대, 승용 27%가 8 psi 이상 저압 1개 이상, 차령별 8 / 11 / 15% | 정부 조사 |
| D_NHTSA_DOT-HS-809-316_TPSS_interview.pdf | https://crashstats.nhtsa.dot.gov/Api/Public/ViewPublication/809316 | 점검 빈도: 매주 9%, 매월 21%, 정비 때 30%, 안 함 7% | 정부 조사 |
| D_NHTSA_DOT-HS-809-359_TPSS_tread_depth.pdf | https://crashstats.nhtsa.dot.gov/Api/Public/ViewPublication/809359 | 9%가 2/32" 이하 타이어 보유 | 정부 조사 |
| D_NHTSA_DOT-HS-811-086_tire_pressure_maintenance_2009.pdf | https://crashstats.nhtsa.dot.gov/Api/Public/ViewPublication/811086 | TPMS 차 57% 적정 대 43%, 평균 저압 14% 대 16% | 정부 조사 |
| D_NHTSA_DOT-HS-811-681_TPMS_effectiveness_2012.pdf | https://crashstats.nhtsa.dot.gov/Api/Public/ViewPublication/811681 | 6,103대. 0.1 psi/°F 보정. 71.1%가 1 psi 이상 저압, 12.4%가 25% 초과. 가장 낮은 타이어 평균 −6.63%. TPMS 효과 55.6% | 정부 조사 |
| D_Bridgestone_US_maintenance_safety_manual_2024.pdf | https://firestonetire.ca/content/dam/consumer/bst/na/warranties/bs_maintenance_and_safety_us_en_2024v2.pdf | 월 1 psi(7 kPa), 10 °F(5.6 °C)당 1 psi, 월 2 psi 넘으면 점검 | 타이어사 |
| D_Bridgestone_Americas_tire_inflation.html | https://bridgestoneamericas.com/en/company/safety/maintaining-tires/tire-inflation | 월 약 1 psi, 10 °F당 1 psi | 타이어사 |
| D_Michelin_CA_wear_on_both_edges.html | https://www.michelin.ca/en/auto/learn/tire-damage/wear-problems/symptom-wear-on-both-edges | 20% 저압이면 수명 20% 감소, 어깨 마모, 연비 1 ~ 2% | 타이어사 |
| D_Michelin_UK_TyreAcademy_benefits_maintenance.html | https://marketing.michelin.co.uk/tyre-academy/level2-1-benefits-of-tyre-maintenance.htm | 20% 저압이면 마일리지 20% 감소(25,000 중 5,000) | 타이어사 교육 페이지 |
| D_India_AIS-110_2013_TPMS_aligned_UNR64.pdf | https://law.resource.org/pub/in/bis/ais/arai.in.ais.110.2013.pdf | R64 정렬: 20% 또는 150 kPa 중 높은 값, 펑크 10분, 확산 60분, 40 km/h 이상 | 규제(인도, R64 원문 대신) |
| D_KR_자동차규칙_제12조의2_TPMS_lawgokr.html | https://www.law.go.kr/LSW//lsSideInfoP.do?lsiSeq=288083&joNo=0012&joBrNo=02&docCls=jo&urlMode=lsScJoRltInfoR | TPMS 의무, 40 km/h ~ 최고속도 작동. % 문턱 없음 | 규제(한국) |
| D_ASTM_F1112-06a_R18_abstract.html | https://store.astm.org/f1112-06ar18e01.html | 공기압 유지 시험 범위만, 수치 없음 | 표준 초록 |

## E. 노화

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| E_NHTSA_tire_aging_summary_slides.pdf | https://www.safetyresearch.net/Library/NHTSA_FINAL.pdf | 65 °C 5주 ≈ 피닉스 4년. 2005 ~ 07 노화 관련 사망 90, 부상 3,200 이상. 고온 지역 집중 | 정부 발표(제3자 호스팅) |
| E_NHTSA_DOT-HS-811-885_tire_aging_phase2.pdf | https://www.nhtsa.gov/sites/nhtsa.gov/files/811885_tireagingtestdevelopmentprojectphase2evallab.pdf | 가장 가혹한 오븐 노화 = 피닉스 3 ~ 6년 이상, 65 °C 8주 = 3 ~ 4년. 2004 평균 사용 3.6년, 트레드 수명 44,700 mi | 정부 보고서 |
| E_NHTSA_MacIsaac_SAE2010_tire_aging.pdf | https://www.nhtsa.gov/sites/nhtsa.gov/files/macisaac_sae2010.pdf | 오븐 55 ~ 70 °C, 3 ~ 12주. 타이어 고장 연 약 400명 사망 | 정부 논문 |
| E_NHTSA_MacIsaac_tireaging_slides.pdf | https://www.nhtsa.gov/sites/nhtsa.dot.gov/files/tireaging_macisaac.pdf | 피닉스 연평균 22.7 °C, 2003 트레드 수명 46,300 mi / 3.8년 | 정부 발표 |
| E_JATMA_long-term_use_5y_10y.pdf | https://www.jatma.or.jp/tekisei/pdf/long-term.pdf | 사용 5년 이후 점검, 제조 10년 교체 권장(만료일 아님) | 산업 표준(일본) |
| E_JATMA_ridingsafely.html | https://www.jatma.or.jp/tyre_user/ridingsafely.html | 같은 내용, 제조 연월 읽는 법 | 산업 표준 |
| E_Michelin_NA_TireServiceLife_bulletin.pdf | https://www.tirerack.com/images/pdf/MI_TireServiceLife.pdf | 5년 이후 매년 점검, 10년 교체, 자동차사 권고가 더 짧으면 따름 | 타이어사(소매점 호스팅) |
| E_Michelin_US_when_do_i_need_new_tires.html | https://www.michelinman.com/auto/auto-tips-and-advice/tire-buying-guide/when-do-i-need-new-tires | 5 / 10년, 마모 표시 2/32" | 타이어사 |
| E_Bridgestone_BFNA_TechBulletin_P-05-10_service_life.pdf | https://www.tirerack.com/images/pdf/BFNA_TireServiceLife.pdf | JATMA 5 / 10년 채택, 매월 공기압, 5,000 mi 교환 | 타이어사(소매점 호스팅) |
| E_Yokohama_TireServiceLife.pdf | https://tirerack.com/images/pdf/YO_TireServiceLife.pdf | 5년 이후 매년 점검, 10년 교체 | 타이어사(소매점 호스팅) |
| E_Continental_KR_replacing_tires.html | https://www.continental-tires.com/kr/ko/tire-knowledge/replacing-tires | 10년 이상 교체(예비 포함), 1.6 mm | 타이어사 |

현대 설명서(아이오닉 5 `NE1_2026/tire-care.html`, 캐스퍼 `AX_2026/tire-care.html`, 로컬): 제조 6년 경과 시 교체, 1.6 mm(일반) / 2.4 mm(고속도로), 주행 직후 4 ~ 6 psi 높음, DOT WWYY. OEM [D].

## F. 신품 깊이, OE 타이어, 한계 깊이

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| F_KumhoUSA_original_equipment_list.html | https://www.kumhotireusa.com/tire-basics/original-equipment/ | 미국 OE: 투싼 HP71 9.3 ~ 9.4/32", UTQG 500 ~ 540. 아반떼 TA31 9.0/32", KU50 10/32". 쏘나타 HEV TA31+ 9.7/32". 싼타페 HP71 255/45R20 9.3/32" (640). 아이오닉 5 HP71ev 9.3/32" | 타이어사 |
| F_KumhoUSA_CrugenHP71_specs.html | https://www.kumhotireusa.com/tires/crugen-hp71 | HP71 규격별 깊이·UTQG | 타이어사 |
| F_Michelin_US_PrimacyTourAS_product.html | https://www.michelinman.com/auto/tires/michelin-primacy-tour-a-s | 현대 전용(HN) 235/55R19, 255/45R20: 9/32" 또는 8.5/32" | 타이어사 |
| F_Continental_press_Tucson_NX4_OE_automotiveworld.html | https://www.automotiveworld.com/news-releases/hyundai-relies-on-continentals-original-equipment-expertise-for-its-tucson/ | 투싼 NX4 CrossContact LX Sport OE 승인(2024-05-07) | 보도자료 재게시 |
| F_KalTire_CrossContactLXSport_376842.html | https://www.kaltire.com/en/tires/crosscontact-lx-sport/376842.html | 235/60R18 10/32", UTQG 480 A A | 2차(소매점) |
| F_Continental_KR_tread_depth.html | https://www.continental-tires.com/kr/ko/tire-knowledge/tread-depth/ | 신품 평균 8 ~ 9 mm, 1.6 mm 미만 그립 부족 | 타이어사 |
| F_Hankook_KR_press_172144_summer_inspection.html | https://www.hankooktire.com/global/ko/company/media-list/media-detail.172144.html | 581대 중 40% 공기압 이상, 15% 1.6 mm 미만. 젖은 노면 100 km/h 1.6 mm는 새 7 mm의 약 2배 제동거리. 월 1회 공기압 | 타이어사 |
| F_Hankook_KR_press_171264_spring_tire_care.html | https://www.hankooktire.com/global/ko/company/media-list/media-detail.171264.html | 약 3 mm 교체 권장, 과압 시 중앙 조기 마모 | 타이어사 |
| F_Hankook_KR_press_600186.html | https://www.hankooktire.com/kr/ko/company/media-list/media-detail.600186.html | 같은 마모에서 젖은 노면 제동거리 1.5배 이상 | 타이어사 |
| F_Hankook_KR_care_guide_tread_wear.html | https://www.hankooktire.com/kr/ko/help-support/care-guide/tread-wear.html | 2 mm 이하 교체 | 타이어사 |
| F_JATMA_tyre_select01_2023_passenger.pdf | https://www.jatma.or.jp/docs/publications/tyre_select01_2023.pdf | 자연 누설 월 5% 정도, 최소 월 1회 조정. 법정 1.6 mm. 그림 9 마모 깊이별 젖은 노면 제동거리 | 산업 표준(일본) |

## G. 조향, 자전거 모델, 횡가속

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| G_hyundainews_2023_Tucson_NX4_specs.pdf, G_hyundainews_2024_Tucson_NX4_specs.pdf, G_hyundainews_2025_Tucson_NX4_specs.pdf | https://www.hyundainews.com/assets/documents/original/53078-2023TucsonProductSpecifications20221114.pdf, …/56243-2024TucsonSpecs061223.pdf, …/62995-2025TucsonSpecsandFeatures20240809.pdf | 13.7:1, 2.41회전, 38.6 ft 회전 지름 | OEM(미국) |
| G_hyundainews_2021_Elantra_CN7_specs.pdf, G_hyundainews_2026_Elantra_CN7_specs.pdf | …/44040-2021ElantraProductGuideSpecs.pdf, …/67686-2026ElantraSpecsFeatures20250702.pdf | 12.9:1(N Line 12.2), 2.48 ~ 2.5회전, 반경 17.72 ft | OEM(미국) |
| G_hyundainews_2024_Sonata_DN8_specs.pdf, G_hyundainews_2026_Sonata_DN8_specs.pdf, G_hyundainews_2020_SonataHEV_DN8_specs.pdf | …/57218-2024SonataSpecs092123.pdf, …/68169-2026SonataSpecsFeatures20250722A.pdf, …/40214-2020SonataHEVProductGuideSpecsSCME.pdf | 13.27:1(N Line 12.35), 2.6회전, 35.9 ft | OEM(미국) |
| G_HMA_2020_Sonata_DN8_specs_motorshowme.pdf | https://www.motorshow.me/uploadImages/GalleryDocs/Doc7463.pdf | 2020 쏘나타 가솔린 13.27 | OEM 문서(제3자 호스팅) |
| G_hyundainews_2024_SantaFe_MX5_specs.pdf | …/58506-2024SantaFeSpecGuide012624.pdf | 13.7, 2.6회전, 37.9 ft | OEM(미국) |
| G_hyundainews_2023_IONIQ5_NE1_specs.pdf, G_hyundainews_2025_IONIQ5_NE1_specs.pdf | …/50313-2023IONIQ5ProductSpecifications20220630.pdf, …/64493-2025IONIQ5SpecsFeatures121124.pdf | 14.26:1 R-MDPS, 2.67회전, 39.3 ~ 39.4 ft | OEM(미국) |
| G_hyundaiAU_MY26_Tucson_NX4_spec_sheet.pdf | https://www.hyundai.com/content/dam/hyundai/au/en/models/my26-tucson/MY26-TUCSON-Specification.pdf | 2.41회전, 11.8 m | OEM(호주) |
| G_hyundaiAU_SantaFe_MX5_spec_sheet_OCT2026.pdf | https://www.hyundai.com/content/dam/hyundai/au/en/models/santa-fe-hybrid/docs/SANTA-FE-Specification-Sheet-OCT2026.pdf | 2.62(2.5T), 2.53(HEV R-MDPS), 11.6 m | OEM(호주) |
| G_hyundaiAU_i30Sedan_CN7_spec_sheet.pdf | https://www.hyundai.com/content/dam/hyundai/au/en/models/i30/docs/Hyundai_i30_Sedan_Specifications.pdf | 2.48 / 2.41회전, 10.8 m | OEM(호주) |
| G_hyundaiAU_2026_IONIQ5_spec_sheet.pdf, G_hyundaiAU_MY27_IONIQ5_spec_sheet.pdf | https://www.hyundai.com/content/dam/hyundai/au/en/models/ioniq5/docs/2026-IONIQ-5-Specification-Sheet.pdf, …/MY27_Specification_Sheet_IONIQ5_Electric.pdf | 2.67회전, 11.98 m | OEM(호주) |
| G_hyundaiUK_MY25_Tucson_NX4_tech_spec.pdf | https://www.hyundai.news/newsroom/dam/uk/press-kits/hyundai-uk-tucson-tech-spec-pricing-model-year-2025-0524.pdf | 2.41(수동 2.54), C/R-MDPS 병기, 11 m | OEM(영국) |
| G_hyundaiUK_MY27_Tucson_NX4_tech_spec.pdf | https://dmassets.hyundai.com/is/content/hyundaiautoever/TUCSON_Tech_Specpdf | MY27 투싼 영국 사양 | OEM(영국) |
| G_hyundaiEU_2024_SantaFe_MX5_technical_data.pdf | https://www.hyundai.news/newsroom/dam/eu/press-kits/20240807_press_kit_santa_fe/20240823-technical-data-santa-fe.pdf | HEV 2.53회전, 반경 5.8 m | OEM(유럽) |
| G_hyundaiUK_INSTER_AXEV_tech_spec_guide.pdf, G_hyundaiUK_2024_INSTER_AXEV_pricing_spec.pdf | https://dmassets.hyundai.com/is/content/hyundaiautoever/INSTER_Tech_and_Spec_Guidepdf, https://www.hyundai.news/newsroom/dam/uk/models/20241003_all_new_inster_pricing_and_specification/20241003_all-new-inster-pricing-and-specification.pdf | 2.58회전 C-MDPS, 10.6 m | OEM(영국) |
| G_hyundaiBelux_2024_INSTER_AXEV_technical_data.pdf | https://cdn.uc.assets.prezly.com/f5a8a084-423d-4e22-a564-abc8f8b15c8f/-/inline/no/2024_Technical%20Data_INSTER%20Belux%20market.pdf | 회전반경 5.3 m | OEM(벨기에·룩셈부르크) |
| G_scielo_JBSMSE_analytical_models_handling.pdf | https://www.scielo.br/j/jbsmse/a/YntnRYDyVRTxmVkJMVZm65m/?lang=en&format=pdf | 언더스티어 구배 3.85, 4.03 deg/g 측정, 모델 단계별 0.53 ~ 4.04, 관심 범위 0.4 g 미만 | 동료심사 |
| G_OSU_thesis_understeer_gradient_definitions.pdf | https://etd.ohiolink.edu/acprod/odb_etd/ws/send_file/send?accession=osu1259700998&disposition=inline | 시험법별 K 2.5 ~ 5.2 deg/g, 바퀴각 기준은 0.25 ~ 0.31 | 학위논문 |
| G_NHTSA_DOTHS813177_simulation_validation_review.pdf | https://rosap.ntl.bts.gov/view/dot/57509/dot_57509_DS1.pdf | a_y/δ 정상상태 식(L, K, u). 측정 예는 6종 트럭 2.72 deg/g | 정부 보고서 |
| G_NHTSA_FMVSS126_126-TRC-21-004.pdf, G_NHTSA_FMVSS126_126-TRC-21-005.pdf | https://static.nhtsa.gov/odi/ctr/9999/TRTR-647601-2021-001.pdf, https://static.nhtsa.gov/odi/ctr/9999/TRTR-647507-2021-001.pdf | SIS 80 km/h 0.3 g 핸들각 28.1°(Highlander), 25.0°(Civic) | 정부 시험 보고서 |
| G_MDPI_ApplSci11-5485_understeer_typical.pdf | https://mdpi-res.com/d_attachment/applsci/applsci-11-05485/article_deploy/applsci-11-05485.pdf | 교과서 값 재인용 4×10⁻³, 2 ~ 9×10⁻³ rad/(m/s²) | 동료심사(재인용) |
| G_Chalmers_Jacobson_Vehicle_Dynamics_Compendium.pdf | https://research.chalmers.se/publication/513850/file/513850_Fulltext.pdf | 특성속도 70 ± 10 km/h 요구 예, ISO 8855 단위 | 교재 |
| G_LiU2006_thesis_yawrate_ay_plausibilisation_AFS.pdf | https://www.diva-portal.org/smash/get/diva2:22916/FULLTEXT01.pdf | 조향 모델 δ 5% 감소·0.1 s 지연이 최적, 구동 뒷바퀴 슬립 시 바퀴속도 요레이트 큰 오차 | 학위논문 |
| G_Carlson2002_AVEC_GPS_differential_wheelspeeds.pdf | http://www-cdr.stanford.edu/dynamic/navigation/WheelSpeedsAVEC2002.pdf | 바퀴속도 방위와 자이로 매끈한 도로에서 같음, 1 mm 반경 차가 10 m/s·300 s에 6 rad 오차 | 학회 논문 |
| G_Ali2023_TITS_SHRP2_surface_accelerations_reference.pdf | https://raw.githubusercontent.com/gibran-ali/surface-accelerations-reference/main/Surface%20Accelerations%20Reference%20Accepted%20FINAL%20VERSION.PDF | SHRP2 운전자별 가속도 이벤트 분포 방법 | 동료심사(저자 수락본) |
| G_Ali2023_SHRP2_Lateral_Positive_Acceleration.csv, G_Ali2023_SHRP2_Lateral_Negative_Acceleration.csv, G_Ali2023_SHRP2_dictionary.xlsx, G_Ali2023_SHRP2_surface_accelerations_README.md | https://raw.githubusercontent.com/gibran-ali/surface-accelerations-reference/main/ 아래 Shared_Acceleration_Profiles/ 및 README.md | 약 3,500명. 95 백분위 중앙값 우 0.328 g, 좌 0.278 g. 속도대별 값 | 공개 데이터 |
| G_VTTI_SHRP2_surface_accelerations_reference.html | https://dataviz.vtti.vt.edu/Surface_Accelerations_Reference/ | 같은 자료 안내 페이지 | 연구기관 |
| G_JSHeld_left_turn_lateral_accel_SHRP2.pdf | https://www.jsheld.com/uploads/PDFs/Lateral-and-Tangential-Accelerations-of-Left-Turning-Vehicles-from-Naturalistic-Observations.pdf | 드론 관측 좌회전(SAE 2019-01-0421). 수치 미사용 | 산업 논문 |
| G_EDC_WP2004-3_steady_state_directional_stability.pdf | https://edccorp.com/library/HveWpPdfs/WP2004-3.pdf | 시뮬레이션만. 수치 미사용 | 산업 백서 |

`…/`는 https://www.hyundainews.com/assets/documents/original/ 이다. 윤거는 로컬 국내 설명서(`../../07_model_specs/ownersmanual/<차종>/dimensions*.html`)에서 읽었다.

## H. 특허

| 파일 | URL | 주는 내용 | 신뢰도 |
|---|---|---|---|
| H_US9873293B2_Goodyear_indirect_tire_wear_state_prediction_gp.html | https://patents.google.com/patent/US9873293B2/en | CAN 관성 신호·조향각·바퀴속도로 타이어 힘과 슬립 속도를 추정, 마찰에너지 = 힘 · 슬립 속도, 누적 마찰일과 기온·노면으로 마모율 조회. 계수 수치 없음 | 특허(Goodyear) |
| H_US9552680B2_tire_rotation_warning_gp.html | https://patents.google.com/patent/US9552680B2/en | 앞뒤 평균 둘레 차의 변화율로 정해진 마모 차 K에 닿는 시점 예측, 교환 알림. K 수치 없음. 구동·조향·제동 분담 때문에 앞뒤 마모가 다르다는 서술 | 특허(Ford) |

## 받지 못한 것

- 유료·차단: Schallamach & Turner 1960, Grosch & Schallamach 1961, Veith 1992, Kondo & Brenner 1971(FWD/RWD 투영 트레드 마모), Le Maître 1998 SAE 980256, Grigoratos 2018, Tonegawa & Sasaki 2021, Charbouillot 2023(ScienceDirect 403), Das 2024 원문(Wiley 403), Bosetti 2014, Xu 2015
- UNECE ECE/TRANS/WP.29/2026/138(새 UN R186 타이어 마모 규정, 403), Emissions Analytics 2024 발표(404), OECD 2020 비배기 보고서(403)
- UN R64·R141 원문(unece.org 403, EUR-Lex 빈 응답), 한국 자동차관리법 시행규칙 별표 트레드 기준(law.go.kr 별표가 JavaScript)
- 국내 출고 타이어 품목, 캐스퍼 조향비·회전반경, Tire Rack 등 소매점 규격표(차단)
- NHTSA 보고서 일부는 www.nhtsa.gov, rosap 직접 다운로드가 403이라 crashstats 경로나 WebFetch로 받았다
