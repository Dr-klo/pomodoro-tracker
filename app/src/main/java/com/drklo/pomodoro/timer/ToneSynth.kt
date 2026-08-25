package com.drklo.pomodoro.timer

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Generates short bell-like "ding" tones as WAV files at runtime, so no audio assets need to be
 * bundled. Produces a crisp, clearly audible sound (decaying sine + harmonic).
 */
object ToneSynth {

    private const val SAMPLE_RATE = 44_100

    /** Writes a decaying bell tone to [file]. */
    fun writeBell(
        file: File,
        freqs: DoubleArray,
        weights: DoubleArray,
        durationSec: Double,
        decay: Double
    ) {
        val n = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(n)
        val weightSum = weights.sum()
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-decay * t)
            val attack = min(1.0, t / 0.004) // 4ms ramp avoids a click at onset
            var s = 0.0
            for (k in freqs.indices) s += weights[k] * sin(2 * PI * freqs[k] * t)
            val v = (s / weightSum) * envelope * attack * 0.95
            samples[i] = (v * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        writeWav(file, samples)
    }

    private fun writeWav(file: File, samples: ShortArray) {
        val dataSize = samples.size * 2
        val byteRate = SAMPLE_RATE * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // PCM header size
        buffer.putShort(1) // PCM format
        buffer.putShort(1) // mono
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(byteRate)
        buffer.putShort(2) // block align
        buffer.putShort(16) // bits per sample
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        for (s in samples) buffer.putShort(s)
        file.writeBytes(buffer.array())
    }
}
