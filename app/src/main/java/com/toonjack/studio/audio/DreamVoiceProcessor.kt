package com.toonjack.studio.audio

import kotlin.math.abs
import kotlin.math.max

/**
 * Offline vocal-tone chain for normalized mono Float PCM.
 *
 * This is a tonal polish effect, not a formant/identity changer. It preserves
 * the input length and is intended to be applied to a duplicate of a take.
 */
class DreamVoiceProcessor(
    sampleRate: Float,
    warmth: Float = 0.28f,
    softness: Float = 0.42f,
    air: Float = 0.32f,
    compression: Float = 0.34f,
    deEss: Float = 0.35f,
    ambience: Float = 0.16f
) {
    data class Settings(
        val warmth: Float = 0.28f,
        val softness: Float = 0.42f,
        val air: Float = 0.32f,
        val compression: Float = 0.34f,
        val deEss: Float = 0.35f,
        val ambience: Float = 0.16f
    )

    private val sr = sampleRate.coerceAtLeast(8000f)
    private val settings = Settings(
        warmth.coerceIn(0f, 1f),
        softness.coerceIn(0f, 1f),
        air.coerceIn(0f, 1f),
        compression.coerceIn(0f, 1f),
        deEss.coerceIn(0f, 1f),
        ambience.coerceIn(0f, 1f)
    )

    private val highPass = ToonJackDsp.ToneFilter(sr, 75f, highPass = true)
    private val warmthEq = ToonJackDsp.PeakingEq(sr, 190f, 0.8f, 1.5f * settings.warmth)
    private val bodyEq = ToonJackDsp.PeakingEq(sr, 850f, 0.9f, -1.2f * settings.softness)
    private val presenceEq = ToonJackDsp.PeakingEq(sr, 3200f, 0.85f, 1.8f * (1f - settings.softness))
    private val airEq = ToonJackDsp.PeakingEq(sr, 10500f.coerceAtMost(sr * 0.4f), 0.7f, 2.0f * settings.air)
    private val compressor = ToonJackDsp.Compressor(
        sr, thresholdDb = -20f, ratio = 1.7f + 1.2f * settings.compression,
        attackMs = 14f, releaseMs = 125f, makeupDb = 0.8f * settings.compression
    )
    private val deEsser = ToonJackDsp.DeEsser(
        sr, threshold = 0.10f, reduction = 0.5f * settings.deEss
    )
    private val limiter = ToonJackDsp.PeakLimiter(0.94f)
    private val echo = SoftAmbience(sr, settings.ambience)

    fun process(input: FloatArray): FloatArray {
        if (input.isEmpty()) return input.copyOf()
        val output = input.copyOf()
        highPass.process(output)
        warmthEq.process(output)
        bodyEq.process(output)
        presenceEq.process(output)
        airEq.process(output)
        compressor.process(output)
        deEsser.process(output)
        echo.process(output)
        limiter.process(output)
        return output
    }

    private class SoftAmbience(sampleRate: Float, amount: Float) {
        private val mix = amount.coerceIn(0f, 1f) * 0.18f
        private val delayA = FloatArray(max(1, (sampleRate * 0.073f).toInt()))
        private val delayB = FloatArray(max(1, (sampleRate * 0.113f).toInt()))
        private var indexA = 0
        private var indexB = 0
        private var lowState = 0f

        fun process(samples: FloatArray) {
            if (mix <= 0f) return
            for (i in samples.indices) {
                val dry = samples[i]
                val a = delayA[indexA]
                val b = delayB[indexB]
                lowState += 0.18f * (dry - lowState)
                val feedback = (lowState * 0.34f + a * 0.18f + b * 0.13f)
                    .coerceIn(-0.8f, 0.8f)
                delayA[indexA] = feedback
                delayB[indexB] = (lowState * 0.27f + a * 0.12f).coerceIn(-0.8f, 0.8f)
                indexA = (indexA + 1) % delayA.size
                indexB = (indexB + 1) % delayB.size
                samples[i] = (dry + (a + b) * mix).coerceIn(-1f, 1f)
            }
        }
    }
}
