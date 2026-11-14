# 엔진오일 열화 모델 근거 자료 모음

접근일은 전부 2026-11-02. 파일 접두어는 주제다. A는 OEM 오일 수명 알고리즘·공회전 환산, B는 연료 희석·수분·워밍업, C는 현장 열화 데이터·현대 정비 기준.
모델에 넣을 수치와 등급은 `model-inputs.md`에 정리했다. 다운로드 파일은 하나도 실행하지 않았다. PDF 텍스트 추출은 이 폴더 밖 스크립트(`python3 -I`)로 했다.

신뢰도 구분: 동료심사(학술지), 학회(SAE 등 심사 학회 논문), 특허, OEM(제조사 문서), 정부, 학위논문, 산업(윤활유·장비사 기술자료), 2차(언론·업계지·재인용).
"초록만"은 본문이 유료라 초록 페이지만 저장했다는 뜻이다.

## A. OEM 오일 수명 알고리즘, 공회전 환산

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| A_US4742476A.pdf, A_US4742476A_gp.html | https://patents.google.com/patent/US4742476A/en | GM OLM 원특허. 25,000,000 rev = 12,000 mi, MAXREV = k(Ne/Nv)×720,000, 오일 온도 벌점표(Fig. 3), 이상 조건 10,000 mi 대 냉간 단거리 1,500 ~ 2,300 mi(4.3 ~ 6.7x의 출처), 1년 상한 | 특허 |
| A_EP0231055A2_gp.html | https://patents.google.com/patent/EP0231055A2/en | 위 특허의 유럽 대응 | 특허 |
| A_US4847768A.pdf, A_US4847768A_gp.html | https://patents.google.com/patent/US4847768A/en | GM 오일 온도 추정식. 0.00154 °C/점화, 평형식 계수, 정차 후 보정 c1 = -20 °C, c3 = 0.3 °C/분, ±5 °C 정확도 | 특허 |
| A_US6327900B1.pdf, A_US6327900B1_gp.html | https://patents.google.com/patent/US6327900B1/en | GM 디젤 OLM. 20,000,000 rev, 7,500 mi 상한, 오염 벌점 | 특허 |
| A_GM_TSB_2018_MC-10137589_oil_life.pdf | https://static.nhtsa.gov/odi/tsbs/2018/MC-10137589-9999.pdf | GM 18-NA-125. 회전수·주행거리(7,500 mi 상한)·시간(1년) 3경로 최솟값, 냉각수 126 °C 초과 시 0%, Volt 2년 타이머 | OEM |
| A_SAE870403_Schwartz_Smolenski_GM_oil_change_indicator_abstract.html | https://saemobilus.sae.org/papers/development-automatic-engine-oil-change-indicator-system-870403 | GM OLM 논문. 초록에 수치 없음 | 학회, 초록만 |
| A_US5060156A.pdf, A_US5060156A_gp.html | https://patents.google.com/patent/US5060156A/en | Ford. 점화 펄스 가중 8/4/2/3/4/8(오일 온도 구간별), 54분 연속 운전 벌점 | 특허 |
| A_US5633796A.pdf, A_US5633796A_gp.html | https://patents.google.com/patent/US5633796A/en | Ford. 오일 온도 추정(rpm × 흡기량 표), 상대 열화율 곡선(-40 °F, 300 °F에서 1.5) | 특허 |
| A_US5382942A.pdf, A_US5382942A_gp.html | https://patents.google.com/patent/US5382942A/en | Ford. 회전수·시간·주행거리 최솟값, 오일 부족 시 2배, 5% 경고 | 특허 |
| A_US9988954_uspto.pdf | https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/9988954 | Ford 2018. 연료 희석 모델을 OLM에 반영. 공연비 지령값과 실측 차이로 희석 증감 판단, 고속 주행 시 경질분 증발. 수치 없음(일부 쪽만 판독) | 특허 |
| A_US6917865B2_Honda.pdf, A_US6917865B2_Honda_gp.html | https://patents.google.com/patent/US6917865B2/en | Honda Maintenance Minder. 30,000,000 rev, PF 최소 1.0 @ 80 °C, 6,000 ~ 16,000 km 클램프, 보충 시 -7,000,000 rev | 특허 |
| A_Honda_RD_TechReview_2003_v15n2_p53_oil_deterioration_monitoring.pdf, A_Honda_TechReview_10003012_oil_deterioration_monitoring.html | https://global.honda/en/technical-review/PDF/015-2/015-2_53e.pdf | Honda 기술지. 운전 모드별 DF(그림), 1차 지연 오일 온도식, 수명 오차 15 ~ 20%, 교환 횟수 30% 감소, 타사 시스템 비교표 | OEM |
| A_US8464576B2_Honda_gp.html | https://patents.google.com/patent/US8464576B2/en | Honda. TBN·산화유도시간 화학모델 식 형태. 계수 미공개 | 특허 |
| A_US4677847A.pdf, A_US4677847A_gp.html | https://patents.google.com/patent/US4677847A/en | Aisin(토요타 계열). 오일 온도별 시간당 열화율 α. 0 ~ 100 °C 200시간, -15 °C 이하·200 °C 초과 5시간 | 특허 |
| A_US4533900A_BMW_gp.html | https://patents.google.com/patent/US4533900A/en | BMW 1981 서비스 간격 표시. 47 °C 미만 오일, 고연료소비, 5,000/6,000 rpm 초과 가중. 가중 크기 미공개 | 특허 |
| A_US4525782A_Daimler_gp.html | https://patents.google.com/patent/US4525782A/en | Daimler. 누적 연료량 기준(2,200 L = 10,000 km) | 특허 |
| A_US5592395A_MercedesASSYST.pdf | https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/5592395 | Mercedes ASSYST. 입력 변수 목록만 | 특허 |
| A_US12366214_uspto.pdf | https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/12366214 | FCA 2025. 크랭크케이스 연료 증발률 모델과 PCV 유량으로 분사량 보정. 정성(앞쪽 3쪽만 판독) | 특허 |
| A_US8332123_uspto.pdf | https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/8332123 | Bosch. 냉간 시동 후 연료 증발을 람다 적응값으로 검출, 10 ~ 30 °C 시동 조건. 표지 그림에 시동 후 약 50분 람다 기록 | 특허 |
| A_US20200398817A1_HMC_gp.html, A_KR20200144230A_HMC_gp.html | https://patents.google.com/patent/US20200398817A1/en , https://patents.google.com/patent/KR20200144230A/en | 현대 HEV 엔진 사용거리. 공회전 0.1 km/s(예시), 시동 구간 모터 rpm별 1 / 1.5 / 2배 | 특허 |
| A_KR102908587B1_HMC_gp.html, A_DE102020121119A1_HMC_gp.html | https://patents.google.com/patent/KR102908587B1/en , https://patents.google.com/patent/DE102020121119A1/en | 현대 오일 수명 추정. 회전당 열화 f = a0 + a1/RPM + a2/(Load+100) + a3·T + a4·T², 산화·질화 평균. 계수 미공개 | 특허 |
| A_US20210340892A1_HMC_oil_replacement_detect_cooldown_gp_extracted.txt | https://patents.google.com/patent/US20210340892A1/en | 현대·기아 오일 교환 감지. 외기 0 °C에서 오일 90 °C가 2시간 뒤 약 30 °C로 냉각(정차 냉각 시정수 근거). Google Patents 접근 차단으로 HTML 대신 앞서 추출해 둔 텍스트를 저장 | 특허 |
| A_KR20090042016A_HMC_gp.html | https://patents.google.com/patent/KR20090042016A/en | 현대 디젤 DPF 후분사 희석 경고. 수치 없음 | 특허 |
| A_Ford_2018_Police_Interceptor_OM_idle_hour_meter_33mi_mirror.pdf | https://boutiqueoils.ro/img/cms/Ford%20Engine%20idle%20hour%20meter%20calculation-%20Special%20Operating%20Conditions.pdf (원본: fordservicecontent.com Police Interceptor 2018 취급설명서) | "Idle hours x 33 = miles (kilometers) equivalency", 계산 예 3,000 mi + 61 h × 33 = 5,015 mi | OEM 문서의 제3자 사본. 같은 문구를 Ford 원본 페이지에서 확인했으나 저장은 거부됨 |

## B. 연료 희석, 수분, 워밍업

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| B_TUWien2022_Zach_thesis_short_trip_oil_dilution_DI_gasoline.pdf | https://repositum.tuwien.at/handle/20.500.12708/80284 | 1.33 L T-GDI 벤치. FTP-75 10회 후 0.5 ~ 1.3 vol%, 단거리(WLTC 앞 10분) 순유입 +90 ~ 97%, 연료 1 L당 약 1 vol%, 순제거 시작 55 ~ 86 °C, E5 120 °C 잔류 30%(재인용) | 학위논문(석사급 Diplomarbeit) |
| B_SAE_04-17-01-0001_HEV_engine_operation_oil_degradation_OA.pdf, B_SAE_04-17-01-0001_HEV_engine_operation_oil_degradation_OA_page.html | https://doi.org/10.4271/04-17-01-0001 | Lubrizol·Shell 2024. 10분 안에 오일 45 ~ 50 °C 초과, PHEV 65 °C 정체, 2.0 L GDI 냉간 공회전 물 5.7 ~ 8.5 wt%·연료 약 10 wt%(7시간), WLTC+RDE 물 0.02·연료 1.9 wt% | 동료심사(SAE 저널, 공개) |
| B_SAE2000-01-1235_Shayler_fuel_transport_crankcase_breather_abstract.html | https://saemobilus.sae.org/content/2000-01-1235 | 냉간 블로바이 HC 85% 보유, 100 °C에서 5%. 평형 희석 100 °C 1 ~ 2%, 70 °C 2 ~ 4% | 학회, 초록만 |
| B_SAE2000-01-2838_Shayler_oil_dilution_buildup_usage_pattern_abstract.html | https://saemobilus.sae.org/content/2000-01-2838 | 사용 패턴별 희석 모델. 초록 수치 없음(Taylor 리뷰가 18 ~ 20% 예측을 인용) | 학회, 초록만 |
| B_Lubricants2021_9-92_Fuel-Lubricant_Interactions_review.pdf | https://doi.org/10.3390/lubricants9090092 | 리뷰. 과다 희석 3 ~ 5%, 현장 10 ~ 20%(Kollman), 여름 3 ~ 5%·겨울 8 ~ 11%(Bergstra), 연료 %별 점도 계산표 | 동료심사(리뷰, 수치 다수는 재인용) |
| B_PMC11547304_Materials2024_HEV_lubricant_fuel_1-8pct_tribology_viscosity.xml | https://doi.org/10.3390/ma17215304 | 연료 1 / 2 / 4 / 8%에서 KV40 -4.2 / -9.3 / -17.9 / -27.1%(신유), HEV 오일 안전 한계 4% | 동료심사 |
| B_SAE2015-01-0967_Hu_TGDI_fuel_injection_crankcase_dilution_abstract.html | https://saemobilus.sae.org/content/2015-01-0967 | 고과급 T-GDI 반복부하 시험 최대 9% | 학회, 초록만 |
| B_SAE2022-01-0633_Tan_TGDI_oil_dilution_cold_starts_abstract.html | https://saemobilus.sae.org/content/2022-01-0633 | 희석이 회전수·토크·냉각수 온도에 선형, 물 회복이 연료보다 한 자릿수 빠름 | 학회, 초록만 |
| B_SAE2002-01-1647_DI_vs_MPI_fuel_dilution_abstract.html | https://saemobilus.sae.org/content/2002-01-1647 | DI는 분사 시기가 추가 변수. 정성 | 학회, 초록만 |
| B_SAE2014-36-0170_Continental_oil_dilution_model_evaporation_abstract.html | https://saemobilus.sae.org/content/2014-36-0170 | 유입·증발 분리 모델 구조. 수치 없음 | 학회, 초록만 |
| B_SAE912387_Schwartz_transparent_oilpan_cold_short_trip_abstract.html | https://saemobilus.sae.org/content/912387 | GM 투명 오일팬. 따뜻해지면 물·연료·불용분 제거. 정성 | 학회, 초록만 |
| B_SCION_AN146_fuel_dilution_GC_ASTM_D7593_appnote.pdf | https://scioninstruments.com/wp-content/uploads/2022/02/AN146-.pdf | 가솔린 희석 안전 한계 4 ~ 5 mass% | 산업(장비사) |
| B_Infineum_Insight_severe_hybrid_engine_oil_testing.html | https://www.infineuminsight.com/en-gb/articles/severe-hybrid-engine-oil-testing/ | PHEV 16대 극한 냉간 현장. 오일 10 °C·냉각수 40 °C 미만에서 물 축적, 연료 최대 20%, 여름 3 ~ 5%, 10%면 0W-20이 0W-8 | 산업(윤활 첨가제사) |
| B_Infineum_Insight_new_generation_lubricants_hybrids.html | https://infineuminsight.com/en-gb/articles/passenger-cars/a-new-generation-of-lubricants-for-hybrids | HEV WLTC 오일 온도 20 ~ 40 °C 낮음 | 산업 |
| B_SAE2025-01-8471_PHEV_extreme_cold_fleet_abstract.html | https://saemobilus.sae.org/papers/a-study-engine-oil-performance-plug-hybrid-electric-vehicles-road-fleet-test-extreme-cold-environmental-condition-2025-01-8471 | 위 Infineum 현장 시험의 SAE 논문 | 학회, 초록만 |
| B_SAE2020-01-0362_PHEV_oil_viscosity_driving_mode_dilution_abstract.html | https://saemobilus.sae.org/content/2020-01-0362 | PHEV 간헐 시동이 희석 가속. 정성 | 학회, 초록만 |
| B_SAE2023-32-0172_hybrid_operation_oil_requirements_abstract.html, B_SAE2023-01-0342_blowby_water_crankcase_hybrid_abstract.html, B_SAE_media12571_emulsion_test_cycle_abstract.html | https://saemobilus.sae.org/content/2023-32-0172 , https://saemobilus.sae.org/content/2023-01-0342 , https://saemobilus.sae.org/media/engine-test-cycle-development-evaluate-effect-oil-formulation-emulsion-formation-12571 | HEV 물 유입·배출, 유화 시험. 정성 | 학회, 초록만 |
| B_SAE2005-01-3896_WhiteRose_ambient_exhaust_thermal_warmup.pdf | https://eprints.whiterose.ac.uk/id/eprint/2041/ | Ford 1.8 L, 도심 루프. 냉각수 88 °C 5.5분(31 °C)·약 9분(-2 °C), 오일 80 °C 10분(여름)·14 ~ 16분(겨울) | 학회 논문 저자 공개본 |
| B_SAE2007-01-2067_Andrews_water_lubeoil_HX_NEDC_oil_warmup_abstract.html | https://saemobilus.sae.org/content/2007-01-2067 | 오일이 NEDC 전체 시간 걸려 90 °C 도달 | 학회, 초록만 |
| B_Morgan2003_NottinghamThesis_PROMETS_engine_thermal.pdf | https://repository.nottingham.ac.uk/bitstreams/e84e6a01-0a2d-4692-b4fb-001a1aadbd6a/download | PROMETS. 1.6 L, -10 °C NEDC 오일 +40 °C에 293초, 열전달계수 10 / 50 W/m²K, 마찰열 오일 분배 10 ~ 15% | 학위논문(박사) |
| B_Bech2018_NottinghamThesis_1.0L_SI_thermal_CDA.pdf | https://repository.nottingham.ac.uk/bitstreams/547a1589-9d1f-4a32-8034-297b8ea9616a/download | 1.0 L 터보, NEDC 20 °C 냉각수 90 °C 882 ~ 938초, 그때 오일 76 ~ 84 °C | 학위논문(박사) |
| B_Roberts2015_NottinghamThesis_thermal_losses_lubricant_cold_start.pdf | https://repository.nottingham.ac.uk/bitstreams/232a5589-6a9a-4841-80e3-9a7de0802f51/download | 오일 0.1 °C/s, 트립 1/3은 오일 60 °C 미도달(재인용), 스톱앤고 시 600초 오일 10 °C 낮음 | 학위논문(박사), 수치 다수 재인용 |
| B_Addison2015_NottinghamThesis_thermal_mgmt_friction.pdf | https://repository.nottingham.ac.uk/bitstreams/8a22c91d-34ac-419a-acf7-8654672d3207/download | 오일 추가 열의 약 30%가 오일에 남음, 마찰열 오일 분배 20%에서 90%(재인용) | 학위논문(박사) |
| B_PMC11791276_HEV_engine_onoff_thermal_dynamics_fulltext.xml | https://www.ebi.ac.uk/europepmc/webservices/rest/PMC11791276/fullTextXML | Toyota 1.8 L 2노드 열모델. 값은 그림에만 | 동료심사(Heliyon 2025) |
| B_SAE910302_KaplanHeywood_SI_warmup_model_abstract.html, B_SAE931153_Shayler_warmup_thermal_friction_model_abstract.html, B_SAE902089_TrapyDamiral_lube_warmup_abstract.html, B_SAE2011-01-1747_Dubouil_hybrid_warmup_NEDC_abstract.html | https://saemobilus.sae.org/content/910302 , /931153 , /902089 , /2011-01-1747 | 워밍업 열모델 원논문들. 초록에 수치 없음 | 학회, 초록만 |

## C. 현장 열화 데이터, OLM 검증, 현대 정비 기준

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| C_Hyundai_KR_manual_NX4_2025_*, C_Hyundai_KR_manual_NX4HEV_2025_*, C_Hyundai_KR_manual_DN8_2024_*, C_Hyundai_KR_manual_DN8HEV_2024_*, C_Hyundai_KR_manual_AX_2025_*, C_Hyundai_KR_manual_AX_2026_* (HTML 다수) | https://ownersmanual.hyundai.com/full_webhelp/{차종}/{연식}/ko_KR/ 하위 페이지 | 오일량·규격·통상/가혹 주기, 가혹 조건 목록(거리 기준 없음) | OEM |
| C_Hyundai_KR_manual_CN7_2025_Avante_full.pdf | https://ownersmanual.hyundai.com/full_pdf/CN7/2025/ko_KR | 아반떼 G1.6 3.8 L, 15,000 km / 7,500 km | OEM |
| C_Hyundai_US_manual_NX4_2025_scheduled_maintenance_en.html | https://ownersmanual.hyundai.com/full_webhelp/NX4/2025/en_US/id3ce613a50f9.html | 단거리 기준 8 km(영상)·16 km(영하), Oil Life Management System 존재, 10년·150,000 km 이후 가혹 주기 | OEM |
| C_Hyundai_US_manual_DN8_2024_scheduled_maintenance_en.html | https://ownersmanual.hyundai.com/full_webhelp/DN8/2024/en_US/ide5b10be92ba.html | 위와 같은 문구(쏘나타) | OEM |
| C_Hyundai_NZ_Petrol_HEV_Turbo_service_requirements.pdf | https://go.hyundai.co.nz/rs/147-PQU-830/images/3.%20Petrol-HEV-Turbo-service-requirements%20Jul%2026.pdf | 가혹 주기는 표준의 절반(10,000 km에서 5,000 km), 여름 8 km·겨울 16 km 미만 단거리 | OEM(뉴질랜드 법인) |
| C_NHTSA_TSB_MC-10235557-0001.pdf | https://static.nhtsa.gov/odi/tsbs/2023/MC-10235557-0001.pdf | 현대 TSB 23-EE-007H(싼타페 HEV/PHEV). 겨울 EV/엔진 잦은 전환으로 연료 희석, 10 °C 미만 분사 전략 변경·오일 가열 기능 추가 | OEM |
| C_NHTSA_TSB_MC-10233643-0001.pdf | https://static.nhtsa.gov/odi/tsbs/2023/MC-10233643-0001.pdf | 기아 TSB ELE285(쏘렌토 HEV/PHEV). 32 °F 이하 반복 단거리에서 연료·수분 희석 | OEM |
| C_NHTSA_TSB_MC-10185030-0001.pdf | https://static.nhtsa.gov/odi/tsbs/2020/MC-10185030-0001.pdf | 기아 ENG222. 감마 1.6 GDI 오일 소모 진단. 희석과 무관 | OEM |
| C_NHTSA_complaints_API_oil_dilution_scan_2026-11-02.json | https://api.nhtsa.gov/complaints/complaintsByVehicle 여러 질의를 모아 만든 파일 | 현대·기아 관련 차종 17,118건 중 키워드 13건, 실제 연료 희석 신고 약 9건 | 정부 데이터를 가공(약한 신호) |
| C_news_Economist_KR_2022-12_Kia_Hyundai_HEV_oil_increase.html | https://economist.co.kr/article/view/ecn202212060098 | 기아 158,638대 무상수리, "고속/장거리 주행 시 자연적으로 해소" | 2차(언론) |
| C_news_NewDaily_2022-04-20_Sorento_HEV_oil_increase.html | https://biz.newdaily.co.kr/site/data/html/2022/04/20/2022042000083.html | 쏘렌토 HEV 오일 증가 현대 측 설명 | 2차(언론) |
| C_KCI_2012_KSTLE_Lim_engine_oil_after_vehicle_driving.pdf | https://www.koreascience.or.kr/article/JAKO201217034923632.pdf | 석유관리원. 렌터카 12대 5,000 / 10,000 km 점도·TAN·마모 | 동료심사(한국트라이볼로지학회지) |
| C_KCI_2013_KPetro_engine_oil_5000_10000km.pdf | https://www.koreascience.or.kr/article/JAKO201335057739371.pdf | 같은 데이터 금속 분석, "10000 km 까지 사용하여도 큰 문제점이 없을 것". 일부 행 전사 오류 있음 | 동료심사(에너지공학) |
| C_news_Kyeonggi_2012-01-17_KPetro_KCA_oil_interval.html | https://kyeonggi.com/article/201201170437342 | 위 연구 보도. 14대로 보도(논문은 12대) | 2차(언론) |
| C_KCI_2017_ACE_KPetro_fuel_in_engine_oil_SIMDIST.pdf | https://www.koreascience.kr/article/JAKO201707153703827.pdf | 5W-30에 가솔린·경유 5 ~ 30% 혼합 실험실 물성, SIMDIST 정량법. 현장 데이터 아님 | 동료심사(공업화학) |
| C_KCI_2017_EnergyEng_JBNU_field_oil_diesel_mineral_vs_synthetic.pdf | https://koreascience.or.kr/article/JAKO201734158606539.pdf | 디젤 5대. 합성유 TBN 1,000 km당 0.11 ~ 0.16 감소 | 동료심사(에너지공학), 디젤 |
| C_Golebiowski_Wolak_Zajac_2018_CombustionEngines_oil_change_intervals.pdf | https://www.combustion-engines.eu/pdf-116452-45706 | 승용차 17대, 문헌 기반 교환 한계표(점도 ±15%, TBN -50% 등) | 동료심사(Combustion Engines) |
| C_PetroCanada_2011_LubeHandbook_p53-54_used_oil_limits.pdf | https://staroilco.net/wp-content/uploads/2015/09/2011_Handbook53-54.pdf | 사용유 교환 기준(점도 40 °C 25%·100 °C 15%, 희석 5%, 물 0.1%, Fe 100 ppm 등) | 산업(윤활유사, 대리점 호스팅) |
| C_MachineryLubrication_Best_Tests_Fuel_Dilution.html | https://www.machinerylubrication.com/Articles/Print/31148?id=31148 | 연료 희석 주의 1.5%, 위험 5% | 2차(업계지, 디젤 맥락) |
| C_MachineryLubrication_GM_Oil_Life_System.html | https://www.machinerylubrication.com/Articles/Print/77?id=77 | GM OLS 검증 주행 약 130,000 + 160,000 km, 오일 물성 직접 측정 안 함 | 2차(업계지) |
| C_Honda_TechReview_2003_15-2_53_Oil_Deterioration_Monitoring.pdf, C_Honda_TechReview_Engine_Oil_Deterioration_Monitoring.html | A 항목과 동일 | A의 Honda 기술지와 같은 파일(중복 저장) | OEM |
| C_MDPI_Materials_2024_17_5304_hybrid_engine_oil_PMC11547304.xml | https://doi.org/10.3390/ma17215304 | B의 Skonieczna 2024와 같은 파일(중복 저장). 15,000 km 주기 HEV 오일 레벨 최대 10 mm 상승 | 동료심사 |
| C_AAA_2017_Synthetic_vs_Conventional_full_report.pdf, C_AAA_2017_Oil_Quality_Fact_Sheet.pdf, C_AAA_2017_spills_truth_oil_changes.html | https://newsroom.aaa.com/wp-content/uploads/2017/06/AAA-Research-Report-Synthetic-vs-Conventional_FINAL-EXTERNAL.pdf 외 | 합성유가 8개 ASTM 시험에서 평균 47% 우수. OLM 검증 데이터는 없음 | 2차(소비자단체 위탁 시험) |
| C_ASTM_D7720_scope.html | https://www.astm.org/d7720-11r17.html | 통계적 경보 한계 설정 지침. 엔진오일 수치 한계 없음 | 표준(범위만) |
| C_SAE_932838_abstract.html | https://saemobilus.sae.org/content/932838 | GM. 주행 형태가 오일 종류보다 영향 큼, 단거리 5,000 km / 3개월 지지 | 학회, 초록만 |
| C_SAE_2003-01-1957_abstract.html | https://saemobilus.sae.org/content/2003-01-1957 | Shell. 4,800 ~ 9,600 km에 첨가제 소모, 대부분 차량이 가혹 조건 | 학회, 초록만 |
| C_SAE_2008-01-1740_abstract.html | https://saemobilus.sae.org/content/2008-01-1740 | 주행거리만으로 오일 수명 예측 불가 | 학회, 초록만(업계단체 공동, 편향 가능) |
| C_SAE_2005-01-3818_abstract.html | https://saemobilus.sae.org/content/2005-01-3818 | 택시 0W-20 10,000 mi 교환 문제없음 | 학회, 초록만 |
| C_SAE_2025-01-5019_abstract.html | https://saemobilus.sae.org/content/2025-01-5019 | 63대, 주행거리·시간과 점도 상관 유의하지 않음 | 학회, 초록만 |
| C_SAE_951035_abstract.html, C_SAE_981443_abstract.html | https://saemobilus.sae.org/content/951035 , /981443 | Daimler·Shell. ASSYST 15,000 ~ 30,000 km | 학회, 초록만 |
| C_SAE_04-17-01-0001_HEV_oil_degradation_abstract.html | https://saemobilus.sae.org/articles/hybrid-electric-vehicle-engine-operation-engine-oil-degradation-a-research-approach-04-17-01-0001 | B의 Growney 2024 초록 페이지(중복) | 동료심사 |

## 접근하지 못한 자료

| 자료 | 사유 |
|---|---|
| SAE 870403(GM OLM 원논문), SAE 2000-01-2838, SAE 2015-01-0967, SAE 2022-01-0633 등 SAE 본문 | 유료. 초록만 저장 |
| Kollman 1998, Bergstra 1998 SAE 원문 | 유료. Taylor 2021 리뷰의 재인용만 사용 |
| Peralta MIT 1997 학위논문 | 저장소가 HTTP 429로 차단 |
| Energies 19:50 (2026) HEV 연료 희석 FTIR 논문 | 출판사 403. 초록만 확인, 수치 없음 |
| Agocs 외 Tribology International 2021 현장 연구 | 유료·봇 차단 |
| Volkswagen LongLife(WIV) 알고리즘, 최신 BMW CBS 특허 | Google Patents 요청 제한으로 확인 못 함 |
| 현대 OLMS 실제 계수, 현대 엔진 실측 희석·온도 곡선 | 비공개 |
| Ford 경찰차 매뉴얼 원본 페이지 저장 | Ford 서버가 직접 다운로드 거부. 제3자 사본 저장 |
