package com.jackgray.winamp;

import java.util.Locale;

/** PCM16 compressor, de-esser, hold gate and block-lookahead peak safety limiter. */
public final class DynamicsProcessor {
    private static final double CEILING=0.89125094;
    private final int sampleRate,channels;
    private final double thresholdDb,ratio,attackMs,releaseMs,makeup,gateThreshold,kneeDb,gateHoldMs,deEssAmount;
    private final double[] envelope,peak,rmsSquares,gateGain,highPassState,highBandEnvelope,limiterGain;
    private final long[] sampleCounts,gateHoldFrames;
    private long limiterHits;
    private double inputPeak;

    public DynamicsProcessor(int sampleRate,int channels,double thresholdDb,double ratio,
            double attackMs,double releaseMs,double makeupDb,double gateThresholdDb,
            double kneeDb,double gateHoldMs,double deEssAmount) {
        this.sampleRate=Math.max(8000,sampleRate);
        this.channels=Math.max(1,Math.min(8,channels));
        this.thresholdDb=thresholdDb;this.ratio=Math.max(1.0,ratio);
        this.attackMs=Math.max(0.1,attackMs);this.releaseMs=Math.max(1.0,releaseMs);
        makeup=Math.pow(10.0,makeupDb/20.0);
        gateThreshold=gateThresholdDb<=-60?0:Math.pow(10.0,gateThresholdDb/20.0);
        this.kneeDb=Math.max(0,kneeDb);this.gateHoldMs=Math.max(0,gateHoldMs);
        this.deEssAmount=Math.max(0,Math.min(1,deEssAmount));
        envelope=new double[this.channels];peak=new double[this.channels];rmsSquares=new double[this.channels];
        gateGain=new double[this.channels];highPassState=new double[this.channels];
        highBandEnvelope=new double[this.channels];limiterGain=new double[this.channels];
        sampleCounts=new long[this.channels];gateHoldFrames=new long[this.channels];
        for(int c=0;c<this.channels;c++){gateGain[c]=1;limiterGain[c]=1;}
    }

    public void process(byte[] data,int length) {
        int frameBytes=channels*2,frames=length/frameBytes;
        if(frames<=0)return;
        double attack=Math.exp(-1.0/(sampleRate*attackMs/1000.0));
        double release=Math.exp(-1.0/(sampleRate*releaseMs/1000.0));
        double gateRelease=Math.exp(-1.0/(sampleRate*Math.max(0.01,releaseMs/1000.0)));
        double hpCoeff=Math.exp(-2.0*Math.PI*6000.0/sampleRate);
        double limiterRelease=Math.exp(-1.0/(sampleRate*0.050));
        double[] input=new double[frames*channels];
        for(int f=0;f<frames;f++)for(int c=0;c<channels;c++){
            int p=f*frameBytes+c*2;
            short v=(short)((data[p]&255)|((data[p+1]&255)<<8));
            input[f*channels+c]=v/32768.0;
            inputPeak=Math.max(inputPeak,Math.abs(input[f*channels+c]));
        }
        for(int f=0;f<frames;f++)for(int c=0;c<channels;c++){
            double x=input[f*channels+c],abs=Math.abs(x);
            double coeff=abs>envelope[c]?attack:release;
            envelope[c]=coeff*envelope[c]+(1-coeff)*abs;
            double levelDb=20*Math.log10(Math.max(1e-9,envelope[c]));
            double over=levelDb-thresholdDb,compressedDb=levelDb;
            if(kneeDb>0&&over>-kneeDb/2&&over<kneeDb/2){
                double soft=over+kneeDb/2;
                compressedDb=levelDb+(1.0/ratio-1.0)*soft*soft/(2.0*kneeDb);
            }else if(over>=kneeDb/2){
                compressedDb=thresholdDb+over/ratio;
            }else if(kneeDb==0&&over>0){
                compressedDb=thresholdDb+over/ratio;
            }
            double gain=Math.pow(10.0,(compressedDb-levelDb)/20.0)*makeup;

            double hp=x-highPassState[c];
            highPassState[c]=x*(1-hpCoeff)+highPassState[c]*hpCoeff;
            double highAbs=Math.abs(hp);
            highBandEnvelope[c]=0.92*highBandEnvelope[c]+0.08*highAbs;
            double sibilance=Math.max(0,highBandEnvelope[c]-envelope[c]*0.32);
            double deEssGain=1.0-deEssAmount*Math.min(0.75,sibilance*2.2);
            gain*=deEssGain;

            if(gateThreshold>0){
                if(envelope[c]>=gateThreshold)gateHoldFrames[c]=(long)(sampleRate*gateHoldMs/1000.0);
                else if(gateHoldFrames[c]>0)gateHoldFrames[c]--;
                else gateGain[c]=gateRelease*gateGain[c]+(1-gateRelease)*Math.max(0,envelope[c]/gateThreshold);
                if(gateHoldFrames[c]>0)gateGain[c]=1.0;
                gain*=gateGain[c];
            }

            // Constant-time peak limiting avoids the old nested 5 ms scan,
            // which repeated hundreds of comparisons per sample during preview.
            double predictedPeak=abs*gain;
            double target=predictedPeak>CEILING?CEILING/predictedPeak:1.0;
            if(target<limiterGain[c])limiterGain[c]=target;
            else limiterGain[c]=limiterRelease*limiterGain[c]+(1-limiterRelease)*target;
            double y=x*gain*limiterGain[c];
            if(Math.abs(y)>CEILING)limiterHits++;
            y=Math.max(-CEILING,Math.min(CEILING,y));
            peak[c]=Math.max(peak[c],Math.abs(y));rmsSquares[c]+=y*y;sampleCounts[c]++;
            int value=(int)Math.round(y*32768.0);value=Math.max(-32768,Math.min(32767,value));
            int p=f*frameBytes+c*2;data[p]=(byte)value;data[p+1]=(byte)(value>>8);
        }
    }

    private static double db(double x){return x>0?20*Math.log10(x):-120;}
    public String getMeterText(){
        if(channels>=2){
            double lr=sampleCounts[0]>0?Math.sqrt(rmsSquares[0]/sampleCounts[0]):0;
            double rr=sampleCounts[1]>0?Math.sqrt(rmsSquares[1]/sampleCounts[1]):0;
            return String.format(Locale.US,"STEREO PEAK L %5.1f R %5.1f dBFS\nSTEREO RMS  L %5.1f R %5.1f dBFS\n%s • LIMITER: %d",
                db(peak[0]),db(peak[1]),db(lr),db(rr),inputPeak>=0.999?"INPUT CLIP DETECTED":"NO INPUT CLIP",limiterHits);
        }
        double r=sampleCounts[0]>0?Math.sqrt(rmsSquares[0]/sampleCounts[0]):0;
        return String.format(Locale.US,"MONO PEAK %.1f dBFS • RMS %.1f dBFS\n%s • LIMITER: %d",
            db(peak[0]),db(r),inputPeak>=0.999?"INPUT CLIP DETECTED":"NO INPUT CLIP",limiterHits);
    }
}
