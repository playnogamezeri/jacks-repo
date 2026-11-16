package com.toonjack.studio.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Offline monophonic vocal pitch correction using a pitch-synchronous
 * overlap-add grain shifter. Input/output are mono normalized Float PCM.
 *
 * This is intentionally conservative: uncertain/unvoiced frames remain dry,
 * correction is smoothed between analysis frames, and output is normalized
 * by the accumulated window weights to avoid amplitude pumping.
 */
class PsolaAutoTune(
    private val sampleRate: Float,
    private val rootMidiNote: Int = 0,
    private val scale: ToonJackDsp.Scale = ToonJackDsp.Scale.CHROMATIC,
    private val strength: Float = 0.85f,
    private val minHz: Float = 70f,
    private val maxHz: Float = 900f,
    private val clarityThreshold: Float = 0.82f,
    private val maxCorrectionCents: Float = 250f
) {
    data class Stats(
        val correctedFrames: Int,
        val bypassedFrames: Int,
        val meanCorrectionCents: Float
    )

    data class Result(val samples: FloatArray, val stats: Stats)

    fun process(input: FloatArray): Result {
        if (input.isEmpty() || sampleRate <= 0f) {
            return Result(input.copyOf(), Stats(0, 0, 0f))
        }

        val frameSize = nextPowerOfTwo((sampleRate * 0.085f).roundToInt()).coerceIn(2048, 8192)
        val hop = frameSize / 4
        val output = FloatArray(input.size)
        val weights = FloatArray(input.size)
        var corrected = 0
        var bypassed = 0
        var centsTotal = 0f
        var smoothedRatio = 1f

        var start = -frameSize / 2
        while (start < input.size) {
            val frame = FloatArray(frameSize)
            for (i in 0 until frameSize) {
                val sourceIndex = start + i
                if (sourceIndex in input.indices) frame[i] = input[sourceIndex]
            }

            val estimate = ToonJackDsp.detectPitch(
                frame, sampleRate, minHz, maxHz, clarityThreshold
            )
            val target = estimate?.let {
                ToonJackDsp.correctionTarget(it.frequencyHz, rootMidiNote, scale, strength)
            }
            val requestedRatio = if (target != null &&
                abs(target.centsToCorrect) <= maxCorrectionCents
            ) target.ratio else 1f

            // Smooth ratio across frames to avoid abrupt jumps and zipper artifacts.
            smoothedRatio += (requestedRatio - smoothedRatio) * 0.38f
            val active = estimate != null && target != null &&
                abs(target.centsToCorrect) <= maxCorrectionCents &&
                abs(target.centsToCorrect) >= 2f

            val shifted = if (active) {
                corrected++
                centsTotal += abs(target!!.centsToCorrect)
                shiftFramePsola(frame, sampleRate / estimate!!.frequencyHz,
                    (sampleRate / estimate.frequencyHz) / smoothedRatio)
            } else {
                bypassed++
                frame
            }

            for (i in 0 until frameSize) {
                val destination = start + i
                if (destination !in input.indices) continue
                val window = (0.5 - 0.5 * cos(2.0 * PI * i / (frameSize - 1))).toFloat()
                output[destination] += shifted[i] * window
                weights[destination] += window
            }
            start += hop
        }

        for (i in output.indices) {
            output[i] = if (weights[i] > 1e-5f) output[i] / weights[i] else input[i]
            output[i] = output[i].coerceIn(-1f, 1f)
        }

        return Result(
            output,
            Stats(corrected, bypassed, if (corrected == 0) 0f else centsTotal / corrected)
        )
    }

    /**
     * Resynthesize a voiced frame by placing source-period grains at target-
     * period intervals. A two-period Hann grain preserves local waveform shape.
     */
    private fun shiftFrame(frame: FloatArray, sourcePeriod: Float, targetPeriod: Float): FloatArray {
        val n = frame.size
        if (!sourcePeriod.isFinite() || !targetPeriod.isFinite() ||
            sourcePeriod < 2f || targetPeriod < 2f
        ) return frame.copyOf()

        val out = FloatArray(n)
        val norm = FloatArray(n)
        val halfGrain = (sourcePeriod * 1.0f).roundToInt().coerceIn(8, n / 3)
        val grainLength = halfGrain * 2
        val synthesisHop = targetPeriod.coerceIn(2f, sourcePeriod * 2f)
        var outCenter = 0f

        while (outCenter < n) {
            // Snap the analysis center to the nearest source-period mark.
            val mark = (outCenter / sourcePeriod).roundToInt() * sourcePeriod
            val sourceCenter = mark.coerceIn(halfGrain.toFloat(), (n - halfGrain - 1).toFloat())
            val destinationCenter = outCenter.roundToInt()
            for (j in 0 until grainLength) {
                val src = (sourceCenter.roundToInt() - halfGrain + j)
                val dst = destinationCenter - halfGrain + j
                if (src !in frame.indices || dst !in out.indices) continue
                val window = (0.5 - 0.5 * cos(2.0 * PI * j / (grainLength - 1))).toFloat()
                out[dst] += frame[src] * window
                norm[dst] += window
            }
            outCenter += synthesisHop
        }

        for (i in out.indices) {
            if (norm[i] > 1e-4f) out[i] /= norm[i] else out[i] = frame[i]
        }
        return out
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var n = 1
        while (n < value) n = n shl 1
        return n
    }
}
