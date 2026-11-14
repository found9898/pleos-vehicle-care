# 브레이크 패드 마모 모델 근거 자료

접근일은 전부 2026-11-02. 파일 접두어는 주제다.

| 접두어 | 주제 |
|---|---|
| A | 마모 법칙, 재질 |
| B | GTR 24, WLTP-Brake 사이클, 배출 시험 |
| C | 디스크 열 모델, 마모와 온도 |
| E | 회생제동 비율, EV·HEV 패드 수명 |
| F | 현대 차종 제원, 취급설명서 |
| G | EPA 주행저항 계수 |
| H | 특허 |

모델에 넣을 수치와 등급은 `model-inputs.md`에 정리했다. 다운로드 파일은 하나도 실행하지 않았다. 텍스트 추출과 그림 판독은 이 폴더 밖 스크립트(`scratchpad/brakescripts/`, `python -I`)로 했다. 기존 `../originals/jensen-2024-wear-205220.pdf`, `jensen-2024-wear-205433.pdf`도 다시 읽어 수치를 확인했다.

신뢰도 구분: 동료심사(학술지), 학위논문, 규제·시험기관(UN·EU·EPA·Green NCAP), OEM(현대 문서·TSB), 특허, 산업(부품사 기술자료), 2차(언론·제3자 미러·재인용).

## A. 마모 법칙, 재질

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| ../originals/jensen-2024-wear-205220.pdf | https://doi.org/10.1016/j.wear.2023.205220 | 1,580 kg FWD 차량. k = 0.75 ~ 1.74×10⁻⁹ mm/J(차량 제동일 기준), 116.11 J/m = 2.14×10⁸ J / 1,843 km(해당 시리즈 전체 평균, 최대치 아님), 전후 65:35 가정, z = 0.163, 패드 면적 7,700 mm², 신품 약 17 mm(백플레이트 포함 추정), C_dA 0.73 m², Cr 0.011, Riefe·Yen 인용 kA 5×10⁻⁶ mm³/J(250 °C 미만), 2×10⁻⁵(400 °C) | 동료심사, CC-BY |
| ../originals/jensen-2024-wear-205433.pdf | https://doi.org/10.1016/j.wear.2024.205433 | 2021 VW Polo 1,200 kg, 10,200 km, 측정 31회, 평균 마모 0.85 mm, k ≈ 1.22×10⁻⁹ mm/J, 일-거리 비 9 ~ 190 J/m(구간), 전체 50 ~ 55 J/m, 누적 일 539 ~ 626 MJ | 동료심사, CC-BY |
| A_PMC8509441_Materials2021_14-5531_NAO_vs_LowSteel_severe_wear.xml | https://doi.org/10.3390/ma14195531 | 고온(디스크 250에서 500 °C) 축소 시험에서 NAO 마모율이 저스틸의 약 6 ~ 8배. NAO 수지·아라미드 열분해 200 ~ 600 °C | 동료심사, OA |

## B. GTR 24, WLTP-Brake, 배출 시험

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| B_GreenNCAP_Brake_Abrasion_Procedure_v1.0.1.pdf | https://www.greenncap.com/wp-content/uploads/GNT_Brake_Abrasion_Procedure_v1.0.1.pdf | GTR 24 마찰제동 비율 c: ICE 1.0, NOVC-HEV 0/1/2 범주 0.9/0.72/0.52, OVC-HEV 0.34. 제동 이벤트 판정(실속도가 주행저항만의 이론 속도보다 낮을 때), 감속에너지 = 1.03·TM·a·d, 주행저항 f0+f1v+f2v² 차감, 회생 효율 0.95 | 시험기관 |
| B_Atmosphere2021_12-377_brake_PM10_dilution_tunnel.pdf | https://doi.org/10.3390/atmos12030377 | WLTP-Brake 5.5 h, 192 km, 303회, 평균 43 km/h, 최고 136 km/h, 트립 시작 디스크 40 °C. 사이클 평균 디스크 58.5 ~ 65.5 °C, 최고 131 ~ 184 °C. 3h-LACT 평균 100 ~ 116 °C, 최고 165 ~ 203 °C, 11.3회/분 대 1.1회/분. 디스크 9 kg, 관성 73.3 kg·m², 굴림반경 332 mm. 대류 h는 100 W/m²K를 넘지 않음. PM10 5.6(WLTP-B), 8.6(LACT) mg/km | 동료심사, CC-BY |
| B_HelmholtzMunich_36674_brake_GTR24.pdf | https://doi.org/10.1016/j.jhazmat.2024.136609 | GTR 24 절차로 LM·NAO 패드 PM10 15.1, 16.3 mg/km, NAO 비교 5.0 ~ 5.4 mg/km. Euro 7 한도 7 mg/km(ICE·HEV), 3 mg/km(PEV), 2035년 전 차종 3 mg/km | 동료심사 |
| B_Wuppertal_diss_3552801_brake_wear_particles.pdf | https://elekpub.bib.uni-wuppertal.de/ubwhsmig/download/pdf/3552801 | WLTP 사이클 303회, 평균 46.5 km/h, 감속 0.49 ~ 2.18 m/s², WLTP DB 743,694 km(한국 포함 5개 지역). 3h-LACT 217회. WLTP 최고 브레이크 온도 약 170 °C. PM 질량 0.064 mg/kJ/브레이크 | 학위논문 |
| B_JARI_2025_Brake_Particle_Emissions_Measurements.pdf | https://img.jari.or.jp/files/user/pdf/2025_Brake%20Particle%20Emissions%20Measurements.pdf | WLTP-Brake 4시간 24분, 평균 44 km/h, 최고 133 km/h, 감속 0.49 ~ 2.18 m/s²(평균 0.97) | 시험기관 |
| E_Storch2023_Atmosphere14-712_brake_mitigation_BEV_PHEV.pdf | https://doi.org/10.3390/atmos14040712 | C세그먼트 1,659.5 kg(312×25 통풍 디스크). 비마찰일 15,184 J/kg/사이클(ICE), PHEV 3,618, BEV 1,591. 전체 질량손실 34 mg/km/대, 앞 12.2·뒤 5.0 mg/km/브레이크, 뒤축이 마모의 약 29%, ECE 패드에서 디스크가 마모의 약 2/3. 범주 3 온도창 IBT 110 °C·FBT 165 °C까지. BEV 회생 최대 0.29 g, 10 ~ 6 km/h에서 마찰로 전환 | 동료심사, CC-BY |
| E_Giechaskiel2024_Atmosphere15-97_LDV_brake_emission_factors.pdf | https://doi.org/10.3390/atmos15010097 | ECE 패드에서 디스크 기여 약 60%, 비산·PM10이 총 마모의 40 ~ 42%, PM10 약 3.1 mg/km/브레이크/1,000 kg. NAO는 미국·일본·한국에서 주로 사용. GTR 기준 회생 비율 HEV 10 ~ 48%, PHEV 66%, BEV 83% | 동료심사(리뷰, JRC 저자) |

## C. 열 모델, 마모와 온도

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| C_IJPHM_3064_lining_thickness_brake_temperature_model.pdf | https://papers.phmsociety.org/index.php/ijphm/article/view/3064 | 그림 판독: 앞 디스크 h 약 7(1.5 m/s) ~ 27(14 m/s) W/m²K, 주철 비열 460(20 °C) ~ 600(400 °C) J/kgK, 비마모계수 ki 1.7×10⁻¹⁴(0 °C), 2.0(100), 3.0(200), 4.8(300), 5.5×10⁻¹⁴ m²/N(350 °C). 1,335 kg 차량, 앞 디스크 5.23 kg, 라이닝 밀도 1,900 kg/m³, 패드 마모 3×10⁻⁴ mm/km | 동료심사(OA) |
| C_Chalmers2012_162888_alpine_descent_brake_thermal_model.pdf | https://publications.lib.chalmers.se/records/fulltext/162888.pdf | Volvo SimAlp(36 km/h 견인 하강). 디스크 흡열 약 93%(인용), 대류 즉시 손실 약 1%. 그림 2.7(Zhang 2009 인용) 판독: 패드 질량손실/회는 300 °C까지 거의 평탄, 340 ~ 378 °C에서 꺾임, 500 °C에서 60 km/h 약 108 mg 대 저온 약 25 mg. 320 °C 미만에서는 마모가 흡수 에너지에 비례(초속 비와 마모 비 1.46 대 1.47 등) | 학위논문 |
| C_Chalmers_thesis_brake_disc_heat_transfer_dyno.pdf | https://odr.chalmers.se/bitstreams/617e68cb-fcfb-4bdf-a2fb-9391d5e64394/download | 통풍 디스크 표면별 HTC(180 km/h, 300 m³/h): 통풍구 70 ~ 78, 마찰면 30 ~ 63 W/m²K, 온도가 오를수록 소폭 감소 | 학위논문(2026) |
| C_Dixcel_tech_lid-2241_pad_wear_vs_temperature.html, C_Dixcel_illust_pad04_wear_vs_temperature_graph.gif | https://dixcel.co.jp/en/literature/lid-2241 | 80 km/h에서 0.4 g 제동 1,000회, 온도별 OEM 패드 마모 판독값 0.22(100 °C), 0.32(150), 0.55(200), 1.58(250), 2.5 초과(300). 단위 미표기 | 산업(일본 패드 제조사) |
| H_US9416835_GM_Antanaitis_Riefe_pad_wear_lumped_cooling_uspto.pdf | https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/9416835 | GM. 집중 열용량 dT/dt = −b(T−Ta) + D, b = hA/(ρVc), 예시 b = 0.00033·V + 0.0033(V: km/h). 제동에너지 = ½M(V1²−V2²) − 구름 − 경사 − 엔진. 앞 비율 = A피스톤·R유효·μ 비. 고온(600 °C 이상) 냉각계수 6% 감소. 마모는 온도별 mm³/kJ 표 | 특허 |

## E. 회생제동 비율, EV·HEV 패드 수명

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| E_Komnos2025_Energies18-4104_PHEV_friction_regen_shares.pdf | https://doi.org/10.3390/en18154104 | GTR 24 PEV 마찰 비율 17%(시장 최저 성능 기준). 실차 PHEV 10,900 km 거리가중 5.3%, 도심 2.3%, 국도 4.2%, 고속 7.3%, 급제동 트립 24 ~ 30%. 주행저항분을 빼고 계산 | 동료심사, JRC 저자 |
| E_Hagino2024_Atmosphere15-75_regen_friction_coordination.pdf | https://doi.org/10.3390/atmos15010075 | 실차 PHEV WLTP-Brake 마찰 평균 0.107. 저속·모터토크·배터리 전력 3구간 제한 | 동료심사 |
| E_Jung2020_WEVJ11-31_PHEV_HEV_regen_temperature.pdf | https://mdpi-res.com/d_attachment/wevj/wevj-11-00031/article_deploy/wevj-11-00031.pdf | INL 실차: 60에서 0 mph 급제동 회생 회수 7 ~ 8%, 60에서 10 mph 완만 감속 50 ~ 58% | 동료심사 |
| E_GreenNCAP_Rating_Procedure_2025_v0.0.1.pdf | https://www.greenncap.com/wp-content/uploads/Rating_Procedure_2025-v0.0.1.pdf | 회생 점수 = 6×(100−마찰%)/90×질량계수(1,200 kg 이하 1, 2,400 kg 이상 0.75) | 시험기관 |
| E_GreenNCAP_Hyundai_Inster_2025_page.html | https://www.greenncap.com/assessments/hyundai-inster-vertex-2025-0222/ | 캐스퍼 일렉트릭 수출형. 회생 점수 5.66, 역산 마찰 약 11 ~ 13% | 시험기관(역산은 derived) |
| E_GreenNCAP_Kia_EV9_2025_page.html, E_GreenNCAP_TeslaModelY_RWD_2026_page.html, E_GreenNCAP_Renault_Symbioz_HEV_2026_page.html | greenncap.com assessments | EV9 역산 약 22%, Model Y 약 10%. Symbioz(HEV c=0.52)로 역산식 검증 | 시험기관 |
| E_Hyundai_OM_Ioniq5_2025_iPEDAL.html, E_Hyundai_OM_InsterEV_2025_iPEDAL.html | https://ownersmanual.hyundai.com/full_webhelp/NE1a/2025/en_US/topic_cfn_2w2_mvb.html | i-PEDAL 정지 시 마찰 보조 필요. 최대 감속 수치 없음 | OEM |
| E_Hyundai_OM_TucsonPHEV_2026_regen_limitations.html | https://ownersmanual.hyundai.com/full_webhelp/NX4PHEV/2026/en_US/topic_zg4_xft_t5b.html | 배터리 과충전·고온·저온 시 회생 제한. 수치 없음 | OEM |
| E_MobileSyrup_20230616_Ioniq5_brakelight_013g.html | https://mobilesyrup.com/2023/06/16/hyundai-update-ioniq-5-brake-lights-more-responsive-with-regeneration-on/ | 감속 약 0.13 g 초과 시 제동등(현대 대변인 발언) | 2차 |
| E_Senol2023_IEEEAccess_EV_low_temperatures.pdf | https://strathprints.strath.ac.uk/85123/ | 저온 배터리에서 회생 제한·차단. 정성 | 동료심사(리뷰) |
| E_ANL_2021_ESD-21-4_TCO_maintenance.pdf | https://afdc.energy.gov/files/u/publication/total_cost_of_ownership_quantification.pdf | 패드 교체 주기 가정 ICEV 50,000 mi, HEV 66,667, PHEV·BEV 75,000. 실측 부족 명시 | 정부 연구소(가정치) |
| E_Hooftman2016_Energies9-84_petrol_diesel_EV_Belgium.pdf | https://doi.org/10.3390/en9010084 | EV 패드가 내연차보다 약 2/3 더 오래 감(원자료 비공개) | 동료심사(재인용) |
| E_HMG_story_Ioniq5_taxi_200000km.html | https://www.hyundaimotorgroup.com/ko/story/CONT0000000000155774 | 아이오닉 5 택시 약 20만 km 패드 미교체 | OEM 홍보(일화) |
| E_Segye_20240530_EV6_taxi_310000km.html | https://www.segye.com/newsView/20240530513797 | EV6 택시 31만 km. 패드 발언은 다른 기사의 것 | 2차 |
| E_TheDriven_2021_TeslaS_400000km_pads.html | https://thedriven.io/2021/07/03/impossible-tesla-model-s-does-400000km-on-one-set-of-brake-pads/ | Model S 1대 40만 km 원래 패드 | 2차(일화) |

## F. 현대 차종 제원, 취급설명서

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| F_NHTSA_MC-10245577-0001_Hyundai_disc_TSB.pdf | https://static.nhtsa.gov/odi/tsbs/2023/MC-10245577-0001.pdf | 현대 TSB 23-BR-004H. 투싼 NX4A·싼타페 TMA 앞 디스크 표준 30 mm, 한계 28 mm | OEM |
| F_NHTSA_MC-11032900-0001_Kia_CHA126_MQ4_NQ5_front_disc.pdf | https://static.nhtsa.gov/odi/tsbs/2026/MC-11032900-0001.pdf | 쏘렌토 MQ4a 앞 디스크 30/28 mm. 스포티지 NQ5a 디스크 품번이 투싼과 같음 | OEM |
| F_2nd_htucson4_NX4_brake_specifications.html, F_2nd_htucson4_NX4_brake_pad.html, F_2nd_htucson4_NX4_front_disc_brake.html, F_2nd_htucson4_NX4_rear_disc_brake.html | https://www.htucson4.com/specifications-2133.html 외 | 투싼 앞 패드 11 mm, 한계 2.0 mm, 뒤 10/2.0 mm, 앞 디스크 Ø325×30/28, 뒤 Ø300×10/8.0. 패드 점검 페이지는 앞 10 mm로 적어 서로 다름 | 2차(미국 정비지침서 미러) |
| F_2nd_helantra7_CN7_brake_specifications.html, F_2nd_helantra7_CN7_brake_pad.html | https://www.helantra7.com/hyundai_elantra_cn7_specifications-3541.html | 아반떼 앞 패드 10.5(15인치)·11(16인치)/2.0 mm, 뒤 10/2.0, 앞 디스크 Ø280·Ø305×25/23, 뒤 드럼 라이닝 4.2/1.0 | 2차(미러) |
| F_hyundai_kr_*_price_*.pdf (12개) | https://www.hyundai.com/contents/repn-car/catalog/tucson-2026-price.pdf 외, 캐스퍼는 casper.hyundai.com | 트림별 공차중량(투싼 1.6T 1,535 ~ 1,630 kg 등, `model-inputs.md` 표 e) | OEM |
| F_hyundai_om_<차종>_2026_brake_wear_indicator.html (9개) | https://ownersmanual.hyundai.com/full_webhelp/<차종>/2026/ko_KR/id15c911f21e6.html | 마모 인디케이터 경고음 설명. 두께 수치 없음 | OEM |
| F_hyundai_om_NE1_2026_brake_disc_cleaning_BDC.html | ownersmanual.hyundai.com NE1 | 디스크 클리닝: 약 10회 제동 동안 회생 차단 | OEM |
| F_hyundai_om_NE1_2026_regen_braking_limits.html | ownersmanual.hyundai.com NE1 | 회생량 변경 불가 조건(SOC 100%, SNOW, SCC 작동 등) | OEM |
| F_hyundai_om_QV2026_brake_pad_21mm.html | ownersmanual.hyundai.com QV | 상용 트럭(에어브레이크) 패드 21 mm, 드럼 16에서 6 mm. 승용 적용 불가, 비교용 | OEM |

## G. EPA 주행저항 계수

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| G_EPA_26-testcar-2026-01-21.xlsx, G_EPA_25-testcar-2026-01-21.xlsx, G_EPA_24-testcar-2025-05.xlsx | https://www.epa.gov/compliance-and-fuel-economy-data/data-cars-used-testing-fuel-economy | 현대 2024 ~ 2026년형 Target Coef A/B/C, 등가시험중량 ETW | 정부 |
| G_EPA_data_cars_used_testing_page.html | 위 URL | 다운로드 목록 페이지 | 정부 |
| G_EPA_hyundai_roadload_metric_derived.csv | 위 xlsx에서 계산 | 2026년형 인증(Tier 2·전기) 시험만, f0(N), f1(N/(km/h)), f2(N/(km/h)²), 50·100 km/h 저항력과 타행 감속 | derived |

## H. 특허

| 파일 | URL | 주는 수치·식 | 신뢰도 |
|---|---|---|---|
| H_US9416835_GM_Antanaitis_Riefe_pad_wear_lumped_cooling_uspto.pdf | C 표 참조 | C 표 참조 | 특허 |
| H_KR1020210047232_HyundaiMobis_pad_residual_thickness_braking_energy_regen.pdf | KIPRIS 공개공보 | Ea = ½M(V1²−V2²)×(1−회생%), 축별 cf·cr 누적, TD = f(V, 유압, 외기온), PW = f(평균 TD), 잔여 = vol − PW·Ebt/EbE. 계수 미공개. 취하 | 특허 |
| H_KR1020220064646_HyundaiMobis_pad_residual_thickness_disc_temp_hill_twofoot.pdf | KIPRIS | 위 모델에 경사로·양발 운전 보정. 배경에 "약 3 mm 이하 교체" | 특허 |
| H_KR1020140021949_Hyundai_ESC_friction_wear_warning.pdf, ..._registered_B1.pdf, H_US20150240895_Hyundai_ESC_brake_friction_pad_abrasion_warning_uspto.pdf | KIPRIS, USPTO | 감속도 0.1 g(또는 0.05 g) 구간 모드, Σ(Z/Ak)·Pk > Z이면 경고, Z = 2 mm 남은 시점, 열간 가중치 예 1.1, 내리막 가중 | 특허(KR 10-1551972 등록) |
| H_KR1020040048636_Hyundai_pad_exchange_time_wheelspeed_pedal_decel.pdf, ..._registered_B1.pdf | KIPRIS | 휠속도·페달만 사용. 감속 모드별 횟수×가중(10%, 20% …) 합이 SPEC 초과 시 경고. SPEC 수치 없음 (KR 10-0580516, 소멸) | 특허 |
| H_KR1020200058810_Hyundai_braking_control_disc_temp_estimation_pad_wear.pdf, H_US20210354671_..., H_US12325406_... | KIPRIS, USPTO | 디스크 열분배 γ = 1/(1+(Apad/Adisc)√(ρp kp cp/ρd kd cd)), 상승온도 = 입력에너지/(질량×열용량), 냉각 = 대류계수×면적. 최대 550 °C 예시. 시험예는 중형 트럭 | 특허 |
| H_KR1020160052688_Hyundai_brake_disc_temperature_estimation.pdf | KIPRIS | 시동 끈 뒤 200분 안 재시동 시 냉각수온 차로 보정한 마지막 추정 온도를 이어 씀 | 특허 |
| H_KR1020030075059_..., H_KR1020040102188_..., H_KR1020200175489_..., H_KR1020220159458_..., H_KR1020230186264_..., H_KR1020240078783_..., H_KR1020240189426_... | KIPRIS | 거절 건, 과열 경고, 압력센서 기반, EMB 기반, 분진량 예측. 차속·페달 입력 방식과 맞지 않음 | 특허 |
| H_US20150081159_Hyundai_sensing_controlling_brake_lining_wear_uspto.pdf | USPTO | 수집만 하고 본문 미검토 | 특허 |
| H_US7244003_InternationalTruck_airbrake_pad_wear_temperature_uspto.pdf | https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/7244003 | 상용 트럭 에어브레이크. 온도 증분 K×P×V²(1−e^(−t/T)). 승용 적용성 낮음 | 특허 |

## 접근하지 못한 자료

| 대상 | 결과 |
|---|---|
| UN GTR 24 원문(unece.org), UN R179, wiki.unece.org PMP 문서 | 403(curl, WebFetch 모두) |
| EUR-Lex Euro 7 시행규칙 2025/1706 | 빈 응답 |
| Jensen·Santos 2025, Wear 206138(패드 온도·마모 집중 모델, CC-BY) | ScienceDirect 403, Elsevier API 키 필요, DTU Orbit 403. 사용자가 DTU Orbit에서 직접 받으면 열 모델 계수 확인 가능 |
| Grigoratos 2023 ILS Part I(mdpi.com) | 403. 초록 수치만(PM10 2.2 ~ 9.5 mg/km/브레이크, 질량손실의 50 ~ 65%가 10 µm 초과 또는 측정 전 손실) |
| SAE J2707, J2522, Riefe·Yen SAE 2007-01-1019 | 유료 |
| 현대·기아 정비지침서(gsw.hyundai.com, hyundaitechinfo) | 로그인·구독 필요 |
| Google Patents, Espacenet, Justia, FreePatentsOnline | 503, 403 |
| brakeandfrontend.com | 403 |
