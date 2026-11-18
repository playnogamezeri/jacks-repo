package com.jackgray.winamp;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public class StudioActivity extends Activity {
    private final int bg=Color.rgb(3,8,15), panel=Color.rgb(7,20,31), cyan=Color.rgb(0,235,220);
    private final int white=Color.rgb(220,250,255), muted=Color.rgb(105,160,175), edge=Color.rgb(0,190,210);
    private Equalizer eq;
    private BassBoost bass;
    private Virtualizer virtualizer;
    private PresetReverb reverb;
    private Visualizer visualizer;
    private AnalyzerView analyzer;
    private boolean reverbEnabled=false, bassEnabled=true, virtualizerEnabled=true;
    private int reverbIndex=0;
    private final short[] reverbPresets={PresetReverb.PRESET_NONE,PresetReverb.PRESET_SMALLROOM,
            PresetReverb.PRESET_MEDIUMROOM,PresetReverb.PRESET_LARGEROOM,
            PresetReverb.PRESET_MEDIUMHALL,PresetReverb.PRESET_LARGEHALL,
            PresetReverb.PRESET_PLATE};
    private final String[] reverbNames={"DRY","SMALL ROOM","MEDIUM ROOM","LARGE ROOM","MEDIUM HALL","LARGE HALL","PLATE"};
    private LinearLayout content;
    private boolean effectsReady=false, eqEnabled=true;
    private String eqPresetName="CUSTOM";
    private TextView eqPresetStatus, bassStatus, virtualizerStatus, reverbStatus, analyzerStatus;
    private int audioSessionId=-1;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        ScrollView scroll=new ScrollView(this);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12),dp(10),dp(12),dp(18)); content.setBackgroundColor(bg);
        scroll.addView(content); setContentView(scroll);
        header();
        int session=getIntent().getIntExtra("audio_session",-1);
        audioSessionId=session;
        if(session>0) initializeEffects(session);
        buildAnalyzer(session);
        if(!effectsReady) {
            TextView notice=label("PLAY A TRACK IN JACK TUNES FIRST. LIVE STUDIO EFFECTS ATTACH TO THE ACTIVE PLAYBACK SESSION.",12,muted,true);
            notice.setPadding(dp(10),dp(14),dp(10),dp(14)); content.addView(notice);
        }
        buildWaveEditor();
        buildEqualizer();
        buildDynamics();
        buildSpace();
        footer();
    }

    private void header(){
        TextView brand=label("CYBER // JACK TUNERS",22,cyan,true);
        brand.setGravity(Gravity.CENTER); brand.setPadding(0,dp(10),0,dp(5)); content.addView(brand);
        TextView sub=label("REAL-TIME AUDIO PROCESSING CONSOLE",10,muted,true);
        sub.setGravity(Gravity.CENTER); content.addView(sub);
        Button back=button("← BACK TO JACK TUNES",()->finish());
        content.addView(back);
        sectionNote("◈ SIGNAL CHAIN     ◈ LIVE DSP     ◈ SESSION LINKED");
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==404){
            if(grantResults.length>0&&grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)recreate();
            else toast("Live analyzer requires microphone permission");
        }
    }

    private void initializeEffects(int session){
        try{
            try { eq=new Equalizer(0,session); eqEnabled=eq.getEnabled(); } catch(Exception ignored) { eq=null; }
            try { bass=new BassBoost(0,session); bassEnabled=bass.getEnabled(); } catch(Exception ignored) { bass=null; }
            try { virtualizer=new Virtualizer(0,session); virtualizerEnabled=virtualizer.getEnabled(); } catch(Exception ignored) { virtualizer=null; }
            // PresetReverb is auxiliary: create it on output mix session 0 and attach it to MediaPlayer.
            try { reverb=new PresetReverb(0,0); reverb.setPreset(PresetReverb.PRESET_NONE); reverb.setEnabled(false); } catch(Exception ignored) { reverb=null; }
            effectsReady=(eq!=null||bass!=null||virtualizer!=null||reverb!=null);
        }catch(Exception e){
            releaseEffects();
            Toast.makeText(this,"Some audio effects are unavailable on this device",Toast.LENGTH_LONG).show();
        }
    }


    private void buildAnalyzer(int session){
        LinearLayout box=section("00 // LIVE SIGNAL ANALYZER");
        analyzer=new AnalyzerView();
        box.addView(analyzer,new LinearLayout.LayoutParams(-1,dp(105)));
        box.addView(label("LIVE FFT OUTPUT  •  PLAYBACK SPECTRUM",9,muted,true));
        analyzerStatus=label("ANALYZER: INITIALIZING",10,cyan,true); box.addView(analyzerStatus);
        if(session<=0){analyzerStatus.setText("ANALYZER: WAITING FOR PLAYBACK SESSION"); box.addView(label("PLAY A TRACK TO CONNECT THE ANALYZER.",10,muted,false));return;}
        if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            analyzerStatus.setText("ANALYZER: PERMISSION REQUIRED");
            box.addView(label("ANDROID REQUIRES MICROPHONE PERMISSION FOR PLAYBACK FFT CAPTURE.",10,muted,false));
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},404);
            return;
        }
        try {
            visualizer=new Visualizer(session);
            int[] range=Visualizer.getCaptureSizeRange();
            visualizer.setCaptureSize(range[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener(){
                public void onWaveFormDataCapture(Visualizer v,byte[] data,int rate){}
                public void onFftDataCapture(Visualizer v,byte[] data,int rate){if(analyzer!=null)analyzer.setFft(data);}
            },Math.max(Visualizer.getMaxCaptureRate()/2,1),false,true);
            visualizer.setEnabled(true);
            analyzerStatus.setText("ANALYZER: LIVE • SESSION "+session);
            box.addView(button("ANALYZER ON / OFF",()->{try{boolean next=!visualizer.getEnabled();visualizer.setEnabled(next);analyzerStatus.setText(next?"ANALYZER: LIVE • FFT CAPTURE":"ANALYZER: PAUSED");toast(next?"Analyzer running":"Analyzer paused");}catch(Exception e){analyzerStatus.setText("ANALYZER: UNAVAILABLE");toast("Analyzer unavailable");}}));
        } catch(Exception e) {
            if(visualizer!=null){try{visualizer.release();}catch(Exception ignored){}visualizer=null;}
            if(analyzerStatus!=null)analyzerStatus.setText("ANALYZER: NOT SUPPORTED");
            box.addView(label("LIVE CAPTURE NOT SUPPORTED BY THIS DEVICE / SESSION.",10,muted,false));
        }
    }

    private class AnalyzerView extends View {
        private final Paint paint=new Paint(3);
        private byte[] bins=new byte[0];
        AnalyzerView(){super(StudioActivity.this);setBackgroundColor(Color.rgb(1,5,10));}
        void setFft(byte[] data){bins=data.clone();postInvalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight();
            int n=Math.min(64,bins.length/2);
            if(n<=1){paint.setColor(muted);paint.setTextSize(dp(10));canvas.drawText("WAITING FOR AUDIO SIGNAL...",dp(8),h/2,paint);return;}
            float gap=dp(2),bar=(w-gap*(n+1))/n;
            for(int i=1;i<=n;i++){
                int re=bins[i*2],im=bins[i*2+1];
                double mag=Math.sqrt(re*re+im*im);
                float level=(float)Math.min(1.0,mag/90.0);
                float bh=Math.max(dp(2),level*(h-dp(8)));
                float x=gap+i*(bar+gap);
                paint.setColor(cyan);canvas.drawRect(x,h-bh,x+bar,h,paint);
            }
        }
    }

    private void buildWaveEditor(){
        LinearLayout box=section("01 // WAVEFORM EDITOR");
        box.addView(label("Import, select a region, apply edge fades and render a new WAV file.",11,muted,false));
        box.addView(button("OPEN WAVEFORM EDITOR",()->startActivity(new android.content.Intent(this,WaveEditorActivity.class))));
    }

    private void buildEqualizer(){
        LinearLayout box=section("02 // GRAPHIC EQUALIZER");
        if(eq==null){box.addView(label("EQUALIZER NOT AVAILABLE ON THIS DEVICE / SESSION.",11,muted,false));return;}
        LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.CENTER);
        actions.addView(button("FLAT / RESET",()->applyEqPreset("FLAT")));
        actions.addView(button(eqEnabled?"EQ: ON / TAP TO BYPASS":"EQ: BYPASSED / TAP TO ENABLE",()->{try{eqEnabled=!eqEnabled;eq.setEnabled(eqEnabled);renderBands();}catch(Exception e){toast("Equalizer control unavailable");}}));
        box.addView(actions);
        LinearLayout presetRow=new LinearLayout(this); presetRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] presets={"FLAT","BASS","BASS+","TREBLE","VOCAL","ROCK","POP","JAZZ","CLASSICAL","DANCE","HIP-HOP","ACOUSTIC","PODCAST"};
        for(String preset:presets) presetRow.addView(button(preset,()->applyEqPreset(preset)));
        HorizontalScrollView presetScroll=new HorizontalScrollView(this); presetScroll.setHorizontalScrollBarEnabled(false); presetScroll.addView(presetRow); box.addView(presetScroll);
        eqPresetStatus=label("EQ PROFILE: "+eqPresetName,10,cyan,true); box.addView(eqPresetStatus);
        renderBandsInto(box);
    }

    private void applyEqPreset(String name){
        if(eq==null)return;
        try{
            short[] range=eq.getBandLevelRange();
            double[][] profiles={
                {0,0,0,0,0,0,0,0,0,0},
                {5,5,4,2,0,0,0,0,0,0},
                {8,7,5,2,0,0,0,0,0,0},
                {0,0,0,0,0,1,2,3,4,4},
                {-2,-1,1,3,4,3,1,0,-1,-2},
                {4,3,1,-1,-2,0,2,3,4,4},
                {-1,1,3,3,1,-1,0,2,3,2},
                {3,2,0,-1,1,2,3,2,1,2},
                {3,2,0,1,-1,-1,0,2,3,3},
                {5,4,1,0,-1,0,2,3,4,3},
                {6,5,2,0,-1,0,2,3,4,4},
                {2,1,0,2,3,2,0,1,2,2},
                {-2,-1,1,3,4,3,1,0,-1,-2}
            };
            String[] names={"FLAT","BASS","BASS+","TREBLE","VOCAL","ROCK","POP","JAZZ","CLASSICAL","DANCE","HIP-HOP","ACOUSTIC","PODCAST"};
            int idx=0;while(idx<names.length&&!names[idx].equals(name))idx++;
            for(short b=0;b<eq.getNumberOfBands();b++){
                double hz=Math.max(20,eq.getCenterFreq(b)/1000.0);
                double position=(Math.log10(hz)-Math.log10(31.25))/(Math.log10(16000)-Math.log10(31.25))*9.0;
                int lo=(int)Math.floor(Math.max(0,Math.min(9,position)));int hi=Math.min(9,lo+1);double t=Math.max(0,Math.min(1,position-lo));
                double db=profiles[idx][lo]*(1-t)+profiles[idx][hi]*t;
                short level=(short)Math.max(range[0],Math.min(range[1],Math.round(db*100)));eq.setBandLevel(b,level);
            }
            eqPresetName=name;if(eqPresetStatus!=null)eqPresetStatus.setText("EQ PROFILE: "+name);renderBands();
        }catch(Exception e){toast("Could not apply "+name+" preset");}
    }

    private void renderBands(){
        // Rebuild the studio screen so the displayed band values match the selected preset.
        if(visualizer!=null){try{visualizer.setEnabled(false);visualizer.release();}catch(Exception ignored){}visualizer=null;}
        content.removeAllViews(); header(); buildAnalyzer(audioSessionId); buildWaveEditor(); buildEqualizer(); buildDynamics(); buildSpace(); footer();
    }

    private void renderBandsInto(LinearLayout box){
        try{
            short count=eq.getNumberOfBands(); short[] range=eq.getBandLevelRange();
            for(short i=0;i<count;i++){
                final short band=i;
                int hz=eq.getCenterFreq(i)/1000;
                String name=hz>=1000?String.format(Locale.US,"%.1f kHz",hz/1000f):hz+" Hz";
                LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(label(name,10,white,true),new LinearLayout.LayoutParams(dp(66),dp(38)));
                SeekBar slider=new SeekBar(this); slider.setMax(100);
                short level=eq.getBandLevel(i);
                slider.setProgress(Math.max(0,Math.min(100,(level-range[0])*100/(range[1]-range[0]))));
                tint(slider);
                TextView value=label(String.format(Locale.US,"%+.1f dB",level/100f),10,cyan,true);
                row.addView(slider,new LinearLayout.LayoutParams(0,dp(38),1));
                row.addView(value,new LinearLayout.LayoutParams(dp(72),dp(38)));
                slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    public void onProgressChanged(SeekBar b,int p,boolean user){
                        if(user)try{short v=(short)(range[0]+(range[1]-range[0])*p/100);eq.setBandLevel(band,v);value.setText(String.format(Locale.US,"%+.1f dB",v/100f));}catch(Exception ignored){}
                    }
                    public void onStartTrackingTouch(SeekBar b){}
                    public void onStopTrackingTouch(SeekBar b){}
                });
                box.addView(row);
            }
        }catch(Exception e){box.addView(label("Equalizer bands could not be loaded.",11,muted,false));}
    }

    private void buildDynamics(){
        LinearLayout box=section("03 // LOW-FREQUENCY CONTROL");
        bassStatus=label("BASS EFFECT: "+(bass!=null?(bassEnabled?"ACTIVE":"BYPASSED"):"UNAVAILABLE"),10,cyan,true);box.addView(bassStatus);
        if(bass==null){box.addView(label("BASS BOOST NOT SUPPORTED BY THIS DEVICE.",11,muted,false));return;}
        box.addView(button("TOGGLE BASS BOOST",()->{try{bassEnabled=!bassEnabled;bass.setEnabled(bassEnabled);bassStatus.setText("BASS EFFECT: "+(bassEnabled?"ACTIVE":"BYPASSED"));toast(bassEnabled?"Bass boost enabled":"Bass boost bypassed");}catch(Exception e){bassStatus.setText("BASS EFFECT: CONTROL ERROR");toast("Bass boost unavailable");}}));
        addStrengthSlider(box,"BASS INTENSITY",bass);
    }

    private void buildSpace(){
        LinearLayout box=section("04 // STEREO & AMBIENCE");
        virtualizerStatus=label("VIRTUALIZER: "+(virtualizer!=null?(virtualizerEnabled?"ACTIVE":"BYPASSED"):"UNAVAILABLE"),10,cyan,true);box.addView(virtualizerStatus);
        if(virtualizer!=null){
            box.addView(button("TOGGLE VIRTUALIZER",()->{try{virtualizerEnabled=!virtualizerEnabled;virtualizer.setEnabled(virtualizerEnabled);virtualizerStatus.setText("VIRTUALIZER: "+(virtualizerEnabled?"ACTIVE":"BYPASSED"));toast(virtualizerEnabled?"Stereo processing enabled":"Stereo processing bypassed");}catch(Exception e){virtualizerStatus.setText("VIRTUALIZER: CONTROL ERROR");toast("Virtualizer unavailable");}}));
            addStrengthSlider(box,"STEREO WIDTH",virtualizer);
        } else box.addView(label("VIRTUALIZER NOT SUPPORTED BY THIS DEVICE.",10,muted,false));
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        reverbStatus=label(reverb==null?"REVERB: NOT SUPPORTED":"REVERB: "+reverbNames[reverbIndex]+" • OFF",11,cyan,true);
        TextView preset=reverbStatus;
        row.addView(preset,new LinearLayout.LayoutParams(0,dp(42),1));
        row.addView(button("NEXT SPACE",()->{
            if(reverb==null){toast("Reverb unavailable");return;}
            reverbIndex=(reverbIndex+1)%reverbPresets.length;
            try{reverb.setPreset(reverbPresets[reverbIndex]);preset.setText("REVERB: "+reverbNames[reverbIndex]+" • "+(reverbEnabled?"ACTIVE":"OFF"));}
            catch(Exception e){toast("Reverb preset unavailable");}
        }));
        box.addView(row);
        if(reverb!=null)box.addView(button("TOGGLE REVERB",()->{
            try{
                if(!reverbEnabled){
                    if(!PlaybackService.attachAuxEffect(reverb.getId())){toast("Start playback to attach reverb");return;}
                    reverb.setEnabled(true);reverbEnabled=true;
                }else{
                    PlaybackService.detachAuxEffect();reverb.setEnabled(false);reverbEnabled=false;
                }
                reverbStatus.setText("REVERB: "+reverbNames[reverbIndex]+" • "+(reverbEnabled?"ACTIVE":"OFF"));
                reverbStatus.setText("REVERB: "+reverbNames[reverbIndex]+" • "+(reverbEnabled?"ACTIVE":"OFF"));
                toast(reverbEnabled?"Reverb enabled":"Reverb bypassed");
            }catch(Exception e){toast("Reverb routing unavailable");}
        }));
        else box.addView(label("AUXILIARY REVERB NOT SUPPORTED.",10,muted,false));
    }

    private interface ToggleAction{void set(boolean value);}
    private void toggle(boolean enabled,ToggleAction action){
        try{action.set(!enabled);toast(!enabled?"Effect enabled":"Effect bypassed");}
        catch(Exception e){toast("Effect control unavailable");}
    }

    private void addEffectToggle(LinearLayout box,String name,android.media.audiofx.AudioEffect effect){
        box.addView(button(name+"  •  ON/OFF",()->toggle(effect.getEnabled(),v->effect.setEnabled(v))));
    }

    private void addStrengthSlider(LinearLayout box,String name,Object effect){
        SeekBar seek=new SeekBar(this); seek.setMax(1000); tint(seek);
        boolean supported=true;
        try{supported=effect instanceof BassBoost?((BassBoost)effect).getStrengthSupported():((Virtualizer)effect).getStrengthSupported();}catch(Exception ignored){}
        if(!supported){box.addView(label(name+": STRENGTH CONTROL NOT SUPPORTED BY DEVICE",9,muted,false));return;}
        try{
            short initial=effect instanceof BassBoost?((BassBoost)effect).getRoundedStrength():((Virtualizer)effect).getRoundedStrength();
            seek.setProgress(initial);
        }catch(Exception ignored){}
        TextView value=label(name+"   "+seek.getProgress()+" / 1000",10,muted,true);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){
                if(user)try{
                    if(effect instanceof BassBoost)((BassBoost)effect).setStrength((short)p);
                    else ((Virtualizer)effect).setStrength((short)p);
                    value.setText(name+"   "+p+" / 1000");
                }catch(Exception e){toast("Strength control unavailable");}
            }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        box.addView(value); box.addView(seek);
    }

    private LinearLayout section(String title){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(9),dp(8),dp(9),dp(10));
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(panel); shape.setStroke(dp(1),edge); shape.setCornerRadius(dp(1)); box.setBackground(shape);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(10); box.setLayoutParams(lp);
        box.addView(label(title,13,white,true)); content.addView(box); return box;
    }
    private void sectionNote(String s){TextView t=label(s,10,muted,true);t.setPadding(dp(3),dp(10),0,dp(2));content.addView(t);}
    private TextView label(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.MONOSPACE,bold?Typeface.BOLD:Typeface.NORMAL);return t;
    }
    private Button button(String s,Runnable r){
        Button b=new Button(this);b.setText(s);b.setTextColor(white);b.setTextSize(10);b.setAllCaps(false);
        b.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(Color.rgb(3,35,49));shape.setStroke(dp(1),cyan);shape.setCornerRadius(dp(1));
        b.setBackground(shape);b.setPadding(dp(8),dp(5),dp(8),dp(5));
        b.setOnClickListener(v->r.run());return b;
    }
    private void tint(SeekBar b){b.setProgressTintList(android.content.res.ColorStateList.valueOf(cyan));b.setThumbTintList(android.content.res.ColorStateList.valueOf(white));}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void footer(){TextView t=label("DJ TUNE JACK AUDIO ENGINE  //  LIVE FX  //  DEVICE SUPPORT VARIES",9,muted,true);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(16),0,dp(6));content.addView(t);}
    private void releaseEffects(){
        if(visualizer!=null){try{visualizer.setEnabled(false);visualizer.release();}catch(Exception ignored){}visualizer=null;}
        if(reverbEnabled)PlaybackService.detachAuxEffect();
        if(eq!=null){eq.release();eq=null;} if(bass!=null){bass.release();bass=null;}
        if(virtualizer!=null){virtualizer.release();virtualizer=null;}
        if(reverb!=null){reverb.release();reverb=null;} effectsReady=false;
    }
    @Override protected void onDestroy(){releaseEffects();super.onDestroy();}
}
