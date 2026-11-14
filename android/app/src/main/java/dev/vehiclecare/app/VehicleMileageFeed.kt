package dev.vehiclecare.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.api.model.capability.AccessType

/** Read-only PERF_ODOMETER (kilometers). Foreground polling also handles service reconnects. */
class VehicleMileageFeed(context: Context, private val update: (Long?, String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val vehicle = VehicleSignals.vehicle(context)
    private var active = false
    private var request = 0
    private val poll = Runnable { read() }
    fun start() {
        if (active) return
        active = true
        handler.postDelayed(poll, 1500)
    }
    fun stop() {
        // The shared connection stays open for the process-scoped wiper feed.
        active = false; request++; handler.removeCallbacksAndMessages(null)
    }
    private fun read() {
        if (!active) return
        val ticket = ++request
        var finished = false
        fun finish(value: Float?, error: String?) {
            handler.post {
                if (active && ticket == request && !finished) {
                    finished = true
                    val km = value?.takeIf { it.isFinite() && it >= 0 && it <= ScheduleCalculator.MAX_ODOMETER.toFloat() }?.toLong()
                    update(km, if (km != null) "차량에서 수신 중" else error ?: "차량 데이터 대기")
                    handler.postDelayed(poll, if (km == null) 5000 else 30000)
                }
            }
        }
        handler.postDelayed({ if (!finished) finish(null, "차량 연결 대기") }, 10000)
        try {
            val odometer = vehicle.getOdometer()
            odometer.checkCurrentDistanceDrivenCapabilities({ capability ->
                handler.post {
                    if (active && ticket == request && !finished) {
                        if (capability.accessType == AccessType.READ || capability.accessType == AccessType.READ_WRITE) {
                            runCatching { odometer.getCurrentDistanceDriven({ finish(it, null) }, { finish(null, "차량 연결 대기") }) }
                                .onFailure { finish(null, "차량 연결 대기") }
                        } else finish(null, "주행거리 연동 미지원")
                    }
                }
            }, { finish(null, "차량 연결 대기") })
        } catch (_: Exception) { finish(null, "차량 연결 대기") }
    }
}
