# 냉방·난방(공조) 모델 근거 자료

접근일은 전부 2026-11-02. 파일 접두어는 주제다. 모델에 넣을 수치와 등급은 `model-inputs.md`에 있다.

| 접두어 | 주제 |
|---|---|
| A | 냉매 손실(정상 누설률, 연식별 누설, 사고·돌 손상 같은 비정상 손실, 신차 누설 기준, 한국 잔류율) |
| B | 냉매량과 냉방 능력, 풀다운 시험법과 실차 냉각 시간 |
| C | 냉매 외 냉방 열화(콘덴서 막힘, 풍량·필터, 압축기 내부 누설, 전압) |
| D | 난방(OBD 서모스탯 감시, 엔진 워밍업과 소크, 히터, 하이브리드·전기차 히트펌프·PTC) |
| E | 현대 차종 자료(냉매량, 점검·냉각수 주기, 설명서 문구, TSB, NHTSA 불만·리콜, 하이브리드 난방 특허) |
| F | 세션 간 변동과 정규화(일사, 소크, 탑승자, 송풍, 차속, 실내 열평형 모델, 실내 온도 센서) |
| G | 압력 센서 없이 냉매 부족·서모스탯 고장을 판정하는 특허와 논문 |

다운로드 파일은 하나도 실행하지 않았다. PDF 텍스트 추출, 쪽 렌더링, 계산은 이 폴더 밖 스크립트(`scratchpad/hvacscripts/`의 `pdftext.py`, `render.py`, `htmltext.py`, `derive.py`, `derive2.py`, `nist7350_parse.py`)로 했다. Python은 `python3 -I`, PDF는 venv의 PyMuPDF를 썼고 경로는 인자로 넘겼다. 추출 텍스트는 `scratchpad/hvac_txt/`에 있다. 스캔본(이건호 1998, USPTO 특허 이미지)과 그림 값(EPA 냉각수 곡선)은 쪽을 렌더링해 눈으로 읽었다. 접근 제한, 유료 구독, 봇 확인 화면을 우회하지 않았다. curl이 막혀 WebFetch로 본문만 읽은 경우 `.txt` 캡처로 남기고 첫 줄에 URL과 날짜를 적었다.

현대 국내판 취급설명서는 이미 `../../07_model_specs/ownersmanual/<차종>_2026/`에 있으므로 투싼 세 쪽만 E로 복사했다. 차종별 냉매량은 각 폴더의 `ac-system.html`, 냉각수 용량은 `lubricants*.html`, 주기는 `schedule-normal.html`을 그대로 읽었다. 같은 NIST IR 7350이 두 번 받아져 B 사본만 남겼다.

신뢰도 구분: 규제(13 CCR, eCFR), 정부 보고서(EU 집행위, 독일 연방환경청, CARB 위탁, EPA, ORNL, NIST, DOE), 표준(요약만), OEM(현대 설명서, 현대·GM·Ford 문서와 TSB), 동료심사, 학위논문, 학회, 부품사, 업계지, 특허, 언론, 2차(초록만, 검색 요약, 제3자 사본).

Google Patents 주소는 `GP <번호>`로 줄였다. 실제 주소는 `https://patents.google.com/patent/<번호>/en`이다. USPTO 이미지 PDF는 `https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/<번호>`다.

## A. 냉매 손실

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| A_SchwarzHarnisch2003_EU_MAC_leakage_final_report.pdf | https://climate.ec.europa.eu/system/files/2016-11/leakage_rates_final_report_en.pdf | 앱 인용 확인. 2002-11 ~ 2003-01 독일·스웨덴·포르투갈 정비소 19곳, 1 ~ 6년차 R-134a 300대 측정, 이상치 24대 제외 276대. 52.4 g/년(6.91%), SE 2.30 g, SD 38.25 g, 차량별 0 ~ 208 g(0 ~ 27.0%)(표 9, 그림 9). 원자료 300대는 59.5 g(7.8%), 회수 잔량 +20 g 보정 뒤 52.4 g(표 10). 연식별 g/년(%): 1년차 80.3(10.5), 2년 50.7(7.0), 3년 49.6(6.5), 4년 40.6(5.3), 5년 39.8(4.9), 6년 42.6(5.4)(표 11, 대수 50·65·66·45·29·21). 제조사 그룹 평균 28.8 g(5.3%) ~ 81.9 g(10.6%)(표 13). 평균 684 g 그룹 44.2 g(6.5%), 883 g 그룹 66.9 g(7.7%)(표 14). 오리피스 49.3, TXV 53.4 g(차이 유의하지 않음). 사고·돌 손상은 제외. p.3 "Up to a certain level (e.g. 50%) cumulative losses do not considerably reduce the cooling performance" | 정부 보고서(EU) |
| A_OekoRecherche2001_MAC_irregular_loss_workshop_records.pdf | https://www.oekorecherche.de/sites/default/files/publikationen/mac-loss-2001.pdf | 독일 연방환경청 의뢰. 공식 딜러 9곳 냉매 기록지 841대(7년 이하), 평균 충전 856 g, 개방 시 평균 손실 546 g(64%). 손실 분포: 40% 미만 39%, 40 ~ 100% 미만 23%, 100% 38%. 손실 질량 비중 9 / 24 / 67%(표 1). 전손 원인 사고 약 40%, 경미 충돌·돌·부품 결함 40 ~ 50%. 원인 부품(70건) 콘덴서 42%, 배관 17%, 증발기 17%, 팽창밸브 9%, 압축기 8%(표 2). 회로에 100 ~ 200 g만 남으면 저압 스위치로 정지(p.17). 정비사 경험상 약 40% 손실(시스템마다 35 ~ 45%)까지는 꽉 찬 시스템과 같은 냉방(p.20). 정상 6.3%, 비정상 1.9%/년, 합 8.2%. 점검 차량 21,344대 중 비정상 손실 480건, 연 2.2%(표 7) | 정부 보고서(독일) |
| A_EPA2009_DraftRIA_LDGHG_via_NHTSA_DEIS_E.pdf | https://static.nhtsa.gov/nhtsa/downloads/CAFE/2012-2016%20Docs-PCLT/2012-2016%20Draft%20Environmental%20Impact%20Statement/DEIS_E.pdf | EPA-420-D-09-003 초안 2장. 신차 기준 18 g/년. 일본 78대 단일 증발기 8.6, 이중 13.3, 오키나와 16 g/년. 유럽 J2727 보정 17.0 ± 4.4 g/년. MY2009 J2727 점수 평균 15.1, 최대 30.1, 최저 8.5 g/년. 압축기가 누설의 58%, 그 절반 이상이 축 씰. 운전 중 누설은 정지 시의 최대 20배. 열화 가정 0년 18에서 5년 59 g/년(선형). Vintaging 7.6%/년, 평균 충전 770 g. 50% 잔량에서 재충전 가정 | 정부 보고서(초안) |
| A_CARB_06-341_Clodic2008_nonprofessional_servicing_MAC.pdf | https://ww2.arb.ca.gov/sites/default/files/classic/research/apr/past/06-341.pdf | ARMINES. 신차 5개 차종 10 ± 4 g/년. 실험실: "no significant deterioration in the cooling performance until the refrigerant was reduced to approximately half of manufacturer's specified charge". Vincent 인용 정비는 주로 8 ~ 9년차. DIY 재충전 직전 표본(약 30대) 3분의 2가 75% 미만, 거의 절반이 50% 미만, 63%가 냉방 부족으로 충전 | 정부 보고서(CARB 위탁) |
| A_IMAC_Service_Team_final_report_CARB.pdf | https://ww2.arb.ca.gov/sites/default/files/2021-07/i-mac_service_team_final_report.pdf | SAE I-MAC 서비스팀(2005 ~ 2007). 신형 여유 냉매 1.5 ~ 3 oz(42 ~ 85 g), 누설 예산 예 이음부 10곳 × 3 ~ 5 g + 서비스 밸브 5 ~ 7 g + 축 씰 15 g = 약 70 g/년, 목표 20 g/년 미만. 압력 게이지로 충전량 판정 불가. 교체 부품 1,477건: 압축기 19%, 증발기 12%, 어큐뮬레이터 12%, 콘덴서 10%. 한 정비소 입고 차량 65%가 거의 비어 있음 | 업계·정부 공동 보고서 |
| A_KR_Kim2014_IPCC_workshop_Korea_HFC_emission_factors.pdf | https://www.ipcc-nggip.iges.or.jp/public/mtdocs/pdfiles/1407_Sofia/28_140701_IPCC_WS_SKIM.pdf | 한림대 김승도, IPCC 워크숍 2014. 1차 반응(잔량 비례) 모델. 사용 중 47대 평균 8.7년 잔류율 65.4 ± 3.9%, ε = 0.0526 ± 0.0051/년, 배출계수 5.1 ± 0.5%/년. 차급별 5.6 / 5.0 / 5.2 / 4.6%, 충전 512 ~ 713 g. 폐차 393대 평균 12.4년 잔류 55.6 ± 1.1%. 회수기 회수율 90.6% 보정 | 학회 발표(정부 인벤토리 연구) |
| A_KR_Kim2012_scrap_vehicle_MAC_HFC134a_residual_rate_abstract.html | https://db.koreascholar.com/Article/Detail/242412 | 폐승용차 196대 잔류율 61.2 ± 2.4%, 최근 연식일수록 높음 | 동료심사(초록) |
| A_CARB2010_LEVIII_MAC_discussion_paper.pdf | https://ww2.arb.ca.gov/sites/default/files/2020-02/discussionpaper_leviii_mac_may18_2010_workshop_final_ac.pdf | 신차 누설 9 g/년 상한 제안, IMAC 신차 누설 50% 감축 시연. 겨울 미가동 시 씰 건조로 누설 증가 가능 | 규제기관 문서 |
| A_40CFR86.1867-12_AC_leakage_credit_LII.html | https://www.law.cornell.edu/cfr/text/40/86.1867-12 | J2727 기준 LeakScore 하한 승용 8.3, 트럭 10.4 g/년(전동 압축기 4.1 / 5.2). 733 g 이하 시스템 LeakThreshold 11.0 g/년 | 규제(미국) |
| A_SAE_J2727_201202_abstract.html | https://saemobilus.sae.org/content/J2727_201202 | 부품 기술로 신차 g/년을 추정하는 차트, R-134a·R-1234yf. 부품별 값은 유료 | 표준 요약 |
| A_Vincent2004_SAE_2004-01-2256_abstract.html | https://saemobilus.sae.org/papers/emissions-hfc-134a-light-duty-vehicles-california-2004-01-2256 | 캘리포니아 소형차 16년 약 1.4 kg, 2003년 차량당 약 80 g | 학회(초록) |
| A_CoolingPost_small_leaks_57pct.html | https://www.coolingpost.com/world-news/repairing-small-leaks-may-be-harmful/ | 앱의 "프랑스 57%"와 숫자가 같은 유일한 자료. 2015 Cemafroid/AFCE, 차량이 아니라 상업용 냉동설비. 약 5 g/년 누설이 건수의 57%, 질량 1.12%. 500 g/년 초과가 건수 4%, 질량 91.6% | 업계 언론 |
| A_MOTOR_mag_2019_RRR_charge_margin_press.html | https://www.motor.com/magazine-summary/r-r-really-safeguarding-planet-march-2019/ | 신형 R-1234yf 시스템은 충전 여유 약 10%, 18 oz 시스템은 약 2 oz 손실이 한계라는 주장(근거 데이터 없음) | 업계지 |
| A_MOTOR_mag_Here_comes_R1234yf_press.html | https://www.motor.com/magazine-summary/here-comes-r-1234yf-finally/ | 신형 시스템 7 ~ 8년 무보충 가능, J2842 증발기 내구 | 업계지 |
| A_CARB_06-342_ERG2010_heavy_duty_MAC_leak_study.pdf | https://ww2.arb.ca.gov/sites/default/files/classic/research/apr/past/06-342.pdf | 대형차. 2005년식 이전 306, 2006년식 이후 103 g/년. 로그정규에 가까운 분포, 소수 과다 누설 차량이 평균을 끌어올림 | 정부 보고서(참고) |
| A_Schwarz2007_OekoRecherche_heavy_truck_MAC_leakage.pdf | https://www.oekorecherche.de/sites/default/files/publikationen/ec-or_truck_mac_study_27-01-07.pdf | 트럭 87.8 ± 8.9 g/년(8.3%). 35개월 이하 차량의 연율이 더 높음 | 정부 보고서(참고) |
| A_MinesParis_RIEP_emission_factors.html | https://www.openriep.minesparis.psl.eu/Inventory-and-Forecast/riep/Model-Databases/Emissions-factors | Clodic 계열 인벤토리. 정상+비정상 g/년, 정비 손실 별도. 수치 없음 | 연구기관(2차) |
| A_IIR_ArminesACEA_MAC_leakage_record.html | https://iifiir.org/en/fridoc/refrigerant-leakage-in-mac-new-evidence-from-the-armines-acea-study-23443 | 서지만 | 2차 |
| A_IIR_irregular_MAC_leaks_record_129535.html | https://iifiir.org/fr/fridoc/fuites-irregulieres-des-systemes-de-conditionnement-d-air-mobiles-au-129535 | Wolf 2008 서지, 수치 없음 | 2차 |

## B. 냉매량과 냉방 능력, 풀다운

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| B_NIST_IR7350_residential_HP_cooling_single_faults.pdf | https://nvlpubs.nist.gov/nistpubs/ir/2006/ir7350.pdf | 앱 인용 확인. 주거용 R410A 3톤 스플릿 히트펌프 냉방, TXV, 충전 4.65 kg, 정상상태. 본문에 % 수치 없음, 부록 D 원자료로 계산: 20% 부족 총용량 −4.0 ~ −12.2%, 30% 부족 −8.9 ~ −19.0%, 과충전 10 ~ 30% −3.5 ~ +4.5%. 콘덴서 35% 차폐 −9.0%, 50% −18.6%. 실내 풍량 −31% 습코일 총용량 −7 ~ −8%, 현열 −17 ~ −19%. 압축기 바이패스 9.5% 능력 −8.8%, 27.2% −25.8%. 고정 오리피스 자료 없음 | 정부 보고서(주거용) |
| B_ORNL_Pub108381_residential_charge_fault_review.pdf | https://info.ornl.gov/sites/publications/Files/Pub108381.pdf | ORNL/TM-2018/485. 주거용 6대. 고정 오리피스는 충전 90% 미만부터, TXV는 80% 미만에서 급락(p.1). TXV 한 대 75% 충전 약 86%, 62% 약 70%(그림 2) | 정부 보고서(주거용) |
| B_Lee1998_KSAE_auto_AC_charge_TXV_scan.pdf, B_Lee1998_KSAE_auto_AC_charge_TXV_abstractpage.html | https://koreascience.kr/article/JAKO199811920265247.pdf, https://www.koreascience.or.kr/article/JAKO199811920265247.page?&lang=ko | 두원공조, 한국자동차공학회 논문집 6(2) 1998, 스캔본. 차량용 외부균압 TXV + 리시버, R-134a, 증발기 입구 30 °C 60%, 압축기 1,800 rpm. 표 4 증발기 용량 kcal/h: 500 g 4,329, 600 g 4,826, 700 g 5,483, 800 g 5,308, 900 g 5,388, 1,000 g 5,503. 과열도 33.3, 25.2, 12.4, 10.6, 10.9, 9.7 °C. COP 1.81, 1.88, 1.97, 1.91, 1.92, 1.92. 증발기 출구 공기 500 g 14.1, 600 g 10.7, 700 g 9.7, 1,000 g 9.2 °C. 적정 700 ~ 900 g | 동료심사(국내) |
| B_Macagnan2013_COBEM_MAC_charge_dutycycle.pdf | https://www.abcm.org.br/anais/cobem/2013/PDF/1415.pdf | 6.4 kW급 차량 시스템, TXV + 리시버, 정격 1,450 g. −35 ~ +28%에서 용량 약 3.5 kW로 평탄(그림 2). Huyghe 2011(SAE) 재인용: 75 ~ 100%에서 영향 무시 가능 | 학회 |
| B_AtikAktas2011_MAC_charge_effects.pdf | https://dergipark.org.tr/en/download/article-file/400736 | 오리피스 튜브 벤치, R-134a. 권장 750 g인데 벤치 최적 500 g. 최적 대비 −20%에서 −12%, −40%에서 −19%, +20%에서 −25% | 동료심사(신뢰도 낮음) |
| B_DyankovKadikyanov2025_Ruse_MAC_charge_LagunaTXV.pdf | https://conf.uni-ruse.bg/bg/docs/cp25/bp/bp-19.pdf | Renault Laguna, TXV·가변용량 압축기, 정차 35 °C. 제어 PWM 듀티 정격 0.57, −50%에서 +23%. 냉방 능력은 측정하지 않음 | 학회 |
| B_ORNL_TM-2016-275_AC_pulldown_period.pdf | https://info.ornl.gov/sites/publications/files/Pub68201.pdf | 앱 인용 확인. 2009 Corolla·Explorer, 땡볕 주차 뒤 MAX A/C·최대 풍량·내기, 실도로. 뒷좌석 머리 높이 열전대. "Pulldown of back seat temperature from 128-134°F initial temperature to 85°F generally took 8-12 minutes"(p.5). 80 °F 도달 8 ~ 25분, 압축기 100% 듀티 7 ~ 25분(표 1·2). 그림 1·2 벤트·실내 시간 곡선. "1.9배"는 원문에 없고 Explorer 비슷한 시작 온도의 800초와 1,500초에서 나온 비 | 정부 보고서 |
| B_40CFR86.160-00_SC03_procedure.html, B_40CFR86.161-00_AC_test_cell.html, B_40CFR1066.835_SC03_procedure.html | https://www.law.cornell.edu/cfr/text/40/86.160-00, https://www.law.cornell.edu/cfr/text/40/86.161-00, https://www.law.cornell.edu/cfr/text/40/1066.835 | SC03: 95 °F, 100 gr/lb, 일사 850 ± 45 W/m², 10분 소크 뒤 600초. MAX A/C, 내기, 최대 풍량(자동 공조 72 °F). 실내 온도 합격 기준 없음(배출가스 시험) | 규제(미국) |
| B_40CFR1066.845_AC17_test.html | https://www.law.cornell.edu/cfr/text/40/1066.845 | AC17: 22 ~ 28 °C, 805 W/m² 도달 뒤 30분 소크. 실내 온도 5초마다, 중앙 벤트와 머리받침 뒤 30 mm. 합격 기준 없음 | 규제(미국) |
| B_capture_SAE_J2765_J1503_J2842_scope_2nd.txt | https://saemobilus.sae.org/content/J2765_201707/ 외 2건 | J2765는 정상상태 벤치 용량·COP, 과도 시험 범위 밖. J1503은 오프로드 장비. J2842는 증발기 인증 | 표준 요약(2차) |
| B_capture_abstracts_Song2023_Datta2014_IS14618_2nd.txt | https://www.mdpi.com/1996-1073/16/24/8017 외 2건 | Song 2023 EV R-1234yf 냉방 최적 0.9 kg. 인도 IS 14618 초안: 40 °C 이상, 800 W/m² 이상, 40분 시점 코 높이 평균 온도를 제조사가 선언 | 2차 |

## C. 냉매 외 냉방 열화

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| C_IIR_Datta_obstructed_condenser_airflow_automotive_abstract.html | https://iifiir.org/en/fridoc/obstructed-airflow-through-the-condenser-of-an-automotive-air-137873 | Datta 외, Appl. Therm. Eng. 70(1) 2014. 차량 마이크로채널 콘덴서 52회 시험, 면적 50% 차폐 시 냉방 능력 −8.16%, COP −16.8%. 측면·중앙 차폐가 더 나쁨 | 동료심사(초록) |
| C_Datta2016_Purdue_IRACC1712_battery_voltage_blower_abstract.html | https://docs.lib.purdue.edu/iracc/1712 | 배터리·발전기 전압이 낮으면 블로어·콘덴서 팬 속도가 줄고 토출 압력·온도 상승. 수치 없음(PDF는 Cloudflare 확인 화면) | 학회(초록) |
| C_Johansson1999_AIVC_automotive_cabin_filter_performance.pdf | https://www.aivc.org/sites/default/files/members_area/medias/pdf/Conf/1999/paper095.pdf | 새 실내 필터 차압 70 L/s에서 40 ~ 80 Pa, PM10 3 ~ 4 g 적재 시 약 +100 Pa, 이후 비선형. 일반 차량 풍량 70 L/s(250 m³/h) | 학회 |
| C_AIVC_1999_car_cabin_filter_loading_abstract.html | https://www.aivc.org/node/25601 | 위 논문 초록 | 2차 |
| C_SAE_960940_cabin_filter_field_loading_abstract.html | https://saemobilus.sae.org/papers/particulate-filter-performance-north-american-environment-960940 | 약 24,000 km 뒤 애리조나 필터 포집량이 앨라배마의 3배 이상 | 학회(초록) |
| C_Wei2020_ESPR_cabin_PM_filter_age_500h_aged_filter.pdf | https://research.chalmers.se/publication/517377/file/517377_Fulltext.pdf | 앱 필터 인용의 원 논문. 500 h는 상하이 외기 덕트(PM2.5 월평균 42 µg/m³, 저속 약 200 kg/h)에서 만든 노화 조건이고 "약 1년 주행, 중국 권장 교체 주기"로 설명. 측정은 걸러냄(PM2.5 I/O 0.20에서 0.60)뿐, 풍량·차압 값 없음 | 동료심사 |
| C_Wei2023_Chalmers_PhD_thesis_vehicle_cabin_air_quality_500h_filter.pdf | https://research.chalmers.se/publication/535304/file/535304_Fulltext.pdf | 같은 500 h 서술. 교체 시점은 효율보다 차압 증가가 결정할 가능성(p.48) | 학위논문 |
| C_BreukerBraun1998_rooftop_faults_abstract_IIR.html | https://iifiir.org/en/fridoc/common-faults-and-their-impacts-for-rooftop-air-conditioners-112848 | 루프탑 고장 영향 정량화, 초록에 수치 없음 | 2차 |
| C_IMEKO_823_automotive_AC_failure_thermal_abstract.html | https://imeko.org/index.php/proceedings/823-mechanical-failure-diagnosis-in-automotive-air-conditioning-systems-through-thermal-measurements | 차량 압축기·콘덴서 고장 열측정, 수치 없음 | 2차 |

## D. 난방

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| D_13CCR1968.2_2006_clean_fulltext.pdf | https://ww2.arb.ca.gov/sites/default/files/barcu/regact/obdii06/19682clean.pdf | 앱 인용 원문. (e)(10.2.1)(A): "Executive Officer approved time interval" 안에 (i) 다른 진단의 최고 허용 온도 또는 (ii) 공칭 조절 온도의 20 °F 이내에 못 닿으면 고장. 시간 숫자 없음. (C) 정상 서모스탯으로도 못 닿는 차량에 대체 기준, 시동 50 °F 초과에서는 감지해야 함. (10.3.1)(A) 시동 ECT가 판정 온도보다 낮은 모든 주행 사이클에서 1회. (B) 외기 20 °F(−6.7 °C) 미만 비활성 허용. (C) 공회전이 워밍업 시간의 50% 초과, 재시동 등은 신청으로 비활성. 단 시동 ECT가 판정 온도보다 35 °F 넘게 낮으면 원칙적으로 불허 | 규제(캘리포니아) |
| D_13CCR1968.2_ACCII_2022_final_reg_order.pdf | https://ww2.arb.ca.gov/sites/default/files/barcu/regact/2022/accii/acciifro1968.2.pdf | 2022 개정. (e)(10) 번호와 기준 유지, SULEV15 등 대체 조항 추가 | 규제 |
| D_13CCR1968.1_2002_finreg_sec11_thermostat.pdf | https://ww2.arb.ca.gov/sites/default/files/barcu/regact/obdii/finreg.pdf | 구 1968.1 (11.0). 2000 ~ 2002 MY 단계 도입, 20 °F 이내, 20 °F 미만 비활성 | 규제(구판) |
| D_40CFR86.1806-17_eCFR.xml | https://www.ecfr.gov/api/versioner/v1/full/2025-01-01/title-40.xml?part=86&section=86.1806-17 | 연방 OBD가 2013년판 13 CCR 1968.2를 인용 편입 | 규제(미국) |
| D_Ford_2012MY_OBD_System_Operation_Summary_Gasoline.pdf | https://www.newtonnet.co.uk/fiesta/fordobd2012.pdf | p.124. 모델 ECT가 WUT(보통 조절 온도 −20 °F)를 넘은 뒤 300초 지연 후 실측 비교. 진입: 시동 ECT 125 °F(51.7 °C) 이하, 흡기 20 °F 이상. 판정 ECT < 172 °F(192 °F 서모스탯). 70 °F에서 300초 안에 완료. 공회전·저부하 비율 50% 초과면 판정 보류. 듀얼 히터 차량과 "sits at idle with the heater on high and the defroster fan on high"면 정상 서모스탯도 조절 온도에 못 닿을 수 있음 | OEM |
| D_GM_2011_OBDG11_engine_diagnostics_P0128.pdf | https://gsi.ext.gm.com/gmspo/mode6/pdf/2011/11OBDG11%20Engine%20Diagnostics.pdf | GM 디젤. P0128 진입 일부, 블록히터 판정(500 rpm 초과 60초에 ECT 변화 1.8 °C 미만). 본표 P0128 행은 추출 깨짐 | OEM(부분) |
| D_SaabWIS_9-4X_2011_GM_V6_P0128_capture.txt | https://saabwisonline.com/9-4x/2011/2-engine/engine-management-and-fuel-system-v6/fault-diagnosis-diagnostic-trouble-codes/dtc-p0128-lau | GM ECM. 흡기 누적량으로 ECT 모델. Path1 약 15분(시동 ECT < 51 °C, 8 km/h 초과, 누적 공기 3,459 g 초과), Path2 약 8분. 실측 ECT가 89 °C보다 11 °C 이상 낮은 상태 4초 초과면 고장. 진단 보조 절차는 약 8 h 소크 후 20분 주행 | 정비 문서 사본 |
| D_SaabWIS_9-2X_2005_Subaru2.0T_P0128_capture.txt | https://saabwisonline.com/9-2x-9346/2005/0-general/general-diagnostics-2-0t/diagnostic-trouble-code-dtc-detecting-criteria/fault-diagnosis-fault-codes/dtc-p0128-coolant-thermostat-coolant-temperature-below-thermostat-regulating-temperature | Subaru 로직. 외기 −7 °C 이상, 시동 ECT < 55 °C, 추정 ECT 75 °C 이상인데 실측 < 75 °C이고 차이 > 20 °C가 30초면 고장, 2사이클 연속 시 경고등 | 정비 문서 사본 |
| D_GM_TSB_NHTSA_MC-10113562_heater_core_pulls_more_heat_than_idle.pdf | https://static.nhtsa.gov/odi/tsbs/2015/MC-10113562-9999.pdf | GM PI1166C. 혹한에 블로어 최고단이면 히터 코어가 공회전·저속 엔진 발열보다 많은 열을 빼서 워밍업이 늦어짐, 3단 권장. AUTO는 이미 워밍업 중 블로어를 낮춤. "DO NOT replace the engine coolant thermostat unless a DTC code is set ... the ECM monitors the thermostat each key cycle" | OEM TSB |
| D_EPA_MOVES_2017_revising_LD_start_soak.pdf | https://www.epa.gov/sites/default/files/2017-08/documents/02-revising-light-duty-start-soak-relationships-2017-06-07.pdf | 2009 Camry·Explorer, 실온 소크 3 ~ 720분 뒤 출발 냉각수 곡선(슬라이드 14·15, 그림). FTP Bag1과 냉시동은 720분 이상 소크 | 정부 발표(그림) |
| D_EPA_MOVES_2018_LD_start_soak_relations.pdf | https://www.epa.gov/sites/default/files/2018-09/documents/04-update-revised-ld-start-soak-relations-2018-06-13.pdf | 소크 구간 0 ~ 6분부터 12 h 이상, PEMS·동력계 병합 | 정부 발표 |
| D_49CFR571.103_FMVSS103_defrost_eCFR.xml | https://www.ecfr.gov/api/versioner/v1/full/2025-01-01/title-49.xml?part=571&section=571.103 | 성에 제거 시험: 처음 5분 워밍업, 이후 35분 1,500 rpm 이하 또는 40 km/h 부하 | 규제(미국) |
| D_Humphries2026_WEVJ_17_168_BEV_heat_pump_vs_PTC_cold.pdf | https://mdpi-res.com/d_attachment/wevj/wevj-17-00168/article_deploy/wevj-17-00168.pdf | 캐나다 환경부. 같은 차종 PTC(2020)와 히트펌프(2022), −10, −7, 0, 25 °C 밤새 소크, 22 °C AUTO. 냉시동 사이클 난방 부하가 가장 큼. −10 °C 냉시동 히트펌프 피크 "almost 8.75 kW", PTC보다 높음("lossy mode"). 0 °C에서는 히트펌프가 PTC의 절반 미만 | 동료심사, OA |
| D_Energies2023_16_8017_EV_HP_refrigerant_charge_heating_mode.pdf | https://mdpi-res.com/d_attachment/energies/energies-16-08017/article_deploy/energies-16-08017.pdf | 부산대·KORENS. R-1234yf 차량 히트펌프. 난방 최적 0.7 kg, 냉방 0.9 kg. 0.55 kg 미만이면 효율 급락, 사이클 미형성. COP(그림) 0.55 kg 약 1.05, 0.70 kg 약 1.15 | 동료심사, OA |
| D_Energies2016_9_881_EV_EVI_heat_pump_-20C.pdf | https://mdpi-res.com/d_attachment/energies/energies-09-00881/article_deploy/energies-09-00881.pdf | SJTU R-134a EVI 시제품. −10 °C 1,350 ~ 1,986 W(COP 1.83 ~ 1.25), −20 °C 1,042 ~ 1,840 W(COP 1.78 ~ 1.07) | 동료심사(벤치) |
| D_Lee2014_KJACR_HallaVisteon_R134a_EV_heat_pump_heating.pdf | https://www.koreascience.or.kr/article/JAKO201417840302935.pdf | 한라비스테온. 인용: −10 °C 약 2.5 kW, 0 °C 전기히터 대비 약 70% 향상, −10 °C에서 PTC 병행, 보조 PTC 1.6 / 3.0 kW. 냉각수 열원이 공기 열원보다 36 ~ 48% 우수(10 °C) | 동료심사(국내) |
| D_Hyundai_newsroom_2020_heat_pump_technology.html | https://www.hyundai.com/worldwide/en/newsroom/detail/recycling-more-heat-hyundai-and-kia-turn-up-ev-efficiency-with-new-heat-pump-technology-0000000683 | PE 모듈·배터리·충전기 폐열 회수. −7 °C 공조 켬에서 26 °C 대비 주행거리 90% 유지(코나·니로) | OEM 보도자료 |
| D_HyundaiEU_press_IONIQ5_winter_heat_pump.html | https://www.hyundai.news/eu/articles/press-releases/hyundai-ioniq-5-makes-the-ideal-winter-driving-companion.html | 아이오닉 5 히트펌프, 원격 예열, 배터리 컨디셔닝. 수치 없음 | OEM 보도자료 |
| D_Steinstraeter2021_WEVJ_12_115_low_temp_EV_range_heating.pdf | https://mdpi-res.com/d_attachment/wevj/wevj-12-00115/article_deploy/wevj-12-00115.pdf | 난방으로 주행거리 최대 31.9% 감소. 같은 경로 −1.5 °C 맑음 0.60 kWh, 2 °C 눈 1.05 kWh(표 6) | 동료심사, OA |
| D_PLOSONE2025_EV_cabin_extreme_cold_-45C.pdf | https://journals.plos.org/plosone/article/file?id=10.1371/journal.pone.0337212&type=printable | 히터를 끈 실내가 0 °C까지: −20 ~ −25 °C 18분, −30 ~ −35 °C 12 ~ 13분, −40 ~ −45 °C 9 ~ 10분 | 동료심사, OA |
| D_ANL_Rask2011_AMR_vss031_cold_temp_HEV_warmup.pdf | https://www1.eere.energy.gov/vehiclesandfuels/pdfs/merit_review_2011/veh_sys_sim/vss031_rask_2011_o.pdf | 2010 프리우스 저온 워밍업 전략, 배기열 회수 약 90 °C 바이패스 | 정부 발표 |
| D_DOE_fueleconomygov_cold_weather.html | https://www.fueleconomy.gov/feg/coldweather.shtml | 20 °F 대 77 °F 시내: 가솔린 −15%(단거리 −24%), HEV −30 ~ −34%, EV 주행거리 −41%, 추가 에너지의 약 2/3가 실내 난방 | 정부 |
| D_Chalmers2023_MSc_thermal_cabin_model_Volvo_heatup.pdf | https://odr.chalmers.se/bitstreams/8486fe73-c944-4142-856e-e25b97ab5275/download | Volvo 풍동 heat-up 시험 조건(−18 °C, 50 km/h 바람). 값은 정규화돼 숫자 없음 | 학위논문(조건만) |
| D_COBEM2009_COB09-0320_engine_heat_rejection_to_coolant.pdf | https://www.abcm.org.br/anais/cobem/2009/pdf/COB09-0320.pdf | 연료 에너지 중 냉각수로 가는 비율 16 ~ 35%(문헌 인용) | 학회(2차 성격) |
| D_Kim2025_Energy335_HEV_cold_heat_pump_engine_on_abstract.txt | https://pure.korea.ac.kr/en/publications/advanced-thermal-management-of-hybrid-electric-vehicles-in-cold-c/ | 고려대. HEV는 추위에 실내 난방 때문에 엔진이 자주 켜짐. 히트펌프로 엔진 가동 시간 −10.1%, 연비 +9.7% | 동료심사(초록) |

## E. 현대 차종

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| E_Hyundai_OM_NX4_2026_KR_ac-system.html | https://ownersmanual.hyundai.com/full_webhelp/NX4/2026/ko_KR/vehicle_information_air_conditioner_system.html (`../../07_model_specs/ownersmanual/NX4_2026/ac-system.html` 복사) | 냉매 R-1234yf 550 ± 25 g, 냉동유 PAG 30 120 ± 10 g. 다른 차종은 같은 폴더 `ac-system.html` | OEM |
| E_Hyundai_OM_NX4_2026_KR_ac-refrigerant.html | https://ownersmanual.hyundai.com/full_webhelp/NX4/2026/ko_KR/id22CKHD0B00A.html (복사) | "냉매량이 부족하면 에어컨의 성능이 저하됩니다. 또한 충전을 지나치게 하는 것도 에어컨에 좋지 않은 영향" | OEM |
| E_Hyundai_OM_NX4_2026_KR_schedule-normal.html | https://ownersmanual.hyundai.com/full_webhelp/NX4/2026/ko_KR/id6b219dd157c.html (복사) | 에어컨 냉매 점검 및 작동 상태 점검 매 12개월. 냉각수 최초 20만 km 또는 10년, 이후 4만 km 또는 2년 | OEM |
| E_Hyundai_OM_NX4_2025_US_ac-system-spec.html | https://ownersmanual.hyundai.com/full_webhelp/NX4/2025/en_US/id2d918a0499d.html | 19.40 ± 0.88 oz(550 ± 25 g) R-1234yf, PAG 120 ± 10 g | OEM(미국) |
| E_Hyundai_OM_NX4_2025_US_normal-maint-schedule-2.html | https://ownersmanual.hyundai.com/full_webhelp/NX4/2025/en_US/ida26dbc5c62b.html | 에어컨 냉매·압축기 12개월 또는 8,000 mi마다 점검 | OEM(미국) |
| E_Hyundai_OM_NX4_2025_US_normal-maint-schedule.html | https://ownersmanual.hyundai.com/full_webhelp/NX4/2025/en_US/topic_ybb_1yh_lwb.html | 냉각수 최초 120,000 mi 또는 120개월, 이후 24,000 mi 또는 24개월 | OEM(미국) |
| E_Hyundai_OM_NX4_2025_US_explanation-ac-refrigerant.html | https://ownersmanual.hyundai.com/full_webhelp/NX4/2025/en_US/id2ee8f50a762.html | "Check the air conditioning lines and connections for leakage and damage" | OEM(미국) |
| E_Hyundai_OM_NX4_2025_GN_system-maintenance.html | https://ownersmanual.hyundai.com/full_webhelp/NX4/2025/en_GN/id28824ad7b0aa.html | "If the amount of refrigerant is too low or too high, the performance of the air conditioning is reduced" | OEM(일반 시장) |
| E_Hyundai_OM_NE1_2025_US_ac-system-spec.html | https://ownersmanual.hyundai.com/full_webhelp/NE1a/2025/en_US/id7847a83de03.html | 히트펌프 900 ± 25 g, 미적용 700 ± 25 g(R-1234yf). POE 190 / 150 g | OEM(미국) |
| E_Hyundai_OM_NE1_2025_GN_ac-system-spec.html | https://ownersmanual.hyundai.com/full_webhelp/NE1/2025/en_GN/id0ba3d8bfda6.html | R-1234yf 900 / 700 g, R-134a 950 / 750 g | OEM(일반 시장) |
| E_Hyundai_OM_NE1_2025_US_normal-maint-schedule-1.html, E_Hyundai_OM_NE1_2025_US_normal-maint-schedule.html | https://ownersmanual.hyundai.com/full_webhelp/NE1a/2025/en_US/idc415036e46f.html, https://ownersmanual.hyundai.com/full_webhelp/NE1a/2025/en_US/id638b3ce41d6.html | 압축기·냉매·성능 12개월 또는 8,000 mi 점검. 냉각수 200,000 km 또는 120개월, 이후 40,000 km 또는 24개월 | OEM(미국) |
| E_Hyundai_OM_NE1_2024_US_system-maintenance_PTC-inner-condenser.html | https://ownersmanual.hyundai.com/full_webhelp/NE1/2024/en_US/id0d0d37e548b.html | 공조 구성도에 "PTC & Inner condenser" | OEM(미국) |
| E_Hyundai_OM_NE1_2026_KR_cabin-filter-hvac-performance.html | https://ownersmanual.hyundai.com/full_webhelp/NE1/2026/ko_KR/id23C1E02A0Y4.html | "필터에 쌓이면 풍량이 약해져 냉난방 성능이 저하되며, 악취가 발생할 수 있습니다" | OEM |
| E_Hyundai_OM_NE1_2026_KR_auto-climate.html | https://ownersmanual.hyundai.com/full_webhelp/NE1/2026/ko_KR/convenience_device_automatic_climate_control_system.html | READY가 꺼진 시동 ON 상태에서도 작동하지만 냉난방 효율이 떨어짐 | OEM |
| E_Hyundai_OM_AXEV_2025_GN_ac-system-spec.html | https://ownersmanual.hyundai.com/full_webhelp/AXEV/2025/en_GN/idefcd8797d62.html | R-1234yf 히트펌프 550 ± 25 g, 미적용 475 ± 25 g. POE 180 g | OEM(일반 시장) |
| E_Hyundai_OM_AXEV_2025_GN_normal-maint-schedule.html | https://ownersmanual.hyundai.com/full_webhelp/AXEV/2025/en_GN/id2685fd67d59.html | 에어컨 24개월 또는 30,000 km 점검, 냉각수 180,000 km 또는 10년 뒤 30,000 km 또는 24개월(국내판과 다름) | OEM(일반 시장) |
| E_NHTSA_TSB_MC-10241804-0001.pdf | https://static.nhtsa.gov/odi/tsbs/2023/MC-10241804-0001.pdf | 23-HA-002H(2023-08), 아이오닉 5 22MY 이후 "inoperative heater or air conditioning", DTC B169588 등, PTC 히터 97191-GI001FFF·고전압 퓨즈 30 A 교체 | OEM TSB |
| E_NHTSA_TSB_MC-10182325-0001.pdf, E_NHTSA_TSB_MC-11017341-0001.pdf | https://static.nhtsa.gov/odi/tsbs/2020/MC-10182325-0001.pdf, https://static.nhtsa.gov/odi/tsbs/2025/MC-11017341-0001.pdf | 쏘나타 DN8: 엔진룸 정션박스 단자·배선 손상으로 "cold air not blowing" | OEM TSB |
| E_NHTSA_TSB_MC-11024538-0001.pdf | https://static.nhtsa.gov/odi/tsbs/2025/MC-11024538-0001.pdf | 2024 ~ 26MY 쏘나타·하이브리드 공조 제어기가 꺼지지 않아 방전, SW 업데이트 | OEM 캠페인 |
| E_NHTSA_TSB_MC-11015479-0001.pdf | https://static.nhtsa.gov/odi/tsbs/2025/MC-11015479-0001.pdf | 아반떼 CN7 2021 ~ 24MY 중앙 벤트 풍량 부족, 모드 액추에이터 레버 교체 | OEM TSB |
| E_NHTSA_TSB_MC-10138238-9999.pdf | https://static.nhtsa.gov/odi/tsbs/2018/MC-10138238-9999.pdf | 투싼 TL(이전 세대) 증발기 온도 센서 교체와 공조 ECU 업데이트, "A/C PERFORMANCE IMPROVEMENT" | OEM TSB(구세대) |
| E_NHTSA_complaints_Hyundai_Tucson_2022.json, …_Elantra_2021.json, …_Sonata_2020.json, …_SantaFe_2024.json, …_Ioniq5_2022.json | https://api.nhtsa.gov/complaints/complaintsByVehicle?make=hyundai&model=tucson&modelYear=2022 (model·modelYear만 바꿈) | 전체 389 / 132 / 182 / 156 / 407건. A/C·냉매·압축기 본문 언급 3 / 1 / 14 / 4 / 25건(아이오닉 5는 대부분 ICCU 고장 중 언급, 쏘나타는 블로어 퓨즈 소손). 안전 결함 DB라 공조 고장률로 쓰지 않음 | 정부 |
| E_NHTSA_recalls_Hyundai_Ioniq5_2022.json, E_NHTSA_recalls_Hyundai_Tucson_2022.json | https://api.nhtsa.gov/recalls/recallsByVehicle?make=hyundai&model=ioniq%205&modelYear=2022 외 | 공조 리콜 없음 | 정부 |
| E_US10850592B2_HMC_HEV_engine_control_for_heating_gp.html | GP US10850592B2 | 현대·기아. 하이브리드는 냉각수 또는 토출 공기 온도가 난방 요구 온도보다 낮으면 "forced engine driving for heating", 그 전에 냉각수를 일정 이상으로 유지하는 선행 엔진 구동 추가. 문턱 수치 없음 | 특허(현대) |
| E_US20190126910A1_HMC_HEV_engine_operation_gp.html | GP US20190126910A1 | 현대·기아 HEV 엔진 가동 제어(촉매 가열 중심). 촉매 온도를 냉각수 온도, 운전 시간·부하, 정지 경과 시간으로 추정 | 특허(현대) |

국내판 `ac-system.html`의 냉매량: 투싼 550, 투싼 HEV 550, 쏘나타 520, 쏘나타 HEV 600, 싼타페·싼타페 HEV 앞좌석 625 / 앞뒤 800, 아이오닉 5 900, 캐스퍼 400, 캐스퍼 일렉트릭 550 g(모두 ± 25 g, R-1234yf). 아반떼 500 ± 25 g은 PDF 설명서(`../../07_model_specs/ownersmanual/CN7_2026/CN7_2026_ko_KR.pdf`, CN7HEV 같음)에서 읽었다. 냉각수 용량은 투싼 8.6, 투싼 HEV 7.3, 쏘나타 2.0 6.8, 싼타페 9.133, 싼타페 HEV 8.9, 캐스퍼 1.0 5.1 L(`lubricants*.html`).

## F. 세션 간 변동과 정규화

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| F_Brusey2017_arXiv1704.07899_cabin_RL_model.pdf | https://arxiv.org/pdf/1704.07899 | 앱 시뮬레이터 원본. 실내 2.5 m³, 공기 약 24 kJ/K, 외피 약 23 W/K, 실내 질량 결합 1/Rm = 75 × 1.08 = 81 W/K, 실내 질량 70.65 kJ/K. 일사 150 W, 탑승자 120 W 고정. 누기·차속 없음. Jaguar XJ 기후 풍동 검증 | 프리프린트 |
| F_TorregrosaJaime2015_ATE_cabin_transient_model_authorver.pdf | https://riunet.upv.es/server/api/core/bitstreams/510361af-9679-4767-bedc-b8cc14395268/content | 미니버스. 1인 현열 70 W, 잠열 35 W. 외기 유입 부하 7 ~ 53%, 일사 18 ~ 31%. 923 대 200 W/m² 부하 차 2,804 W. 외기 1 K당 136.9 W | 동료심사(저자판) |
| F_Fayazbakhsh2015_SFU_PhD_thesis_vehicle_AC_loads.pdf | https://summit.sfu.ca/_flysystem/fedora/sfu_migrate/15908/etd9346_MFayazbakhsh.pdf | 열평형 부하 모델. 지수 풀다운은 가정. 예제 34.4 °C, 목표 23 °C, 풀다운 600초, 최대 부하 약 3,000 W | 학위논문 |
| F_Rugh2013_NREL_SAE2013-01-0553_solarPVB_soak_cooldown.pdf | https://digital.library.unt.edu/ark:/67531/metadc827481/m2/1/high_res_d/1078075.pdf | NREL. 반복 시험일 기준 일사 700 W/m² 초과, 풍속 3 m/s 미만. 벤트 0.136 kg/s, 77 km/h. 벤트 온도(그림 4) 시작 약 48 °C, 1분 약 15, 2.5분 약 10, 5분 약 8, 15분 약 6.5 °C | 정부 연구소 논문(사본) |
| F_Lee2011_KAIS_electric_AC_FCEV_driving_vs_idle.pdf | https://koreascience.kr/article/JAKO201106064731729.pdf | 자동차부품연구원, 수소차 전동 압축기·TXV. 실내 40 °C, 외기 20 °C, 최대 풍량, 내기. 24 °C 도달 정차 9.2분, 50 km/h 6.0분, 100 km/h 9.6분. COP 정차 대비 +14.8%(50), +22.2%(100). 외기 30 °C면 24 °C까지 16.0분, 실내 평균 +5.2 °C | 동료심사(국내) |
| F_McLaren2005_Pediatrics_enclosed_vehicle_temp_rise.pdf | https://www.noheatstroke.org/s/Null_AAP.pdf | 맑은 날 어두운 세단 60분 평균 +40 °F(+22 °C), 상승의 80%가 처음 30분, 외기와 무관 | 동료심사 |
| F_Nain2015_Thapar_MEthesis_rear_cabin_cooldown.pdf | https://tudr.thapar.edu/bitstreams/027ca300-233c-43db-b57a-b3686fb5ad55/download | 뒷좌석 10분 하강 블로어 2 / 3 / 4단 11.6 / 12.5 / 15.7 °C(다른 날, 통제 없음) | 학위논문 |
| F_Amphenol_JS6780_active_incar_sensor.html | https://amphenol-sensors.com/en/thermometrics/assemblies/3379-js6780 | 팬 흡입식 실내 센서 열 시정수 최대 10초 | 부품사 |
| F_arXiv2605.05919_EV_HVAC_traffic_climate_cabin_RC.pdf | https://arxiv.org/pdf/2605.05919 | 1노드 실내 1.8 × 10⁵ J/K, 외기 55 W/K, HVAC 최대 15 kW, 냉시동 유효 열용량 약 200 kJ/K | 프리프린트 |
| F_arXiv2604.24726_VEHRON_BEV_sim.pdf | https://arxiv.org/pdf/2604.24726 | 식 구조만(UA = UAbody + kv·v) | 프리프린트 |
| F_Lustbader2016_DOE_AMR_vs134_vehicle_thermal.pdf | https://www.energy.gov/sites/default/files/2016/06/f33/vs134_lustbader_2016_o_web.pdf | CoolSim 1 ~ 2 공기 노드라는 언급뿐 | 정부 발표 |

## G. 판정 특허와 논문

| 파일 | 출원인·저자 | 주는 내용 | 신뢰도 |
|---|---|---|---|
| G_US7337619_refrigerant_charge_assess_ambient_clutch_uspto.pdf | Ford 2008 | 압력 없이 외기 온도·습도, 샘플 기간 최소 벤트 온도, 클러치 사이클 수, 압축기 ON 시간으로 충전율 회귀(MARS 12항, 2003 Taurus). 힌지 상수·탐지율 미공개 | 특허 |
| G_US8830079_low_AC_refrigerant_detection_uspto.pdf | Ford 2014 | 증발기 출구 냉매 온도와 출구 공기 서미스터 차를 외기별 문턱과 비교, 안정 조건(압축기·블로어·차속·팬) 필요. "calibrated to detect low charge at 40% charge level". 카운터 1차 DTC, 2차 압축기 정지 | 특허 |
| G_US5251453_low_charge_detection_automotive_uspto.pdf | GM 1993 | 외기·증발기 온도·차속 선형식, A/C 요청 뒤 지연·차속 조건, 문턱 초과 횟수로 저충전 플래그. 상수 미공개 | 특허 |
| G_US5201862_low_charge_protection_clutch_cycling_uspto.pdf | GM 1993 | 오리피스 튜브(CCOT)에서 냉매 부족은 짧은 압축기 ON 시간과 높은 흡입 온도. 선행 특허 인용: 짧은 ON 3회 연속을 유효 판정 | 특허 |
| G_US10870332_vehicle_AC_refrigerant_shortage_discharge_temp_uspto.pdf | Sanden 2020 | 압축기 회전수와 정상 토출 온도 관계를 보유하고 실측 토출 온도와 비교(히트펌프 차량) | 특허 |
| G_EP2581242A1_vehicle_AC_refrigerant_leak_diagnosis_epo.html | Sanden 2013 | 압력 센서 2개로 체적 유량, 맵 문턱과 비교(압력 필요, 대조 사례). URL https://data.epo.org/publication-server/rest/v1.2/patents/EP2581242NWA1/document.html | 특허 |
| G_US20050224019_thermostat_monitoring_modeled_coolant_uspto.pdf | 한국 우선권(출원인 표기 없음) | 모델 냉각수 온도로 서모스탯 열림 고착 판정. 정상 85 °C, 2차 74 °C. 외기 보정 −7 °C 0.5, 30 °C 1.0. 블로어 보정 최대 0.5, 끔 1.0(외기 5 °C 미만). 금지: 부하 30% 미만 시간 80% 초과, 연료 차단 50% 초과, 10 km/h 미만 시간 50% 초과 | 특허 |
| G_US8046150_GM_cooling_system_diag_two_ECT_uspto.pdf | GM 2011 | 엔진 출구와 라디에이터 냉각수 온도 차를 엔진 발열(연료, 흡기량, 히터 요청)과 비교 | 특허 |
| G_ACEEE2008_3-514_virtual_charge_sensor.pdf | Li & Braun 2008 | 가정·상업용. 표면 온도 4점으로 충전량 추정, 75 ~ 125%에서 ± 9%, 범위 밖 ± 15%, 조정 시 ± 5%. URL https://www.aceee.org/files/proceedings/2008/data/papers/3_514.pdf | 학회 |
| G_OpenAlex_EnergyAI2024_ebus_AC_FDD_undercharge.json | Guo 외 2024, Energy and AI | 전기버스 플릿 동료 비교 비지도 방식 + GPR 불확실성 제외로 냉매 부족을 낮은 오경보로 검출(초록, 수치 없음). URL https://api.openalex.org/works/doi:10.1016/j.egyai.2024.100364 | 동료심사(초록) |
| G_Crossref_FDI_automotive_AC_first_principle_2015.json | Zhang & Canova 2015, Control Eng. Practice 43 | 앱 참고문헌의 자동차 공조 고장 진단 논문 서지만. 본문 403 | 서지(2차) |
| G_arXiv1701.03674_FTC_automotive_AC_GIMC.pdf | 2017 | 1원리 모델 기반 고장 허용 제어. 센서·액추에이터 고장만, 냉매 누설 없음 | 프리프린트 |

## 받지 못한 것

- SAE J2727·J2763·J2765·J2842·J638 본문, Huyghe 2011, Vincent 2004, Fayazbakhsh 2013, SAE 960940, 2023-01-0763 등 SAE 논문 본문(유료)
- Schwarz & Wartmann 2005 승용차 후속 보고서, JRC·TNO 차량 누설 실측, IMAC 누설팀 보고서, 국립환경과학원 공식 배출계수 문서, R-1234yf 차량의 운행 중 누설 실측(검색에서 나오지 않음)
- 앱이 인용한 "Chemours·Honeywell 2026 프랑스 정비 기록 57%"(어디에서도 찾지 못함)
- NREL Rugh 2001·2007, Farrington 2000 원문(NREL DNS 실패, OSTI 연결 거부), Marcos 2014(무료 원문 없음)
- Datta 2014·2016 Purdue PDF(Cloudflare 확인 화면), MDPI Song 2023(403), BIS IS 14618 초안(404), UniPA 루프탑 오염 논문(봇 차단), Zhang & Canova 2015 본문(ScienceDirect 403)
- 13 CCR 1968.2 현행 통합본, EU 2017/1151 부속서 XI(EUR-Lex 빈 응답), Ford 2020 ~ 23 OBD 요약(403), Toyota·Honda·현대 P0128 진입 조건 공개본
- Shayler 그룹 학위논문(교내 전용), Witaszek 2023(봇 차단), GM US8370052(Google Patents 503)
- 현대 정비지침서의 풀다운·히터 성능 기준과 서모스탯 진단 조건(구독 사이트, 로그인하지 않음), 아이오닉 5·캐스퍼 일렉트릭 PTC kW와 히트펌프 작동 하한(공식 자료 없음)
- 현대·기아·모비스의 냉매 부족 판정 특허(Google Patents 503, Justia 403, KIPRIS는 API 키 필요)
- car.go.kr 결함 정보(스크립트 접근 404)
