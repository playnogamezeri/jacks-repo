package com.jackgray.winamp;

/**
 * Offline/preview PCM16 parametric EQ.
 * RBJ cookbook biquads: peaking, high-pass and low-pass.
 */
public final class BiquadEqProcessor {
    private final int sampleRate, channels;
    private final double[] gainsDb;
    private final Filter[][] filters;
    private final boolean highPassEnabled, lowPassEnabled;
    private final double highPassHz, lowPassHz;

    public BiquadEqProcessor(int sampleRate,int channels,double[] gainsDb,double[] frequencies,double[] qValues,
                             boolean highPassEnabled,double highPassHz,
                             boolean lowPassEnabled,double lowPassHz) {
        this.sampleRate=sampleRate;this.channels=channels;
        this.gainsDb=gainsDb.clone();
        this.highPassEnabled=highPassEnabled;this.highPassHz=highPassHz;
        this.lowPassEnabled=lowPassEnabled;this.lowPassHz=lowPassHz;
        filters=new Filter[channels][];
        for(int c=0;c<channels;c++){
            java.util.ArrayList<Filter> chain=new java.util.ArrayList<>();
            if(highPassEnabled)chain.add(Filter.highPass(sampleRate,highPassHz,0.707));
            for(int i=0;i<this.gainsDb.length;i++)if(Math.abs(this.gainsDb[i])>0.01)
                chain.add(Filter.peak(sampleRate,frequencies[i],qValues[i],this.gainsDb[i]));
            if(lowPassEnabled)chain.add(Filter.lowPass(sampleRate,lowPassHz,0.707));
            filters[c]=chain.toArray(new Filter[0]);
        }
    }

    /** Processes little-endian interleaved signed 16-bit PCM in place. */
    public void process(byte[] data,int length){
        int frameBytes=channels*2;
        for(int offset=0;offset+frameBytes<=length;offset+=frameBytes){
            for(int c=0;c<channels;c++){
                int p=offset+c*2;
                int raw=(data[p]&255)|((data[p+1]&255)<<8);
                double value=(short)raw/32768.0;
                for(Filter filter:filters[c])value=filter.process(value);
                int out=(int)Math.round(Math.max(-1.0,Math.min(0.999969,value))*32768.0);
                data[p]=(byte)(out&255);data[p+1]=(byte)((out>>8)&255);
            }
        }
    }

    private static final class Filter {
        final double b0,b1,b2,a1,a2;
        double x1,x2,y1,y2;
        Filter(double b0,double b1,double b2,double a0,double a1,double a2){
            this.b0=b0/a0;this.b1=b1/a0;this.b2=b2/a0;
            this.a1=a1/a0;this.a2=a2/a0;
        }
        double process(double x){
            double y=b0*x+b1*x1+b2*x2-a1*y1-a2*y2;
            x2=x1;x1=x;y2=y1;y1=y;
            return y;
        }
        static Filter peak(int sr,double hz,double q,double db){
            double w=2*Math.PI*Math.min(hz,sr*0.45)/sr;
            double a=Math.pow(10,db/40.0),alpha=Math.sin(w)/(2*q),c=Math.cos(w);
            return new Filter(1+alpha*a,-2*c,1-alpha*a,1+alpha/a,-2*c,1-alpha/a);
        }
        static Filter highPass(int sr,double hz,double q){
            double w=2*Math.PI*Math.min(hz,sr*0.45)/sr;
            double c=Math.cos(w),alpha=Math.sin(w)/(2*q);
            return new Filter((1+c)/2,-(1+c), (1+c)/2,1+alpha,-2*c,1-alpha);
        }
        static Filter lowPass(int sr,double hz,double q){
            double w=2*Math.PI*Math.min(hz,sr*0.45)/sr;
            double c=Math.cos(w),alpha=Math.sin(w)/(2*q);
            return new Filter((1-c)/2,1-c,(1-c)/2,1+alpha,-2*c,1-alpha);
        }
    }
}