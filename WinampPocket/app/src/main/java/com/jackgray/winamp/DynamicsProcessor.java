package com.jackgray.winamp;

import java.util.Locale;

/**
 * Interleaved PCM16 dynamics processor for offline rendering and audition.
 * Includes a feed-forward compressor, soft noise gate, makeup gain,
 * and a -1 dBFS peak safety limiter.
 */
public final class DynamicsProcessor {
    private final int sampleRate, channels;
    private final double thresholdDb, ratio, attackMs, releaseMs, makeup;
    private final double gateThreshold;
    private final double[] envelope;
    private double peakDb=-120.0, rmsDb=-120.0;

    public DynamicsProcessor(int sampleRate,int channels,double thresholdDb,double ratio,
                             double attackMs,double releaseMs,double makeupDb,double gateThresholdDb) {
        this.sampleRate=Math.max(8000,sampleRate);
        this.channels=Math.max(1,channels);
        this.thresholdDb=thresholdDb;
        this.ratio=Math.max(1.0,ratio);
        this.attackMs=Math.max(0.1,attackMs);
        this.releaseMs=Math.max(1.0,releaseMs);
        this.makeup=Math.pow(10.0,makeupDb/20.0);
        this.gateThreshold=gateThresholdDb<=-60.0?0.0:Math.pow(10.0,gateThresholdDb/20.0);
        this.envelope=new double[this.channels];
    }

    /** Processes little-endian, interleaved signed 16-bit PCM in place. */
    public void process(byte[] data,int length) {
        double attack=Math.exp(-1.0/(sampleRate*(attackMs/1000.0)));
        double release=Math.exp(-1.0/(sampleRate*(releaseMs/1000.0)));
        double peak=0.0,sumSquares=0.0;
        long sampleCount=0;
        for(int offset=0;offset+channels*2<=length;offset+=channels*2) {
            for(int c=0;c<channels;c++) {
                int p=offset+c*2;
                short raw=(short)((data[p]&255)|((data[p+1]&255)<<8));
                double input=raw/32768.0;
                double abs=Math.abs(input);
                double coeff=abs>envelope[c]?attack:release;
                envelope[c]=coeff*envelope[c]+(1.0-coeff)*abs;

                double levelDb=20.0*Math.log10(Math.max(1.0e-9,envelope[c]));
                double compressedDb=levelDb;
                if(levelDb>thresholdDb)
                    compressedDb=thresholdDb+(levelDb-thresholdDb)/ratio;
                double gainDb=compressedDb-levelDb;
                double gain=Math.pow(10.0,gainDb/20.0)*makeup;

                if(gateThreshold>0.0 && envelope[c]<gateThreshold) {
                    double gateGain=Math.max(0.0,Math.min(1.0,envelope[c]/gateThreshold));
                    gain*=gateGain;
                }

                double output=input*gain;
                // Safety limiter: hard ceiling at -1 dBFS prevents digital clipping.
                output=Math.max(-0.89125094,Math.min(0.89125094,output));
                peak=Math.max(peak,Math.abs(output));
                sumSquares+=output*output;
                sampleCount++;
                int value=(int)Math.round(output*32768.0);
                if(value>32767)value=32767;
                if(value< -32768)value=-32768;
                data[p]=(byte)(value&255);
                data[p+1]=(byte)((value>>8)&255);
            }
        }
        peakDb=peak>0?20.0*Math.log10(peak):-120.0;
        double rms=sampleCount>0?Math.sqrt(sumSquares/sampleCount):0.0;
        rmsDb=rms>0?20.0*Math.log10(rms):-120.0;
    }

    public String getMeterText() {
        return String.format(Locale.US,"OUTPUT METER: %.1f dBFS PEAK  |  %.1f dBFS RMS",peakDb,rmsDb);
    }
}
