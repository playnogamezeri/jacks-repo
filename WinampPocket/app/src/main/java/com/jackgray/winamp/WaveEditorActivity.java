package com.jackgray.winamp;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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
    private File pcmFile;
    private int sampleRate=44100,channels=2;
    private long totalFrames=0;
    private boolean busy=false;
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
        root.addView(sectionLabel("EDIT REGION"));
        startSeek=slider(root,"START POINT",0);
        endSeek=slider(root,"END POINT",100);
        selectionLabel=label("SELECT A FILE TO BEGIN",11,cyan,true);root.addView(selectionLabel);
        root.addView(sectionLabel("EDGE FADES"));
        fadeInSeek=slider(root,"FADE IN (0–10 SEC)",0);
        fadeOutSeek=slider(root,"FADE OUT (0–10 SEC)",0);
        root.addView(button("RENDER SELECTION AS WAV",()->saveWav()));
        status=label("READY // ORIGINAL FILES ARE NEVER OVERWRITTEN",10,muted,true);
        status.setPadding(0,dp(12),0,0);root.addView(status);
    }

    private void pickAudio(){
        if(busy)return;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_AUDIO);
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==PICK_AUDIO){
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            decode(uri);
        }else if(request==SAVE_WAV){renderWav(uri);}
    }

    private void decode(Uri uri){
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

    private SeekBar slider(LinearLayout root,String title,int initial){
        TextView t=label(title+"   "+initial+"%",10,white,true);root.addView(t);
        SeekBar b=new SeekBar(this);b.setMax(100);b.setProgress(initial);tint(b);root.addView(b);
        b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){t.setText(title+"   "+p+(title.contains("SEC")?"%":"%"));if(user&&totalFrames>0)updateSelection();}
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

    private void saveWav(){
        if(busy||pcmFile==null||totalFrames<=0){toast("Import an audio file first");return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("audio/wav");i.addCategory(Intent.CATEGORY_OPENABLE);
        String base=fileLabel.getText().toString().replaceAll("\\.[^.]+$","");
        i.putExtra(Intent.EXTRA_TITLE,(base.isEmpty()?"JackTunes-edit":base)+"-edited.wav");
        startActivityForResult(i,SAVE_WAV);
    }

    private void renderWav(Uri destination){
        busy=true;status.setText("RENDERING WAV // DO NOT CLOSE");
        long first=totalFrames*startSeek.getProgress()/100;
        long last=totalFrames*endSeek.getProgress()/100;
        long fadeInFrames=(long)fadeInSeek.getProgress()*sampleRate/10;
        long fadeOutFrames=(long)fadeOutSeek.getProgress()*sampleRate/10;
        long frameBytes=channels*2L;
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
