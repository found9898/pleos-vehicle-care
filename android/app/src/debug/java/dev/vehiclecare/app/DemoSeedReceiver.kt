package dev.vehiclecare.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

/**
 * Debug builds only. Loads the demo story ([DemoData]) so every detail screen has something to show. Send it with the
 * app stopped, so no running process writes over it:
 *   adb shell am force-stop --user 10 dev.vehiclecare.app
 *   adb shell am broadcast --user 10 -n dev.vehiclecare.app/.DemoSeedReceiver [--el odometer <km>]
 */
class DemoSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DemoData.apply(context, DemoData.build(LocalDate.now(), intent.getLongExtra("odometer", 0L)))
    }
}
