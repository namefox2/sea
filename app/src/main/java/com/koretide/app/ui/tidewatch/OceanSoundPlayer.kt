package com.koretide.app.ui.tidewatch

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedurally-synthesised ocean / wave ambience — no audio asset required.
 *
 * The sound is built from filtered noise (a low "rush" + high "foam hiss")
 * shaped by slow swell envelopes so it ebbs and flows like real surf. Wind
 * intensity (0..1, from Beaufort/12) raises the volume, the foam content and
 * the swell rate, so a calm sea sounds soft and a gale sounds rough.
 *
 * Runs on its own thread feeding a streaming [AudioTrack]; start/stop are safe
 * to call repeatedly from the UI thread.
 */
class OceanSoundPlayer {

    private companion object {
        const val SAMPLE_RATE = 22050
        const val CHUNK = 1024
    }

    @Volatile private var intensity = 0.25f      // 0..1 from wind
    @Volatile private var running = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    /** Wind 0..1 → louder, harsher, faster swells. */
    fun setIntensity(value: Float) { intensity = value.coerceIn(0f, 1f) }

    fun start() {
        if (running) return
        running = true
        thread = Thread({ runLoop() }, "OceanSound").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.let { try { it.join(300) } catch (_: InterruptedException) {} }
        thread = null
        track?.let {
            try { it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        track = null
    }

    private fun runLoop() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CHUNK * 4)

        val at = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = at
        try { at.play() } catch (_: IllegalStateException) { return }

        val rng = Random(System.nanoTime())
        val buf = ShortArray(CHUNK)

        // Filter / oscillator state
        var brown = 0f          // low rumble (leaky-integrated noise)
        var lpBody = 0f         // mid-band low-pass
        var lpFoam = 0f         // tracks low freq → high-pass = white - lpFoam
        var lfo1 = 0.0          // slow swell
        var lfo2 = 0.0          // second swell (irregularity)
        var gain = 0f           // smooth fade-in to avoid a click on start
        val dt = 1.0 / SAMPLE_RATE

        while (running) {
            val inten = intensity
            // Swell rate rises a little with wind (waves arrive more often).
            val f1 = 0.11 + inten * 0.06          // ~9 s → ~6 s period
            val f2 = 0.17 + inten * 0.05
            val foamAmt = 0.12f + inten * 0.55f   // high-freq hiss content
            val master = 0.55f * (0.40f + 0.60f * inten)

            for (i in 0 until CHUNK) {
                val white = rng.nextFloat() * 2f - 1f

                brown += 0.020f * white
                brown *= 0.992f                    // leaky → stays low-frequency
                lpBody += 0.06f * (white - lpBody)
                lpFoam += 0.40f * (white - lpFoam)
                val hiss = white - lpFoam          // high-pass → foam/spray

                lfo1 += 2.0 * PI * f1 * dt
                lfo2 += 2.0 * PI * f2 * dt
                // 0..1 swell envelope; squared for a softer rise / sharper wash
                var env = 0.5 + 0.5 * (0.62 * sin(lfo1) + 0.38 * sin(lfo2))
                env = env.coerceIn(0.0, 1.0)
                val envf = (env * env).toFloat()

                var s = brown * 6.0f * 0.55f +
                        lpBody * 0.9f +
                        hiss * foamAmt * (0.25f + 0.75f * envf)
                s *= (0.45f + 0.55f * envf)        // whole bed breathes with the swell

                if (gain < 1f) gain = (gain + 0.0008f).coerceAtMost(1f)
                var out = s * master * gain
                if (out > 1f) out = 1f else if (out < -1f) out = -1f
                buf[i] = (out * 32767f).toInt().toShort()
            }
            try {
                at.write(buf, 0, CHUNK)
            } catch (_: Exception) {
                break
            }
        }
    }
}
