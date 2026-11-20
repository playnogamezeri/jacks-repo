package com.jackgray.winamp;

/**
 * Sample-based stereo Schroeder reverb for signed 16-bit little-endian PCM.
 * No Android audio-effect APIs are used, so the DSP itself is device independent.
 *
 * The caller owns the audio pipeline and invokes process() on consecutive blocks.
 */
public final class SoftwareReverbProcessor {
    private static final double[] COMB_SECONDS = {0.0297, 0.0371, 0.0411, 0.0437};
    private static final double[] ALLPASS_SECONDS = {0.005, 0.0017};
    private final int channels;
    private final Delay[] combL, combR, allpassL, allpassR;
    private final Delay preL, preR;
    private final double wet, dry, feedback, damping;
    private double dampL, dampR;

    public SoftwareReverbProcessor(int sampleRate, int channels, double roomSize,
                                   double decaySeconds, double wetPercent,
                                   double preDelayMs) {
        if (sampleRate < 8000) throw new IllegalArgumentException("Invalid sample rate");
        if (channels < 1 || channels > 2) throw new IllegalArgumentException("Mono or stereo required");
        this.channels = channels;
        double room = clamp(roomSize, 0.0, 1.0);
        this.wet = clamp(wetPercent / 100.0, 0.0, 1.0);
        this.dry = 1.0 - this.wet * 0.55;
        double decay = clamp(decaySeconds, 0.15, 12.0);
        this.feedback = Math.min(0.94, Math.pow(10.0, -3.0 * 0.0297 / decay) * (0.55 + room * 0.40));
        this.damping = 0.12 + room * 0.28;

        combL = new Delay[COMB_SECONDS.length];
        combR = new Delay[COMB_SECONDS.length];
        for (int i = 0; i < COMB_SECONDS.length; i++) {
            int base = (int)(COMB_SECONDS[i] * sampleRate);
            combL[i] = new Delay(Math.max(1, base + (int)(room * 17)));
            combR[i] = new Delay(Math.max(1, base + 23 + (int)(room * 19)));
        }
        allpassL = new Delay[ALLPASS_SECONDS.length];
        allpassR = new Delay[ALLPASS_SECONDS.length];
        for (int i = 0; i < ALLPASS_SECONDS.length; i++) {
            int base = (int)(ALLPASS_SECONDS[i] * sampleRate);
            allpassL[i] = new Delay(Math.max(1, base + (int)(room * 7)));
            allpassR[i] = new Delay(Math.max(1, base + 11 + (int)(room * 9)));
        }
        int pre = Math.max(0, Math.min(sampleRate / 2, (int)(sampleRate * clamp(preDelayMs, 0, 250) / 1000.0)));
        preL = new Delay(Math.max(1, pre));
        preR = new Delay(Math.max(1, pre));
    }

    /** Process complete PCM frames in place. State is retained across calls. */
    public void process(byte[] pcm, int byteCount) {
        int frameBytes = channels * 2;
        int end = byteCount - (byteCount % frameBytes);
        for (int p = 0; p < end; p += frameBytes) {
            double left = read16(pcm, p) / 32768.0;
            double right = channels == 2 ? read16(pcm, p + 2) / 32768.0 : left;
            double inL = preL.push(left);
            double inR = preR.push(right);
            double revL = 0, revR = 0;
            for (int i = 0; i < combL.length; i++) {
                double a = combL[i].peek();
                double b = combR[i].peek();
                dampL += damping * (a - dampL);
                dampR += damping * (b - dampR);
                combL[i].push(inL + dampL * feedback);
                combR[i].push(inR + dampR * feedback);
                revL += a;
                revR += b;
            }
            revL *= 0.25;
            revR *= 0.25;
            for (int i = 0; i < allpassL.length; i++) {
                revL = allpass(allpassL[i], revL);
                revR = allpass(allpassR[i], revR);
            }
            write16(pcm, p, clampSample(left * dry + revL * wet * 1.8));
            if (channels == 2) write16(pcm, p + 2, clampSample(right * dry + revR * wet * 1.8));
        }
    }

    private static double allpass(Delay delay, double input) {
        double delayed = delay.peek();
        double output = delayed - input * 0.5;
        delay.push(input + delayed * 0.5);
        return output;
    }

    private static int read16(byte[] b, int p) {
        return (short)((b[p] & 255) | (b[p + 1] << 8));
    }
    private static void write16(byte[] b, int p, double v) {
        int n = (int)Math.round(clamp(v, -1.0, 0.999969) * 32768.0);
        b[p] = (byte)n;
        b[p + 1] = (byte)(n >> 8);
    }
    private static double clampSample(double v) { return clamp(v, -1.0, 0.999969); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    private static final class Delay {
        private final double[] data;
        private int index;
        Delay(int size) { data = new double[Math.max(1, size)]; }
        double peek() { return data[index]; }
        void push(double value) { data[index] = value; if (++index == data.length) index = 0; }
    }
}
