package dev.vehiclecare.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.time.LocalDate

/**
 * Keeps the signal feed running while the app is out of sight. A hidden app's process is frozen within seconds, so
 * without this only the minutes someone spends looking at the screen would ever be counted. Read-only: it controls
 * nothing in the car. The microphone joins only when the service was started from the open screen, which is the only
 * time Android lets a background service hold it; after a reboot it waits until the app is opened once.
 */
class SignalService : Service() {
    private var mileage: VehicleMileageFeed? = null
    private var lastKm: Long? = null
    private var refused = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Android refuses foreground from the background, e.g. when it restarts this sticky service after an ANR kill.
        if (!promote(false)) { refused = true; stopSelf(); return }
        VehicleSignals.hold(this, VehicleSignals.SERVICE)
        mileage = VehicleMileageFeed(this) { km, _ -> km?.let(::onOdometer) }.also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (refused || !enabled(this) || !promote(intent?.getBooleanExtra(FROM_SCREEN, false) == true)) {
            stopSelf(); return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mileage?.stop(); mileage = null
        if (!refused) {
            VehicleSignals.backgroundMic = false
            VehicleSignals.release(VehicleSignals.SERVICE)
        }
        super.onDestroy()
    }

    /** The odometer reaches reminders through the care store; the open screen writes it itself and is left to do so. */
    private fun onOdometer(km: Long) {
        if (VehicleSignals.screenShown || km == lastKm) return
        val store = CareStore(this)
        if (store.loadFailed) return
        val old = store.mileage
        if (old?.source == "vehicle" && km < old.km) return
        lastKm = km
        store.mileage = Mileage(km, LocalDate.now(), "vehicle", System.currentTimeMillis())
        runCatching { store.save() }.onSuccess { Reminders.evaluate(this) }
    }

    /** False when Android would not let the service into the foreground; it must then stop rather than crash. */
    private fun promote(fromScreen: Boolean): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "차량 데이터 수집", NotificationManager.IMPORTANCE_MIN).apply {
            description = "앱을 닫아도 소모품 추정에 쓰는 차량 신호를 기록하고 있다는 표시"
            setShowBadge(false)
        })
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification).setGroup(CHANNEL)
            .setContentTitle("차량 데이터 기록 중").setContentText(Reminders.readable("운행·제동·공조·와이퍼 신호를 소모품 추정에 쓰고 있어요"))
            .setContentIntent(open).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).build()
        if (Build.VERSION.SDK_INT < 34) return runCatching { startForeground(NOTE_ID, note) }.isSuccess
        val mic = fromScreen && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val base = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        // Asking for the microphone from the background throws; keep collecting without it.
        val withMic = mic && runCatching { startForeground(NOTE_ID, note, base or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) }.isSuccess
        if (withMic) { VehicleSignals.backgroundMic = true; return true }
        if (VehicleSignals.backgroundMic) return true
        return runCatching { startForeground(NOTE_ID, note, base) }.isSuccess
    }

    companion object {
        const val CHANNEL = "vehicle-collection"
        private const val NOTE_ID = 7
        private const val FROM_SCREEN = "from-screen"
        private const val PREFS = "vehicle-collection"

        fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("background", true)

        fun setEnabled(context: Context, on: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("background", on).apply()
            if (on) start(context, true) else context.stopService(Intent(context, SignalService::class.java))
        }

        /** [fromScreen] is true only from the visible activity, where Android allows the microphone to come along. */
        fun start(context: Context, fromScreen: Boolean) {
            if (!enabled(context)) return
            runCatching { context.startForegroundService(Intent(context, SignalService::class.java).putExtra(FROM_SCREEN, fromScreen)) }
        }
    }
}
