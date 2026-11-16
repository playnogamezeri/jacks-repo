package com.toonjack.studio.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Toon Jack's offline/real-time PCM DSP building blocks.
 *
 * Samples are normalized Float PCM in [-1, 1]. Processors are stateful:
 * keep the same instance across consecutive blocks to avoid clicks.
 * The original recording should always be retained separately.
 */
object ToonJackDsp {
    data class PitchEstimate(
        val frequencyHz: Float,
        val clarity: Float,
        val midiNote: Float
    )

    data class CorrectionTarget(
        val sourceFrequencyHz: Float,
        val targetFrequencyHz: Float,
        val centsToCorrect: Float,
        val ratio: Float
    )

    enum class Scale(val pitchClasses: Set<Int>) {
        CHROMATIC((0..11).toSet()),
        MAJOR(setOf(0, 2, 4, 5, 7, 9, 11)),
        MINOR(setOf(0, 2, 3, 5, 7, 8, 10)),
        PENTATONIC_MAJOR(setOf(0, 2, 4, 7, 9)),
        PENTATONIC_MINOR(setOf(0, 3, 5, 7, 10))
    }

    /** RBJ cookbook peaking EQ biquad. Gain is in dB; Q controls bandwidth. */
    class PeakingEq(sampleRate: Float, centerHz: Float, q: Float, gainDb: Float) {
        private val filter = Biquad.peaking(sampleRate, centerHz, q, gainDb)
        fun process(samples: FloatArray) = filter.process(samples)
    }

    /** RBJ cookbook low/high-pass biquad. */
    class ToneFilter(sampleRate: Float, cutoffHz: Float, q: Float = 0.7071f, highPass: Boolean) {
        private val filter = if (highPass) Biquad.highPass(sampleRate, cutoffHz, q)
            else Biquad.lowPass(sampleRate, cutoffHz, q)
        fun process(samples: FloatArray) = filter.process(samples)
    }

    /** Smooth single-band compressor with attack/release envelope following. */
    class Compressor(
        private val sampleRate: Float,
        private val thresholdDb: Float = -18f,
        private val ratio: Float = 2.5f,
        attackMs: Float = 10f,
        releaseMs: Float = 100f,
        private val makeupDb: Float = 0f
    ) {
        private val attack = exp((-1f / (sampleRate * attackMs.coerceAtLeast(0.1f) / 1000f))).toFloat()
        private val release = exp((-1f / (sampleRate * releaseMs.coerceAtLeast(1f) / 1000f))).toFloat()
        private var envelope = 0f

        fun process(samples: FloatArray) {
            val makeup = dbToGain(makeupDb)
            for (i in samples.indices) {
                val input = abs(samples[i])
                val coefficient = if (input > envelope) attack else release
                envelope = coefficient * envelope + (1f - coefficient) * input
                val levelDb = gainToDb(envelope)
                val over = max(0f, levelDb - thresholdDb)
                val reductionDb = over * (1f - 1f / ratio.coerceAtLeast(1f))
                val gain = dbToGain(-reductionDb) * makeup
                samples[i] = (samples[i] * gain).coerceIn(-1f, 1f)
            }
        }
    }

    /** Gentle downward expander/noise gate. Set threshold around the measured room noise. */
    class NoiseGate(
        private val sampleRate: Float,
        private val thresholdDb: Float = -48f,
        attackMs: Float = 3f,
        releaseMs: Float = 90f,
        private val floorGain: Float = 0.08f
    ) {
        private val attack = exp(-1f / (sampleRate * attackMs.coerceAtLeast(0.1f) / 1000f)).toFloat()
        private val release = exp(-1f / (sampleRate * releaseMs.coerceAtLeast(1f) / 1000f)).toFloat()
        private var envelope = 0f
        private var gain = 1f

        fun process(samples: FloatArray) {
            for (i in samples.indices) {
                val level = abs(samples[i])
                val c = if (level > envelope) attack else release
                envelope = c * envelope + (1f - c) * level
                val wanted = if (gainToDb(envelope) < thresholdDb) floorGain else 1f
                val gc = if (wanted < gain) attack else release
                gain = gc * gain + (1f - gc) * wanted
                samples[i] *= gain
            }
        }
    }

    /** De-esser: detects energy in the sibilance band and applies restrained dynamic attenuation. */
    class DeEsser(sampleRate: Float, private val threshold: Float = 0.12f, private val reduction: Float = 0.45f) {
        private val detector = Biquad.bandPass(sampleRate, 6500f.coerceAtMost(sampleRate * 0.42f), 0.9f)
        private var envelope = 0f
        fun process(samples: FloatArray) {
            val highBand = samples.copyOf()
            detector.process(highBand)
            for (i in samples.indices) {
                envelope = 0.92f * envelope + 0.08f * abs(highBand[i])
                val excess = ((envelope - threshold) / threshold.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                samples[i] *= 1f - reduction.coerceIn(0f, 0.9f) * excess
            }
        }
    }

    /** Soft peak limiter with a small amount of headroom. */
    class PeakLimiter(private val ceiling: Float = 0.96f) {
        fun process(samples: FloatArray) {
            val limit = ceiling.coerceIn(0.1f, 1f)
            for (i in samples.indices) {
                val x = samples[i]
                samples[i] = (x / (1f + abs(x) / limit * 0.15f)).coerceIn(-limit, limit)
            }
        }
    }

    /**
     * YIN-style monophonic fundamental-frequency estimator.
     * Use voice-oriented bounds (e.g. 70-1000 Hz) and reject low-clarity frames.
     * It estimates pitch; it does not by itself alter audio.
     */
    fun detectPitch(
        samples: FloatArray,
        sampleRate: Float,
        minHz: Float = 70f,
        maxHz: Float = 1000f,
        clarityThreshold: Float = 0.78f
    ): PitchEstimate? {
        if (samples.size < 512 || sampleRate <= 0f) return null
        val minTau = max(2, (sampleRate / maxHz).toInt())
        val maxTau = min(samples.size / 2, (sampleRate / minHz).toInt())
        if (maxTau <= minTau) return null

        val difference = FloatArray(maxTau + 1)
        for (tau in 1..maxTau) {
            var sum = 0.0
            val end = samples.size - tau
            var i = 0
            while (i < end) {
                val delta = samples[i] - samples[i + tau]
                sum += delta * delta
                i++
            }
            difference[tau] = sum.toFloat()
        }

        val cmnd = FloatArray(maxTau + 1)
        cmnd[0] = 1f
        var running = 0.0
        for (tau in 1..maxTau) {
            running += difference[tau]
            cmnd[tau] = if (running <= 1e-12) 1f else difference[tau] * tau / running.toFloat()
        }

        var candidate = -1
        var tau = minTau
        while (tau <= maxTau) {
            if (cmnd[tau] < 0.15f) {
                while (tau + 1 <= maxTau && cmnd[tau + 1] < cmnd[tau]) tau++
                candidate = tau
                break
            }
            tau++
        }
        if (candidate < 0) {
            var best = minTau
            for (i in minTau..maxTau) if (cmnd[i] < cmnd[best]) best = i
            if (cmnd[best] > 1f - clarityThreshold) return null
            candidate = best
        }

        val left = cmnd[(candidate - 1).coerceAtLeast(1)]
        val center = cmnd[candidate]
        val right = cmnd[(candidate + 1).coerceAtMost(maxTau)]
        val denominator = 2f * (2f * center - right - left)
        val correction = if (abs(denominator) > 1e-9f) (right - left) / denominator else 0f
        val refinedTau = candidate + correction.coerceIn(-0.5f, 0.5f)
        val frequency = sampleRate / refinedTau
        val clarity = (1f - center).coerceIn(0f, 1f)
        if (clarity < clarityThreshold || frequency !in minHz..maxHz) return null
        val midi = 69f + 12f * (ln(frequency / 440f) / ln(2f))
        return PitchEstimate(frequency, clarity, midi)
    }

    /** Finds the nearest allowed scale note and calculates a correction ratio. */
    fun correctionTarget(
        detectedFrequencyHz: Float,
        rootMidiNote: Int,
        scale: Scale,
        strength: Float = 1f
    ): CorrectionTarget? {
        if (detectedFrequencyHz <= 0f) return null
        val midi = 69f + 12f * (ln(detectedFrequencyHz / 440f) / ln(2f))
        val nearest = (midi.roundToIntSafe() - 12..midi.roundToIntSafe() + 12)
            .filter { Math.floorMod(it - rootMidiNote, 12) in scale.pitchClasses }
            .minByOrNull { abs(it - midi) } ?: return null
        val targetMidi = midi + (nearest - midi) * strength.coerceIn(0f, 1f)
        val targetHz = 440f * 2f.pow((targetMidi - 69f) / 12f)
        return CorrectionTarget(
            detectedFrequencyHz,
            targetHz,
            (targetMidi - midi) * 100f,
            targetHz / detectedFrequencyHz
        )
    }

    /** Convert an equal-tempered frequency interval to cents. */
    fun centsBetween(frequencyA: Float, frequencyB: Float): Float {
        if (frequencyA <= 0f || frequencyB <= 0f) return 0f
        return (1200f * ln(frequencyB / frequencyA) / ln(2f))
    }

    private fun Float.roundToIntSafe(): Int = kotlin.math.floor(this + 0.5f).toInt()
    private fun dbToGain(db: Float) = 10f.pow(db / 20f)
    private fun gainToDb(gain: Float) = 20f * kotlin.math.log10(gain.coerceAtLeast(1e-8f))

    private class Biquad private constructor(
        private val b0: Float, private val b1: Float, private val b2: Float,
        private val a1: Float, private val a2: Float
    ) {
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun process(samples: FloatArray) {
            for (i in samples.indices) {
                val x = samples[i]
                val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = x
                y2 = y1; y1 = if (y.isFinite()) y else 0f
                samples[i] = y1
            }
        }

        companion object {
            private fun normalized(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double): Biquad {
                val inv = 1.0 / a0
                return Biquad((b0 * inv).toFloat(), (b1 * inv).toFloat(), (b2 * inv).toFloat(),
                    (a1 * inv).toFloat(), (a2 * inv).toFloat())
            }

            fun lowPass(sr: Float, hz: Float, q: Float): Biquad {
                val w = 2.0 * PI * hz.coerceIn(10f, sr * 0.45f) / sr
                val c = cos(w); val s = sin(w); val alpha = s / (2.0 * q.coerceAtLeast(0.1f))
                return normalized((1-c)/2, 1-c, (1-c)/2, 1+alpha, -2*c, 1-alpha)
            }

            fun highPass(sr: Float, hz: Float, q: Float): Biquad {
                val w = 2.0 * PI * hz.coerceIn(10f, sr * 0.45f) / sr
                val c = cos(w); val s = sin(w); val alpha = s / (2.0 * q.coerceAtLeast(0.1f))
                return normalized((1+c)/2, -(1+c), (1+c)/2, 1+alpha, -2*c, 1-alpha)
            }

            fun bandPass(sr: Float, hz: Float, q: Float): Biquad {
                val w = 2.0 * PI * hz.coerceIn(10f, sr * 0.45f) / sr
                val c = cos(w); val s = sin(w); val alpha = s / (2.0 * q.coerceAtLeast(0.1f))
                return normalized(alpha, 0.0, -alpha, 1+alpha, -2*c, 1-alpha)
            }

            fun peaking(sr: Float, hz: Float, q: Float, gainDb: Float): Biquad {
                val w = 2.0 * PI * hz.coerceIn(10f, sr * 0.45f) / sr
                val a = 10.0.pow(gainDb / 40.0)
                val c = cos(w); val s = sin(w); val alpha = s / (2.0 * q.coerceAtLeast(0.1f))
                return normalized(1+alpha*a, -2*c, 1-alpha*a, 1+alpha/a, -2*c, 1-alpha/a)
            }
        }
    }
}
