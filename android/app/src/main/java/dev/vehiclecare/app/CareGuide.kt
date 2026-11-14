package dev.vehiclecare.app

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * One figure behind the judgment, in the driver's words. [fraction] fills a bar where 1 is the limit used up, and
 * [extra] adds what the car's data counted on top of the plain reading (rough driving, short trips, heat), so the bar
 * shows where an "equivalent" figure comes from. [now] and [limit] label the bar's two ends; [note] stands in when
 * there is no limit to measure against. [alert] marks the figure that moved the status.
 */
data class Factor(val title: String, val value: String, val fraction: Float? = null, val note: String = "",
                  val alert: Boolean = false, val marker: Float? = null, val extra: Float = 0f,
                  val now: String = "", val limit: String = "")

/** What the detail screen says first: one instruction, one reason, and the few figures that back them. */
data class Guide(val headline: String, val advice: String, val factors: List<Factor>)

/**
 * Turns an assessment into plain guidance. The research figures (m/s², kJ/km, dB) stay on the detail list; here every
 * number is a distance, a day count or a share of something the driver already knows.
 */
object CareGuide {
    private val k = Locale.KOREA
    private fun n(v: Number) = String.format(k, "%,d", Math.round(v.toDouble()))
    private fun db(v: Double) = String.format(k, "%.1f", v)

    /** The instruction for a part, as a verb phrase. */
    fun task(part: Part, action: ServiceAction) = when {
        action == ServiceAction.ROTATE -> "타이어 위치를 바꿔 주세요"
        part == Part.TIRE && action == ServiceAction.REPLACE -> "타이어 네 개를 함께 교체해 주세요"
        part == Part.ENGINE_OIL -> "엔진오일을 갈아 주세요"
        part == Part.WIPER -> "와이퍼를 바꿔 주세요"
        part == Part.BRAKE_PAD -> "브레이크 패드 점검을 받아 주세요"
        part == Part.TIRE -> "타이어 공기압과 마모를 확인해 주세요"
        part == Part.HVAC -> "냉방·난방 점검을 받아 주세요"
        else -> "실내 필터를 바꿔 주세요"
    }

    /**
     * The short line under a part's ring: the job and how far away it is, taken from the figure that set the state (or,
     * while nothing is near, the schedule figure closest to its limit). Never a percentage: a share of an interval read
     * as "part life" (0% on a pad with 9.6 mm left), which the requirements rule out.
     */
    fun label(plan: MaintenancePlan, a: Assessment, factors: List<Factor>): String {
        val task = (a.task ?: plan.action).label
        val by = a.drivenBy.orEmpty()
        // A measured thickness says more than a job name: the reading itself.
        if (by.startsWith("패드 잔량") || by.startsWith("홈 깊이")) return by.replace(" 추정", "")
        if (by.startsWith("작동 소리")) return "먼저 닦아 보기"
        val schedule = factors.filter { it.title in SCHEDULE_ROWS && it.fraction != null }
        val f = factors.firstOrNull { it.alert } ?: schedule.maxByOrNull { it.fraction!! }
        val v = f?.value.orEmpty()
        return when {
            v.endsWith(" 남음") && a.status != DueStatus.DUE -> "${task}까지 ${v.removeSuffix(" 남음")}"
            v.endsWith(" 지남") -> "$task $v"
            a.status == DueStatus.DUE -> "지금 $task"
            a.status == DueStatus.SOON -> "곧 $task"
            else -> a.status.label
        }
    }
    private val SCHEDULE_ROWS = setOf("기간", "주행거리", "월 점검", "공기압·마모 점검")

    fun build(part: Part, plan: MaintenancePlan, record: ServiceRecord?, a: Assessment, s: SignalSnapshot,
              records: List<ServiceRecord>, mileage: Mileage?, report: OwnerReport?, today: LocalDate): Guide {
        val r = a.schedule
        val rotation = if (part == Part.TIRE) Rotation.of(records, mileage, s.tire) else null
        val factors = factors(part, plan, record, a, s, rotation, today, report, mileage, records)
        if (record == null) return Guide("마지막 ${plan.action.label} 날짜를 알려 주세요", "날짜만 넣어도 다음 시점을 계산해요. 주행거리는 몰라도 돼요.", factors)

        val sound = if (part == Part.WIPER) s.sound.shift(record.id) else null
        // The wiper's first cue is the manual's own: clean first, replace only if the noise stays.
        if (sound != null && !sound.confirmed && a.status != DueStatus.DUE)
            return Guide("유리와 블레이드를 먼저 닦아 보세요",
                "작동 소리가 새 블레이드 때보다 커졌어요. 유막이나 이물질도 같은 소리를 내요. 닦은 뒤에도 크면 교체를 권해요.", factors)

        val task = a.task ?: plan.action
        val treadCheck = part == Part.TIRE && a.drivenBy?.startsWith("홈 깊이") == true
        val doIt = if (treadCheck) "타이어 홈 깊이를 확인해 주세요" else task(part, task)
        val age = if (part == Part.TIRE) TireAge.of(records, today) else null
        // Short trips wear oil faster than the odometer says; the headline counts the same equivalent distance the rows show.
        val oilSince = if (part == Part.ENGINE_OIL) record.odometerKm?.let { at -> mileage?.km?.let { (it - at).coerceAtLeast(0).toDouble() } } else null
        val oil = oilSince?.let { s.oil.summary(record.id, it) }?.takeIf { it.trips > 0 }
        // Below zero once the equivalent distance has passed the interval; kept signed so an overdue oil still binds.
        val oilLeftRaw = oil?.let { o -> plan.intervalKm?.let { it * (1 - o.effectiveKm.first / OilModel.normalKm()) } }
        val oilLeftKm = oilLeftRaw?.coerceAtLeast(0.0)
        val oilBinds = oilLeftRaw != null && r.remainingKm != null && oilLeftRaw < r.remainingKm
        val remaining = when {
            task == ServiceAction.ROTATE -> remaining(r, rotation, plan)
            task == ServiceAction.REPLACE && age != null -> "${maxOf(0, Math.round((TireModel.AGE_REPLACE_YEARS - age.years) * 12).toInt())}개월"
            oilBinds -> "${n(oilLeftKm!!)} km"
            else -> remaining(r, null, plan)
        }
        val headline = when (a.status) {
            DueStatus.DUE -> "지금 $doIt"
            // When the car's data, not the calendar, made it soon, the calendar's days left would undersell it.
            // The oil model counts in kilometres, so its own remaining distance is the honest headline.
            DueStatus.SOON -> if (treadCheck || a.drivenBy != null && a.task == null && !oilBinds) "곧 $doIt" else remaining?.let { "$it 안에 $doIt" } ?: "곧 $doIt"
            DueStatus.UPCOMING -> remaining?.let { "$it 남았어요" } ?: "지금은 할 일이 없어요"
            DueStatus.NEEDS_INFO -> if (r.missing.any { "주행거리" in it }) "차량 주행거리를 기다리는 중이에요" else "정보가 더 필요해요"
            else -> "지금은 할 일이 없어요"
        }
        val tread = if (treadCheck) TireTread.of(records, mileage, s.tire) else null
        val advice = tread?.let { t -> "주행 기록으로 추정한 홈 깊이가 앞 ${String.format(k, "%.1f", t.front)} · 뒤 ${String.format(k, "%.1f", t.rear)} mm예요. " +
                "같은 운전에도 타이어마다 닳는 속도가 달라 실제와 차이가 날 수 있어요. 정비소에서 깊이를 재 보거나 홈 안의 마모 한계 표시를 봐 주세요. 점검을 기록하면 그만큼 추정 범위를 좁혀요." }
            ?: (if (task == ServiceAction.REPLACE && age != null) ageReason(age, a.status) else null)
            // The headline used the equivalent distance; say where the difference from the odometer comes from.
            ?: (if (oilBinds && oilSince != null && a.drivenBy?.startsWith("연료 희석") != true)
                    if (a.status == DueStatus.DUE && oilLeftRaw!! < 0) "엔진이 덜 데워진 채 달린 시간과 공회전이 많아, 오일로 치면 권장 주기를 ${n(-oilLeftRaw)} km 넘겼어요."
                    else "엔진이 덜 데워진 채 달린 시간과 공회전이 많아, 오일로 치면 실제 ${n(oilSince)} km보다 ${n(plan.intervalKm!! - oilLeftRaw!! - oilSince)} km를 더 달렸어요."
                else null)
            ?: reason(part, plan, record, a, s, rotation, sound, report)
            ?: when (a.status) {
                DueStatus.UPCOMING -> "지금은 할 일이 없어요. ${policy(plan)} 기준이고, 가까워지면 알려 드려요."
                // The odometer comes from the car; there is nothing for the owner to type.
                DueStatus.NEEDS_INFO -> if (r.missing.any { "주행거리" in it }) "차량과 연결되면 주행거리를 자동으로 읽어요. 연결 전에는 날짜 주기만 계산해요."
                    else r.missing.firstOrNull() ?: "기록을 확인해 주세요."
                // The row that moved the status already says how far past it is, additions included; quote that same figure.
                DueStatus.DUE -> (factors.firstOrNull { it.alert && it.value.endsWith(" 지남") }?.value?.removeSuffix(" 지남") ?: overdue(r))
                    ?.let { "권장 주기를 $it 넘겼어요." } ?: "권장 주기에 닿았어요."
                else -> "${policy(plan)} 기준이에요. 최근 ${record.action.label} ${record.date?.toString()?.replace('-', '.') ?: "날짜 미상"}."
            }
        return Guide(headline, advice, factors)
    }

    /** How far past the interval, by whichever limit was passed. */
    private fun overdue(r: DueResult) = listOfNotNull(r.remainingKm?.takeIf { it <= 0 }?.let { "${n(-it)} km" }, r.remainingDays?.takeIf { it <= 0 }?.let { "${-it}일" })
        .firstOrNull()?.takeIf { !it.startsWith("0") }

    /** Months as the driver says them: "6개월", "1년", "1년 3개월". */
    private fun span(m: Int) = when { m < 12 -> "${m}개월"; m % 12 == 0 -> "${m / 12}년"; else -> "${m / 12}년 ${m % 12}개월" }

    private fun policy(p: MaintenancePlan) = listOfNotNull(p.intervalMonths?.let { "${it}개월" }, p.intervalKm?.let { "${n(it)} km" }).joinToString(" 또는 ") + "마다 ${p.action.label}"

    /** The nearer of the two limits, as the driver would say it. */
    private fun remaining(r: DueResult, rotation: Rotation?, plan: MaintenancePlan): String? {
        if (rotation != null) return "${n((TireModel.ROTATION_KM - maxOf(rotation.sinceKm, rotation.equivalentKm)).coerceAtLeast(0.0))} km"
        val days = r.remainingDays; val km = r.remainingKm
        val dayShare = days?.let { d -> plan.intervalMonths?.let { d / (it * 30.4) } }
        val kmShare = km?.let { d -> plan.intervalKm?.let { d.toDouble() / it } }
        return when {
            km != null && (dayShare == null || (kmShare ?: 1.0) < dayShare) -> "${n(km.coerceAtLeast(0))} km"
            days != null -> "${days.coerceAtLeast(0)}일"
            else -> null
        }
    }

    private fun ageReason(age: TireAge, status: DueStatus) = if (status == DueStatus.DUE)
        "타이어를 단 지 ${age.label}이 지났어요. 설명서는 제조일 기준 6년이 지나면 쓰지 않았어도 교체하라고 해요. 네 바퀴를 한꺼번에 같은 규격으로 바꿔 주세요."
        else "타이어를 단 지 ${age.label}이 됐어요. 제조일 기준 6년이면 교체해야 하는데, 제조일은 장착일보다 앞서요. 타이어 옆면 DOT 끝 네 자리로 확인해 주세요."

    /** Why the vehicle data moved the part, in one or two sentences; null when the schedule alone did. */
    private fun reason(part: Part, plan: MaintenancePlan, record: ServiceRecord, a: Assessment, s: SignalSnapshot,
                       rotation: Rotation?, sound: SoundShift?, report: OwnerReport?): String? {
        val by = a.drivenBy
        if (a.task == ServiceAction.ROTATE && rotation != null) return if (rotation.broughtForward)
            "급가속·급회전이 잦아, 타이어로 치면 실제 ${n(rotation.sinceKm)} km보다 ${n(rotation.equivalentKm - rotation.sinceKm)} km를 더 달렸어요. 1만 km마다 바꾸면 네 바퀴가 고르게 닳아요."
            else if (rotation.baseline.action == ServiceAction.REPLACE) "교체 후 ${n(rotation.sinceKm)} km 동안 위치 교환 기록이 없어요. 1만 km마다 바꾸면 네 바퀴가 고르게 닳아요."
            else "위치 교환 후 ${n(rotation.sinceKm)} km를 달렸어요. 1만 km마다 바꾸면 네 바퀴가 고르게 닳아요."
        if (by == null) return null
        if (report == OwnerReport.WEAK_AIRFLOW && by == report.label) return "풍량이 약해졌다고 알려 주셨어요. 필터가 막히면 풍량이 줄고 냉난방도 약해져요. 흡입구 이물이나 송풍기 쪽일 수도 있어요."
        if (report == OwnerReport.ODOR && by == report.label) return "냄새가 난다고 알려 주셨어요. 에어컨 냄새라면 에어컨을 끄고 송풍만 5분 켜서 말리면 줄어요."
        return when {
            by.startsWith("브레이크액") -> "차량이 브레이크액 부족을 알렸어요. 새는 곳이 있거나 패드가 많이 닳았을 수 있어 바로 점검을 받아 주세요."
            by.startsWith("엔진 오일량") -> "차량이 엔진 오일량 부족을 알렸어요. 정차 후 오일량을 확인해 주세요."
            by.startsWith("운행 조건") -> "엔진이 덜 데워진 채 달리거나 공회전이 길면 오일이 거리보다 빨리 지쳐서, 그만큼 더 달린 것으로 계산했어요."
            by.startsWith("연료 희석") -> "식은 엔진으로 짧게 달리는 일이 잦아 오일에 연료가 섞였을 수 있어요. 섞이면 오일이 묽어져요."
            by.startsWith("제동 에너지") -> "브레이크를 보통보다 많이 밟아서, 그만큼 더 달린 것으로 계산했어요."
            by.startsWith("패드 잔량") -> "패드 교체 뒤 제동한 양으로 계산하면 교체 시점에 가까워요. 실제 두께는 점검으로 확인해 주세요."
            by.startsWith("닦은 뒤에도") -> "닦은 뒤에도 작동 소리가 새 블레이드 때보다 커요. 고무가 닳았을 가능성이 높아요."
            by.startsWith("블레이드 노화") -> "더운 날, 햇볕 아래 주차, 평소보다 잦은 작동은 고무를 빨리 굳게 해서, 그만큼 교체 날짜를 앞당겼어요."
            by.startsWith("냉방") -> if (a.status == DueStatus.DUE) "에어컨이 거의 식히지 못해요. 냉매가 크게 줄었을 수 있어요."
                else "같은 날씨에서 최근 ${ClimateTracker.RECENT}번 모두 점검 직후보다 느리게 식혔어요. 냉매, 바람길, 압축기 중 어디인지는 점검에서 확인해요."
            by.startsWith("난방") -> if (a.status == DueStatus.DUE) "히터가 거의 데우지 못해요. 냉각수나 히터 쪽을 확인할 때예요."
                else "같은 날씨에서 최근 ${ClimateTracker.RECENT}번 모두 점검 직후보다 느리게 데웠어요."
            by.startsWith("먼지 적재") -> "송풍량과 그달 미세먼지로 센 필터 먼지가 교체 기준의 ${by.substringAfterLast(' ')}까지 찼어요."
            else -> null
        }
    }

    private fun factors(part: Part, plan: MaintenancePlan, record: ServiceRecord?, a: Assessment, s: SignalSnapshot,
                        rotation: Rotation?, today: LocalDate, report: OwnerReport?, mileage: Mileage?, records: List<ServiceRecord>): List<Factor> {
        val r = a.schedule
        val by = a.drivenBy.orEmpty()
        val last = record?.action?.label ?: plan.action.label
        // With no estimate behind the state, the schedule row that reached its window is the reason and carries the state's colour.
        val scheduled = a.drivenBy == null && (a.status == DueStatus.DUE || a.status == DueStatus.SOON)
        val days = record?.date?.let { from -> plan.intervalMonths?.let { m ->
            val total = ChronoUnit.DAYS.between(from, from.plusMonths(m.toLong())).toFloat()
            val left = r.remainingDays ?: return@let null
            // A monthly tire check counted from a tire change or rotation has no check behind it: how many days past it is
            // says nothing about the owner, so the row says there is no check on record instead.
            val unchecked = part == Part.TIRE && record?.action != ServiceAction.INSPECT && left < 0
            Factor(if (part == Part.TIRE) "월 점검" else "기간", when { unchecked -> "점검 기록 없음"; left < 0 -> "${-left}일 지남"; else -> "${left}일 남음" },
                (1 - left / total).coerceIn(0f, 1f), now = "$last 후 ${n(total - left)}일", limit = "권장 ${m}개월",
                alert = scheduled && left <= ScheduleCalculator.soonDays(m))
        } }
        val km = record?.odometerKm?.let { plan.intervalKm?.let { interval ->
            val left = r.remainingKm ?: return@let null
            Factor("주행거리", if (left < 0) "${n(-left)} km 지남" else "${n(left)} km 남음", (1 - left.toFloat() / interval).coerceIn(0f, 1f),
                now = "$last 후 ${n(interval - left)} km", limit = "권장 ${n(interval)} km", alert = scheduled && left <= ScheduleCalculator.SOON_KM)
        } }
        val id = record?.id
        return when (part) {
            Part.ENGINE_OIL -> {
                val since = record?.odometerKm?.let { at -> mileage?.km?.let { (it - at).coerceAtLeast(0).toDouble() } }
                val sum = s.oil.summary(id, since)
                // One distance row: the odometer fills the bar and what short and cold trips added sits on top, so the
                // row and the headline count the same equivalent distance.
                val interval = plan.intervalKm?.toDouble()
                val distance = km?.let { k0 -> if (sum.trips == 0 || since == null || interval == null) k0 else {
                    val equivalent = sum.effectiveKm.first / OilModel.normalKm() * interval
                    val added = (equivalent - since).coerceAtLeast(0.0)
                    val left = interval - maxOf(since, equivalent)
                    k0.copy(value = if (left < 0) "${n(-left)} km 지남" else "${n(left)} km 남음", fraction = (since / interval).toFloat().coerceIn(0f, 1f),
                        extra = (added / interval).toFloat().coerceIn(0f, 1f), alert = by.startsWith("운행 조건") || k0.alert,
                        now = if (added >= 1) "실제 ${n(since)} km + 운행 조건 ${n(added)} km" else k0.now)
                } }
                // Fuel in the oil, on a bar that ends at the 5 % change point.
                val fuel = sum.dilution?.let { f -> Factor("연료 희석 추정", "${String.format(k, "%.1f", f)}%", (f / OilModel.CHANGE_PCT).toFloat().coerceIn(0f, 1f),
                    alert = f >= OilModel.CAUTION_PCT, now = "식은 엔진 운행으로 섞인 연료", limit = "${n(OilModel.CHANGE_PCT)}%면 교체") }
                listOfNotNull(distance, days, fuel)
            }
            Part.BRAKE_PAD -> {
                val (work, distance, _) = s.brake.since(id)
                val perKm = if (distance >= 1_000) work / distance else null
                val ref = CompositeJudge.brakeReferenceKjPerKm()
                // Braking per km on a bar to twice this car's typical, with the typical as the line in the middle.
                val habit = perKm?.let { Factor("제동 습관", if (it <= ref) "보통보다 ${Math.round((1 - it / ref) * 100)}% 덜 밟아요" else "보통보다 ${Math.round((it / ref - 1) * 100)}% 더 밟아요",
                    (it / ref / 2).toFloat().coerceIn(0f, 1f), alert = by.startsWith("제동 에너지"), marker = 0.5f,
                    now = "${n(distance / 1000)} km 동안 제동량", limit = "가운데 선이 이 차 보통") }
                // Pad left since the last replacement, on a bar from new to the 3 mm replacement line its label names.
                val p = BrakeWearModel.params()
                val pad = CompositeJudge.padLeft(records, s)?.let { left -> val usable = p.padNewMm - BrakeWearModel.REPLACE_MM
                    Factor("앞 패드 잔량 추정", "${String.format(k, "%.1f", left.coerceAtLeast(0.0))} mm", ((p.padNewMm - left) / usable).toFloat().coerceIn(0f, 1f),
                        alert = by.startsWith("패드 잔량"),
                        now = CompositeJudge.padMeasured(records, s)?.measuredMm?.let { m -> "점검 때 잰 ${String.format(k, "%.1f", m)} mm부터" } ?: "새 패드 ${n(p.padNewMm)} mm",
                        limit = "${n(BrakeWearModel.REPLACE_MM)} mm면 교체") }
                listOfNotNull(pad, km, habit)
            }
            Part.TIRE -> {
                // The plain distance fills the bar and rough driving's share sits on top, so the equivalent figure explains itself.
                // Past the interval from a rotation it says how far past; from a tire change it says no rotation is on record,
                // since the app cannot tell a skipped rotation from one that was never entered.
                val turn = rotation?.let { val added = (it.equivalentKm - it.sinceKm).coerceAtLeast(0.0)
                    val left = TireModel.ROTATION_KM - maxOf(it.sinceKm, it.equivalentKm)
                    Factor("위치 교환", when { left >= 0 -> "${n(left)} km 남음"; it.baseline.action == ServiceAction.REPLACE -> "기록 없음"; else -> "${n(-left)} km 지남" },
                        (it.sinceKm / TireModel.ROTATION_KM).toFloat().coerceIn(0f, 1f), alert = a.task == ServiceAction.ROTATE,
                        extra = (added / TireModel.ROTATION_KM).toFloat().coerceIn(0f, 1f),
                        now = if (added >= 1) "실제 ${n(it.sinceKm)} km + 운전 습관 ${n(added)} km" else "실제 ${n(it.sinceKm)} km",
                        limit = "권장 ${n(TireModel.ROTATION_KM)} km") }
                    ?: Factor("위치 교환", "기록 없음", note = "위치를 바꾸면 기록해 주세요")
                val age = TireAge.of(records, today)
                // Replacement comes from whichever is nearer: six years on the car or the tread estimate.
                val tread = TireTread.of(records, mileage, s.tire)
                val tp = TireParams.of(CarModel.current)
                val replace = age?.let { val months = Math.round((TireModel.AGE_REPLACE_YEARS - it.years) * 12).toInt()
                    val treadShare = tread?.let { t -> (tp.newMm - t.lowest) / (tp.newMm - TireModel.MOTORWAY_TREAD_MM) } ?: 0.0
                    if (tread != null && treadShare > it.share)
                        Factor("홈 깊이 추정", "앞 ${String.format(k, "%.1f", tread.front)} · 뒤 ${String.format(k, "%.1f", tread.rear)} mm", treadShare.toFloat().coerceIn(0f, 1f),
                            alert = a.drivenBy?.startsWith("홈 깊이") == true, now = tread.measured?.measuredMm?.let { m -> "점검 때 잰 ${String.format(k, "%.1f", m)} mm부터 · 장착 후 ${it.label}" }
                                ?: "새 타이어 ${String.format(k, "%.1f", tp.newMm)} mm · 장착 후 ${it.label}", limit = "${String.format(k, "%.1f", TireModel.MOTORWAY_TREAD_MM)} mm면 확인")
                    else Factor("교체", when { months < 0 -> "${span(-months)} 지남"; months == 0 -> "교체 시기"; months < 12 -> "${months}개월 남음"; months % 12 == 0 -> "${months / 12}년 남음"; else -> "${months / 12}년 ${months % 12}개월 남음" },
                        it.share.toFloat().coerceIn(0f, 1f), alert = a.task == ServiceAction.REPLACE,
                        now = "장착 후 ${it.label}${tread?.let { t -> " · 홈 깊이 앞 ${String.format(k, "%.1f", t.front)} mm 추정" } ?: ""}", limit = "권장 ${n(TireModel.AGE_REPLACE_YEARS)}년") }
                    ?: Factor("교체", "기록 없음", note = "타이어를 바꾸면 기록해 주세요")
                listOfNotNull(days?.copy(title = "공기압·마모 점검"), turn, replace)
            }
            Part.WIPER -> {
                val (low, high, _) = s.wiper.since(id)
                val onCar = record?.date?.let { ChronoUnit.DAYS.between(it, today) } ?: 0
                val idle = s.wiper.lastWipedAt?.let { ChronoUnit.DAYS.between(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate(), today) }
                    ?.takeIf { it >= WiperModel.IDLE_DAYS }
                // One count (the lower bound, the one that ages the blade) against what a typical driver wipes in the same days,
                // on a bar to twice that with the typical as the line in the middle. Less wiping never takes days off: the
                // estimate only brings a change forward.
                val heavyDays = record?.let { CompositeJudge.bladeWipeDays(it, today, s.wiper) } ?: 0.0
                val typical = WiperModel.TYPICAL_PER_YEAR * onCar / 365
                val wipes = when {
                    low <= 0 -> null
                    onCar >= 30 -> {
                        val diff = Math.round((low / typical - 1) * 100)
                        Factor("닦은 횟수", if (heavyDays >= 0.5) "노화 +${n(heavyDays)}일" else "날짜 그대로",
                            (low / typical / 2).toFloat().coerceIn(0f, 1f), alert = heavyDays >= 0.5 && by.startsWith("블레이드 노화"), marker = 0.5f,
                            now = idle?.let { "${it}일째 안 씀 · 주 2회쯤 작동해 주세요" }
                                ?: "${n(Math.round(low / 100) * 100.0)}회 · 보통보다 ${if (diff >= 0) "$diff% 많음" else "${-diff}% 적음"}",
                            limit = "보통 ${n(Math.round(typical / 100) * 100.0)}회")
                    }
                    else -> Factor("닦은 횟수", "${n(low)}회", (onCar / 30.0).toFloat().coerceIn(0f, 1f),
                        now = "${onCar}일째 기록 중", limit = "30일 뒤 보통과 비교")
                }
                val shift = s.sound.shift(id)
                val cleaning = s.sound.sinceCleaning(id)
                val (change, sessions) = s.sound.change(id)
                // Decibels against the blade's own first sessions are the value; the bar is how far the session rule has got, since
                // the decision is the pattern (all of the latest six louder), not a fixed dB line.
                val louder = s.sound.louder(id)
                val signed = { v: Double -> "${if (v >= 0.05) "+" else if (v <= -0.05) "−" else ""}${db(kotlin.math.abs(v))} dB" }
                val sound = when {
                    shift?.confirmed == true -> Factor("작동 소리", "+${db(shift.db)} dB", 1f, alert = true,
                        now = "닦은 뒤 ${WiperSound.CONFIRM}회도 모두 큼", limit = "교체 권장")
                    cleaning != null -> Factor("작동 소리", "닦은 뒤 듣는 중", cleaning.toFloat() / WiperSound.CONFIRM,
                        now = "닦은 뒤 ${cleaning}회 들음", limit = "${WiperSound.CONFIRM}회 모두 크면 교체")
                    shift != null -> Factor("작동 소리", "+${db(shift.db)} dB", 1f, alert = true,
                        now = "최근 ${WiperSound.RECENT}회 모두 새 블레이드보다 큼", limit = "먼저 닦아 보세요")
                    change != null -> Factor("작동 소리", signed(change), ((louder ?: 0).toFloat() / WiperSound.RECENT),
                        now = "최근 ${WiperSound.RECENT}회 중 ${louder ?: 0}회 더 큼", limit = "${WiperSound.RECENT}회 모두 크면 점검")
                    sessions > 0 -> Factor("작동 소리", "새 블레이드 소리 익히는 중", sessions.toFloat() / WiperSound.BASELINE,
                        now = "${minOf(sessions, WiperSound.BASELINE)}회 들음", limit = "${WiperSound.BASELINE}회 들으면 비교 시작")
                    else -> null
                }
                val aged = record?.date?.let { RubberAging.equivalentDays(it, today, s.outside, RubberAging.EA_LOW) }
                val real = record?.date?.let { ChronoUnit.DAYS.between(it, today) }
                val heavy = record?.let { CompositeJudge.bladeWipeDays(it, today, s.wiper) } ?: 0.0
                // Hot and sunny days and heavy wiping count extra; the plain days fill the bar and the additions sit on top.
                val heat = if (aged != null && real != null && aged > real + 0.5) aged - real else 0.0
                val total = record?.date?.let { from -> plan.intervalMonths?.let { ChronoUnit.DAYS.between(from, from.plusMonths(it.toLong())).toDouble() } }
                val added = heat + heavy
                val wiperDays = days?.let { d -> if (added < 0.5 || real == null || total == null) d.copy(alert = by.startsWith("블레이드 노화") || d.alert)
                    else d.copy(value = (total - real - added).let { if (it < 0) "${n(-it)}일 지남" else "${n(it)}일 남음" },
                        fraction = (real / total).toFloat().coerceIn(0f, 1f), extra = (added / total).toFloat().coerceIn(0f, 1f),
                        now = listOfNotNull("실제 ${n(real)}일", if (heat >= 0.5) "더위·햇볕 ${n(heat)}일" else null, if (heavy >= 0.5) "많이 닦음 ${n(heavy)}일" else null).joinToString(" + "),
                        alert = by.startsWith("블레이드 노화") || d.alert) }
                listOfNotNull(wiperDays, sound, wipes)
            }
            Part.HVAC -> {
                val recovery = { t: ClimateTracker, title: String, verb: String, what: String ->
                    val (ratio, sessions) = t.recovery(id)
                    when {
                        // The bar runs to the point where the system barely works: under a quarter of its post-service speed.
                        ratio != null -> Factor(title, if (ratio >= .995) "점검 직후와 같아요" else "${Math.round((1 - ratio) * 100)}% 느려졌어요",
                            ((1 - ratio) / (1 - ClimateTracker.LOST_SHARE)).toFloat().coerceIn(0f, 1f), alert = by.startsWith(what),
                            now = "점검 직후와 비교", limit = "${Math.round((1 - ClimateTracker.LOST_SHARE) * 100)}% 느려지면 바로 점검")
                        sessions > ClimateTracker.BASELINE -> Factor(title, "비슷한 날씨를 기다리는 중")
                        else -> Factor(title, "점검 직후 속도 익히는 중", sessions.toFloat() / ClimateTracker.BASELINE,
                            now = "${minOf(sessions, ClimateTracker.BASELINE)}회 측정", limit = "${ClimateTracker.BASELINE}회")
                    }
                }
                listOfNotNull(days, recovery(s.cooldown, "냉방", "식히는", "냉방"), recovery(s.heatup, "난방", "데우는", "난방"))
            }
            Part.CABIN_FILTER -> {
                // Dust in the filter, counted from blower air and the month's PM2.5 (FilterModel); the row says the real hours behind it.
                val (seconds, _) = s.fresh.since(id)
                val air = s.filter.since(id)?.takeIf { it > 0 }?.let { load -> val share = load / FilterModel.END_OF_SERVICE_HOURS
                    Factor("먼지 적재 추정", if (share >= 1) "교체 기준 넘음" else "${Math.round(share * 100)}% 찼어요",
                        share.toFloat().coerceIn(0f, 1f), alert = by.startsWith("먼지 적재"),
                        now = "외기 송풍 ${n(seconds / 3600.0)}시간", limit = "가득 차면 교체") }
                val told = report?.let { Factor("알려 주신 증상", if (it == OwnerReport.WEAK_AIRFLOW) "풍량이 약해요" else "냄새가 나요", alert = true) }
                listOfNotNull(told, days, air, km).take(3)
            }
        }
    }
}
