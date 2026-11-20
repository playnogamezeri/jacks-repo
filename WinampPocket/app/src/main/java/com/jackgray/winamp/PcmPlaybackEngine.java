package com.jackgray.winamp;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.IOException;

/**
 * Streaming MediaCodec -> PCM16 -> AudioTrack player.
 * Decoded audio is processed in bounded blocks; no complete track is held in memory.
 */
public final class PcmPlaybackEngine {
    public interface PreparedListener { void onPrepared(PcmPlaybackEngine engine); }
    public interface CompletionListener { void onCompletion(PcmPlaybackEngine engine); }
    public interface ErrorListener { boolean onError(PcmPlaybackEngine engine, Exception error); }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Uri source;
    private volatile boolean released, playing, prepared, decodeDone;
    private volatile long seekRequest = -1;
    private volatile int positionMs, durationMs;
    private volatile float volume = 1f;
    private volatile boolean reverbEnabled;
    private volatile double roomSize = 0.65, decaySeconds = 2.8, wetPercent = 28, preDelayMs = 18;
    private volatile AudioTrack output;
    private volatile int audioSessionId;
    private Thread worker;
    private PreparedListener preparedListener;
    private CompletionListener completionListener;
    private ErrorListener errorListener;
    private final Object pauseLock = new Object();
    private long framesWritten;
    private int sampleRate = 44100, channels = 2;
    private SoftwareReverbProcessor reverb;

    public PcmPlaybackEngine(Context context) {
        this.context = context.getApplicationContext();
        android.content.SharedPreferences p = this.context.getSharedPreferences("jack_audio_fx", Context.MODE_PRIVATE);
        reverbEnabled = p.getBoolean("software_reverb_enabled", false);
        roomSize = p.getFloat("reverb_room", 0.65f);
        decaySeconds = p.getFloat("reverb_decay", 2.8f);
        wetPercent = p.getFloat("reverb_wet", 28f);
        preDelayMs = p.getFloat("reverb_predelay", 18f);
    }
    public void setDataSource(Context ignored, Uri uri) { source = uri; }
    public void setOnPreparedListener(PreparedListener l) { preparedListener = l; }
    public void setOnCompletionListener(CompletionListener l) { completionListener = l; }
    public void setOnErrorListener(ErrorListener l) { errorListener = l; }
    public void setWakeMode(Context ignored, int mode) { }
    public int getAudioSessionId() { return audioSessionId; }
    public int getDuration() { return durationMs; }
    public int getCurrentPosition() { return positionMs; }
    public boolean isPlaying() { return playing; }
    public boolean isPrepared() { return prepared; }

    public void setVolume(float left, float right) {
        volume = Math.max(0f, Math.min(1f, (left + right) * 0.5f));
        AudioTrack t = output;
        if (t != null) try { t.setStereoVolume(volume, volume); } catch (Exception ignored) { }
    }

    public void setReverb(boolean enabled, double room, double decay, double wet, double predelay) {
        reverbEnabled = enabled;
        roomSize = room; decaySeconds = decay; wetPercent = wet; preDelayMs = predelay;
        synchronized (this) { reverb = null; }
    }

    public void prepareAsync() {
        if (source == null || worker != null) return;
        released = false;
        worker = new Thread(this::decodeAndPlay, "JackTunes-PCM-Playback");
        worker.start();
    }

    public void start() {
        synchronized (pauseLock) {
            playing = true;
            AudioTrack t = output;
            if (t != null) try { t.play(); } catch (Exception ignored) { }
            pauseLock.notifyAll();
        }
    }

    public void pause() {
        synchronized (pauseLock) {
            playing = false;
            AudioTrack t = output;
            if (t != null) try { t.pause(); } catch (Exception ignored) { }
        }
    }

    public void seekTo(int milliseconds) {
        if (!prepared) return;
        seekRequest = Math.max(0, Math.min(durationMs, milliseconds));
        synchronized (pauseLock) { pauseLock.notifyAll(); }
    }

    public void stop() { pause(); seekTo(0); }

    public void release() {
        released = true;
        playing = false;
        synchronized (pauseLock) { pauseLock.notifyAll(); }
        Thread t = worker;
        if (t != null && t != Thread.currentThread()) {
            try { t.join(700); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        AudioTrack a = output;
        output = null;
        if (a != null) { try { a.pause(); a.flush(); a.stop(); } catch (Exception ignored) { } try { a.release(); } catch (Exception ignored) { } }
        prepared = false;
    }

    private void decodeAndPlay() {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        AudioTrack track = null;
        try {
            extractor.setDataSource(context, source, null);
            int audioTrack = -1;
            MediaFormat inputFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { audioTrack = i; inputFormat = f; break; }
            }
            if (audioTrack < 0 || inputFormat == null) throw new IOException("No supported audio stream was found");
            extractor.selectTrack(audioTrack);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (inputFormat.containsKey(MediaFormat.KEY_DURATION))
                durationMs = (int)Math.min(Integer.MAX_VALUE, inputFormat.getLong(MediaFormat.KEY_DURATION) / 1000L);
            sampleRate = inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            channels = inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            if (channels < 1 || channels > 2) throw new IOException("Only mono and stereo playback are currently supported");
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(inputFormat, null, null, 0);
            codec.start();

            int channelConfig = channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
            int min = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) throw new IOException("Android audio output does not support this format");
            int bufferSize = Math.max(min * 2, sampleRate * channels / 2);
            track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IOException("Could not initialize audio output");
            output = track;
            audioSessionId = track.getAudioSessionId();
            track.setStereoVolume(volume, volume);
            track.play();
            playing = true;

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEos = false, outputEos = false;
            prepared = true;
            main.post(() -> { if (!released && preparedListener != null) preparedListener.onPrepared(this); });

            while (!released && !outputEos) {
                waitWhilePaused();
                if (released) break;

                long requested = seekRequest;
                if (requested >= 0) {
                    seekRequest = -1;
                    extractor.seekTo(requested * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    codec.flush();
                    inputEos = false; outputEos = false;
                    track.pause(); track.flush(); track.play();
                    framesWritten = requested * sampleRate / 1000L;
                    positionMs = requested;
                    synchronized (this) { reverb = null; }
                }

                if (!inputEos) {
                    int index = codec.dequeueInputBuffer(10000);
                    if (index >= 0) {
                        ByteBuffer in = codec.getInputBuffer(index);
                        if (in == null) throw new IOException("Decoder input buffer unavailable");
                        int size = extractor.readSampleData(in, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEos = true;
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(info, 10000);
                if (outIndex >= 0) {
                    ByteBuffer out = codec.getOutputBuffer(outIndex);
                    if (out != null && info.size > 0) {
                        out.position(info.offset); out.limit(info.offset + info.size);
                        byte[] pcm = convertToPcm16(out.slice(), info.size, codec.getOutputFormat());
                        processAndWrite(pcm, track);
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEos = true;
                    codec.releaseOutputBuffer(outIndex, false);
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat decoded = codec.getOutputFormat();
                    int newRate = decoded.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE) : sampleRate;
                    int newChannels = decoded.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : channels;
                    if (newRate != sampleRate || newChannels != channels) throw new IOException("Decoder changed audio format mid-stream; this track cannot be played");
                }
            }
            if (!released) {
                playing = false;
                main.post(() -> { if (!released && completionListener != null) completionListener.onCompletion(this); });
            }
        } catch (Exception e) {
            playing = false;
            main.post(() -> { if (!released && errorListener != null) errorListener.onError(this, e); });
        } finally {
            prepared = false;
            output = null;
            if (track != null) { try { track.pause(); track.flush(); track.stop(); } catch (Exception ignored) { } try { track.release(); } catch (Exception ignored) { } }
            if (codec != null) { try { codec.stop(); } catch (Exception ignored) { } try { codec.release(); } catch (Exception ignored) { } }
            try { extractor.release(); } catch (Exception ignored) { }
            worker = null;
        }
    }

    private void waitWhilePaused() throws InterruptedException {
        synchronized (pauseLock) {
            while (!playing && !released && seekRequest < 0) pauseLock.wait(100);
        }
    }

    private byte[] convertToPcm16(ByteBuffer source, int size, MediaFormat format) throws IOException {
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
        if (android.os.Build.VERSION.SDK_INT >= 24 && format.containsKey(MediaFormat.KEY_PCM_ENCODING))
            encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING);
        byte[] result;
        if (encoding == AudioFormat.ENCODING_PCM_16BIT) {
            result = new byte[size]; source.get(result); return result;
        }
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            int samples = size / 4; result = new byte[samples * 2];
            source.order(ByteOrder.nativeOrder());
            for (int i = 0; i < samples; i++) {
                float v = Math.max(-1f, Math.min(0.999969f, source.getFloat()));
                int n = Math.round(v * 32768f); result[i*2]=(byte)n; result[i*2+1]=(byte)(n>>8);
            }
            return result;
        }
        if (encoding == AudioFormat.ENCODING_PCM_8BIT) {
            result = new byte[size * 2];
            for (int i=0;i<size;i++){int n=((source.get()&255)-128)<<8;result[i*2]=(byte)n;result[i*2+1]=(byte)(n>>8);}
            return result;
        }
        throw new IOException("Unsupported decoded PCM format: " + encoding);
    }

    private void processAndWrite(byte[] pcm, AudioTrack track) throws IOException {
        if (pcm.length == 0) return;
        if (reverbEnabled) {
            synchronized (this) {
                if (reverb == null) reverb = new SoftwareReverbProcessor(sampleRate, channels, roomSize, decaySeconds, wetPercent, preDelayMs);
                reverb.process(pcm, pcm.length);
            }
        }
        int offset = 0;
        while (offset < pcm.length && !released) {
            waitWhilePaused();
            if (released) return;
            int n = track.write(pcm, offset, pcm.length - offset);
            if (n < 0) throw new IOException("Audio output write failed: " + n);
            if (n == 0) continue;
            offset += n;
            framesWritten += n / (channels * 2);
            positionMs = (int)(framesWritten * 1000L / sampleRate);
        }
    }
}
