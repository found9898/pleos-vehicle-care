# 와이퍼 블레이드 마모 모델 근거 자료

접근일은 전부 2026-11-02. 파일 접두어는 주제다. 모델에 넣을 수치와 등급은 `model-inputs.md`에 있다.

| 접두어 | 주제 |
|---|---|
| A | 내구 기준과 시험법(한국소비자원, JIS, FMVSS, EU, 국내 규칙, 부품사 시험) |
| B | 고무 재질, 열노화·오존·영구 변형, 날 끝 마모 |
| C | 닦음 품질, 마찰, 끽 소리·떨림, 조건별 마찰 |
| D | 실제 사용량(강수 시간, 주행 시간, 스위치 사용, 연간 닦음 수) |
| E | 제조사·부품사 교체 안내, 소비자 시험 |
| F | 노출 조건(오존, 일사, 주차 차량 온도) |
| G | 현대 차종 자료 |
| H | 와이퍼 마모·수명 판정 특허 |

다운로드 파일은 하나도 실행하지 않았다. 텍스트 추출과 계산은 이 폴더 밖 스크립트(`scratchpad/wiperscripts/`, `python3 -I`, PDF는 venv의 PyMuPDF, 소비자원 .hwp는 읽기 전용 `hwptext.py`)로 했고 추출 텍스트는 `scratchpad/wiper_txt/`에 있다. 현대 취급설명서 와이퍼 페이지는 이미 `../../07_model_specs/ownersmanual/<차종>_2026/wiper*.html`에 있으므로 대표 한 장만 G로 복사했다. 접근 제한이나 유료 구독을 우회하지 않았다. WebFetch로 본문만 읽은 경우 `.txt` 캡처로 남기고 첫 줄에 URL과 날짜를 적었다.

신뢰도 구분: 규제(한국 법령, 미국 eCFR, EU), 표준(원문 또는 요약), 정부 시험기관(한국소비자원), 정부 통계(기상청, 교통안전공단, 서울시), 동료심사, 학위논문, 학회, 시험기관(ARDL, TCS·ADAC), 소비자 단체(Consumer Reports), OEM(현대·기아·Ford 문서), 부품사(Bosch, Valeo, DENSO, Champion, Trico, Rain-X), 특허, 언론, 2차(블로그, 소매점, 제3자 사본, 검색 요약).

Google Patents 주소는 표에서 `GP <번호>`로 줄였다. 실제 주소는 `https://patents.google.com/patent/<번호>/en`(한국 특허는 `/ko`)이다.

## A. 내구 기준과 시험법

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| A_KCA_2016_press_release_wiper_blade.hwp | https://www.kca.go.kr/home/board/download.do?menukey=4002&fno=10013559&bid=00000013&did=1001887714 | 2016-07-11 보도자료 원문. 일반형 10종, 운전석 600 mm, 조수석 500 mm. "와이퍼를 왕복 20만회 작동한 후 … 물을 골고루 뿌리고 … 닦임 성능을 평가". 각주 "신뢰성평가기준 : 1년 또는 2만 km 상당하는 수명 예측 횟수". 운전석 양호 8, 보통 1, 미흡 1. 조수석도 8 / 1 / 1. 판정은 흔적이 3초 안에 사라지는지. 노화 시험은 전 제품 통과: (70±5) °C 100 h, (−40±5) °C 2 h 뒤 20회 낙하, 햇빛 재현 1,200 h, 오존 (50±5) pphm 72 h. RS R 0031:2003 준용. 20만 회 동안의 하중·속도·온도·살수 여부는 적혀 있지 않음 | 정부 시험기관 |
| A_KCA_2016_press_release_1001887714.html | https://www.kca.go.kr/home/sub.do?menukey=4002&mode=view&no=1001887714 | 같은 보도자료 게시 페이지 | 정부 시험기관 |
| A_KCA_wiper_comparison_A0000922.html | https://www.consumer.go.kr/user/ftc/consumer/cnsmrBBS/80/selectInfoCmprDetail.do?cntntsId=00000205&searchGbn=REGIST_DT&infoId=A0000922 | 앱이 인용한 페이지. 2016-07-13 게시, 각주 동일 | 정부 시험기관 |
| A_KCA_cardnews_1001887823.html | https://www.kca.go.kr/home/sub.do?menukey=4004&mode=view&no=1001887823 | 같은 시험 카드뉴스 | 정부 시험기관 |
| A_KCA_2016_press_khan.html, A_KCA_2016_press_wowtv.html, A_KCA_2016_press_medicalworldnews.html | https://www.khan.co.kr/article/201607131200001, https://www.wowtv.co.kr/NewsCenter/News/Read?articleId=A201607130262, https://www.medicalworldnews.co.kr/m/view.php?idx=1468394180 | 같은 보도자료 기사 | 언론 |
| A_KATECH_2011_flat_wiper_durability_reliability.pdf, A_KATECH_2011_flat_wiper_durability_reliability_abstract.html | https://koreascience.or.kr/article/CFKO201133254121889.pdf (서명 붙은 임시 주소, 안정 주소는 https://koreascience.or.kr/article/CFKO201133254121889.page?lang=en) | 자동차부품연구원, 한국신뢰성학회 2011. 20만 회 내구 후 닦임 등급: 기존 플랫 블레이드 10에서 7, 접촉각 변화 2.6°. 최적 설계 10에서 9, 0.4° | 연구기관(학회) |
| A_JIS_D5710_1998_wiper_arms_blades_kikakurui.html, A_JIS_D5710_1998_p3_table1.png, A_JIS_D5710_1998_p6_table3.png | https://kikakurui.com/d5/D5710-1998-01.html, …/page-3.png?v=1, …/page-6.png?v=1 | 5.4 블레이드 50만 회 왕복 뒤 닦음 기준 만족, 암 150만 회에서 누름힘 변화 15% 이내. 내구 조건(표 3): 상온 20±15 °C, 물 38 °C 이하, 유리에 고르게 살수 800 ml/min 이상, 45회/min 이상. 표 1 닦음 흔적 허용치: 중앙(M) 신품 가는 선 3·굵은 선 1, 내구 뒤 5·2. 측면(S) 신품 5·2, 내구 뒤 7·3·폭 2 | 표준 전문(제3자 사본) |
| A_FMVSS104_49CFR571.104_eCFR.html | https://www.ecfr.gov/api/renderer/v1/content/enhanced/current/title-49?part=571&section=571.104 (화면용 https://www.ecfr.gov/current/title-49/subtitle-B/chapter-V/part-571/subpart-B/section-571.104) | 2단 이상. 한 단은 45회/min 이상, 최고 단과 낮은 단 차이 15 이상, 낮은 단 20 이상. 닦임 면적은 SAE J903a 젖은 시험으로 A 80%, B 94%, C 99%. 블레이드 내구·노화 요구 없음 | 규제(미국) |
| A_EU_Reg1008-2010_AnnexIII_legislationgovuk.html | https://www.legislation.gov.uk/eur/2010/1008/annex/III | A 98%, B 80%. 한 단 10 ~ 55회/min, 한 단 45 이상, 차이 15 이상. −18 ± 3 °C 4 h 뒤 마른 유리 2분. 젖은 유리 20분 예비 작동 뒤 속도 확인. 블레이드 횟수 내구·오존 시험 없음. 영국 존치판 | 규제(EU, 영국 존치) |
| A_KR_vehicle_safety_rule_wiper_article.html | https://www.law.go.kr/LSW/lsLawLinkInfo.do?lsJoLnkSeq=1000641041&chrClsCd=010202 | 자동차 및 자동차부품의 성능과 기준에 관한 규칙 제109조. 가 영역 98%, 나 영역 80%. −18 °C 4 h 뒤 2분 작동. 최고속도 80% 또는 120 km/h 상대풍속에서 가 98% 유지. 블레이드 내구 요구 없음 | 규제(한국) |
| A_KSSN_search_wiper_KS_standards_list.html | https://www.standard.go.kr/KSCI/standardIntro/getStandardSearchList.do?menuId=919&topMenuId=502&upperMenuId=503&pageIndex=1&pageUnit=50&search_gubun=1&s_koreanStdNm=%EC%99%80%EC%9D%B4%ED%8D%BC | KS R 3015(와이퍼 블레이드·암) 폐지, 최종 개정 2013-12-26. KS R ISO 9619, KS R 5032, KS R ISO 6255 남음 | 표준 목록 |
| A_SAE_J903_202410_abstract.html, A_SAE_J903a_1966_abstract.html, A_SAE_J198_201210_abstract.html | https://saemobilus.sae.org/standards/j903_202410-ground-vehicle-windshield-wiper-systems, …/j903a_196605-passenger-car-windshield-wiper-systems, …/j198_201210-windshield-wiper-systems-trucks-buses-multipurpose-vehicles | 범위 설명만, 수치 없음(유료) | 표준 요약 |
| A_GB15085-2013_SAMR_info.html | https://openstd.samr.gov.cn/bzgk/gb/newGbInfo?hcno=BD71A536E22921455226B2522DBC2B51 | 시행 상태만(2015-01-01 시행) | 표준 요약 |
| A_CN_QCT44-2009_codeofchina_summary.html | http://www.codeofchina.com/standard/QCT44-2009.html | 목차만, 수치 없음 | 표준 요약 |
| A_DENSO_EU_wiper_blade_test_procedures_100k_cycles.html | https://denso-am.eu/news/denso-wiper-blade-product-development-and-test-procedures | "after 100,000 wiping cycles, which equates to two years of typical use". 살수 장치 있는 시험대, 5단계 중 3 이상 합격. SWOM 200 h | 부품사 |
| A_ARDL_wiper_blade_testing_lab.html | https://ardl.com/?p=4287 | 내구 150만 회까지, 오존 시험, 닦음 등급 1 ~ 10(9 = 3 mm 미만 줄 1 ~ 2개가 3초 안에 사라짐). 영구 변형(set)을 "가장 중요한 첫 시험"으로 둠 | 시험기관 |
| A_patent_KR101186669B1_RSR0031_wiping_test.html | GP KR101186669B1 | KS R 3015, RS R 0031 시험장치에 준한 장치. 물 뿌린 뒤 10점 만점 주관 평가 | 특허 |
| A_patent_KR100681006B1_RSR0031.html | GP KR100681006B1 | JIS D5710 내구, 50만·100만·150만 회에 5점 척도 평가. 자사 예시가 150만 회에서 약 5에서 약 3 | 특허 |
| A_patent_US7776400B2_JISD5710_500k_wet.html | GP US7776400B2 | JIS D5710 장치 "40 times/min … spraying water … 500 ml/min", 50만 회 뒤 평가 | 특허 |
| A_patent_US11851576B2_SAEJ903_100k_cycles.html | GP US11851576B2 | SAE J903 내구, 60 °C에서 36일 또는 51일 노화 뒤 10만 회. 비실리콘 블레이드는 약 2만 회에서 접촉각 60° 미만 | 특허 |
| A_patent_US7237445B1_lab_aging_fixture.html | GP US7237445B1 | Ford 오븐 지그. 암 하중과 유리 곡률로 립의 영구 변형 재현. 잦은 교체는 "primarily due to the aging of the elastomeric material". 수치 없음 | 특허 |

## B. 고무 재질, 열화, 마모

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| B_patent_US8679638_app_cited.html | GP US8679638B2 | Mitsuba. 블레이드 고무는 "normally" NR 또는 NR/CR, 황 가교. NR·CR은 오존·산소와 반응. EPDM은 염소화가 안 돼 마찰이 높음, 저온은 NR과 같고 CR보다 나음 | 특허(앱 인용 확인) |
| B_patent_EP0832015_Bosch_wiper_blade_CR.html | GP EP0832015B1 | Bosch 1996. 종래 NR, NR/CR, 디엔 고무는 오존에 약함. 할로겐화로 표면 경화. EPDM 단독은 오일 삼출, 알코올 워셔액에 약함, 마찰 높음. 립은 정지 시 수직에서 최대 약 45° 기울어 있음. 할로겐 경화 부위는 영구 압축 변형과 일사 균열에 약함 | 특허 |
| B_patent_US4638525_wiper_blade_rubber.html | GP US4638525A | Denso·Toyoda Gosei 1984. EPDM 몸체 + 염소화 디엔 립, 립 두께 0.5 ~ 1.0 mm. 배합 EPDM 100, HAF 50 | 특허 |
| B_patent_US4716618_wiper_blade_rubber.html | GP US4716618A | EPDM 몸체 + NBR/PVC 표층. 오존 시험 15% 신장, 50 pphm, 72 h. 굽힘 응력이 걸리는 목 부위에 오존 균열 | 특허 |
| B_patent_US4669144_wiper_blade.html | GP US4669144A | NR, NR/CR, NR/SBR이 최적 몸체 고무, 경도 50 ~ 70(예 60). EPDM이 오존에 강함 | 특허 |
| B_patent_US4616060_Ford_EPDM_graphite_wiper.pdf | https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/4616060 | Ford 1986, 스캔본. EPDM + 흑연 50 ~ 100 phr. 압축 영구 변형 14%(NR 11%, 70 °C 22 h). 모터 전류 기준 마찰 NR보다 35% 낮음. 닦음 점수 7.0에서 100만 회 뒤 5.0(물 40초 분사, 20초 정지) | 특허 |
| B_patent_US10946837_wiper_blade_rubber.pdf | https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/10946837 | Fukoku 2021. NR, CR 또는 혼합. 흑연 코팅 2 ~ 10 µm. 코팅 뒤 립을 잘라 날 끝 단면은 맨 고무 | 특허 |
| B_patent_US11247639_wiper_blade_rubber.pdf | https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/11247639 | Fukoku 2022. NR/CR 염소화 + 흑연 2층. 깨끗한 유리 정지마찰 신품 0.20 ~ 0.47, 1시간 내구(45회/min, 약 2,700회, 2분마다 물 1초, 16 g/cm) 뒤 0.41 ~ 0.75. 실시예 1은 0.24에서 0.45 | 특허 |
| B_patent_US8245350_wiper_edge_radius_50um.html | GP US8245350B2 | Bosch. 신품 립 접촉 반경 50 µm 이하 목표(75 µm 허용), 100 µm 초과 피함 | 특허(설계값) |
| B_ElastComp1998_wiper_chlorination_friction.html | https://koreascience.or.kr/article/JAKO199815836058977.page?lang=ko | 계명대·경창와이퍼. 염소화 전 마찰계수 1.27, 완전 염소화 0.20 ~ 0.23 | 동료심사(초록) |
| B_JKAIS2014_15-5-3292_chlorinated_wiper_rubber_EPMA.html | https://koreascience.or.kr/journal/view.jsp?kj=SHGSCZ&py=2014&vnc=v15n5&sp=3292 | 5분 처리 시 염소 침투 1.2 µm | 동료심사(초록) |
| B_Bouaziz2020_Polymers12-2354_CR_ageing.pdf | https://mdpi-res.com/d_attachment/polymers/polymers-12-02354/article_deploy/polymers-12-02354.pdf | 앱 인용 확인. CR 2 mm 판, 무충전·카본 40 phr, 공기 70·80·90 °C. 인장·경도는 3,000 h, OIT만 8,000 h. 표 1 활성화 에너지: 탄성률 90 / 84, OIT 78 / 78, 파단 신율 86 / 74, 경도 53 / 72 kJ/mol(무충전 / 충전). 저자 요약 "~80 kJ/mol" | 동료심사, OA |
| B_MottRoland2001_RCT74-79_NR_aging_Ea90kJ_crossref_abstract.json | https://api.crossref.org/works/10.5254/1.3547641 | NR 가교물 파단 변형률. 공기 90 ± 4, 해수 63 ± 3 kJ/mol | 동료심사(초록) |
| B_Woo2006_eJSoftMater2-7_NR_heat_aging_KIMM.pdf | https://www.jstage.jst.go.jp/article/ejsm/2/0/2_0_7/_pdf | 한국기계연구원. NR 5종 50·70·85·100 °C, 1 ~ 90일. NR50 파단 신율 감소율 0.213, 3.42, 8.61, 33.6 %/일. 이 값으로 맞추면 50 ~ 100 °C에서 70 ~ 100 kJ/mol | 동료심사 |
| B_HaAnh_VuKhanh_ICF11_CR_thermooxidative_Arrhenius.pdf | https://gruppofrattura.it/ocs/index.php/ICF/ICF11/paper/download/10731/10076 | CR 약 100 ~ 160 °C. C1 74.9, C2 71.8, E 74.3 kJ/mol. 150 °C 72 h에 산화 경화층 | 학회 |
| B_ElastComp2017_52-4-237_CR-CB_lifetime_Ea70kJ.html | https://www.koreascience.or.kr/article/JAKO201708642734540.page?&lang=ko | 자동차용 CR/카본 부품, 100 ~ 150 °C. 70.30 kJ/mol. 40 °C에서 인장강도 20% 손실까지 2.3년 | 동료심사(초록) |
| B_RSCAdv2024_EPDM_nonArrhenius_TTS_PMC10853807.xml | https://www.ebi.ac.uk/europepmc/webservices/rest/PMC10853807/fullTextXML | EPDM 파단 신율. 70 ~ 100 °C 51.1, 115 ~ 130 °C 99.4, 한 직선 85.6 kJ/mol. 100 °C 위아래가 다름 | 동료심사, OA |
| B_Polymers2019_11-136_modified_Arrhenius_oxygen_block.pdf | https://mdpi-res.com/d_attachment/polymers/polymers-11-00136/article_deploy/polymers-11-00136.pdf | 성균관대. NR/BR 50/50, 70 ~ 100 °C, 17 ~ 35일. 시간이 갈수록 노화 속도가 느려짐. 단순 Arrhenius는 42% 이상 벗어남 | 동료심사, OA |
| B_LeGac2012_PDS97_CR_marine_accelerated_vs_longterm.pdf | https://archimer.ifremer.fr/doc/00073/18429/20232.pdf | 실리카 충전 CR, 해수 20 ~ 80 °C, 약 2년, 23년 해양 실물과 비교. 카보닐은 바깥 200 µm. 공기 노화가 아님 | 동료심사 |
| B_Polymers2025_17-819_NR-CR_ozone_accelerated_lifespan.pdf | https://mdpi-res.com/d_attachment/polymers/polymers-17-00819/article_deploy/polymers-17-00819.pdf | 한국기계연구원 NR/CR 방현재. 오존 0.5 ppm, 50 °C, 9일에 경도 13.8%, 14.2% 상승(60.8, 60.9), 약 12일 뒤 평탄. 오존 가속 선형 가정(0.5 대 0.02 ppm = 25배), 인용 0.73 eV(약 70 kJ/mol). 총 가속 배수를 225.7과 53.25로 서로 다르게 적음. 시편 무신장 | 동료심사, OA |
| B_UniBW_ozone_ageing_NR_project.pdf | https://www.unibw.de/lrt4/forschung/forschungsprojekte/forschungsprojekte_englisch/experimental-analysis-of-ozone-ageing-in-natural-rubber.pdf | 신장된 NR은 여름 오후 측정 수준 오존에서 "after a few hours" 균열. 75 pphm 균열 깊이 곡선(6PPD 유무) | 대학 포스터 |
| B_ARDL_modulus_profiling_used_wiper_blades.pdf | https://ardl.com/wp-content/uploads/2025/02/Application-of-modulus-profiling-to-understand-aging-in-rubber-and-plastic-components-Terrill-Lewis-Pannikottu.s.pdf | Rubber World 2004-10. 오하이오 Ford Windstar에서 1년 쓴 Anco 블레이드 표면 탄성률 3.6에서 4.6 MPa(+28%), 산화 탓으로 봄 | 업계지 |
| B_SAE2020_05-13-02-0014_LeeKim_blade_rubber_after1yr_abstract.html | https://saemobilus.sae.org/articles/analysis-cross-sectional-shape-wiping-angle-a-wiper-blade-05-13-02-0014 | 서울과기대. 1년 넘게 쓴 블레이드는 새것과 닦음 각도 30° 이상, 접촉력 분포 1.5배 이상 차이. 압축 영구 변형 값은 유료 본문 | 동료심사(초록) |
| B_Bodai2012_BME_PhD_wiper_rubber_friction.pdf, B_Bodai2012_BME_PhD_booklet.pdf | https://repozitorium.omikk.bme.hu/server/api/core/bitstreams/86acca50-39da-477e-9b50-3b9a4ab072dd/content, https://repozitorium.omikk.bme.hu/server/api/core/bitstreams/fe11c94f-2cf4-4be6-9ee7-a26a94fee6fb/content | 실제 블레이드 고무 유리전이 약 −50 °C(DMA 10 Hz), tan δ 최대 0.6, 45 Shore A, 고무 Ra 1.8 µm, 유리 Ra 0.03 µm. 깨끗한 유리에서는 마모를 무시 | 학위논문 |
| B_Thapar_thesis_windshield_dry_abrasion_wiper.pdf | https://tudr.thapar.edu/bitstreams/a4106573-6563-48fa-b8e1-f022486686b6/download | 4년·25,000 km 블레이드 대 신품. 헌 날 끝은 불규칙하고 더 단단하며 덜 닦임 | 석사논문 |
| B_bibliotekanauki_2134827_road_dust_windshield.pdf | https://bibliotekanauki.pl/articles/2134827.pdf | Tribologia 2022. 석영·해사·커런덤 먼지 벤치 시험, 0.28 m 흑연 코팅 블레이드, 유리 투과율 70%까지 떨어짐 | 동료심사 |
| B_RubberWear_review_arXiv2503.19494.pdf | https://arxiv.org/pdf/2503.19494 | 고무 마모는 날카로움보다 마찰 응력이 결정, 윤활이 마모를 크게 줄임 | 프리프린트 리뷰 |
| B_press_IMechE_wiper_blade_EPDM_NR.html | https://www.imeche.org/news/news-article/the-science-and-ingenuity-behind-the-humble-wiper-blade | NR 블레이드는 주차 중 한쪽으로 "set", EPDM은 덜함. 수치 없음 | 언론(학회지) |
| B_press_Volvo_one_year_lifetime_wiper.html | https://clients.foleon.com/volvo-en/vinter-2017-18-en/wipers/ | 1년 지나면 효과가 떨어짐(열, 자외선, 먼지) | OEM 홍보 |
| B_supplier_OXIMO_SEM_new_vs_10month_blade.html | https://www.oximo.pl/en/about/oximo-quality/ | 신품과 10개월 사용품 SEM 사진, 수치 없음 | 부품사 |

초록만 확인(파일 없음): Le Saux, Le Gac 외 2014(Polym. Degrad. Stab. 99, 254). HAL은 봇 확인, Elsevier는 403이었다. 확인 가능한 서지 정보로는 해수 노화 연구다.

## C. 닦음 품질, 마찰, 소리

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| C_Reddyhoff2015_JVA137_wiper_FIV_abstract.txt | https://api.openalex.org/works/doi:10.1115/1.4029987 | 앱 인용. 관찰 대역 500 ~ 3,500 Hz, 소리는 약 1,000 Hz 또는 2,000 ~ 2,500 Hz(굽힘 1·2차). 혼합 윤활에서 속도에 따라 마찰이 떨어질 때 발생, 물이 질량을 더해 주파수·크기 변화. 중간 표면에너지 유리에서만 진동. dB 값 없음 | 동료심사(초록) |
| C_Dobre2018_Imperial_PhD_wiper_tribology_abstract_webfetch.txt | https://spiral.imperial.ac.uk/entities/publication/86e27bf7-0025-4994-8beb-60c9e0e8afb1 | 유리 표면에너지가 높을수록 마찰이 낮음. 물이 많을수록 끽 소리 주파수가 낮아짐. 수치 없음 | 학위논문(초록) |
| C_LeRouzic_thesis2015_ECL_crissement_verre_elastomere.pdf | http://www.theses.fr/2015ECDL0019/document | 끽 소리는 약 1,000 Hz 좁은 대역(이 연구 약 1,300 Hz). 반전 소리 약 500 Hz(인용). 떨림 10.99 Hz(FE 11.55 Hz). 실차 선하중 약 30 N/m. 젖은 흑연 코팅 고무 83 N/m: 0.02 m/s 미만 μ ≈ 0.38, 1 m/s에서 0.10. 마른 23 N/m: μ ≈ 1. 마르는 동안 마찰 봉우리. 끽 소리 방사 약 49 dB(Lw). Koenen·Deleau 인용: 400 mm/s·100 N/m에서 젖으면 늘 끽 소리, 마르면 없음 | 학위논문 |
| C_Frontiers2024_wiper_yaw_angle_vibration.pdf | https://www.frontiersin.org/journals/mechanical-engineering/articles/10.3389/fmech.2024.1471045/pdf | 끽 소리 약 1 kHz, 떨림 100 Hz 미만, 반전 충격. 10 mm Denso 조각 0.17 N(약 17 N/m), 5 ~ 20 mm/s, 물 윤활. 진동 29 Hz. 수막 최소 2 ~ 6 µm, 평균 0.6·V + 1.5 µm(V mm/s). μ = −0.001·V + 0.88(0°) | 동료심사, OA |
| C_SuzukiYasuda1998_JSMEC41_wiper_chatter.pdf | https://www.jstage.jst.go.jp/article/jsmec1997/41/3/41_3_616/_pdf | 두 블레이드 떨림. μ = 1.18·sgn(v) − 0.984·v + 0.474·v³. 암 힘 7.35·5.98 N. 스펙트럼 봉우리 약 8 ~ 10 Hz, 17 ~ 20 Hz | 동료심사 |
| C_SuzukiYasuda1993_JSME59_wiper_chatter.pdf | https://www.jstage.jst.go.jp/article/kikaic1979/59/559/59_559_658/_pdf | 일본어 스캔, 제목 외 미판독 | 동료심사 |
| C_JSME2000_66-641_wiper_chatter_report1.pdf | https://www.jstage.jst.go.jp/article/kikaic1979/66/641/66_641_31/_pdf | 물 분무 유리 원판. 떨림 약 11 Hz(0.57, 1.14, 2.06 m/s). μ 0.1 ~ 0.2 m/s에서 약 0.18 ~ 0.26, 2 ~ 2.5 m/s에서 약 0.06 ~ 0.09(그림) | 동료심사 |
| C_JSME2000_66-644_wiper_chatter_report2_quenching.pdf | https://www.jstage.jst.go.jp/article/kikaic1979/66/644/66_644_1110/_pdf | 일본어 스캔, 제목 외 미판독 | 동료심사 |
| C_KSNVE2014_24-5_wiper_squeal_reduction_kr.pdf | https://koreascience.or.kr/article/JAKO201415642602214.pdf | 한양대·현대자동차. 실차 끽 소리 기본 1,350 Hz와 2·3배 고조파, 시험대 3,000 Hz. 하중 감소·속도 증가·감쇠 증가·마찰 기울기 완화가 진폭을 줄임(모델) | 동료심사 |
| C_KTS2020_36-5_wiper_squeal_MRE_kr.pdf | http://koreascience.or.kr/article/JAKO202032961435498.pdf | 실제 블레이드 아님(MRE 시편). 끽 소리 4,500 ~ 6,000 Hz, μ 0.336 | 동료심사 |
| C_Min2011_JASA130_wiper_squeal_rotating_glass_abstract.txt | https://api.crossref.org/works/10.1121/1.3655277 | 워셔액 회전 유리. 온도·수분·속도 조건, 압축·블레이드 종류·워셔액 종류 영향. 수치 없음 | 학회 초록 |
| C_IJVSS2009_wiper_chatter_complex_eigenvalue.html | https://doi.org/10.4273/ijvss.1.1-3.03 | 떨림 복소 고유치 해석, 초록에 수치 없음 | 동료심사(초록) |
| C_ITU2012_thesis_new_wiper_blade_test_device.pdf | https://polen.itu.edu.tr/bitstreams/bd1c77d3-f032-450d-9494-fa17d61f707f/download | 왕복 시험대 40 mm/s: 젖음 μ 0.55, 마르는 중 1.5, 마름 1.15. 수명 시험 약 6,674회(적신 뒤 마르게 둠): 일반 코팅 블레이드 40 mm 길이에서 0.0441 mm³, 마모 삼각형 0.110 × 0.040 mm. Archard K 5.68×10⁻⁴ 등. 본문 계산에 두 배 차 모순 있음 | 석사논문 |
| C_KR20130132181A_Hyundai_silicone_wiper_abrasion_gp.html | GP KR20130132181A | 현대자동차. 발수 유리에서는 수막이 없어 마찰이 커져 떨림·소음. 건식 마모 시험 3만 회, 15 g/cm, 100 mm 행정. 떨림·소음은 10만 회 뒤 평가 | 특허(현대) |
| C_JP2019064281A_Fukoku_blade_friction_gp.html | GP JP2019064281A | 청구 한계: 정지마찰 신품 1.0 이하, 10만 회 뒤 4.0 이하. 신품 0.9 이하, 50만 회 뒤 2.8 이하. 17 g/cm. 내구 주기 물 5.5분, 정지 30초 등, 100 ~ 500 ml/min | 특허 |
| C_JP2020164681A_Bando_wiper_rubber_chatter_gp.html | GP JP2020164681A | 떨림 전압 시험(12 V에서 낮춰 떨림 시작 전압): 6 V 이하 매우 우수, 12 V 이상 불량. 3만 회 시점 실시예 8.5 V, 비교예 NR 10.5 V, EPDM 12 V. 닦음 5점 척도(3초 안 소멸 기준) 1만·3만 회 평가 | 특허 |
| C_US10883017B2_CAP_silicone_blade_chatter_gp.html | GP US10883017B2 | 캡(CAP). JIS D5710 시험기, 마른 15분 뒤 500 cc/min 살수 50만 회(40회/min), 10만·30만·50만에 확인. Rain-X·Rain OK·Soft99 처리 유리에서 떨림 시험 | 특허(국내 업체) |
| C_JP2001206200A_Denso_water_repellent_chatter_gp.html | GP JP2001206200A | 발수 유리는 수막이 적어 마찰과 속도 의존성이 커져 떨림. 반전 부근(약 1.0 m/s 미만)에서 차이가 가장 큼 | 특허 |
| C_JP7081569B2_Denso_wiper_speed_water_repellent_gp.html | GP JP7081569B2 | 점수: 발수 유리 +3, 코팅 0.5 ~ 1년 경과 +2, 저마찰 블레이드 −2, 블레이드 0.5 ~ 1년 경과 +2. 4 이상이면 빠른 모드. 0.5 ~ 1년 뒤 교체 알림 | 특허 |
| C_US4045838A_Acushnet_blade_coating_dry_wipe_streaks_gp.html | GP US4045838A | 블레이드 μ 2.1 이하(1.5 권장). 코팅 없는 블레이드가 신품·마른 100·500회 뒤 모두 줄무늬 약 1.5배 | 특허 |

## D. 실제 사용량

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| D_KMA_press_2021-03-25_new_normals_1991-2020.html | https://www.kma.go.kr/kma/flexer/html/press/2021/03/25/ATC202103250932093_863521e7-fd31-4f26-bfce-04a9e14ffb33.hwp.files/Sections1.html | 1991 ~ 2020 평년. 연강수량 전국 1,306.3, 서울 1,417.9, 강릉 1,444.9, 대전 1,351.2, 광주 1,380.6, 부산 1,576.7, 대구 1,080.8, 제주 1,502.3 mm. 여름 710.9 mm(54%). 강수일수 전국 104.5, 서울 108.6, 강릉 113.6, 대전 113.9, 광주 120.8, 부산 99.0, 대구 96.9, 제주 127.8일. 눈 일수 전국 20.1 | 정부 통계 |
| D_KMA_Daejeon_2022_climate_analysis_normals1991-2020.pdf | https://www.kma.go.kr/daejeon/html/pop/files/new_climate_report.pdf | 대전·세종·충남 1,271.7 mm, 108.6일. 월별 강수일수(7월 14.9, 8월 13.8, 10월 6.1) | 정부 통계 |
| D_IEM_METAR_RKSS_Gimpo_2015-2024_wxcodes.csv, D_IEM_METAR_RKSI_Incheon_2015-2024_wxcodes.csv, D_IEM_METAR_RKPK_Gimhae-Busan_2015-2024_wxcodes.csv, D_IEM_METAR_RKTN_Daegu_2015-2024_wxcodes.csv, D_IEM_METAR_RKTU_Cheongju_2015-2024_wxcodes.csv, D_IEM_METAR_RKNY_Yangyang-Gangwon_2015-2024_wxcodes.csv, D_IEM_METAR_RKPC_Jeju_2015-2024_wxcodes.csv, D_IEM_METAR_RKJJ_Gwangju_2015-2024_wxcodes.csv | https://mesonet.agron.iastate.edu/cgi-bin/request/asos.py?station=RKSS&data=wxcodes&year1=2015&month1=1&day1=1&year2=2025&month2=1&day2=1&tz=Etc/UTC&format=onlycomma&latlon=no&missing=M&trace=T&direct=no&report_type=3 (station만 바꿈) | 공항 METAR 2015 ~ 2024 시간별 현천. 강수 보고 시간 비율: 김포 8.36%(연 733 h), 인천 8.13%, 김해 9.51%, 대구 8.79%, 청주 10.41%, 양양 8.80%, 제주 12.21%(1,069 h). 07 ~ 21시 비율 7.9 ~ 11.9%. 약한 강수가 84 ~ 89%. 광주(RKJJ)는 운영 시간만 보고해 제외 | 원자료(항공 관측), 집계는 우리 계산 |
| D_Kim2008_Atmosphere_diurnal_hourly_precip_frequency_Korea.pdf | https://koreascience.or.kr/article/JAKO200828839767058.pdf | 60개 지점 1973 ~ 2007 시간 강수 빈도. 정규화가 불분명해 강수 시간으로 못 씀 | 동료심사 |
| D_KICS_1min_rain_rate_time_percentage_Korea.pdf | https://journal.kics.or.kr/digital-library/manuscript/file/34395/NODE01881025.pdf | 1분 강우강도 5 ~ 9월, 0.01% 초과 강도 평균 76.67 mm/h. 총 강수 시간 정보 없음 | 동료심사 |
| D_FHWA-RD-87-105_Harwood1988_wet_weather_exposure_WETTIME.pdf | https://rosap.ntl.bts.gov/view/dot/897/dot_897_DS1.pdf (curl 403, WebFetch로 저장) | 비 그친 뒤 노면 건조 5 ~ 60분, 평균 31.6분. 캔자스시티 1984 노면 젖음 8.3% | 정부 보고서(미국) |
| D_SWOV_TRID33120_rainfall_vehicle-km_share.txt | https://trid.trb.org/View/33120 | 네덜란드 지방도. 강우 중 주행은 전체 차량 km의 10% 미만 | 연구기관(초록 캡처) |
| D_Finland2022_road_condition_km_share_record.html | https://forskning.fi/sv/results/publication/0385311122 | 핀란드 차량 km 노면 비율: 마름 56%, 젖음 29%, 겨울 노면 12% | 동료심사(초록) |
| D_Andrey2003_weather_chronic_hazard_abstract.html | https://ideas.repec.org/a/spr/nathaz/v28y2003i2p319-343.html | 강수 시 사고 75% 증가. 노출 비율 없음 | 동료심사(초록) |
| D_KR1999_weather_urban_expressway_flow_abstract.html | https://koreascience.or.kr/article/JAKO199911920931000.page | 올림픽대로 강우 시 자유류 용량 10 ~ 20% 감소 | 동료심사(초록) |
| D_KR2015_rain_rural_highway_speed_volume_abstract.html | https://www.koreascience.or.kr/article/JAKO201513251335254.page | 국도 지방부 강우 시 평균 속도 3.2% 감소(최대 8.8%) | 동료심사(초록) |
| D_UMTRI2004-35_Wetzel_naturalistic_wiper_usage_capture.txt | https://deepblue.lib.umich.edu/items/9f4e6554-9ed6-431d-b63b-5d5f2b1b971b (403) | 미시간 96명 약 26일. 와이퍼가 운행 시간의 8.6%에 켜짐(검색 요약 한 곳만, 미확인). 가장 느린 간헐이 가장 많이 쓰임 | 2차(본문 미확인) |
| D_Hill2019_wiper_rainfall_maps_arxiv.pdf, D_Hill2019_SciRep_supplement.pdf | https://arxiv.org/pdf/1806.10988, https://media.springernature.com/original/springer-static/esm/art%3A10.1038%2Fs41598-018-36282-7/MediaObjects/41598_2018_36282_MOESM1_ESM.pdf | 앤아버 커넥티드카 약 70대. 와이퍼 켜짐으로 비 판정 TPR 93.1%, TNR 98.2%. 보충자료 그림 S1: 와이퍼 단수와 강우강도 사이에 "No clear relationship" | 동료심사 |
| D_Ford_Motorcraft_wiper_motor_keylife_1.5M_cycles.txt | https://www.ford.com/product/wiper-motor-p4000018717 | 모터 수명 시험 "10 year/150,000 mile … over 1.5 million operating cycles" | OEM(WebFetch 캡처) |
| D_Bosch_autobiz_aerotwin_500000_cycles.html | https://www.autobiz.ie/aftermarket/ready-for-any-weather-with-bosch-wipers | "500,000 wiping cycles … average service life, according to SAE J903A" | 업계지 |
| D_Fiat_UK_wiper_blades_125000_wipes_6months.txt | https://www.fiat.co.uk/owners/maintenance/wiper-blades (403) | "within 6 months … 125 000 wipes in average"(검색 조각만) | 2차(미확인) |
| D_JustAuto2002_wiper_blades_1million_wipes_per_year.txt | https://www.just-auto.com/features/wiper-blades-a-safety-critical-component | 연 100만 회(출처 없음). 1990년대 교체 주기 미국 2.9년(1993), 3.8년(1997), 일본 약 1.3년 | 업계지 |
| D_KOTSA_2025_mileage_press_fnnews_2026-06-29.html | https://www.fnnews.com/news/202606291005221113 | 교통안전공단 2025: 전체 37.8 km/일, 비사업용 30.8 km/일, 사업용 94.7 km/일 | 정부 통계(언론 경유) |
| D_Carrot_insurance_2024_annual_km_fnnews_2025-02-19.html | https://www.fnnews.com/news/202502190902411936 | 퍼마일 보험 가입자 연 8,275 km(저주행 쏠림) | 언론 |
| D_Seoul_mediahub_rainy_monday_traffic.html | https://mediahub.seoul.go.kr/archives/861429 | 서울 2014 평균 25.7 km/h, 도심 17.4, 도시고속 58.3. 비 오는 날은 예외 없이 느림 | 정부(서울시) |
| D_Seoul_travel_speed_2021-2025_daum.html | https://v.daum.net/v/29k6zc3Gk1 | 서울 2025 평균 22.5 km/h, 도심 18.0 | 언론 |
| D_MOLIT_2024_road_traffic_survey_kbmaeil_2025-05-01.html | https://kbmaeil.com/article/20250501500654 | 2024 도로 교통량 하루 5.16억 km | 언론 |
| D_AAAFTS_American_Driving_Survey_2024_capture.txt | https://aaafoundation.org/american-driving-survey-2024 | 미국 하루 60.4분, 31.1마일 | 연구재단 |
| D_Hyundai_NX4_Tucson_2025_owners_manual_wipers_en.html | https://ownersmanual.hyundai.com/full_webhelp/NX4/2025/en_GN/id631c024a334.html | 스위치 HI, LO, INT, AUTO, MIST와 간헐·자동 조절 링. 초 단위 값 없음 | OEM |
| D_Kia_MQ4_2022_owners_manual_wiper_modes_ko.html | https://ownersmanual.kia.com/full_webhelp/MQ4/2022/ko_KR/topics/t00582.html | INT는 약한 비용. AUTO는 앞유리 상단 센서로 비가 많으면 빨라지고 그치면 멈춤 | OEM |
| D_Saab93_2005_wiper_motor_techdata_mirror.html | https://saabwisonline.com/9-3-9440/2005/3-electrical-system/wash-wipe-systems/technical-data/windscreen-wiper-motor | 현대 아님(Trico 모터): 13.5 V 젖은 유리 저속 45 ± 6, 고속 71 ± 8회/min | 2차(정비 문서 사본) |
| D_patent_US9407193_wiper_control_apparatus.html | GP US9407193B2 | 현대·기아 와이퍼 릴레이 제어, 속도 수치 없음 | 특허 |

## E. 제조사 안내와 소비자 시험

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| E_ConsumerReports_2008_wiper_tests_press_release.html | https://www.consumerreports.org/media-room/press-releases/2008/10/consumer-reports-windshield-wiper-tests-reveal-good-wipers-arent-pricey-but-most-fade-fast/ | 15종을 직원 차 185대에 실사용. 신품은 모두 Very Good 이상, 대부분 "degrade after six to nine months". 6개월을 버틴 것은 1종. "replace their wipers every six to twelve months" | 소비자 단체 |
| E_ConsumerReports_2008_via_ABC30_3_6_9_months.html | https://abc30.com/archive/6479032 | 3·6·9개월 평가, 6개월에 Good보다 나은 것은 1종 | 언론 |
| E_ConsumerReports_wiper_blades_buying_guide.html | https://www.consumerreports.org/cars/wiper-blades/buying-guide/ | 햇빛이 고무를 분해, 먼지가 마모. 약 6개월 교체, 185대 중 약 절반이 시작 시점에 이미 교체 필요. 지금은 시험하지 않음 | 소비자 단체 |
| E_TCS_ADAC_2015_Scheibenwischer_im_Vergleich.pdf, E_TCS_scheibenwischer_test.html | https://www.tcs.ch/mam/Digital-Media/PDF/Booklets/Scheibenwischer-im-Vergleich.pdf, https://www.tcs.ch/de/testberichte-ratgeber/tests/autozubehoer/scheibenwischer.php | 8종. 가중치: 취급 10%, −10 °C 닦음 27%, 상온 63%. 상온 점수 48 ~ 74%, 저온 26 ~ 60% | 시험기관 |
| E_ADAC_2015_wiper_test_firmenauto.html, E_ADAC_2015_wiper_test_kfzauskunft.html, E_ADAC_wiper_test_webde.html, E_AutoBild_scheibenwischer_test.html, E_Bosch_press_release_617.html | https://www.firmenauto.de/fahrzeuge/pkw/scheibenwischertest-bei-kaelte-wischt-nur-einer-richtig-gut/, https://www.kfz-auskunft.de/news/19654.html, https://web.de/magazine/auto/scheibenwischer-adac-test-worauf-autofahrer-achten-32660876, https://www.autobild.de/artikel/scheibenwischer-test-5578932.html, https://www.bosch-presse.de/pressportal/nl/en/press-release-617.html | ADAC 2015: 신품, 그리고 "nach 150 000 Wischzyklen"과 자외선·온도 모사 뒤 +20 °C, −10 °C 평가. 8종 중 gut 3, −10 °C에서 좋은 것은 1종. 제조일 표기 요구 | 언론(시험기관 인용) |
| E_AutoZeitung_scheibenwischertest_8.html | https://www.autozeitung.de/scheibenwischertest-acht-scheibenwischer-im-test-32552.html | 젖은 내구, −30 °C 결빙, 여름 열 모사(영구 변형), 염수. 횟수 없음 | 언론 시험 |
| E_PMM_OE_quality_wiper_blade_development.html | https://pmmonline.co.uk/?p=11721 | Champion(Federal-Mogul) 2017: OE 요구 최대 50만 회 "equates to five years of use in many markets". 70 °C, −30 °C 72 h 뒤 시험. "Cars parked each day in the shade can expect two or three times the life" | 부품사(업계지) |
| E_DENSO_EU_aftermarket_wiper_blades.html | https://www.denso-am.eu/products/wiper-blades | 12개월마다 교체 | 부품사 |
| E_DENSO_US_2013_MTD_replace_6to12_months.html | https://www.moderntiredealer.com/industry-news/service/article/11512015/denso-wiper-blades-need-regular-replacement-2013-05-22 | 6개월 ~ 1년, 원인 햇빛·왁스·모래·산성비 | 부품사(언론) |
| E_Valeo_Everguard_replace_once_a_year.html, E_Valeo_Infinium_durability_claim.html | https://www.valeoservice.com/en-com/passenger-car/car-wiper-blades/valeo-everguard, https://www.valeoservice.com/passenger-car/car-wiper-blades/infinium-most-durable-valeo-wiper-blade-driver | 1년에 한 번 교체. 실리콘 "twice as long"(오존 시험), Infinium "up to 80% longer". 원인 열·저온·자외선·오존 | 부품사 |
| E_Valeo_2025_AutomotiveInsights_wiper_test.html | https://www.valeoservice.com/en-com/swf-best-wiper-blades | 6개 브랜드 25대 3개월. 열화 수치 없음 | 부품사 홍보 |
| E_Bosch_US_ICON_wiper_blades_product.html | https://boschautoparts.com/p/icon-wiper-blades | 장기 오존 노출 모사 시험, "Lasts Up To 40% Longer". 주기 없음 | 부품사 |
| E_BoschKorea_press_996561_wiper_check.html, E_BoschKorea_BCS_press_932962_wiper_6months.html | https://www.newswire.co.kr/newsRead.php?no=996561, https://www.newswire.co.kr/newsRead.php?no=932962 | "6개월마다 정기 점검 후 6개월~1년 사이에 한 번씩 교체" | 부품사(한국) |
| E_BoschKorea_APAC_survey_898664.html | https://www.newswire.co.kr/newsRead.php?no=898664 | 4개국 2,405명(한국 298명). 6개월마다 교체 9.7%, 7 ~ 12개월 30.9%, 1 ~ 1.5년 34.5%. 한국 최다 응답 7 ~ 12개월(33.9%) | 부품사 설문 |
| E_Trico_UK_2017_tyrepress_6to12_months.html | https://tyrepress.com/2017/10/trico-suggests-garages-offer-a-free-winter-wiper-blade-check | 일반 수명 약 6 ~ 12개월 | 부품사(언론) |
| E_RainX_help_blade_life_6to12_months.html, E_RainX_press_replace_every_6_months_movinout.html | https://help.rainx.com/support/solutions/articles/19000124348-do-rain-x-wiper-blades-have-an-expiration-date-, https://movinout.com/magazine/article/rain-x-reminds-you-to-replace-your-wiper-blades-every-6-months-370.php | 6 ~ 12개월, 12개월 넘으면 교체 | 부품사 |
| E_SimplyBrands_2024_lab_test_500k_tyrepress.html | https://tyrepress.com/2024/05/simply-brands-wiper-blades-succeed-in-independent-lab-tests | ARDL에서 SAE J903 C 조건 50만 회 내구 | 언론 |
| E_Hyundai_DN8_2026_owner_manual_EN_blade_replacement.html | https://ownersmanual.hyundai.com/full_webhelp/DN8/2026/en_US/id5f7cf47b209.html | "check the wiper blades every 6 months". 점검 주기만, 교체 주기 없음. 워셔액 없이 계속 작동하면 약 10초 정지 | OEM(현대 미국판) |
| E_Hyundai_US4_2026_owner_manual_KR_wiper_blade_inspection.html | https://ownersmanual.hyundai.com/full_webhelp/US4/2026/ko_KR/id23C5G0NI0ROA.html | 주기적으로 점검, 주 2회 작동, 닦아도 남으면 블루핸즈. 개월·km 없음 | OEM |
| E_Kia_MQ4_2026_owner_manual_KR_blade_inspection.html, E_Kia_MQ4_2026_owner_manual_KR_wiper_blades.html | https://ownersmanual.kia.com/full_webhelp/MQ4/2026/ko_KR/topics/chapter8_15_1.html, …/chapter8_15_2.html | 같은 안내, 주기 없음 | OEM |
| E_Toyota_dealer_SET_wipers_inspect_5000mi.html | https://exploresetoyota.com/parts/wipers | 5,000마일 정비마다 점검, 균열 원인은 자외선 | 2차(토요타 유통사) |
| E_HyundaiPartsDeal_retailer_wiper_interval.html | https://hyundaipartsdeal.com/oem-hyundai-wiper_blade.html | 6개월 또는 먼지 지역 3,000 ~ 5,000마일, 덥고 볕 강한 곳 최소 1년 1회 | 2차(소매점) |
| E_patent_US6047436A_service_life_indicator_ozone.html | GP US6047436A | 오존이 "one of the major contributors", 뒤집힘 실패·떨림·균열. 수명 고오존 지역 3개월 ~ 2년. 근거 데이터 없음 | 특허 |
| E_AKatsastus_FI_wiper_interval.html | https://www.a-katsastus.fi/en/frequently-asked-questions/what-is-the-reasonable-interval-for-replacing-the-wiper-blades/ | 약 1년, 많이 달리면 6개월 | 2차(검사업체) |
| E_JP_Response_2017_wiper_annual_check.html | https://response.jp/article/2017/06/12/295985.html | 연 1회 점검·교체(NWB 후원 기사) | 언론 |
| E_CN_Tencent_2021_wiper_replacement_QCT44.html | https://news.qq.com/rain/a/20210908A0BQ4300 | QC/T 44-2009가 고무 "不低于5×10⁴次"라고 적음(단체표준 조각은 50만). 103.1일 × 1 h × 50회 = 연 약 30만 회 추정 | 언론 |
| E_blog_braindetox_6month_rule_origin.html | https://braindetox.kr/en/posts/wiper_blades_interval_guide.html | 6개월 규칙은 부품 업계 관행이라는 주장, 기아 2026 설명서에 주기 없음 | 2차(블로그) |

## F. 노출 조건

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| F_Seoul_env_ozone_trend_559396.html | https://news.seoul.go.kr/env/archives/559396 | 서울 연평균 오존 0.024 ppm(2015)에서 0.037 ppm(2024). 2024년 5 ~ 8월 평균 0.044 ppm(2000년 이후 최고). 주의보 일수 3일에서 31일(8월 기준). 일사 합계 4,620 MJ/m²(2015)에서 5,186 MJ/m²(2023) | 정부(서울시) |
| F_KSES2009_solar_radiation_resources_Korea.pdf | https://koreascience.kr/article/CFKO200935161992338.pdf | 에너지연 16개 지점 1982 ~ 2008 수평면 3.61 kWh/m²/일(연 약 4,740 MJ/m²), 서울 최저 | 학회(연구기관 자료) |
| F_JAKO201027463259243_yearly_solar_radiation_Korea.pdf | https://koreascience.or.kr/article/JAKO201027463259243.pdf | 같은 자료 3.60 kWh/m²/일 | 동료심사 |
| F_KIOST_Korea_erythemal_UV_seasonal_climate_record.html | https://sciwatch.kiost.ac.kr/handle/2020.kiost/5850?mode=full | 서울 1994 ~ 2000 홍반 UV-B 일평균 1.6 kJ/m²(6월 2.7, 12월 0.5) | 동료심사(초록) |
| F_Hong2008_Anmyeon_EUVB_abstract.html | https://koreascience.kr/article/JAKO200807841289137.do | 안면도 UV-B 7월 최대, 황사 시 35 ~ 60% 감소 | 동료심사(초록) |
| F_Vanos2018_Temperature5_parked_vehicle_surfaces_PMC6204985.xml | https://www.ebi.ac.uk/europepmc/webservices/rest/PMC6204985/fullTextXML | 앱 인용 확인. 템피, 햇빛 60분: 대시보드 68.9 °C, 실내 공기 46.7 °C. 그늘 차 대시보드 47.8 °C | 동료심사, OA |
| F_press_daum_2026_KoROAD_dashboard_85C.html | https://v.daum.net/v/20260807073601942 | 도로교통공단 시험 34 °C, 4 h: 대시보드 검은 차 85.5, 흰 차 73.7 °C | 언론(기관 인용) |
| F_KAIS2025_spring042_parked_car_dashboard_92C.pdf | https://www.kais99.org/jkais/springNfall/spring2025/oral/2025_spring_042.pdf | 교통안전공단 시험 인용: 약 35 °C, 1 h, 대시보드 최대 92 °C | 학회(재인용) |
| F_FSEC-PF-160-89_parked_car_temps.pdf, F_FSEC-PF-198-90_parked_car_dash_temps.pdf | https://publications.energyresearch.ucf.edu/wp-content/uploads/2018/06/FSEC-PF-160-89.pdf, …/FSEC-PF-198-90.pdf | 플로리다 실내 공기 흔히 66 °C, 대시보드 약 93 °C | 연구기관 |
| F_NBSIR74-533_windshield_tint_car_temps.pdf | https://nvlpubs.nist.gov/nistpubs/Legacy/IR/nbsir74-533.pdf | 착색 앞유리로 실내 공기 2 ~ 3 °C, 유리 근처 대시보드 최대 16 °C 낮음. 바깥 유리 온도 없음 | 정부 보고서(미국) |

## G. 현대 차종

| 파일 | URL | 주는 수치 | 신뢰도 |
|---|---|---|---|
| G_Hyundai_OM_NX4_2026_KR_blade_inspection.html | https://ownersmanual.hyundai.com/full_webhelp/NX4/2026/ko_KR/id4b106f77f9f.html (`../../07_model_specs/ownersmanual/NX4_2026/wiper-2.html` 복사) | 국내판 공통 문구: 경화·마찰 증가로 소음·떨림, 언 블레이드 작동 금지, 주 2회 작동, 오래 안 쓰면 블레이드 변형, 오염 제거 뒤에도 선·소음·떨림·잔물이면 점검. 교체 주기 없음 | OEM |
| G_blog_SantaFe_TM_MX5_wiper_sizes.html | https://info.searcheditors.com/?p=10200 | 싼타페 MX5 650 / 450 mm, TM 650 / 400 mm, 뒤 300 ~ 350 mm | 2차(블로그) |

차종별 블레이드 길이는 `../../07_model_specs/specs.json`의 `wipers`(현대모비스 애프터마켓 적용표)를 그대로 쓴다. 국내판 설명서 AX, AXEV, NE1, MX5, DN8, NX4의 와이퍼 페이지를 모두 읽었고 교체 주기 문구는 어디에도 없었다.

## H. 판정 특허

| 파일 | 출원인 | 주는 내용 | 신뢰도 |
|---|---|---|---|
| H_US10308225B2_Ford_accelerometer_wiper_gp.html | Ford | 앱 인용 확인. 실내 앞유리 가속도계, 1 ~ 10 kHz, 끽 소리 "around 2.5 kHz". 마모 블레이드가 1 ~ 10 kHz에서 "measurably higher"(그림만, dB 없음). 기준과의 차이 판정, 모터 전류 보조 | 특허 |
| H_US10068388B2_suggest_wiper_replacement_gp.html | Ford | 실내 마이크. 하한 약 300 Hz(B 세그먼트), 대역폭 5 kHz 이상 유지. 문턱은 깨끗한 14회 + 소음 1회의 RMS 90% 값(N10). 기록마다 "discarding the first X cycles as dust, dirt or other elements might induce noises in new wipers". 마모 소음 중심 256 Hz(그림 5 설명) | 특허 |
| H_US20180126955A1_Ford_wiper_noise_detect_gp.html | Ford | 실내 마이크 5 ~ 8 kHz 대역, 행정 중간에서만 떨림 값을 셈(반전 제외), 저속·간헐에서 | 특허 |
| H_US20160207500A1_GM_monitor_wiper_blades_gp.html | GM | 습도, 빛(자외선), 온도, 모터 부하, 저온·고전압(눈·얼음), 닦음 횟수, 지역, 경과 시간의 백분율 계수 합. 계수 수치 없음 | 특허 |
| H_US20250222900A1_obstruction_wiper_wear_gp.html | Ford | 카메라로 물·이물 구분, 등급별 마모 배수 예 2배·3배·10배. 워셔 없는 마른 모래, 0 °C 근처 얼음이 높은 등급 | 특허 |
| H_US9539984B2_Valeo_wear_assess_gp.html | Valeo | 모터 전력으로 마찰계수 추정. "usual wet friction coefficient values … around 0.2 or 0.3, whereas on dry surface … above 0.8", 마름/젖음 4 ~ 5배. 마름 판정 문턱 젖음 기준의 4배 이상. 젖은 마찰은 마모와 무관, 마른 마찰이 코팅 마모(자외선·온도)로 증가. 마모 판정 예: 신품 대비 1.5배 | 특허 |
| H_US20180215349A1_Valeo_monitor_wiper_use_gp.html | Valeo | 이상 사용 기록: 빈 탱크로 워셔, 3 °C 미만에 비, 모터 구속, 닦음 각 비정상 | 특허 |
| H_US10769871B2_Valeo_wiping_use_gp.html | Valeo | 교체 감지 뒤 왕복 횟수를 문턱과 비교, 수치 없음 | 특허 |
| H_KR101927946B1_predict_wiper_replacement_gp.html | 미래테크놀로지 | 레인센서 작동 25만, 와이퍼 스위치 175만, 워셔 스위치 15만 중 먼저 닿는 것 | 특허 |
| H_KR101053022B1_wiper_blade_alarm_gp.html | ADM21 | 블레이드 부착 카운터, 100회마다 LED, 최대 10만 회(기후에 따라 조정) | 특허 |
| H_KR20230166222A_blade_replace_indicator_gp.html | 대한솔루션 | 립 마모 시 구멍이 드러나 줄이 남는 표시 | 특허 |
| H_DE102009046132A1_Bosch_blade_condition_gp.html | Bosch | 노화 립이 반전 때 뒤집히지 못해 덜컥거림, 모터 전류 스파이크 수가 나이와 함께 증가 | 특허 |
| H_US6057660A_gp.html | Bosch | 레인센서 신호의 느리거나 빠른 변화로 마모 판정 | 특허 |
| H_DE10254684A1_Valeo_optical_streak_gp.html | Valeo | 광학 센서로 줄무늬 감지 | 특허 |
| H_JP2010000963A_Alpine_replacement_warning_gp.html | Alpine | 카메라로 고르지 않은 닦음 감지 | 특허 |
| H_JP5454465B2_Mitsubishi_wiper_change_notify_gp.html | Mitsubishi | 닦은 뒤 수막을 속도·강우·차속·바람으로 예상한 값과 비교 | 특허 |
| H_CN114347952A_wiper_residual_life_gp.html | Xinghe Zhilian | 정격 수명 + 환경·사용·영상·실내 소리 유사도 가중, 가중치 없음 | 특허 |
| H_CN109552255A_judder_state_detect_gp.html | 샤먼이공대 | 카메라 블레이드 각도 스펙트럼으로 떨림 판정 | 특허 |
| H_CN109466510B_residual_rain_streak_detect_gp.html | 샤먼이공대 | 남은 빗줄기로 고장 판정(세부 미독) | 특허 |
| H_EP3835147B1_aircraft_wiper_usage_gp.html | Rosemount | 항공기. 저·중·고속 시간마다 다른 함수로 남은 수명 차감, 절반에서 노란 표시 | 특허 |
| H_US20260097742A1_Tesla_wiper_friction_estimation_gp.html | Tesla | 에너지 수지로 마찰 추정, 마른 닦음과 물·비·얼음의 마찰 차 3배 초과 | 특허 |
| H_DE10035042A1_Bosch_blade_state_gp.html | Bosch | 링크 미끄럼 보정, 관련성 낮음 | 특허 |

기존 `../originals/wiper-wear-EP2709879.html`(광학 센서 마모 판정 특허)은 다시 받지 않았다.

## 받지 못한 것

- RS R 0031:2003 원문(비공개, 소비자원 요약만), KS R 3015(폐지), SAE J903·J198 본문(유료), QC/T 44-2009·GB 15085-2013 본문(유료·비공개, 절강 단체표준 PDF는 504), JASO 와이퍼 표준(못 찾음)
- 소비자원 2016 이외 와이퍼 시험(게시판에 없음), Stiftung Warentest·Which? 와이퍼 시험(없음), ADAC 원문 페이지(404, 언론 사본만), ADAC 자외선 시간·오존량
- Le Saux 2014, Gillen 2005(CR 24년 현장 노화), Reddyhoff 2015 본문, Deleau 2008, Koenen·Sanon 2007, Bódai·Goda 2012/2013 논문, Lancioni 2016, Le Rouzic 2013(유료 또는 봇 확인)
- Lake 1970, Braden·Gent 오존 임계 변형·균열 성장률(유료)
- 기상청 강수계속시간 평년값, 1 mm 이상 강수일수(data.kma.go.kr 로그인 필요)
- UMTRI-2004-35 본문(403, 미러는 Cloudflare 확인), SHRP2 날씨 노출 자료(429/404)
- 현대 정비지침서의 와이퍼 속도(회/min)·간헐 시간(구독 사이트), 현대모비스·블루핸즈 공식 교체 주기, 아이오닉 5·캐스퍼·싼타페 MX5 순정 블레이드 길이(현대 공식 자료)
- 현대·기아·모비스의 와이퍼 마모 판정 특허(검색에서 나오지 않음), DE19955874A1(Google Patents 503)
- Ford 와이퍼 모터 페이지 원문(403, WebFetch 캡처만), Fiat UK 페이지(403)
