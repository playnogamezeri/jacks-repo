package com.jackgray.winamp;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

public class WaveEditorActivity extends Activity {
    private final int bg=Color.rgb(3,8,15), panel=Color.rgb(7,20,31), cyan=Color.rgb(0,235,220);
    private final int white=Color.rgb(220,250,255), muted=Color.rgb(105,160,175), edge=Color.rgb(0,190,210);
    private TextView fileLabel,selectionLabel,status;
    private WaveView wave;
    private SeekBar startSeek,endSeek,fadeInSeek,fadeOutSeek;
    private final double[] eqGains={0,0,0,0,0};
    private final double[] eqFrequencies={80,250,1000,4000,12000};
    private final double[] eqQ={1,1,1,1,1};
    private final SeekBar[] gainBars=new SeekBar[5],frequencyBars=new SeekBar[5],qBars=new SeekBar[5];
    private SeekBar hpfBar,lpfBar;
    private boolean hpfEnabled=false,lpfEnabled=false;
    private double hpfHz=40, lpfHz=18000;
    private TextView eqStatus,presetStatus;
    private SeekBar thresholdBar,ratioBar,attackBar,releaseBar,makeupBar,gateBar;
    private TextView dynamicsStatus,meterStatus;
    private double compressorThreshold=-18.0, compressorRatio=3.0, attackMs=10.0, releaseMs=120.0, makeupDb=0.0, gateThresholdDb=-60.0;
    private final String[] presetNames={"FLAT","WARM VOCALS","PRESENCE BOOST","PODCAST VOICE","DEEP BASS","CLUB / EDM","HIP-HOP","ROCK GUITARS","ACOUSTIC","JAZZ","CLASSICAL","BRIGHT POP","LO-FI","AIR & DETAIL"};
    private EqCurveView eqCurve;
    private final java.util.ArrayList<SeekBar> advancedEqBars=new java.util.ArrayList<>();
    private File pcmFile;
    private int sampleRate=44100,channels=2;
    private long totalFrames=0;
    private boolean busy=false;
    private volatile boolean previewStop=true;
    private AudioTrack previewTrack;
    private Thread previewThread;
    private static final int PICK_AUDIO=301, SAVE_WAV=302;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(18));root.setBackgroundColor(bg);
        scroll.addView(root);setContentView(scroll);
        TextView title=label("JACK TUNERS // WAVE EDITOR",19,cyan,true);title.setGravity(Gravity.CENTER);root.addView(title);
        TextView sub=label("NON-DESTRUCTIVE SOURCE  •  NEW-FILE RENDER",10,muted,true);sub.setGravity(Gravity.CENTER);root.addView(sub);
        root.addView(button("← BACK TO STUDIO",()->finish()));
        root.addView(sectionLabel("SOURCE AUDIO"));
        fileLabel=label("NO AUDIO FILE SELECTED",11,muted,true);root.addView(fileLabel);
        root.addView(button("IMPORT AUDIO FILE",()->pickAudio()));
        root.addView(sectionLabel("WAVEFORM OVERVIEW"));
        wave=new WaveView();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,dp(150));wp.topMargin=dp(8);root.addView(wave,wp);
        root.addView(sectionLabel("PLAYBACK PREVIEW"));
        root.addView(button("PLAY SELECTED REGION",()->playSelection()));
        root.addView(button("STOP PREVIEW",()->stopPreview()));
        root.addView(sectionLabel("EDIT REGION"));
        startSeek=slider(root,"START POINT",0);
        endSeek=slider(root,"END POINT",100);
        selectionLabel=label("SELECT A FILE TO BEGIN",11,cyan,true);root.addView(selectionLabel);
        root.addView(sectionLabel("EDGE FADES"));
        fadeInSeek=slider(root,"FADE IN (0–10 SEC)",0);
        fadeOutSeek=slider(root,"FADE OUT (0–10 SEC)",0);
        root.addView(sectionLabel("STAGE 2 // ADVANCED PARAMETRIC EQ"));
        root.addView(label("5-BAND BIQUAD IIR • PROCESSING IS APPLIED TO PREVIEW AND EXPORTED WAV",9,muted,true));
        eqStatus=label("EQ: FLAT • HPF OFF • LPF OFF",10,cyan,true);root.addView(eqStatus);
        presetStatus=label("PRESET: FLAT",10,white,true);root.addView(presetStatus);
        HorizontalScrollView presetScroll=new HorizontalScrollView(this);presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow=new LinearLayout(this);presetRow.setOrientation(LinearLayout.HORIZONTAL);
        for(String name:presetNames)presetRow.addView(button(name,()->applyEqPreset(name)));
        presetScroll.addView(presetRow);root.addView(presetScroll);
        root.addView(label("FREQUENCY RESPONSE PREVIEW",9,muted,true));
        eqCurve=new EqCurveView();root.addView(eqCurve,new LinearLayout.LayoutParams(-1,dp(130)));
        String[] eqNames={"SUB / BASS 80 Hz","LOW 250 Hz","MID 1 kHz","PRESENCE 4 kHz","AIR 12 kHz"};
        for(int i=0;i<eqNames.length;i++)addEqBand(root,i,eqNames[i]);
        root.addView(button("TOGGLE HIGH-PASS FILTER",()->{hpfEnabled=!hpfEnabled;updateEqStatus();}));
        addCutoffSlider(root,"HPF CUTOFF 20–500 Hz",20,500,40,true);
        root.addView(button("TOGGLE LOW-PASS FILTER",()->{lpfEnabled=!lpfEnabled;updateEqStatus();}));
        addCutoffSlider(root,"LPF CUTOFF 2–20 kHz",2000,20000,18000,false);
        root.addView(button("RESET ADVANCED EQ",()->resetAdvancedEq()));
        root.addView(button("RENDER SELECTION AS PROCESSED WAV",()->saveWav()));
        root.addView(sectionLabel("STAGE 3 // DYNAMICS & METERING"));
        root.addView(label("COMPRESSOR • NOISE GATE • PEAK LIMITER • MAKEUP GAIN",9,muted,true));
        meterStatus=label("STEREO PEAK / RMS METERS • NO CLIP",10,cyan,true);root.addView(meterStatus);
        dynamicsStatus=label("DYNAMICS: COMPRESSOR -18 dB / 3:1 • GATE OFF",10,white,true);root.addView(dynamicsStatus);
        thresholdBar=dynamicsSlider(root,"COMPRESSOR THRESHOLD",-36,0,-18," dB",0);
        ratioBar=dynamicsSlider(root,"COMPRESSOR RATIO",1,10,3," : 1",1);
        attackBar=dynamicsSlider(root,"ATTACK",1,100,10," ms",2);
        releaseBar=dynamicsSlider(root,"RELEASE",20,500,120," ms",3);
        makeupBar=dynamicsSlider(root,"MAKEUP GAIN",0,12,0," dB",4);
        gateBar=dynamicsSlider(root,"NOISE GATE THRESHOLD (OFF AT -60)",-60,-20,-60," dB",5);
        root.addView(button("RESET DYNAMICS",()->resetDynamics()));
        status=label("READY // ORIGINAL FILES ARE NEVER OVERWRITTEN",10,muted,true);
        status.setPadding(0,dp(12),0,0);root.addView(status);
    }

    private void pickAudio(){
        if(busy)return;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_AUDIO);
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        stopPreview();
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==PICK_AUDIO){
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            decode(uri);
        }else if(request==SAVE_WAV){renderWav(uri);}
    }

    private void decode(Uri uri){
        stopPreview();
        busy=true;status.setText("DECODING AUDIO // PLEASE WAIT");
        fileLabel.setText(displayName(uri));
        new Thread(()->{
            File output=new File(getCacheDir(),"jackstudio_"+System.currentTimeMillis()+".pcm");
            MediaExtractor extractor=new MediaExtractor();MediaCodec decoder=null;
            try{
                extractor.setDataSource(this,uri,null);
                int track=-1;MediaFormat format=null;
                for(int i=0;i<extractor.getTrackCount();i++){
                    MediaFormat f=extractor.getTrackFormat(i);
                    String mime=f.getString(MediaFormat.KEY_MIME);
                    if(mime!=null&&mime.startsWith("audio/")){track=i;format=f;break;}
                }
                if(track<0)throw new Exception("No audio stream found");
                sampleRate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                extractor.selectTrack(track);
                decoder=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
                decoder.configure(format,null,null,0);decoder.start();
                boolean inputDone=false,outputDone=false;
                MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
                try(FileOutputStream out=new FileOutputStream(output)){
                    while(!outputDone){
                        if(!inputDone){
                            int inIndex=decoder.dequeueInputBuffer(10000);
                            if(inIndex>=0){
                                ByteBuffer in=decoder.getInputBuffer(inIndex);
                                int size=extractor.readSampleData(in,0);
                                if(size<0){decoder.queueInputBuffer(inIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
                                else{long pts=extractor.getSampleTime();decoder.queueInputBuffer(inIndex,0,size,pts,0);extractor.advance();}
                            }
                        }
                        int outIndex=decoder.dequeueOutputBuffer(info,10000);
                        if(outIndex>=0){
                            ByteBuffer outBuffer=decoder.getOutputBuffer(outIndex);
                            if(outBuffer!=null&&info.size>0){
                                outBuffer.position(info.offset);outBuffer.limit(info.offset+info.size);
                                byte[] bytes=new byte[info.size];outBuffer.get(bytes);out.write(bytes);
                            }
                            decoder.releaseOutputBuffer(outIndex,false);
                            if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)outputDone=true;
                        }else if(outIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                            MediaFormat decoded=decoder.getOutputFormat();
                            if(decoded.containsKey(MediaFormat.KEY_SAMPLE_RATE))sampleRate=decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                            if(decoded.containsKey(MediaFormat.KEY_CHANNEL_COUNT))channels=decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                            if(android.os.Build.VERSION.SDK_INT>=24&&decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)){
                                int enc=decoded.getInteger(MediaFormat.KEY_PCM_ENCODING);
                                if(enc!=android.media.AudioFormat.ENCODING_PCM_16BIT)throw new Exception("This decoder returned a PCM format other than 16-bit");
                            }
                        }
                    }
                    out.flush();
                }
                long bytes=output.length();long frameBytes=channels*2L;
                if(frameBytes<=0||bytes<frameBytes)throw new Exception("Decoded audio is empty");
                totalFrames=bytes/frameBytes;pcmFile=output;
                runOnUiThread(()->{
                    busy=false;startSeek.setProgress(0);endSeek.setProgress(100);
                    wave.load(pcmFile,totalFrames,channels);
                    updateSelection();status.setText("DECODE COMPLETE // "+sampleRate+" Hz  •  "+channels+" CH");
                });
            }catch(Exception e){
                output.delete();runOnUiThread(()->{busy=false;status.setText("DECODE FAILED // "+e.getMessage());toast("Could not decode this audio file");});
            }finally{
                try{if(decoder!=null){decoder.stop();decoder.release();}}catch(Exception ignored){}
                try{extractor.release();}catch(Exception ignored){}
            }
        }).start();
    }

    private void addEqBand(LinearLayout root,int index,String title){
        TextView gainLabel=label(title+"  "+String.format(Locale.US,"%+.1f dB",eqGains[index]),10,white,true);root.addView(gainLabel);
        SeekBar gain=new SeekBar(this);gain.setMax(240);gain.setProgress(120);tint(gain);root.addView(gain);gainBars[index]=gain;
        gain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){eqGains[index]=(p-120)/10.0;gainLabel.setText(title+"  "+String.format(Locale.US,"%+.1f dB",eqGains[index]));if(user)updateEqStatus();}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        });
        TextView freqLabel=label("FREQUENCY  "+(int)eqFrequencies[index]+" Hz",9,muted,true);root.addView(freqLabel);
        SeekBar freq=new SeekBar(this);freq.setMax(1000);freq.setProgress(freqProgress(eqFrequencies[index]));tint(freq);root.addView(freq);frequencyBars[index]=freq;
        freq.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){eqFrequencies[index]=20*Math.pow(1000.0,p/1000.0);freqLabel.setText("FREQUENCY  "+(int)eqFrequencies[index]+" Hz");if(user)updateEqStatus();}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        });
        TextView qLabel=label("Q / BANDWIDTH  "+String.format(Locale.US,"%.2f",eqQ[index]),9,muted,true);root.addView(qLabel);
        SeekBar q=new SeekBar(this);q.setMax(100);q.setProgress((int)((eqQ[index]-0.3)/4.7*100));tint(q);root.addView(q);qBars[index]=q;
        q.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){eqQ[index]=0.3+p/100.0*4.7;qLabel.setText("Q / BANDWIDTH  "+String.format(Locale.US,"%.2f",eqQ[index]));if(user)updateEqStatus();}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        });
    }
    private void applyEqPreset(String name){
        double[] g={0,0,0,0,0},f={80,250,1000,4000,12000},q={1,1,1,1,1};
        boolean hp=false,lp=false;double hpHz=40,lpHz=18000;
        switch(name){
            case "WARM VOCALS": g=new double[]{1.5,1.5,0.5,-1.0,-1.5};f=new double[]{90,220,900,3200,10000};break;
            case "PRESENCE BOOST": g=new double[]{-1,0,1.5,3,1.5};f=new double[]{80,250,1800,4500,11000};break;
            case "PODCAST VOICE": g=new double[]{-3,1.5,2,2.5,-2};f=new double[]{100,180,900,3200,9000};hp=true;hpHz=75;lp=true;lpHz=14500;break;
            case "DEEP BASS": g=new double[]{5,3,0,-1.5,-1};f=new double[]{65,140,700,3500,10000};hp=true;hpHz=28;break;
            case "CLUB / EDM": g=new double[]{4,2,-1,2,3};f=new double[]{55,160,900,5000,12000};hp=true;hpHz=30;break;
            case "HIP-HOP": g=new double[]{4,2,0,1.5,1};f=new double[]{60,180,900,3800,10500};hp=true;hpHz=30;break;
            case "ROCK GUITARS": g=new double[]{2,1,0,2.5,1};f=new double[]{90,300,1000,2800,8500};break;
            case "ACOUSTIC": g=new double[]{1,1.5,1,1.5,2};f=new double[]{100,250,1200,4500,12000};break;
            case "JAZZ": g=new double[]{1.5,0.5,-0.5,1,1.5};f=new double[]{80,220,800,4000,11000};break;
            case "CLASSICAL": g=new double[]{0.5,0,-0.5,0.5,1};f=new double[]{70,250,1000,5000,13000};break;
            case "BRIGHT POP": g=new double[]{0,0.5,1,2.5,2};f=new double[]{80,250,1200,4500,11500};break;
            case "LO-FI": g=new double[]{2,1,-2,-3,-5};f=new double[]{100,300,1000,3500,8000};lp=true;lpHz=9000;break;
            case "AIR & DETAIL": g=new double[]{-1,-0.5,0.5,1.5,4};f=new double[]{80,250,1500,6000,14000};break;
            default: break;
        }
        System.arraycopy(g,0,eqGains,0,5);System.arraycopy(f,0,eqFrequencies,0,5);System.arraycopy(q,0,eqQ,0,5);
        hpfEnabled=hp;lpfEnabled=lp;hpfHz=hpHz;lpfHz=lpHz;
        for(int i=0;i<5;i++){gainBars[i].setProgress((int)Math.round(g[i]*10)+120);frequencyBars[i].setProgress(freqProgress(f[i]));qBars[i].setProgress(15);}
        if(hpfBar!=null)hpfBar.setProgress((int)hpHz-20);if(lpfBar!=null)lpfBar.setProgress((int)lpHz-2000);
        presetStatus.setText("PRESET: "+name);updateEqStatus();
    }

    private SeekBar dynamicsSlider(LinearLayout root,String title,int min,int max,int initial,String unit,int kind){
        TextView value=label(title+"  "+initial+unit,9,muted,true);root.addView(value);
        SeekBar seek=new SeekBar(this);seek.setMax(max-min);seek.setProgress(initial-min);tint(seek);root.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){
                int v=min+p; value.setText(title+"  "+v+unit);
                if(kind==0)compressorThreshold=v;
                else if(kind==1)compressorRatio=v;
                else if(kind==2)attackMs=v;
                else if(kind==3)releaseMs=v;
                else if(kind==4)makeupDb=v;
                else gateThresholdDb=v;
                updateDynamicsStatus();
            }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        return seek;
    }

    private DynamicsProcessor createDynamicsProcessor(){
        return new DynamicsProcessor(sampleRate,channels,compressorThreshold,compressorRatio,attackMs,releaseMs,makeupDb,gateThresholdDb);
    }

    private void updateMeterDisplay(String meter){
        if(meterStatus==null)return;
        meterStatus.setText(meter);
        boolean clipping=meter.contains("CLIP DETECTED");
        meterStatus.setTextColor(clipping?Color.rgb(255,80,80):cyan);
    }

    private void updateDynamicsStatus(){
        if(dynamicsStatus!=null)dynamicsStatus.setText(String.format(Locale.US,
            "DYNAMICS: COMP %.0f dB / %.1f:1 • GATE %s • MAKEUP +%.1f dB",
            compressorThreshold,compressorRatio,gateThresholdDb<=-60?"OFF":String.format(Locale.US,"%.0f dB",gateThresholdDb),makeupDb));
    }

    private void resetDynamics(){
        compressorThreshold=-18;compressorRatio=3;attackMs=10;releaseMs=120;makeupDb=0;gateThresholdDb=-60;
        thresholdBar.setProgress(18);ratioBar.setProgress(2);attackBar.setProgress(9);releaseBar.setProgress(100);makeupBar.setProgress(0);gateBar.setProgress(0);
        updateDynamicsStatus();if(meterStatus!=null)updateMeterDisplay("STEREO PEAK / RMS METERS • NO INPUT CLIP • LIMITER: 0");
    }

    private int freqProgress(double hz){return (int)Math.max(0,Math.min(1000,Math.log(hz/20.0)/Math.log(1000.0)*1000));}
    private void addCutoffSlider(LinearLayout root,String title,int min,int max,int initial,boolean highPass){
        TextView value=label(title+"  "+initial+" Hz",9,muted,true);root.addView(value);
        SeekBar seek=new SeekBar(this);seek.setMax(max-min);seek.setProgress(initial-min);tint(seek);root.addView(seek);
        if(highPass)hpfBar=seek;else lpfBar=seek;
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){int hz=min+p;if(highPass)hpfHz=hz;else lpfHz=hz;value.setText(title+"  "+hz+" Hz");if(user)updateEqStatus();}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        });
    }
    private void updateEqStatus(){
        if(eqStatus==null)return;double sum=0;for(double g:eqGains)sum+=Math.abs(g);
        eqStatus.setText("EQ: "+(sum<0.01?"FLAT":"CUSTOM")+" • HPF "+(hpfEnabled?"ON "+(int)hpfHz+"Hz":"OFF")+" • LPF "+(lpfEnabled?"ON "+(int)lpfHz+"Hz":"OFF"));
        if(eqCurve!=null)eqCurve.invalidate();
    }
    private void resetAdvancedEq(){
        for(int i=0;i<eqGains.length;i++){eqGains[i]=0;eqFrequencies[i]=new double[]{80,250,1000,4000,12000}[i];eqQ[i]=1;gainBars[i].setProgress(120);frequencyBars[i].setProgress(freqProgress(eqFrequencies[i]));qBars[i].setProgress(15);}
        hpfEnabled=false;lpfEnabled=false;hpfHz=40;lpfHz=18000;
        if(hpfBar!=null)hpfBar.setProgress(20);if(lpfBar!=null)lpfBar.setProgress(16000);
        if(presetStatus!=null)presetStatus.setText("PRESET: CUSTOM / FLAT");updateEqStatus();toast("Advanced EQ reset to flat");
    }
    private BiquadEqProcessor createEqProcessor(){
        return new BiquadEqProcessor(sampleRate,channels,eqGains,eqFrequencies,eqQ,hpfEnabled,hpfHz,lpfEnabled,lpfHz);
    }

    private SeekBar slider(LinearLayout root,String title,int initial){
        TextView t=label(title+"   "+initial+"%",10,white,true);root.addView(t);
        SeekBar b=new SeekBar(this);b.setMax(100);b.setProgress(initial);tint(b);root.addView(b);
        b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){t.setText(title+"   "+(title.contains("SEC")?String.format(Locale.US,"%.1f sec",p/10f):p+"%"));if(user&&totalFrames>0)updateSelection();}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });return b;
    }

    private void updateSelection(){
        if(totalFrames<=0)return;
        if(startSeek.getProgress()>=endSeek.getProgress()){
            if(startSeek.hasFocus())startSeek.setProgress(Math.max(0,endSeek.getProgress()-1));
            else endSeek.setProgress(Math.min(100,startSeek.getProgress()+1));
        }
        long first=totalFrames*startSeek.getProgress()/100;
        long last=totalFrames*endSeek.getProgress()/100;
        selectionLabel.setText(String.format(Locale.US,"IN %s     OUT %s     LENGTH %s",time(first/(double)sampleRate),time(last/(double)sampleRate),time((last-first)/(double)sampleRate)));
        wave.setSelection(startSeek.getProgress(),endSeek.getProgress());
    }

    private void playSelection(){
        if(busy||pcmFile==null||totalFrames<=0){toast("Import an audio file first");return;}
        stopPreview();
        final long first=totalFrames*startSeek.getProgress()/100;
        final long last=totalFrames*endSeek.getProgress()/100;
        if(last<=first){toast("Select a valid audio region");return;}
        previewStop=false;
        final BiquadEqProcessor previewEq=createEqProcessor();
        final DynamicsProcessor previewDynamics=createDynamicsProcessor();
        status.setText("PREVIEW PLAYING // EQ + DYNAMICS DSP");
        previewThread=new Thread(()->{
            AudioTrack track=null;
            try(java.io.RandomAccessFile input=new java.io.RandomAccessFile(pcmFile,"r")){
                int channelConfig=channels==1?AudioFormat.CHANNEL_OUT_MONO:AudioFormat.CHANNEL_OUT_STEREO;
                int min=AudioTrack.getMinBufferSize(sampleRate,channelConfig,AudioFormat.ENCODING_PCM_16BIT);
                if(min<=0)throw new Exception("Audio output format unavailable");
                int bufferSize=Math.max(min,32768);
                track=new AudioTrack(AudioManager.STREAM_MUSIC,sampleRate,channelConfig,
                        AudioFormat.ENCODING_PCM_16BIT,bufferSize,AudioTrack.MODE_STREAM);
                previewTrack=track;
                track.play();
                long remaining=(last-first)*channels*2L;
                input.seek(first*channels*2L);
                byte[] buffer=new byte[bufferSize];
                while(remaining>0&&!previewStop){
                    int count=input.read(buffer,0,(int)Math.min(buffer.length,remaining));
                    if(count<0)break;
                    previewEq.process(buffer,count);
                    previewDynamics.process(buffer,count);
                    if(meterStatus!=null){String meter=previewDynamics.getMeterText();runOnUiThread(()->updateMeterDisplay(meter));}
                    int written=0;
                    while(written<count&&!previewStop){
                        int n=track.write(buffer,written,count-written);
                        if(n<0)throw new Exception("Audio preview output failed");
                        written+=n;
                    }
                    remaining-=count;
                }
            }catch(Exception e){
                runOnUiThread(()->{status.setText("PREVIEW STOPPED // "+e.getMessage());});
            }finally{
                if(track!=null){try{track.pause();track.flush();track.stop();}catch(Exception ignored){}track.release();}
                if(previewTrack==track)previewTrack=null;
                runOnUiThread(()->{if(!busy)status.setText("PREVIEW COMPLETE // SOURCE AUDIO UNCHANGED");});
            }
        },"JackTunes-WavePreview");
        previewThread.start();
    }

    private void stopPreview(){
        previewStop=true;
        AudioTrack track=previewTrack;
        if(track!=null){try{track.pause();track.flush();}catch(Exception ignored){}}
    }

    private void saveWav(){
        if(busy||pcmFile==null||totalFrames<=0){toast("Import an audio file first");return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("audio/wav");i.addCategory(Intent.CATEGORY_OPENABLE);
        String base=fileLabel.getText().toString().replaceAll("\\.[^.]+$","");
        i.putExtra(Intent.EXTRA_TITLE,(base.isEmpty()?"JackTunes-edit":base)+"-edited.wav");
        startActivityForResult(i,SAVE_WAV);
    }

    private void renderWav(Uri destination){
        stopPreview();
        busy=true;status.setText("RENDERING WAV // DO NOT CLOSE");
        long first=totalFrames*startSeek.getProgress()/100;
        long last=totalFrames*endSeek.getProgress()/100;
        long fadeInFrames=(long)fadeInSeek.getProgress()*sampleRate/10;
        long fadeOutFrames=(long)fadeOutSeek.getProgress()*sampleRate/10;
        long frameBytes=channels*2L;
        final BiquadEqProcessor renderEq=createEqProcessor();
        final DynamicsProcessor renderDynamics=createDynamicsProcessor();
        new Thread(()->{
            try(InputStream in=new BufferedInputStream(new FileInputStream(pcmFile));
                OutputStream out=new BufferedOutputStream(getContentResolver().openOutputStream(destination))){
                if(out==null)throw new Exception("Could not open destination");
                long dataBytes=(last-first)*frameBytes;
                writeWavHeader(out,dataBytes,sampleRate,channels);
                long skip=first*frameBytes;while(skip>0){long n=in.skip(skip);if(n<=0)throw new Exception("Could not seek decoded audio");skip-=n;}
                byte[] block=new byte[32768];long remaining=dataBytes,frame=0;
                while(remaining>0){
                    int count=in.read(block,0,(int)Math.min(block.length,remaining));if(count<0)break;
                    renderEq.process(block,count);
                    renderDynamics.process(block,count);
                    if(meterStatus!=null){String meter=renderDynamics.getMeterText();runOnUiThread(()->updateMeterDisplay(meter));}
                    for(int pos=0;pos+1<count;pos+=2){
                        long f=frame;
                        double gain=1.0;
                        if(fadeInFrames>0&&f<fadeInFrames)gain=Math.min(gain,f/(double)fadeInFrames);
                        long fromEnd=(last-first)-f;
                        if(fadeOutFrames>0&&fromEnd<=fadeOutFrames)gain=Math.min(gain,Math.max(0,fromEnd/(double)fadeOutFrames));
                        short sample=(short)((block[pos]&255)|((block[pos+1]&255)<<8));
                        int adjusted=(int)Math.round(sample*gain);
                        if(adjusted>32767)adjusted=32767;if(adjusted< -32768)adjusted=-32768;
                        block[pos]=(byte)(adjusted&255);block[pos+1]=(byte)((adjusted>>8)&255);
                        if((pos+2)%(channels*2)==0)frame++;
                    }
                    out.write(block,0,count);remaining-=count;
                }
                out.flush();
                runOnUiThread(()->{busy=false;status.setText("RENDER COMPLETE // NEW WAV FILE CREATED");toast("Edited WAV exported");});
            }catch(Exception e){runOnUiThread(()->{busy=false;status.setText("EXPORT FAILED // "+e.getMessage());toast("Could not export WAV");});}
        }).start();
    }

    private void writeWavHeader(OutputStream out,long dataBytes,int rate,int ch)throws Exception{
        long byteRate=(long)rate*ch*2;
        out.write(new byte[]{'R','I','F','F'});write32(out,dataBytes+36);
        out.write(new byte[]{'W','A','V','E','f','m','t',' '});write32(out,16);write16(out,1);write16(out,ch);
        write32(out,rate);write32(out,byteRate);write16(out,ch*2);write16(out,16);
        out.write(new byte[]{'d','a','t','a'});write32(out,dataBytes);
    }
    private void write16(OutputStream o,int v)throws Exception{o.write(v&255);o.write((v>>8)&255);}
    private void write32(OutputStream o,long v)throws Exception{o.write((int)v&255);o.write((int)(v>>8)&255);o.write((int)(v>>16)&255);o.write((int)(v>>24)&255);}
    private String displayName(Uri u){
        String name="Audio file";try(Cursor c=getContentResolver().query(u,null,null,null,null)){
            if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)name=c.getString(i);}
        }catch(Exception ignored){}return name;
    }
    private String time(double sec){int n=(int)Math.max(0,sec);return String.format(Locale.US,"%d:%02d",n/60,n%60);}
    private TextView sectionLabel(String s){TextView t=label(s,11,cyan,true);t.setPadding(0,dp(14),0,dp(5));return t;}
    private TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(android.graphics.Typeface.MONOSPACE,bold?1:0);return t;}
    private Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextColor(white);b.setTextSize(10);b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(8,55,72)));b.setOnClickListener(v->{if(!busy)r.run();});return b;}
    private void tint(SeekBar b){b.setProgressTintList(android.content.res.ColorStateList.valueOf(cyan));b.setThumbTintList(android.content.res.ColorStateList.valueOf(white));}
    private LinearLayout section(String s){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(9),dp(8),dp(9),dp(8));android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(panel);d.setStroke(dp(1),edge);d.setCornerRadius(dp(5));b.setBackground(d);return b;}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onPause(){stopPreview();super.onPause();}
    @Override protected void onDestroy(){stopPreview();if(pcmFile!=null&&pcmFile.exists())pcmFile.delete();super.onDestroy();}

    private class EqCurveView extends View{
        private final Paint p=new Paint(3);
        EqCurveView(){super(WaveEditorActivity.this);setBackgroundColor(Color.rgb(1,5,10));}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight(),left=dp(30),right=w-dp(8),top=dp(8),bottom=h-dp(22);
            p.setStrokeWidth(1);p.setColor(Color.rgb(30,65,78));
            for(int i=0;i<=4;i++){float y=top+(bottom-top)*i/4f;c.drawLine(left,y,right,y,p);}
            for(int i=0;i<=5;i++){float x=left+(right-left)*i/5f;c.drawLine(x,top,x,bottom,p);}
            p.setColor(muted);p.setTextSize(dp(8));c.drawText("+12",dp(2),top+dp(4),p);c.drawText("0",dp(12),(top+bottom)/2,p);c.drawText("-12",dp(2),bottom,p);
            p.setColor(cyan);p.setStrokeWidth(dp(2));
            float prevX=left,prevY=(top+bottom)/2;
            for(int i=0;i<=160;i++){
                double hz=20*Math.pow(1000.0,i/160.0);
                double db=0;
                for(int b=0;b<5;b++){double z=Math.log(hz/eqFrequencies[b])/(1.0/Math.max(0.3,eqQ[b]));db+=eqGains[b]*Math.exp(-0.5*z*z);}
                if(hpfEnabled)db-=Math.max(0,Math.log10(hpfHz/hz)*24);
                if(lpfEnabled)db-=Math.max(0,Math.log10(hz/lpfHz)*24);
                db=Math.max(-12,Math.min(12,db));
                float x=left+(right-left)*i/160f;
                float y=bottom-(float)((db+12)/24.0)*(bottom-top);
                if(i>0)c.drawLine(prevX,prevY,x,y,p);prevX=x;prevY=y;
            }
            p.setColor(muted);p.setStrokeWidth(1);p.setTextSize(dp(8));
            c.drawText("20 Hz",left,bottom+dp(15),p);c.drawText("1 kHz",(left+right)/2-dp(10),bottom+dp(15),p);c.drawText("20 kHz",right-dp(35),bottom+dp(15),p);
        }
    }

    private class WaveView extends View{
        private final Paint p=new Paint(3);private float[] peaks=new float[0];private int from=0,to=100;
        WaveView(){super(WaveEditorActivity.this);setBackgroundColor(Color.rgb(1,5,10));}
        void load(File file,long frames,int ch){
            try{
                int count=240;peaks=new float[count];long frameBytes=ch*2L;
                try(RandomAccessFileCompat raf=new RandomAccessFileCompat(file)){
                    for(int i=0;i<count;i++){
                        long a=frames*i/count,b=frames*(i+1)/count;float max=0;
                        long stride=Math.max(1,(b-a)/180);
                        for(long f=a;f<b;f+=stride){
                            raf.seek(f*frameBytes);
                            for(int c=0;c<ch;c++){int lo=raf.read(),hi=raf.read();if(lo<0||hi<0)break;short v=(short)(lo|(hi<<8));max=Math.max(max,Math.abs(v/32768f));}
                        }
                        peaks[i]=max;
                    }
                }
                postInvalidate();
            }catch(Exception ignored){}
        }
        void setSelection(int a,int b){from=a;to=b;postInvalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight(),mid=h/2;
            p.setColor(Color.rgb(12,45,57));p.setStrokeWidth(1);c.drawLine(0,mid,w,mid,p);
            if(peaks.length==0){p.setColor(muted);p.setTextSize(dp(11));c.drawText("IMPORT AUDIO TO ANALYZE WAVEFORM",dp(12),mid,p);return;}
            float col=w/peaks.length;
            for(int i=0;i<peaks.length;i++){
                float x=i*col;float amp=Math.max(dp(2),peaks[i]*(h*.43f));
                p.setColor(i>=peaks.length*from/100&&i<peaks.length*to/100?cyan:Color.rgb(42,88,99));
                p.setStrokeWidth(Math.max(1,col*.62f));c.drawLine(x+col/2,mid-amp,x+col/2,mid+amp,p);
            }
            p.setColor(white);p.setStrokeWidth(dp(1));c.drawLine(w*from/100,0,w*from/100,h,p);c.drawLine(w*to/100,0,w*to/100,h,p);
        }
    }
    private static class RandomAccessFileCompat implements AutoCloseable{
        private final java.io.RandomAccessFile f;RandomAccessFileCompat(File file)throws Exception{f=new java.io.RandomAccessFile(file,"r");}
        void seek(long p)throws Exception{f.seek(p);}int read()throws Exception{return f.read();}public void close()throws Exception{f.close();}
    }
}
