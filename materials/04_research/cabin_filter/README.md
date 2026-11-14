# 실내 필터(공조 장치용 에어필터) 모델 근거 자료

접근일은 전부 2026-11-02. 파일 접두어는 주제다. 모델에 넣을 수치와 등급은 `model-inputs.md`에 있다.

| 접두어 | 주제 |
|---|---|
| A | 시험 표준(ISO 11155, DIN 71460, SAE J1669)과 수명 끝 정의 |
| B | 필터 매체의 적재·효율 변화(전기 집진 섬유의 전하 감쇠, 습도, 활성탄 흡착) |
| C | 실차 적재와 풍량(현장 수거 필터, 차압 증가, 송풍 단계별 풍량, 차속 램 공기) |
| D | 실내 공기질 연구(Wei 500시간 노화 필터, 고효율 필터, 실내 농도 모델) |
| E | 현대 자료(국내·해외 설명서 교체 주기, 가혹 조건, 공기 청정 기능, 순정 필터 표기) |
| F | 냄새와 미생물(필터·증발기 곰팡이, 계절, 교체 후 주행거리, 타사 TSB) |
| G | 외기 농도와 내기 순환(차량 환기량, 내기 순환 시 실내 농도, 한국 PM10·PM2.5·황사 통계) |
| H | 운행 정보로 필터 수명을 추정하는 특허(한라공조, Ford, Volvo, Valeo, GM, 3M) |

다운로드 파일은 하나도 실행하지 않았다. PDF 텍스트 추출, 쪽 렌더링, 계산은 이 폴더 밖 스크립트(`scratchpad/filterscripts/`의 `pdftext.py`, `render.py`, `htmltext.py`, `ctx.py`, `derive.py`)로 했다. Python은 `python3 -I`, PDF는 venv의 PyMuPDF를 썼고 경로는 인자로 넘겼다. 추출 텍스트는 `scratchpad/filter_txt/`에 있다. USPTO 이미지 PDF(US7717986, US7686872)는 쪽을 렌더링해 눈으로 읽었다. 접근 제한, 유료 구독, Cloudflare·봇 확인 화면을 우회하지 않았다. 원문이 막힌 논문은 OpenAlex·Europe PMC의 서지와 초록을 `.json`으로 남겼고 [2nd]로 쓴다. Europe PMC 오픈 원문은 `fullTextXML`로 받아 `.xml`로 남겼다.

hvac 회차에서 받은 필터 자료 5개(Johansson 1999, SAE 960940, Wei 2020, Wei 2023 학위논문, 아이오닉 5 설명서 쪽)와 `../originals/cabin-air-quality-2023.html`을 이름만 바꿔 복사했다. 현대 설명서 쪽은 `../../07_model_specs/ownersmanual/`과 `../hvac/`에서 필터 관련 쪽만 복사했다.

신뢰도 구분: 표준(요약만), 정부·공공 통계, OEM(현대 설명서·보도자료, 타사 TSB), 동료심사, 학위논문, 학회, 특허, 부품사·시험기관, 업계 언론, 언론, 2차(초록만, 검색 요약, 판매 목록).

Google Patents 주소는 `GP <번호>`로 줄였다. 실제 주소는 `https://patents.google.com/patent/<번호>/en`이다. USPTO 이미지 PDF는 `https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/<번호>`다. 현대 설명서 주소의 앞부분 `https://ownersmanual.hyundai.com/full_webhelp/`는 `OM/`로 줄였다.

## A. 시험 표준과 수명 끝 정의

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| A_US7717986_cabin_filter_DIN71460_loading_uspto.pdf | USPTO 7717986 | 3M 특허 2010. E DIN 71460-1:2003-05 시험 조건이 그대로 적혀 있다. 23 ± 2 °C, 50 ± 3% RH, 225 m³/h, 시험 먼지 ISO 12103-1 A4(SAE coarse), 160 mg/m³로 "초기 차압 + 100 Pa"까지 25 Pa씩 4단계 적재. 250 × 200 mm 필터 10종의 초기 차압 49 ~ 78 Pa, +100 Pa까지 적재량 23.0 ~ 36.7 g. 0.4 µm 초기 효율 95.7 ~ 98.8%, 적재 중(+50 Pa, +100 Pa)에 94.4 ~ 99.9%로 거의 유지. 가스(E DIN 71460-2:1994-11): n-부탄·톨루엔 80 ppm, SO₂ 30 ppm, 225 m³/h. 초기 효율 n-부탄 61 ~ 90%, 15분 흡착량 n-부탄 1.4 ~ 4.7 g, 톨루엔 12.4 ~ 30.7 g | 특허(표준 조건 인용) |
| A_ISO_TS11155-1_2001_scope_ndls.html | https://ndls.org.cn/standard/detail/fee6ed9a293be58d1c8a0913e09be6cc | ISO/TS 11155-1:2001 범위. 0.3 µm 초과 입자, 외기·내기 필터 모두 대상. 차압, 부분 효율, "가속" 입자 보유 능력을 표준 시험 먼지로 평가. "시험이 실제 입자와 환경을 다 담지 못하므로 실사용에서 순위가 바뀔 수 있다"고 범위에 적혀 있음 | 표준 요약 |
| A_ISO11155-2_2009_scope_sfs.html | https://store.sfs.fi/en/iso-11155-2-2009 | ISO 11155-2:2009(가스 여과) 서지. ISO/TS 11155-2:2002 대체. 본문 없음 | 표준 서지 |
| A_SAE_J1669_200210_abstract.html | https://saemobilus.sae.org/standards/j1669_200210-passenger-compartment-air-filter-test-code | SAE J1669 범위: 차압, 전체·부분 효율, 입자 보유 능력, 구조 강도. 1994 제정, 2002-10 폐지 | 표준 요약 |
| A_SAE_J1669-2_gas_abstract.html | https://www.sae.org/standards/content/j1669/2_199703/ | J1669/2 가스 여과 시험 범위 | 표준 요약 |
| A_SAE970678_SAE_vs_DIN_abstract.html | https://saemobilus.sae.org/papers/testing-automotive-interior-air-filters-a-comparison-sae-din-standards-970678 | SAE와 DIN 시험대·계측·시험 입자가 달라 결과가 다르다(수치 없음) | 학회(초록) |
| A_ILK_Dresden_cabin_filter_testing.html | https://www.ilkdresden.de/en/service/efficiency-testing-of-particle-gas-filters.html | 시험기관이 DIN 71460-1, ISO 11155-1로 분리 효율·차압 거동·보유 능력을 시험한다는 안내. 수치 없음 | 시험기관 |

## B. 필터 매체의 적재와 효율 변화

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| B_AppSci2021_cabin_filters_BC_BTEX_openalex.json | https://api.openalex.org/works/doi:10.3390/app11199048 | Chan 외 2021(캐나다 환경부). 차량 필터 11종. 정전(전기 집진) 필터가 6 ~ 520 nm 전 구간에서 20 ~ 60% 더 잘 거름. 100 ~ 300 nm 효율 상위군 44 ~ 46%, 하위군 10 ~ 11%. 일반 필터는 1 ~ 2년 사용 동안 효율이 안정. 정전 필터의 이점은 약 1년에 걸쳐 사라져 일반 필터와 같아짐. 활성탄 BTEX 제거 11 ~ 41% | 동료심사(초록) |
| B_Hanley1999_AIVC_electret_inuse_efficiency_loss.pdf | https://www.aivc.org/sites/default/files/members_area/medias/pdf/Conf/1999/paper097.pdf | 건물용 정전 필터 3종을 외기 덕트(24시간 연속)에 2주 간격으로 노출, 가정 9곳에서 2 ~ 12주 사용. 모든 외기·가정 노출 필터에서 효율이 시간에 따라 크게 떨어짐. 표준 먼지 적재 시험은 이 저하를 재현하지 못하고 오히려 효율이 오름 | 학회(건물용) |
| B_HVAC_filters_realistic_7months_agris.html | https://agris.fao.org/search/en/records/65df98020f3e94b9e5da36e8 | 건물 HVAC 폴리프로필렌 정전 필터 7개월 실외 공기(20 °C, 60% RH, 입자 10 µg/m³). 차압 약 +30 Pa, 0.35 µm 효율이 전반기에 약 30% 떨어진 뒤 안정. 필터 미생물 100 ~ 2,000 CFU/cm² | 동료심사(초록, 건물용) |
| B_Ji2003_KIST_electret_loading_abstract.html | https://pubs.kist.re.kr/handle/201004/138133 | 지준호 외 2003, J. Aerosol Sci. 34. 차량 필터용 정전 매체에 NaCl·DOS 적재. 적재할수록 미세 입자 효율이 떨어져 정상 상태에서 최저. 작은 입자·액체 입자일수록 더 떨어짐 | 동료심사(초록) |
| B_KIST_charge_depletion_electret_cabin_filter_abstract.html | https://pubs.kist.re.kr/handle/201004/107972 | 지준호 외 2001 대한기계학회. 승용차 정전 필터 전하 감쇠와 미세 입자 효율. 서지만 | 학회(서지) |
| B_AIVC_electret_ambient_exposed_abstract.html | https://www.aivc.org/node/25603 | Hanley 1999 초록 페이지 | 2차 |
| B_He2016_AST_RH_soot_service_life_openalex.json | https://api.openalex.org/works/doi:10.1080/02786826.2016.1167832 | He 외 2016(ETH·Empa). 활성탄 필터는 습도가 오르면 물을 많이 흡착하지만 차압은 크게 오르지 않음. ARD에 매연을 함께 적재하면 적재 곡선이 가팔라지고 최대 투과 입경이 작아짐. 적재 곡선으로 수명을 정하는 방법 제안(본문 미확보) | 동료심사(초록) |
| B_XuB2014_AAQR_humidity_dP_openalex.json | https://api.openalex.org/works/doi:10.4209/aaqr.2013.06.0204 | Xu B. 외 2014. 시판 필터 평균 효율 약 70%, 차압 75 Pa. 습한 공기 노출로 효율 최대 +15%, 차압 최대 +250 Pa. 90% RH 약 60분에 물 140 g 흡수. 12개월 사용 필터는 새 필터보다 효율·차압이 2배 빨리 변함 | 동료심사(초록) |
| B_KIST2019_BuildEnv_OEM_aftermarket_ACAF_comparison_abstract.html | https://pubs.kist.re.kr/handle/201004/119680 | Heo 외 2019, Building and Environment 161. 한국 OEM 6종·애프터마켓 9종. ISO 12103-1 A2로 0.3 ~ 0.5 µm 효율 50 ~ 90%, OEM 72.3 ± 13.8%, 애프터마켓 56.4 ± 23.7%. 한국공기청정협회 기준(0.3 ~ 0.5 µm 50% 초과)을 OEM은 모두, 애프터마켓은 9종 중 5종만 통과. 가스 기준(n-부탄 1분 70%·5분 45% 초과, 톨루엔 1분 80%·5분 70% 초과)을 동시에 통과한 OEM은 6종 중 1종 | 동료심사(초록) |
| B_2022_ACSOmega_PTFE_cabin_filter_dust_holding.xml | https://www.ebi.ac.uk/europepmc/webservices/rest/PMC9730776/fullTextXML | PTFE 막 필터와 시판 정전 필터 비교. 보유 능력을 초기 차압의 2.5배까지로 정의(ISO 5011 인용). 표 2의 보유 능력 15.6과 11.4 g/m²(추출 텍스트에서 열 구분이 불분명, 저자도 같은 기간 비교가 아니라고 적음). 시판 정전 필터는 물 처리 뒤 97% 초과를 유지했지만 고온 처리 뒤 84%, IPA 처리(전하 제거) 뒤 40% | 동료심사 |
| B_SAE970669_cabin_media_environmental_conditions_abstract.html | https://saemobilus.sae.org/papers/filtration-efficiency-automotive-cabin-air-filter-media-subjected-different-aerosols-various-environmental-conditions-970669 | 정전 매체의 신뢰성이 의심받는다는 서술, SAE fine dust 적재와 −40 °C부터 온도 순환 노출 시험. 수치는 유료 | 학회(초록) |
| B_Filtron_carbon_cabin_filter_gas_life_claim.html | https://filtron.eu/en/engineered-to-protect/cabin-filters/protects-against-harmful-gas-for-5-times-longer.html | 부품사 주장: 도로의 약 1,000배 농도 가스 시험에서 포화까지 5배 오래 버팀. 근거 데이터 없음 | 부품사 |

## C. 실차 적재와 풍량

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| C_Johansson1999_AIVC_cabin_filter_loading_dP.pdf | https://www.aivc.org/sites/default/files/members_area/medias/pdf/Conf/1999/paper095.pdf | 스톡홀름 터널 실제 교통 먼지로 적재(볼보 사양 필터, 면적 0.04 m², 70 L/s). 새 필터 차압 40 ~ 80 Pa. PM10 3 ~ 4 g 적재에 약 +100 Pa, 그 위로는 선형보다 빠름. 70에서 105 L/s로 늘리면 차압 약 +60%, 35 L/s로 줄이면 약 −60%. 0.1 ~ 0.35 µm 투과 약 55%(좋은 필터), 90% 초과(나쁜 필터). 대부분 적재 후 투과가 줄지만 정전 섬유로 보이는 필터는 적재 중 투과가 늘었다. 6.9 g PM10 적재 예. 적재 질량은 약 1년 사용으로 추정. 일반 풍량 70 L/s(250 m³/h)에 실내 공기 시정수 약 1분. 적재 필터에서 냄새 물질 방출 가능 | 학회 |
| C_Xu2011_AST_UFP_filter_45Pa_10months_openalex.json | https://api.openalex.org/works/doi:10.1080/02786826.2010.531792 | Xu B., Liu, Liu, Zhu 2011(UCLA). 최대 투과 약 300 nm, 효율 20% 미만. 면속도 0.1에서 0.5 m/s로 효율 10 ~ 20% 하락. 차압 +45 Pa까지 교통량 65,000 ~ 72,000대/일 도로 10개월, 10,000 ~ 24,000대 20개월, 700 ~ 2,000대 30개월. 외기·송풍에서 UFP I/O가 40% 오르기까지 혼잡 도로 약 10개월 | 동료심사(초록) |
| C_Wang2024_EnvInt_22filters_31cities_service_life_europepmc.json | https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=DOI:10.1016/j.envint.2024.108939&resultType=core&format=json | Wang 외 2024, Environ. Int. 190. 중국 필터 22종. 0.1 ~ 0.3 µm 최저 평균 효율 56.1%, 2.5 m/s에서 차압 33.2 ~ 250 Pa, 보유 능력 5.8 ~ 19.4 g. 면적 0.23에서 0.50 m²로 늘면 효율 56.7에서 77.5%, 차압 96.1에서 62.5 Pa, 보유 능력 2.7배. 31개 도시 수명 하이커우 1,730 h에서 스자좡 352 h. 수명 끝을 차압이 초기의 2배가 되는 때로 정의(검색 요약, 원문 403) | 동료심사(초록) |
| C_SAE960940_NorthAmerica_field_loading_abstract.html | https://saemobilus.sae.org/papers/particulate-filter-performance-north-american-environment-960940 | 약 24,000 km 뒤 애리조나 필터가 앨라배마의 3배 넘게 포집. 실차 필터의 차압은 SAE fine 먼지 적재가 가장 잘 재현 | 학회(초록) |
| C_SAE960541_new_used_filters_abstract.html | https://saemobilus.sae.org/papers/vehicle-performances-new-used-automotive-cabin-air-filters-960541 | 미국 여러 지역에서 주행거리 다양한 사용 필터의 효율·차압, 지역별 적재 속도 비교(수치 유료) | 학회(초록) |
| C_KR1996_passenger_car_cabin_filter_performance.html | https://koreascience.kr/article/JAKO199611919855990.pub?lang=ko | 국내 1996. 필터 2종 차압·부분 효율. 한 종이 임계 차압 도달 시간이 더 길었다(수치 없음) | 동료심사(초록) |
| C_IRSST_waste_truck_cabin_filters.html | https://pharesst.irsst.qc.ca/etudes-primaires/1769 | Wingert 외 2026. 청소차 필터 최저 효율 1.3 ~ 42.5%(여과 속도에 따라). 막힘이 일반 승용차보다 심함. 교체 지침 필요 | 동료심사(초록) |
| C_Knibbs2009_IndoorAir_six_cars_air_flow_openalex.json | https://api.openalex.org/works/doi:10.1111/j.1600-0668.2009.00593.x | 6대, 모든 유입로를 닫은 상태 침투 60 km/h 1 ~ 33.1 ACH, 110 km/h 2.6 ~ 47.3 ACH, 정차 0 ~ 1.4 ACH. 외기 저속·고속 송풍 풍량도 측정(수치 본문) | 동료심사(초록) |
| C_AIVC_32382_automobile_HVAC_airflow_leakage.html | https://www.aivc.org/node/32382 | Knibbs 2007. 4대 모두 HVAC 풍량이 차속에 선형 증가. 신형 차가 같은 설정에서 풍량이 더 작음 | 학회(초록) |
| C_Ott2008_JESEE_air_change_rates_vehicles.html | https://www.nature.com/articles/7500601 | Ott 외 2008. 송풍 끔·외기(수동 환기)에서 환기율이 25 ~ 116 km/h 범위에서 차속에 선형. 정차·송풍 끔 1 ACH 미만. 내기 순환 시 32 ~ 116 km/h에서 6.6 ACH 미만. 담배 PM2.5 침착률 k = 1.3a | 동료심사(초록) |
| C_EPJ2014_FiserPokorny_car_speed_ventilation_openalex.json | https://api.openalex.org/works/doi:10.1051/epjconf/20146702027 | 실주행에서 HVAC 풍량이 차속과 혼합 문 위치에 따라 변함, 경험식 도출(수치 본문, 403) | 학회(초록) |
| C_Pokorny2016_EPJ_car_fan_operating_points_openalex.json | https://api.openalex.org/works/doi:10.1051/epjconf/201611402097 | 차량 송풍기 운전점. 압력차는 송풍 전압에 비례하고 송풍구 개폐에 크게 좌우(수치 본문, 403) | 학회(초록) |

## D. 실내 공기질 연구

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| D_Wei2020_ESPR_500h_aged_filter.pdf | https://research.chalmers.se/publication/517377/file/517377_Fulltext.pdf | 앱 500시간의 원 논문. 정전 합성 + 활성탄 필터(337 × 238 × 41 mm). 2018-04 상하이 외기 덕트, PM2.5 월평균 42 µg/m³, 송풍 저속 1,430 rpm(약 200 kg/h), 내기 순환 없음, 500 h. "약 1년 주행, 중국 권장 교체 주기"로 설명. 볼보 4단계 풍량 약 100 / 180 / 260 / 380 kg/h. 노화 필터 PM2.5 I/O 0.20에서 0.60, UFP 0.24에서 0.57. 차압·풍량 값 없음 | 동료심사 |
| D_Wei2023_ESPR_recirculation_PMC10076388.html | https://pmc.ncbi.nlm.nih.gov/articles/PMC10076388/ | 앱이 인용하는 크기별 효율 표(부록 표 8). 새 필터 100 nm 0.71, 1.1 µm 0.94, 2.5 µm 0.97. 500 h 노화 0.33, 0.58, 0.61. 내기 순환 30 / 50 / 70%로 실내 PM2.5 −22 / −36 / −55%(새 필터), 노화 필터는 조금 덜 줄어듦. XC90 실내 4.1 m³ | 동료심사 |
| D_Wei2023_Chalmers_PhD_thesis_cabin_air.pdf | https://research.chalmers.se/publication/535304/file/535304_Fulltext.pdf | 정전 필터는 전하 손실로 노화. 수명 지표 후보는 입자·가스 제거, 보유 능력, 미생물 성장. 교체 시점은 효율보다 차압 증가와 냄새·미생물이 정할 가능성(p.48). 실차 송풍 저속 약 40 L/s | 학위논문 |
| D_Wei2022_AIVC_HEPA_vehicle_cabin.pdf | https://www.aivc.org/sites/default/files/046.pdf | 볼보 4단계 약 20 / 40 / 60 / 85 L/s. 원래 필터 UFP 76%, PM2.5 87% 제거. 노화 상태를 "A2 먼지와 환경 순환으로 80 L/s에서 차압 +50 Pa"로 정의. 고효율 필터 적용 연구에서 풍량 7 ~ 22% 감소 인용(Lee & Zhu 2014) | 학회 |
| D_Wei2022_ESPR_size_resolved_cabin_simulation.xml | https://www.ebi.ac.uk/europepmc/webservices/rest/PMC9209366/fullTextXML | 4단계 약 23 / 40 / 59 / 86 L/s. 필터 시험은 288 m³/h(80 L/s) 기준. 실주행 외기 풍량 44 ~ 291 m³/h, 128건 중 116건이 58 m³/h 초과. 외기 58 ~ 133 m³/h면 103 ~ 123 km/h까지 침투 없음(Lee 2015 인용). 실내 농도 질량수지 식 | 동료심사 |
| D_Chalmers_HEPA_vehicle_cabin_538906.html | https://research.chalmers.se/en/publication/538906 | 위 AIVC 논문 서지. 차량별 효율 보고값 0.2 ~ 0.9 | 2차 |
| D_LeeZhu2014_EST_HE_cabin_filter_UFP_CO2_airflow_openalex.json | https://api.openalex.org/works/doi:10.1021/es404952q | 12대. 사용 중 OEM 필터 UFP 약 50% 감소, 고효율 필터 93%. 내기 순환은 UFP 약 90% 감소지만 CO₂ 2,500 ~ 4,000 ppm, 외기+고효율 필터는 620 ~ 930 ppm | 동료심사(초록) |
| D_2017_PLOSONE_high_efficiency_cabin_filter_drivers.xml | https://www.ebi.ac.uk/europepmc/webservices/rest/PMC5703570/fullTextXML | 대만 택시 외기·중간 송풍. 약 11,000 km에 약 300 h(평균 약 37 km/h) | 동료심사 |

## E. 현대 자료

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| E_Hyundai_OM_NX4_2026_KR_cabin-filter.html | OM/NX4/2026/ko_KR/id22CN990A05Z.html | 투싼 국내: 12개월마다 교체. 혼잡하거나 먼지 많은 도로는 더 자주. 제때 바꾸지 않으면 "풍량이 약해져 냉난방 성능이 나빠질 수 있으며 악취가 발생할 수 있습니다". 송풍구 냄새는 고장이 아니며 에어컨 사용 후 송풍 약 5분 건조 | OEM |
| E_Hyundai_OM_NX4_2026_KR_cabin-filter-inspect.html | OM/NX4/2026/ko_KR/idbd19f705a55.html | 12개월마다 교체. 대기 오염이 심한 도시나 먼지 많은 비포장 도로를 자주 달리면 수시 점검해 조기 교체 | OEM |
| E_Hyundai_OM_NX4_2026_KR_schedule-normal.html | OM/NX4/2026/ko_KR/id6b219dd157c.html | 정기 점검표에는 "매 12개월마다 점검"으로 적혀 있어 필터 쪽("교체")과 문구가 다름 | OEM |
| E_Hyundai_OM_NX4_2026_KR_schedule-severe.html | OM/NX4/2026/ko_KR/id7b77f1c5ffe.html | 가혹 조건표에서 필터는 조건 2(모래·먼지 많은 지역)에만 해당, "상태에 따라 수시 점검 또는 필요시 교체". 주기를 절반으로 줄이라는 숫자는 없음. 교통 체증(4번)은 필터 행에 없음 | OEM |
| E_Hyundai_OM_AX_2026_KR_cabin-filter-15000km.html | OM/AX/2026/ko_KR/id23C1E02A0Y4.html | 캐스퍼 필터 쪽: 매 15,000 km마다 교체 | OEM |
| E_Hyundai_OM_AX_2026_KR_schedule-normal.html | OM/AX/2026/ko_KR/id6b219dd157c.html | 같은 캐스퍼 정기 점검표: 매 12개월마다 교체. 한 설명서 안에서 거리·시간 기준이 갈림 | OEM |
| E_Hyundai_OM_NE1_2026_KR_cabin-filter-hvac-performance.html | OM/NE1/2026/ko_KR/id23C1E02A0Y4.html | 아이오닉 5 국내: 매 12개월. 오염 물질이 쌓이면 풍량 약화, 냉난방 저하, 악취 | OEM |
| E_Hyundai_OM_NX4_2025_US_normal-maint-schedule-2.html | OM/NX4/2025/en_US/ida26dbc5c62b.html | 투싼 미국: 표의 칸 구조를 읽으면 24 / 48 / 72개월(16,000 / 32,000 / 48,000마일)에 R. 즉 24개월 또는 26,000 km마다 교체, 사이 점검 없음 | OEM |
| E_Hyundai_OM_NE1_2025_US_normal-maint-schedule.html | OM/NE1a/2025/en_US/idc415036e46f.html | 아이오닉 5 미국: 12개월 점검, 24개월(16,000마일) 교체 | OEM |
| E_Hyundai_OM_AXEV_2025_GN_normal-maint-schedule.html | OM/AXEV/2025/en_GN/id2685fd67d59.html | 캐스퍼 일렉트릭 일반 수출: 유럽은 매 점검 교체, 유럽 외 15,000 km(10,000마일) 또는 12개월 | OEM |
| E_Hyundai_OM_NX4_2025_GN_system-maintenance.html | OM/NX4/2025/en_GN/id28824ad7b0aa.html | 먼지·험로, 반려동물, 실내 흡연이면 더 자주 점검·교체. "풍량이 갑자기 줄면" 딜러 점검 권장 | OEM |
| E_HyundaiNews_2020_afterblow_fine_dust_indicator.html | https://www.hyundai.news/uk/articles/press-releases/new-air-conditioning-technologies-maintain-clean-air-in-vehicles.html | 현대차그룹 2020-07. 애프터 블로우: 시동을 끄고 약 30분 응축수가 빠진 뒤 10분 송풍으로 증발기 건조(곰팡이·냄새 억제). 실내 미세먼지 표시: PM2.5 0 ~ 15 파랑, 16 ~ 35 초록, 36 ~ 75 주황, 76 이상 빨강. 36 µg/m³ 넘으면 공기 청정 모드 작동 | OEM 보도자료 |
| E_KiaNews_2020_clean_air_technologies.html | https://www.kianewscenter.com/News/hyundai-motor-group-develops-air-conditioning-technologies-to-maintain-clean-air-in-vehicles/s/a6856a19-6ac6-4075-ac95-9396d28005c7 | 같은 보도자료 기아판 | OEM 보도자료 |
| E_HMG_story_cabin_PM25_display_colors.html | https://www.hyundaimotorgroup.com/ko/story/CONT0000000000003756 | 같은 내용 국문. 아반떼에 미세먼지 센서 공기청정 시스템과 "마이크로 에어 필터" 적용 | OEM |
| E_Danawa_OEM_filter_listing_PM03_HEPA.html | https://plan.danawa.com/info/?nPlanSeq=8859 | 판매 목록 표기: 현대모비스 순정 97133-L1100(DN8, CN7, ST1 등), 현대모비스 "PM0.3 헤파 E11" 제거효율 99.8%(NX4, DN8, GN7, TM 등). 모비스 공식 사양서 아님 | 2차(판매 목록) |

## F. 냄새와 미생물

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| F_2023_ESPR_AC_filters_infections_toxic_moulds.xml | https://www.ebi.ac.uk/europepmc/webservices/rest/PMC10611836/fullTextXML | 폴란드 승용차 30대(겨울 3개월 15대, 여름 5개월 15대) 필터. 곰팡이 평균 여름 5.4 × 10⁴, 겨울 2.4 × 10⁴ CFU/m². 새 필터 배경 1.7 ~ 2.2 × 10³. 교체 후 주행거리와 Spearman R = 0.853(p < 0.001), 총 주행거리 0.525. 최고 1.8 × 10⁵ CFU/m²는 교체 후 263,500 km 차. 표 1에 차별 교체 후 km(6,595 ~ 263,500), 표 3에 차별 농도. 습도 70 ~ 80% 이상에서 필터에 곰팡이 증식(인용). 결론: 최소 1년 또는 15,000 km마다 교체 | 동료심사 |
| F_Li2013_EST_automobile_AC_bioaerosol_openalex.json | https://api.openalex.org/works/doi:10.1021/es402848d | 중국 4개 지역 30대. 필터 먼지 세균 최대 26,150 CFU/mg, 곰팡이 1,287 CFU/mg, 내독소 5,527 EU/mg. 에어컨을 켠 처음 5분에 2.5 µm 근처 형광 입자 피크(필터 미생물 재비산) | 동료심사(초록) |
| F_Simmons1997_JIMB_fungal_colonization_auto_AC_openalex.json | https://api.openalex.org/works/doi:10.1038/sj.jim.2900451 | 애틀랜타 29대. 냄새 민원 차 증발기에 Penicillium 밀집. 곰팡이는 공조 내부 금속면·단열재에서 증식 | 동료심사(초록) |
| F_Viegas2018_EnvRes_taxi_AC_filters_bioburden_europepmc.json | https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=DOI:%2210.1016/j.envres.2018.03.032%22&resultType=core&format=json | 포르투갈 택시 19대·자가용 28대 필터(겨울). 세균 증식 택시 63.2%, 자가용 26.3%. 곰팡이 증식 자가용 53.6%, 택시 21.1 ~ 31.6% | 동료심사(초록) |
| F_2026_PartFibreTox_aged_cabin_filters_secondary_pollution.xml | https://www.ebi.ac.uk/europepmc/webservices/rest/PMC13127088/fullTextXML | 지난 교체 후 약 15,000 ~ 20,000 km(12 ~ 18개월) 사용한 필터 입자가 염증 반응을 일으킴. 노화 필터가 재비산으로 2차 오염원이 될 수 있다는 주장 | 동료심사 |
| F_NHTSA_TSB_MC-10239999-9999.pdf | https://static.nhtsa.gov/odi/tsbs/2023/MC-10239999-9999.pdf | Jeep TSB 24-004-23(2023). 에어컨 곰팡내는 냉각 코일 미생물. 조치는 필터 교체 + 증발기 세정 | OEM TSB(타사) |
| F_NHTSA_TSB_MC-10113708-9999.pdf | https://static.nhtsa.gov/odi/tsbs/2015/MC-10113708-9999.pdf | Cadillac PI1408(2015). 21 °C 이상 일사에서 2 ~ 6시간 주차 뒤 시동 시 30 ~ 60초 곰팡내. 증발기 응축수와 미생물이 원인 | OEM TSB(타사) |
| F_NHTSA_TSB_MC-10163731-9999.pdf | https://static.nhtsa.gov/odi/tsbs/2019/MC-10163731-9999.pdf | Lexus L-SB-0060-13. 공조 냄새는 없앨 수 없고 줄이는 절차만 있음 | OEM TSB(타사) |
| F_NHTSA_TSB_MC-10168364-0001.pdf | https://static.nhtsa.gov/odi/tsbs/2019/MC-10168364-0001.pdf | VW 2019. 냄새 조치: 최고 송풍 약 30분 건조, 필터 빼고 세정기 15분, 새 활성탄·꽃가루 필터 장착 | OEM TSB(타사) |

## G. 외기 농도와 내기 순환

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| G_Zhu2008_EHP_recirculating_filtration.xml | https://www.ebi.ac.uk/europepmc/webservices/rest/PMC2453152/fullTextXML | 내기 순환 + 저효율 필터로 혼잡 도로 나노입자가 약 3분 안에 사무실 수준으로 떨어짐. 필터 없이도 지수 감소(13분에 4,000개/cm³) | 동료심사 |
| G_Jung2017_AtmosEnv_fractional_recirculation_PMC.html | https://pmc.ncbi.nlm.nih.gov/articles/PMC5544137/ | 송풍 8단 차량. 내기 50 ~ 75%로 CO₂를 낮게 유지하며 입자 감소 | 동료심사 |
| G_Hudda2012_EST_vehicle_air_exchange_model_PMC.html | https://pmc.ncbi.nlm.nih.gov/articles/PMC4041012/ | 내기 순환 환기율 예측 모형. 외기 설정이나 창문 개방은 환기율을 한 자릿수 높임. 송풍 단계 영향은 작음 | 동료심사 |
| G_KR_index_8058_PM25_by_sido.html | https://www.index.go.kr/unity/potal/main/EachDtlPageDetail.do?idx_cd=8058 | 국가지표: 17개 시도 PM2.5 인구가중 2024년 16 µg/m³. 서울·인천·경기·충북·충남 높은 편, 부산·대구·강원·전남·경남·제주 15 미만 | 정부 통계 |
| G_KR_index_1395_yellow_dust_days.html | https://www.index.go.kr/unify/idx-info.do?idxCd=1395 | 기상청: 황사 관측일수 평년(1992 ~ 2021) 7.2일, 2012 ~ 2021 평균 4.1일, 2022년 3.5일. 봄 평년 4.3일. 계기 기준 1시간 PM10 300 µg/m³ 미만 옅은 황사, 300 ~ 800 짙은 황사, 800 이상 매우 짙은 황사 | 정부 통계 |
| G_KR_Seoul_winter_PM25_highest.html | https://m.go.seoul.co.kr/news/2025/04/16/20250416021002 | 환경부 6차 계절관리제(2024-12 ~ 2025-03): 전국 PM2.5 20.3 µg/m³(전년 21.0), 서울 24.3(전년 22.8)으로 17개 시도 최고, 전남 14.55 최저. 전국 좋음 54일, 나쁨 12일 | 언론(정부 발표 인용) |
| G_KR_Khan2023_monthly_PM25_Dec_Mar.html | https://www.khan.co.kr/article/202302241933001 | 2015 ~ 2022년 PM2.5 월평균 12월 24, 1·2월 27, 3월 28 µg/m³. 나쁨 이상 일수 12월 6, 1·2월 7, 3월 9일 | 언론(환경부 인용) |
| G_KR_SeoulShinmun_2024_PM25_16_vs_2015_26.html | https://m.seoul.co.kr/news/2024/12/10/20241210033004 | 2024년 11월까지 전국 PM2.5 16 µg/m³, 2015년 26. 2029년 목표 13 | 언론(정부 기고) |
| G_KR_NewsTomato2022_annual_PM25_2015_2021.html | https://newstomato.com/ReadNews.aspx?no=1098167 | 2022-01 기사. 2021년 PM2.5 18.0 µg/m³(503개 측정소), 시도별 14 ~ 21. 2015 26, 2019 23, 2020 19. 좋음 183일, 나쁨 이상 23일 | 언론(환경부 인용) |
| G_KR_Asiae2026_Gyeonggi_PM25_2025.html | https://view.asiae.co.kr/article/2026010807170796117 | 경기 PM2.5 2019 26, 2020 21, 2021 21, 2022 20, 2023 21, 2024 18, 2025 18 µg/m³. 2025 시군별 평택·김포·안성·여주 20, 동두천 13 | 언론(도 연구원 인용) |
| G_KR_TheSegye_PM10_2023_38.html | https://m.thesegye.com/news/view/1065569512146671 | 충북 2024년 PM10 29, PM2.5 17 µg/m³. PM10은 전국 평균과 같고 PM2.5는 1 높음(전국 PM10 29, PM2.5 16으로 읽힘) | 언론(도 연구원 인용) |
| G_Lee_Ewha_Seoul_PM25_monthly_March_peak_August_low_abstract.html | https://pure.ewha.ac.kr/en/publications/when-and-why-pmsub25sub-is-high-in-seoul-south-korea-interpreting/ | 서울·백령도 2015 ~ 2021 시간 자료. PM2.5 월평균 따뜻한 철 약 20, 추운 철 약 40 µg/m³. 서울 연 −0.7 µg/m³ | 동료심사(초록) |
| G_KR_datagokr_AirKorea_realtime_API_15073861.html | https://www.data.go.kr/data/15073861/openapi.do | 한국환경공단 에어코리아 대기오염정보 오픈API. 측정소별 실시간, 시도별, 기간별 조회 | 정부 API 안내 |

## H. 운행 정보로 수명을 추정하는 특허

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| H_US7686872B2_Halla_air_filter_replacement_time_gp.html, H_US7686872_Halla_air_filter_replacement_time_uspto.pdf | GP US7686872B2, USPTO 7686872 | 한라공조(현 한온시스템) 2010, 한국 우선권 2006. 먼지 공급량(µg) = 오염도(µg/m³) × 유입 시간(h) × 유입 풍량(m³/h), 적재량 = 공급량 × 필터 효율, 누적이 기준값을 넘으면 교체 알림. 오염도는 운행 지역의 연평균 대기 오염도(메모리 저장), 운전자 입력, 상류 센서 중 하나. 풍량은 송풍 단계·문 위치별 저장값. 외기 모드에서 송풍이 꺼져 있으면 주행풍 70 m³/h를 고정값으로 씀. 내기 모드는 처음 진입 때 실내 = 대기 농도로 보고, 이후에는 필터를 통과한 먼지/실내 체적으로 실내 농도를 갱신. 선행 기술은 송풍 시간과 외기 유지 시간만 셌다고 서술. 그림 6: 계산과 실측 적재량이 같은 기울기, 초반 실측이 0.1 ~ 0.2 높고 끝에서 1.07 대 1.0 | 특허 |
| H_US11433733B2_HVAC_detecting_cabin_filter_usage_gp.html, H_US11433733_HVAC_detecting_cabin_filter_usage_uspto.pdf | GP US11433733B2 | Ford 2022. 사용률 100%는 실제 막힘 10, 15, 20% 등에 대응. 기본 추정은 거리 기반, 예시 약 25,000 km(15,000 ~ 30,000 km). 외기·실내·설정 온도와 바람 방향으로 데우기·식히기 오차(설정에 못 미치는 정도)를 피드백 신호로 만들어 사용률을 보정. 2,500 / 7,500 / 12,500 / 17,500마일에 재추정 | 특허 |
| H_US10190957B2_calculation_based_cabin_filtration_gp.html | GP US10190957B2 | Ford 2019. 송풍 모드·전류·설정으로 풍량 조회. 예시 송풍 높음 140 L/s, 낮음 50 L/s. 내기·외기 실내 PM2.5 질량수지 식(풍량, 필터 효율, 누설, 외기 농도). 필터 수명 센서 예로 풍량 감소, 차압, 송풍 전류, 토크 | 특허 |
| H_US9688194B2_incabin_particulate_sensor_gp.html, H_US9688194_incabin_particulate_sensor_filter_uspto.pdf | GP US9688194B2 | Ford 2017. 실내 입자 센서 자료 분석, 필터 앞뒤 비교. 외기가 깨끗할 뿐인데 필터 교체가 필요하다고 오판하지 않게 하는 목적 | 특허 |
| H_US20240078853A1_route_data_blower_current_filter_gp.html | GP US20240078853A1 | Volvo 2024(US12444250B2). 송풍기 3,000 rpm에서 새 필터 65 mA, 막힌 필터 50 mA. 가스 농도·MVOC·입자·차압으로 필터 상태 판단 | 특허 |
| H_US9120366B2_blower_energization_filter_status_gp.html | GP US9120366B2 | Ford. 속도 제어 송풍기가 시험 속도를 내는 데 드는 전력을 새 필터 기준과 비교 | 특허 |
| H_US6582295_blower_current_clogging_gp.html | GP US6582295B1 | Valeo. 송풍 모터 전류(또는 회전수)를 새 필터 기준값과 비교. 막힘이 압력 손실을 늘려 전류가 줄어드는 원리 | 특허 |
| H_US11034212B2_cabin_air_quality_PM25_filter_life_gp.html | GP US11034212B2 | GM. PM2.5 센서와 필터 풍량 센서로 잔여 수명·교체 상태 출력 | 특허 |
| H_US20230256373A1_cabin_filter_monitoring_gp.html | GP US20230256373A1 | 3M 2023. 실내 공기질 스냅숏으로 학습 모델이 필터 수명 끝을 예측 | 특허 |
| H_US20240010044A1_Airlib_GPS_air_quality_intake_gp.html | GP US20240010044A1 | Airlib. GPS 위치·시각을 대기질 지도 서버에 보내 받은 값으로 흡입 문 제어 | 특허 |

## 받지 못한 것

- ISO/TS 11155-1, DIN 71460-1, SAE J1669 본문(유료). 수명 끝 기준은 특허(+100 Pa)와 논문(초기의 2배, 2.5배)에서만 확인
- Wang 2024 Environ. Int. 본문(ScienceDirect·DOAJ가 Cloudflare 확인 화면). 도시별 수명 계산식과 입력 농도
- Xu B. 2011, He 2016, Xu B. 2014 본문(Taylor & Francis·AAQR 403). 개월별 차압 곡선과 수명 추정식
- SAE 960541, 960940, 970669 본문, Sager 2012 활성탄 실사용 흡착 감소(Filtration+Separation 403)
- Knibbs 2009, Fišer·Pokorný 2014, 2016 본문(송풍 단계별 L/s 표). EPJ PDF 403, VUT 저장소 봇 확인
- CDC 저장소의 Zhu 그룹 원고(Access Denied), NOAA 서울 장기 PM 원문(403)
- 현대·기아 필터 막힘·휘파람 소리·냄새 관련 TSB(NHTSA 불만 자료 5개 차종 1,266건에 실내 필터 언급 0건), 모비스 순정 필터 공식 사양서
- 한국 KS 실내 필터 시험 규격, 한국공기청정협회 차량 필터 단체표준 번호. JIS D 1612는 엔진 흡기 필터 규격이었음
- 대기환경연보 시도별·월별 표 원문(에어코리아 PDF 검색 실패)
