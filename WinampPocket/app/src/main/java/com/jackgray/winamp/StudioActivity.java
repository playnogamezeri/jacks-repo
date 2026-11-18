package com.jackgray.winamp;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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
    private int reverbIndex=0;
    private final short[] reverbPresets={PresetReverb.PRESET_NONE,PresetReverb.PRESET_SMALLROOM,
            PresetReverb.PRESET_MEDIUMROOM,PresetReverb.PRESET_LARGEROOM,
            PresetReverb.PRESET_MEDIUMHALL,PresetReverb.PRESET_LARGEHALL,
            PresetReverb.PRESET_PLATE};
    private final String[] reverbNames={"DRY","SMALL ROOM","MEDIUM ROOM","LARGE ROOM","MEDIUM HALL","LARGE HALL","PLATE"};
    private LinearLayout content;
    private boolean effectsReady=false, eqEnabled=true;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        ScrollView scroll=new ScrollView(this);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12),dp(10),dp(12),dp(18)); content.setBackgroundColor(bg);
        scroll.addView(content); setContentView(scroll);
        header();
        int session=getIntent().getIntExtra("audio_session",-1);
        if(session>0) initializeEffects(session);
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
        TextView brand=label("JACK TUNERS // STUDIO",20,cyan,true);
        brand.setGravity(Gravity.CENTER); brand.setPadding(0,dp(10),0,dp(5)); content.addView(brand);
        TextView sub=label("REAL-TIME AUDIO PROCESSING CONSOLE",10,muted,true);
        sub.setGravity(Gravity.CENTER); content.addView(sub);
        Button back=button("← BACK TO JACK TUNES",()->finish());
        content.addView(back);
        sectionNote("SIGNAL CHAIN  •  PLAYBACK SESSION");
    }

    private void initializeEffects(int session){
        try{
            eq=new Equalizer(0,session); eq.setEnabled(true);
            bass=new BassBoost(0,session); bass.setEnabled(true);
            virtualizer=new Virtualizer(0,session); virtualizer.setEnabled(true);
            reverb=new PresetReverb(0,session); reverb.setPreset(PresetReverb.PRESET_NONE); reverb.setEnabled(true);
            effectsReady=true;
        }catch(Exception e){
            releaseEffects();
            Toast.makeText(this,"Some audio effects are unavailable on this device",Toast.LENGTH_LONG).show();
        }
    }


    private void buildWaveEditor(){
        LinearLayout box=section("01 // WAVEFORM EDITOR");
        box.addView(label("Import, select a region, apply edge fades and render a new WAV file.",11,muted,false));
        box.addView(button("OPEN WAVEFORM EDITOR",()->startActivity(new android.content.Intent(this,WaveEditorActivity.class))));
    }

    private void buildEqualizer(){
        LinearLayout box=section("02 // GRAPHIC EQUALIZER");
        if(!effectsReady){box.addView(label("Unavailable until a track is playing.",11,muted,false));return;}
        LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.CENTER);
        actions.addView(button("FLAT",()->{try{eq.usePreset((short)0);renderBands();}catch(Exception e){toast("Preset unavailable");}}));
        actions.addView(button("ENABLE / BYPASS",()->{eqEnabled=!eqEnabled;eq.setEnabled(eqEnabled);toast(eqEnabled?"Equalizer enabled":"Equalizer bypassed");}));
        box.addView(actions);
        renderBandsInto(box);
    }

    private void renderBands(){
        // Rebuild the studio screen so the displayed band values match the selected preset.
        content.removeAllViews(); header(); buildWaveEditor(); buildEqualizer(); buildDynamics(); buildSpace(); footer();
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
        if(!effectsReady){box.addView(label("Bass processing unavailable.",11,muted,false));return;}
        addEffectToggle(box,"BASS BOOST",bass);
        addStrengthSlider(box,"BASS INTENSITY",bass);
    }

    private void buildSpace(){
        LinearLayout box=section("04 // STEREO & AMBIENCE");
        if(!effectsReady){box.addView(label("Spatial effects unavailable.",11,muted,false));return;}
        addEffectToggle(box,"STEREO VIRTUALIZER",virtualizer);
        addStrengthSlider(box,"STEREO WIDTH",virtualizer);
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView preset=label("REVERB: "+reverbNames[reverbIndex],11,cyan,true);
        row.addView(preset,new LinearLayout.LayoutParams(0,dp(42),1));
        row.addView(button("NEXT SPACE",()->{
            reverbIndex=(reverbIndex+1)%reverbPresets.length;
            try{reverb.setPreset(reverbPresets[reverbIndex]);preset.setText("REVERB: "+reverbNames[reverbIndex]);}
            catch(Exception e){toast("Reverb preset unavailable");}
        }));
        box.addView(row);
        box.addView(button("REVERB ON / OFF",()->toggle(reverb.getEnabled(),v->reverb.setEnabled(v))));
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
        shape.setColor(panel); shape.setStroke(dp(1),edge); shape.setCornerRadius(dp(5)); box.setBackground(shape);
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
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(8,55,72)));
        b.setOnClickListener(v->r.run());return b;
    }
    private void tint(SeekBar b){b.setProgressTintList(android.content.res.ColorStateList.valueOf(cyan));b.setThumbTintList(android.content.res.ColorStateList.valueOf(white));}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void footer(){TextView t=label("JACK TUNES AUDIO ENGINE  //  LIVE FX",9,muted,true);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(16),0,dp(6));content.addView(t);}
    private void releaseEffects(){
        if(eq!=null){eq.release();eq=null;} if(bass!=null){bass.release();bass=null;}
        if(virtualizer!=null){virtualizer.release();virtualizer=null;}
        if(reverb!=null){reverb.release();reverb=null;} effectsReady=false;
    }
    @Override protected void onDestroy(){releaseEffects();super.onDestroy();}
}
