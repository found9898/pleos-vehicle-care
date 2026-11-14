package dev.vehiclecare.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Listens only while the front wiper runs and the app is visible. Samples are reduced to band levels on a worker thread
 * and discarded frame by frame; nothing audible is kept or leaves this process.
 */
class WiperMic(private val onSession: (SoundSession) -> Unit, private val onState: (String) -> Unit) {
    // One flag per session, so a quick off-on cannot leave the previous thread reading.
    private var active: java.util.concurrent.atomic.AtomicBoolean? = null

    fun granted(context: Context) = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (active != null) return
        if (!granted(context)) { onState("마이크 권한 필요"); return }
        val rate = listOf(44_100, 48_000).firstOrNull { AudioRecord.getMinBufferSize(it, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0 }
            ?: run { onState("마이크 미지원"); return }
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // VOICE_RECOGNITION skips the noise suppression and AGC that would flatten the wiper's own level.
        val record = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, WiperAcoustics.FRAME * 4))
        }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED } ?: run { onState("마이크 사용 불가"); return }
        val running = java.util.concurrent.atomic.AtomicBoolean(true).also { active = it }
        val startedAt = System.currentTimeMillis()
        Thread({
            val frames = ArrayList<DoubleArray>()
            val buf = ShortArray(WiperAcoustics.FRAME)
            runCatching {
                record.startRecording()
                while (running.get()) {
                    var filled = 0
                    while (running.get() && filled < buf.size) { val n = record.read(buf, filled, buf.size - filled); if (n <= 0) { running.set(false); break }; filled += n }
                    if (filled == buf.size) frames += WiperAcoustics.bandDb(buf, rate)
                }
            }
            runCatching { record.stop() }; record.release()
            val excess = WiperAcoustics.excess(frames)
            if (excess != null) onSession(SoundSession(startedAt, (frames.size.toLong() * WiperAcoustics.FRAME / rate).toInt(), excess))
            else onState("와이퍼 작동 대기")
        }, "wiper-mic").apply { priority = Thread.MIN_PRIORITY; start() }
        onState("소리 분석 중")
    }

    fun stop() { active?.set(false); active = null }
}
