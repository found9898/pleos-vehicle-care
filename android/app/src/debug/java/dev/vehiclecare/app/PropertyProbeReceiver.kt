package dev.vehiclecare.app

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Debug builds only: tries every AAOS standard vehicle property with this app's permissions and logs what came back,
 * so the list of signals the app cannot read is measured, not assumed. Run with
 * `adb shell am broadcast --user 10 -n dev.vehiclecare.app/.PropertyProbeReceiver`, read with `adb logcat -s PropProbe`.
 */
class PropertyProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val car = Car.createCar(context.applicationContext)
        val m = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        val supported = runCatching { m.propertyList.map { it.propertyId }.toSet() }.getOrDefault(emptySet())
        VehiclePropertyIds::class.java.fields.filter { it.type == Int::class.javaPrimitiveType }.forEach { f ->
            val id = f.getInt(null)
            val result = runCatching {
                val config = m.getCarPropertyConfig(id)
                val area = config?.areaIds?.firstOrNull() ?: 0
                val v = m.getProperty<Any>(id, area)?.value
                "OK " + (if (v is Array<*>) v.joinToString(",") else v.toString()).take(60)
            }.getOrElse { e -> "${e.javaClass.simpleName}: ${e.message?.take(160)}" }
            Log.i("PropProbe", "${f.name}\t${if (id in supported) "listed" else "unlisted"}\t$result")
        }
        car.disconnect()
        Log.i("PropProbe", "DONE")
    }
}
