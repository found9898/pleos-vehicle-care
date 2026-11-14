package dev.vehiclecare.app

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import java.time.LocalDate
import java.util.Calendar

object Reminders {
    const val CHANNEL = "maintenance-schedules"
    const val ACTION_CHECK = "dev.vehiclecare.action.CHECK_SCHEDULE"
    private const val PREFS = "vehicle-care-reminders"
    private const val BODY_COLOR = 0xFFC8CCD2.toInt()
    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(context, 40,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_CHECK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "소모품 교체·점검 주기", NotificationManager.IMPORTANCE_LOW).apply {
            description = "정비 이력의 기간·거리 기준과 차량 데이터 추정을 함께 본 알림"
        })
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
        }
        context.getSystemService(AlarmManager::class.java).setInexactRepeating(
            AlarmManager.RTC_WAKEUP, next.timeInMillis, AlarmManager.INTERVAL_DAY, alarmIntent(context))
    }

    /** Pleos draws an app's notification body almost black on its dark panel, in day and night mode alike; an explicit span survives. */
    fun readable(text: String): CharSequence = SpannableString(text).apply {
        setSpan(ForegroundColorSpan(BODY_COLOR), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** The jobs due now, in the words a reminder uses ("브레이크 패드 점검"); paused parts stay out, as they do from alerts. */
    fun due(context: Context): List<String> {
        val store = CareStore(context)
        if (store.loadFailed) return emptyList()
        val signals = SignalStore(context).snapshot()
        return store.spec.parts.mapNotNull { part ->
            val plan = store.plans[part]?.takeIf { !it.paused } ?: return@mapNotNull null
            val judged = CompositeJudge.assess(plan, store.activeRecord(part), store.mileage, LocalDate.now(), signals, store.records, store.report(part))
            if (judged.status == DueStatus.DUE) "${part.title} ${(judged.task ?: plan.action).label}" else null
        }
    }

    fun evaluate(context: Context) {
        val store = CareStore(context)
        if (store.loadFailed) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val permitted = (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        val signals = SignalStore(context).snapshot()
        store.spec.parts.forEach { part ->
            val plan = store.plans[part]
            val judged = CompositeJudge.assess(plan, store.activeRecord(part), store.mileage, today, signals, store.records, store.report(part))
            val result = judged.schedule.copy(status = judged.status)
            val id = 500 + part.ordinal
            if (result.status !in listOf(DueStatus.DUE, DueStatus.SOON) || plan == null || plan.paused) {
                manager.cancel(id); prefs.edit().remove(part.name).apply(); return@forEach
            }
            if (!permitted) return@forEach
            // The source is part of the fingerprint, so an estimate reaching the same state as the schedule does not alert twice,
            // but a schedule catching up with an earlier estimate-driven alert does.
            val fingerprint = "${plan.recordId}:${plan.intervalMonths}:${plan.intervalKm}:${plan.action}:${result.dueDate}:${result.dueKm}:${result.status}:${judged.drivenBy != null}:${judged.task}"
            if (prefs.getString(part.name, null) == fingerprint) return@forEach
            val open = PendingIntent.getActivity(context, id,
                Intent(context, MainActivity::class.java).putExtra("part", part.name).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val body = when {
                judged.task == ServiceAction.ROTATE && result.status == DueStatus.DUE -> "타이어 위치 교환 시점이에요(${judged.drivenBy}). 취급설명서는 편마모를 막으려고 1만 km마다 위치 교환을 권해요."
                judged.task == ServiceAction.ROTATE -> "타이어 위치 교환 시점이 가까워졌어요(${judged.drivenBy}). 취급설명서는 편마모를 막으려고 1만 km마다 위치 교환을 권해요."
                part == Part.TIRE && judged.task == ServiceAction.REPLACE && result.status == DueStatus.DUE -> "타이어를 단 지 ${judged.drivenBy?.removePrefix("장착 후 ")}가 지났어요. 취급설명서는 제조일 기준 6년이 지난 타이어는 쓰지 않았어도 교체하라고 해요."
                part == Part.TIRE && judged.task == ServiceAction.REPLACE -> "타이어를 단 지 ${judged.drivenBy?.removePrefix("장착 후 ")}가 됐어요. 제조일 기준 6년이면 교체해야 하니, 타이어 옆면 DOT 끝 네 자리로 제조일을 확인해 주세요."
                judged.drivenBy?.startsWith("닦은 뒤에도") == true -> "유리와 블레이드를 닦은 뒤에도 작동 소리가 교체 직후보다 커요(${judged.drivenBy!!.removePrefix("닦은 뒤에도 ")}). 현대 취급설명서는 이 경우 점검을 권해요."
                judged.drivenBy?.startsWith("작동 소리") == true -> "와이퍼 작동 소리가 교체 직후보다 커졌어요(${judged.drivenBy}). 유막이나 이물질도 같은 소리를 내니, 유리와 블레이드를 먼저 닦고 앱에서 알려 주세요."
                judged.drivenBy == OwnerReport.WEAK_AIRFLOW.label -> "풍량이 약해졌다고 알려 주셨어요. 취급설명서는 필터가 막히면 풍량이 약해지고 냉난방 성능이 떨어진다고 안내해요."
                judged.drivenBy == OwnerReport.ODOR.label -> "냄새가 난다고 알려 주셨어요. 필터를 오래 쓰면 악취가 날 수 있고, 에어컨 냄새는 에어컨을 끈 뒤 송풍 5분으로 말리면 줄어든다고 설명서는 안내해요."
                judged.drivenBy != null && result.status == DueStatus.DUE -> "차량 데이터(${judged.drivenBy}) 기준으로 기간·거리 주기보다 먼저 도달했어요. 정차 후 확인해 주세요."
                judged.drivenBy != null -> "차량 데이터(${judged.drivenBy}) 기준으로 기간·거리 주기보다 먼저 가까워졌어요. 연구 자료 기반 추정이며 실제 마모 판정은 아니에요."
                result.status == DueStatus.DUE -> "기록한 기간 또는 거리 기준에 도달했어요. 정차 후 확인해 주세요."
                else -> "추천 주기가 가까워졌어요. 입력한 기록 기준이며 실제 마모 판정은 아니에요."
            }
            // Android folds an app's loose notifications into one at four, the always-on one plus three reminders, and the
            // car's panel then shows only "3 more". A group of its own keeps each reminder on the panel.
            val note = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setGroup("reminder-${part.name}")
                .setContentTitle("${part.title} ${(judged.task ?: plan.action).label} · ${result.status.label}").setContentText(readable(body))
                .setStyle(Notification.BigTextStyle().bigText(readable(body))).setContentIntent(open).setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER).setOnlyAlertOnce(true).build()
            try {
                manager.notify(id, note)
                prefs.edit().putString(part.name, fingerprint).apply()
            } catch (_: SecurityException) { /* Permission can change while evaluating. */ }
        }
    }

    /** A new set of records is a new story: what was already said about the old one must not silence it. */
    fun forget(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        Part.entries.forEach { manager.cancel(500 + it.ordinal) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun clear(context: Context) {
        forget(context)
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Reminders.ACTION_CHECK, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        // Boot and app updates are the moments Android lets a hidden app start collecting again.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) SignalService.start(context, false)
        Reminders.schedule(context)
        Reminders.evaluate(context)
    }
}
