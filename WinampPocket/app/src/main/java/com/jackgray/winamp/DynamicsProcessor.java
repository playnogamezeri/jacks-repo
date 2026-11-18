package com.jackgray.winamp;

import java.util.Locale;

/** Interleaved PCM16 compressor, gate, makeup gain and safety limiter with stereo metering. */
public final class DynamicsProcessor {
    private static final double CEILING=0.89125094; // -1 dBFS
    private final int sampleRate,channels;
    private final double thresholdDb,ratio,attackMs,releaseMs,makeup,gateThreshold;
    private final double[] envelope,peak,rmsSquares;
    private long[] sampleCounts;
    private long limiterHits=0;
    private double inputPeak=0.0;

    public DynamicsProcessor(int sampleRate,int channels,double thresholdDb,double ratio,
                             double attackMs,double releaseMs,double makeupDb,double gateThresholdDb) {
        this.sampleRate=Math.max(8000,sampleRate);
        this.channels=Math.max(1,Math.min(8,channels));
        this.thresholdDb=thresholdDb;
        this.ratio=Math.max(1.0,ratio);
        this.attackMs=Math.max(0.1,attackMs);
        this.releaseMs=Math.max(1.0,releaseMs);
        this.makeup=Math.pow(10.0,makeupDb/20.0);
        this.gateThreshold=gateThresholdDb<=-60.0?0.0:Math.pow(10.0,gateThresholdDb/20.0);
        envelope=new double[this.channels];
        peak=new double[this.channels];
        rmsSquares=new double[this.channels];
        sampleCounts=new long[this.channels];
    }

    /** Processes little-endian, interleaved signed 16-bit PCM in place. */
    public void process(byte[] data,int length) {
        double attack=Math.exp(-1.0/(sampleRate*(attackMs/1000.0)));
        double release=Math.exp(-1.0/(sampleRate*(releaseMs/1000.0)));
        int usableChannels=Math.min(channels,Math.max(1,length/2));
        for(int offset=0;offset+usableChannels*2<=length;offset+=usableChannels*2) {
            for(int c=0;c<usableChannels;c++) {
                int p=offset+c*2;
                short raw=(short)((data[p]&255)|((data[p+1]&255)<<8));
                double input=raw/32768.0;
                double abs=Math.abs(input);
                inputPeak=Math.max(inputPeak,abs);
                double coeff=abs>envelope[c]?attack:release;
                envelope[c]=coeff*envelope[c]+(1.0-coeff)*abs;

                double levelDb=20.0*Math.log10(Math.max(1.0e-9,envelope[c]));
                double compressedDb=levelDb;
                if(levelDb>thresholdDb)compressedDb=thresholdDb+(levelDb-thresholdDb)/ratio;
                double gain=Math.pow(10.0,(compressedDb-levelDb)/20.0)*makeup;
                if(gateThreshold>0.0&&envelope[c]<gateThreshold)
                    gain*=Math.max(0.0,Math.min(1.0,envelope[c]/gateThreshold));

                double output=input*gain;
                if(Math.abs(output)>CEILING)limiterHits++;
                output=Math.max(-CEILING,Math.min(CEILING,output));
                peak[c]=Math.max(peak[c],Math.abs(output));
                rmsSquares[c]+=output*output;
                sampleCounts[c]++;
                int value=(int)Math.round(output*32768.0);
                value=Math.max(-32768,Math.min(32767,value));
                data[p]=(byte)(value&255);
                data[p+1]=(byte)((value>>8)&255);
            }
        }
    }

    private static double db(double linear){
        return linear>0.0?20.0*Math.log10(linear):-120.0;
    }

    public String getMeterText(){
        if(channels>=2){
            double leftRms=sampleCounts[0]>0?Math.sqrt(rmsSquares[0]/sampleCounts[0]):0;
            double rightRms=sampleCounts[1]>0?Math.sqrt(rmsSquares[1]/sampleCounts[1]):0;
            return String.format(Locale.US,
                "STEREO PEAK  L %5.1f  R %5.1f dBFS\nSTEREO RMS   L %5.1f  R %5.1f dBFS\n%s  •  LIMITER: %d",
                db(peak[0]),db(peak[1]),db(leftRms),db(rightRms),
                inputPeak>=1.0?"INPUT CLIP DETECTED":"NO INPUT CLIP",limiterHits);
        }
        double monoRms=sampleCounts[0]>0?Math.sqrt(rmsSquares[0]/sampleCounts[0]):0;
        return String.format(Locale.US,"MONO PEAK %.1f dBFS  •  RMS %.1f dBFS\n%s  •  LIMITER: %d",
            db(peak[0]),db(monoRms),inputPeak>=1.0?"INPUT CLIP DETECTED":"NO INPUT CLIP",limiterHits);
    }
}
