package dev.vehiclecare.app

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.time.LocalDate
import java.util.Locale

/**
 * One spoken sentence as the car sets off, when a part is due, so the driver hears it without looking at the screen. It runs
 * on the car's on-device engine, needs no network or account, and speaks each set of due parts once a day.
 */
object VoiceBrief {
    private const val PREFS = "vehicle-care-voice"
    private const val TAG = "VoiceBrief"
    private var engine: TextToSpeech? = null

    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true)
    fun setEnabled(context: Context, on: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply() }

    /** Spoken text for the due jobs ("브레이크 패드 점검"), or null with nothing due. */
    fun sentence(due: List<String>): String? = when (due.size) {
        0 -> null
        1 -> "${due[0]} 시기가 됐어요. 세운 뒤 미리정비에서 확인해 주세요."
        else -> "${due[0]} 외 ${due.size - 1}건의 정비 시기가 됐어요. 세운 뒤 미리정비에서 확인해 주세요."
    }

    /** Called as the car starts or leaves Park after a stop; both can fire for one departure, and the day's key keeps it to once. */
    fun onDeparture(context: Context) {
        if (!enabled(context) || engine != null) return
        val app = context.applicationContext
        val due = runCatching { Reminders.due(app) }.getOrDefault(emptyList())
        val line = sentence(due) ?: return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "${LocalDate.now()}:${due.joinToString()}"
        if (prefs.getString("spoken", null) == key) return
        runCatching { speak(app, line) { prefs.edit().putString("spoken", key).apply() } }
            .onFailure { Log.w(TAG, "speak failed", it); engine = null }
    }

    private fun speak(context: Context, line: String, spoken: () -> Unit) {
        val tts = TextToSpeech(context, Mode.ON_DEVICE)
        engine = tts
        val listener = object : EventListener {
            private fun finish() { runCatching { tts.removeEventListener(this); tts.release() }; engine = null }
            override fun onReady() = Unit
            override fun onStart() { spoken() }
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onDone() = finish()
            override fun onStop() = finish()
            override fun onError(errorType: ErrorType) { Log.w(TAG, "error $errorType"); finish() }
        }
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.addEventListener(listener)
                tts.setLocale(Locale.KOREA)
                tts.speak(line)
            }
            override fun onFailure(errorType: ErrorType) { Log.w(TAG, "init $errorType"); runCatching { tts.release() }; engine = null }
        })
    }
}
