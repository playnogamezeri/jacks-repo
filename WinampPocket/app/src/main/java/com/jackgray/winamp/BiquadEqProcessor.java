package com.jackgray.winamp;

/** RBJ biquad EQ with per-band filter modes, bypass and cascaded cutoff slopes. */
public final class BiquadEqProcessor {
    public static final String PEAK="PEAK",LOW_SHELF="LOW SHELF",HIGH_SHELF="HIGH SHELF",NOTCH="NOTCH",BAND_PASS="BAND-PASS";
    private final int sampleRate,channels;
    private final Filter[][] filters;
    private final boolean enabled;

    public BiquadEqProcessor(int sampleRate,int channels,double[] gains,double[] frequencies,double[] qValues,
            boolean highPassEnabled,double highPassHz,boolean lowPassEnabled,double lowPassHz) {
        this(sampleRate,channels,gains,frequencies,qValues,defaultTypes(gains.length),defaultEnabled(gains.length),
                highPassEnabled,highPassHz,12,lowPassEnabled,lowPassHz,12,true);
    }

    public BiquadEqProcessor(int sampleRate,int channels,double[] gains,double[] frequencies,double[] qValues,
            String[] types,boolean[] bandEnabled,boolean highPassEnabled,double highPassHz,int highPassSlope,
            boolean lowPassEnabled,double lowPassHz,int lowPassSlope,boolean eqEnabled) {
        this.sampleRate=Math.max(8000,sampleRate);this.channels=Math.max(1,channels);this.enabled=eqEnabled;
        filters=new Filter[this.channels][];
        for(int c=0;c<this.channels;c++){
            java.util.ArrayList<Filter> chain=new java.util.ArrayList<>();
            if(eqEnabled){
                if(highPassEnabled)addCutoff(chain,this.sampleRate,highPassHz,highPassSlope,true);
                for(int i=0;i<gains.length;i++){
                    if(!bandEnabled[i])continue;
                    String type=types[i]==null?PEAK:types[i];
                    double g=gains[i],hz=frequencies[i],q=qValues[i];
                    if(PEAK.equals(type)&&Math.abs(g)>0.001)chain.add(Filter.peak(this.sampleRate,hz,q,g));
                    else if(LOW_SHELF.equals(type)&&Math.abs(g)>0.001)chain.add(Filter.lowShelf(this.sampleRate,hz,q,g));
                    else if(HIGH_SHELF.equals(type)&&Math.abs(g)>0.001)chain.add(Filter.highShelf(this.sampleRate,hz,q,g));
                    else if(NOTCH.equals(type))chain.add(Filter.notch(this.sampleRate,hz,q));
                    else if(BAND_PASS.equals(type))chain.add(Filter.bandPass(this.sampleRate,hz,q));
                }
                if(lowPassEnabled)addCutoff(chain,this.sampleRate,lowPassHz,lowPassSlope,false);
            }
            filters[c]=chain.toArray(new Filter[0]);
        }
    }
    private static String[] defaultTypes(int n){String[] a=new String[n];java.util.Arrays.fill(a,PEAK);return a;}
    private static boolean[] defaultEnabled(int n){boolean[] a=new boolean[n];java.util.Arrays.fill(a,true);return a;}

    private static void addCutoff(java.util.ArrayList<Filter> chain,int sr,double hz,int slope,boolean high){
        int s=Math.max(6,Math.min(24,slope));
        if(s==6){chain.add(Filter.firstOrder(sr,hz,high));return;}
        if(s==12){chain.add(high?Filter.highPass(sr,hz,0.70710678):Filter.lowPass(sr,hz,0.70710678));return;}
        if(s==18){
            chain.add(high?Filter.highPass(sr,hz,0.70710678):Filter.lowPass(sr,hz,0.70710678));
            chain.add(Filter.firstOrder(sr,hz,high));return;
        }
        chain.add(high?Filter.highPass(sr,hz,0.5411961):Filter.lowPass(sr,hz,0.5411961));
        chain.add(high?Filter.highPass(sr,hz,1.306563):Filter.lowPass(sr,hz,1.306563));
    }

    public void process(byte[] data,int length){
        if(!enabled)return;
        int frameBytes=channels*2;
        for(int offset=0;offset+frameBytes<=length;offset+=frameBytes){
            for(int c=0;c<channels;c++){
                int p=offset+c*2,raw=(data[p]&255)|((data[p+1]&255)<<8);
                double value=(short)raw/32768.0;
                for(Filter filter:filters[c])value=filter.process(value);
                int out=(int)Math.round(Math.max(-1.0,Math.min(0.999969,value))*32768.0);
                data[p]=(byte)(out&255);data[p+1]=(byte)(out>>8);
            }
        }
    }

    public static double responseDb(int sr,double hz,double[] gains,double[] frequencies,double[] qValues,
            String[] types,boolean[] bandEnabled,boolean hp,double hpHz,int hpSlope,
            boolean lp,double lpHz,int lpSlope,boolean eqEnabled){
        if(!eqEnabled)return 0;
        double magnitude=1.0;
        if(hp)magnitude*=cutoffMagnitude(sr,hz,hpHz,hpSlope,true);
        for(int i=0;i<gains.length;i++)if(bandEnabled[i]){
            String t=types[i];Filter f=null;
            if(PEAK.equals(t))f=Filter.peak(sr,frequencies[i],qValues[i],gains[i]);
            else if(LOW_SHELF.equals(t))f=Filter.lowShelf(sr,frequencies[i],qValues[i],gains[i]);
            else if(HIGH_SHELF.equals(t))f=Filter.highShelf(sr,frequencies[i],qValues[i],gains[i]);
            else if(NOTCH.equals(t))f=Filter.notch(sr,frequencies[i],qValues[i]);
            else if(BAND_PASS.equals(t))f=Filter.bandPass(sr,frequencies[i],qValues[i]);
            if(f!=null)magnitude*=f.magnitude(sr,hz);
        }
        if(lp)magnitude*=cutoffMagnitude(sr,hz,lpHz,lpSlope,false);
        return 20*Math.log10(Math.max(1e-8,magnitude));
    }
    private static double cutoffMagnitude(int sr,double hz,double cutoff,int slope,boolean high){
        java.util.ArrayList<Filter> a=new java.util.ArrayList<>();addCutoff(a,sr,cutoff,slope,high);
        double m=1;for(Filter f:a)m*=f.magnitude(sr,hz);return m;
    }

    private static final class Filter {
        final double b0,b1,b2,a1,a2;double x1,x2,y1,y2;
        Filter(double b0,double b1,double b2,double a0,double a1,double a2){
            this.b0=b0/a0;this.b1=b1/a0;this.b2=b2/a0;this.a1=a1/a0;this.a2=a2/a0;
        }
        double process(double x){double y=b0*x+b1*x1+b2*x2-a1*y1-a2*y2;x2=x1;x1=x;y2=y1;y1=y;return y;}
        double magnitude(int sr,double hz){
            double w=2*Math.PI*Math.min(Math.max(0,hz),sr*0.499)/sr;
            double cr=Math.cos(w),ci=-Math.sin(w),z2r=Math.cos(2*w),z2i=-Math.sin(2*w);
            double nr=b0+b1*cr+b2*z2r,ni=b1*ci+b2*z2i;
            double dr=1+a1*cr+a2*z2r,di=a1*ci+a2*z2i;
            return Math.hypot(nr,ni)/Math.max(1e-12,Math.hypot(dr,di));
        }
        static double w(int sr,double hz){return 2*Math.PI*Math.min(Math.max(1,hz),sr*0.45)/sr;}
        static Filter peak(int sr,double hz,double q,double db){
            double w=w(sr,hz),a=Math.pow(10,db/40),alpha=Math.sin(w)/(2*Math.max(.1,q)),c=Math.cos(w);
            return new Filter(1+alpha*a,-2*c,1-alpha*a,1+alpha/a,-2*c,1-alpha/a);
        }
        static Filter notch(int sr,double hz,double q){
            double w=w(sr,hz),alpha=Math.sin(w)/(2*Math.max(.1,q)),c=Math.cos(w);
            return new Filter(1,-2*c,1,1+alpha,-2*c,1-alpha);
        }
        static Filter bandPass(int sr,double hz,double q){
            double w=w(sr,hz),alpha=Math.sin(w)/(2*Math.max(.1,q));
            return new Filter(alpha,0,-alpha,1+alpha,-2*Math.cos(w),1-alpha);
        }
        static Filter highPass(int sr,double hz,double q){
            double w=w(sr,hz),c=Math.cos(w),alpha=Math.sin(w)/(2*q);
            return new Filter((1+c)/2,-(1+c),(1+c)/2,1+alpha,-2*c,1-alpha);
        }
        static Filter lowPass(int sr,double hz,double q){
            double w=w(sr,hz),c=Math.cos(w),alpha=Math.sin(w)/(2*q);
            return new Filter((1-c)/2,1-c,(1-c)/2,1+alpha,-2*c,1-alpha);
        }
        static Filter firstOrder(int sr,double hz,boolean high){
            double k=Math.tan(Math.PI*Math.min(Math.max(1,hz),sr*.45)/sr),norm=1/(1+k);
            if(high)return new Filter(norm,-norm,0,1,(k-1)*norm,0);
            return new Filter(k*norm,k*norm,0,1,(k-1)*norm,0);
        }
        static Filter lowShelf(int sr,double hz,double q,double db){
            double w=w(sr,hz),a=Math.pow(10,db/40),c=Math.cos(w),sn=Math.sin(w),alpha=sn/(2*Math.max(.1,q)),beta=2*Math.sqrt(a)*alpha;
            return new Filter(a*((a+1)-(a-1)*c+beta),2*a*((a-1)-(a+1)*c),a*((a+1)-(a-1)*c-beta),
                (a+1)+(a-1)*c+beta,-2*((a-1)+(a+1)*c),(a+1)+(a-1)*c-beta);
        }
        static Filter highShelf(int sr,double hz,double q,double db){
            double w=w(sr,hz),a=Math.pow(10,db/40),c=Math.cos(w),sn=Math.sin(w),alpha=sn/(2*Math.max(.1,q)),beta=2*Math.sqrt(a)*alpha;
            return new Filter(a*((a+1)+(a-1)*c+beta),-2*a*((a-1)+(a+1)*c),a*((a+1)+(a-1)*c-beta),
                (a+1)-(a-1)*c+beta,2*((a-1)-(a+1)*c),(a+1)-(a-1)*c-beta);
        }
    }
}