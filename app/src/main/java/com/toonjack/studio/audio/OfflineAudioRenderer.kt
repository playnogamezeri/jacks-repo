package com.toonjack.studio.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Decodes an existing audio file, applies an offline processor, and writes a
 * new AAC/M4A file. The source file is never modified.
 *
 * Audio is downmixed to mono for the current vocal-processing chain.
 */
object OfflineAudioRenderer {
    fun renderDreamVoice(source: File, destination: File) {
        require(source.exists() && source.length() > 0L) { "Source recording is missing or empty." }
        require(source.canonicalFile != destination.canonicalFile) { "Output must be a separate file." }
        destination.parentFile?.mkdirs()

        val decoded = decodeToMono(source)
        require(decoded.samples.isNotEmpty()) { "No audio samples were decoded." }

        val processed = DreamVoiceProcessor(decoded.sampleRate.toFloat()).process(decoded.samples)
        encodeMonoAac(processed, decoded.sampleRate, destination)
    }

    private data class DecodedAudio(val samples: FloatArray, val sampleRate: Int)

    private fun decodeToMono(file: File): DecodedAudio {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = i
                    format = candidate
                    break
                }
            }
            require(track >= 0 && format != null) { "No supported audio track was found." }
            extractor.selectTrack(track)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val inputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcm = FloatCollector()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var outputChannels = inputChannels
            var pcmEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = decoder.outputFormat
                        outputChannels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding = if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        else android.media.AudioFormat.ENCODING_PCM_16BIT
                    }
                    outputIndex >= 0 -> {
                        val buffer = decoder.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val slice = buffer.slice().order(ByteOrder.nativeOrder())
                            appendDownmixed(slice, pcm, outputChannels, pcmEncoding)
                        }
                        outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            return DecodedAudio(pcm.toArray(), sampleRate)
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun appendDownmixed(
        buffer: java.nio.ByteBuffer,
        collector: FloatCollector,
        channels: Int,
        encoding: Int
    ) {
        val count = max(1, channels)
        when (encoding) {
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                val frameCount = buffer.remaining() / (4 * count)
                repeat(frameCount) {
                    var sum = 0f
                    repeat(count) { sum += buffer.float }
                    collector.add(sum / count)
                }
            }
            else -> {
                val frameCount = buffer.remaining() / (2 * count)
                repeat(frameCount) {
                    var sum = 0f
                    repeat(count) { sum += buffer.short / 32768f }
                    collector.add(sum / count)
                }
            }
        }
    }

    private fun encodeMonoAac(samples: FloatArray, sampleRate: Int, destination: File) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var trackIndex = -1
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val info = MediaCodec.BufferInfo()
            var inputOffset = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = encoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputIndex)!!
                        inputBuffer.clear()
                        val capacitySamples = inputBuffer.remaining() / 2
                        val count = minOf(capacitySamples, samples.size - inputOffset)
                        if (count <= 0) {
                            val pts = samples.size * 1_000_000L / sampleRate
                            encoder.queueInputBuffer(inputIndex, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            for (i in 0 until count) {
                                val value = (samples[inputOffset + i].coerceIn(-1f, 1f) * 32767f).toInt()
                                inputBuffer.putShort(value.toShort())
                            }
                            val pts = inputOffset * 1_000_000L / sampleRate
                            encoder.queueInputBuffer(inputIndex, 0, count * 2, pts, 0)
                            inputOffset += count
                        }
                    }
                }

                val outputIndex = encoder.dequeueOutputBuffer(info, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outputIndex >= 0 -> {
                        val outputBuffer = encoder.getOutputBuffer(outputIndex)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0) {
                            check(muxerStarted) { "Encoder produced audio before its output format was ready." }
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, outputBuffer, info)
                        }
                        outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        encoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } catch (error: Exception) {
            destination.delete()
            throw error
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            encoder.release()
            if (muxerStarted) try { muxer.stop() } catch (_: Exception) {}
            muxer.release()
        }
        check(destination.exists() && destination.length() > 0L) { "The processed audio export was empty." }
    }

    private class FloatCollector {
        private var data = FloatArray(16_384)
        private var size = 0
        fun add(value: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = value.coerceIn(-1f, 1f)
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }
}
