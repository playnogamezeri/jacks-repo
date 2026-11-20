package com.jackgray.winamp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline, per-track lyrics with optional LRC timestamp synchronization. */
public class LyricsActivity extends Activity {
    private static final int REQUEST_IMPORT_LRC=7101, REQUEST_EXPORT_LRC=7102;
    private final int bg=Color.rgb(3,8,15), panel=Color.rgb(7,20,31);
    private final int green=Color.rgb(0,235,220), white=Color.rgb(220,250,255), muted=Color.rgb(105,160,175);
    private final Handler handler=new Handler();
    private final ArrayList<LyricLine> lines=new ArrayList<>();
    private LinearLayout lyricList;
    private ScrollView scroll;
    private TextView trackLabel, syncLabel;
    private String trackKey="", trackTitle="Current track";
    private int activeIndex=-1;
    private boolean autoFollow=true;

    private static class LyricLine {
        final long timeMs;
        final String text;
        TextView view;
        LyricLine(long timeMs,String text){this.timeMs=timeMs;this.text=text;}
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        trackKey=getIntent().getStringExtra("track_uri");
        trackTitle=getIntent().getStringExtra("track_title");
        if(trackKey==null)trackKey="";
        if(trackTitle==null||trackTitle.trim().isEmpty())trackTitle="Current track";
        buildUi();
        renderLyrics(loadLyrics());
    }

    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String value,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);
        v.setTypeface(bold?Typeface.MONOSPACE:Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);return v;
    }
    private Button button(String value,Runnable action){
        Button b=new Button(this);b.setText(value);b.setTextSize(11);b.setAllCaps(false);b.setTextColor(white);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(8,55,72)));
        b.setOnClickListener(v->action.run());return b;
    }
    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(10));root.setBackgroundColor(bg);setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("‹ BACK",()->finish());top.addView(back);
        trackLabel=label(trackTitle,14,white,true);trackLabel.setGravity(Gravity.CENTER);top.addView(trackLabel,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(top);
        TextView heading=label("TRACK LYRICS",12,green,true);heading.setGravity(Gravity.CENTER);heading.setPadding(0,dp(15),0,dp(4));root.addView(heading);
        syncLabel=label("OFFLINE LYRICS  •  ADD LRC TIMESTAMPS TO SYNC",10,muted,true);syncLabel.setGravity(Gravity.CENTER);root.addView(syncLabel);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.BLACK);
        lyricList=new LinearLayout(this);lyricList.setOrientation(LinearLayout.VERTICAL);lyricList.setPadding(dp(8),dp(22),dp(8),dp(22));
        scroll.addView(lyricList);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(12);sp.bottomMargin=dp(10);root.addView(scroll,sp);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);
        actions.addView(button("IMPORT LRC",()->importLrc()),new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(button("EDIT",()->editLyrics()),new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(button("EXPORT",()->exportLrc()),new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(button("FOLLOW: ON",()->{autoFollow=!autoFollow;((Button)actions.getChildAt(3)).setText("FOLLOW: "+(autoFollow?"ON":"OFF"));}),new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(actions);
        TextView hint=label("Plain text works offline. For line-by-line highlighting, use LRC timestamps such as [00:12.50] before each line.",10,muted,false);
        hint.setGravity(Gravity.CENTER);hint.setPadding(dp(6),dp(8),dp(6),0);root.addView(hint);
    }

    private String prefKey(){return "lyrics_"+Integer.toHexString(trackKey.hashCode());}
    private String loadLyrics(){return getSharedPreferences("track_lyrics",MODE_PRIVATE).getString(prefKey(),"");}
    private void saveLyrics(String value){getSharedPreferences("track_lyrics",MODE_PRIVATE).edit().putString(prefKey(),value).apply();}


    private void importLrc(){
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","application/octet-stream"});
        startActivityForResult(pick,REQUEST_IMPORT_LRC);
    }

    private void exportLrc(){
        if(loadLyrics().trim().isEmpty()){
            Toast.makeText(this,"There are no lyrics to export",Toast.LENGTH_SHORT).show();
            return;
        }
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("text/plain");
        String safe=trackTitle.replaceAll("[^A-Za-z0-9 _.-]","_").trim();
        if(safe.isEmpty())safe="lyrics";
        save.putExtra(Intent.EXTRA_TITLE,safe+".lrc");
        startActivityForResult(save,REQUEST_EXPORT_LRC);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            if(requestCode==REQUEST_IMPORT_LRC){
                StringBuilder text=new StringBuilder();
                try(InputStream in=getContentResolver().openInputStream(data.getData());
                    BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
                    String row;int total=0;
                    while((row=reader.readLine())!=null){
                        total+=row.length();
                        if(total>500000)throw new java.io.IOException("Lyrics file is too large");
                        text.append(row).append('\n');
                    }
                }
                String imported=text.toString().trim();
                if(imported.isEmpty())throw new java.io.IOException("The selected file is empty");
                saveLyrics(imported);renderLyrics(imported);
                Toast.makeText(this,"Lyrics imported and saved offline",Toast.LENGTH_SHORT).show();
            }else if(requestCode==REQUEST_EXPORT_LRC){
                try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){
                    if(out==null)throw new java.io.IOException("Could not open destination");
                    out.write(loadLyrics().getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this,"Lyrics exported",Toast.LENGTH_SHORT).show();
            }
        }catch(Exception e){
            Toast.makeText(this,"Lyrics file error: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private void editLyrics(){
        EditText input=new EditText(this);input.setText(loadLyrics());input.setGravity(Gravity.TOP|Gravity.START);
        input.setMinLines(10);input.setTextSize(16);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("Paste or type lyrics here.\n\nFor synced lyrics, add timestamps:\n[00:12.50] First line\n[00:17.20] Second line");
        ScrollView wrapper=new ScrollView(this);wrapper.setPadding(dp(16),dp(8),dp(16),dp(8));wrapper.addView(input);
        new AlertDialog.Builder(this).setTitle("Edit track lyrics").setView(wrapper)
            .setNegativeButton("CANCEL",null).setNeutralButton("CLEAR",(d,w)->{saveLyrics("");renderLyrics("");})
            .setPositiveButton("SAVE",(d,w)->{String value=input.getText().toString();saveLyrics(value);renderLyrics(value);Toast.makeText(this,"Lyrics saved offline for this track",Toast.LENGTH_SHORT).show();}).show();
    }

    private ArrayList<LyricLine> parse(String raw){
        ArrayList<LyricLine> parsed=new ArrayList<>();
        Pattern stamp=Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
        for(String row:raw.split("\\r?\\n")){
            Matcher m=stamp.matcher(row);ArrayList<Long> times=new ArrayList<>();int end=0;
            while(m.find()){
                long min=Long.parseLong(m.group(1)),sec=Long.parseLong(m.group(2));
                String fraction=m.group(3);long ms=fraction==null?0:Long.parseLong(fraction.length()==1?fraction+"00":fraction.length()==2?fraction+"0":fraction);
                times.add((min*60+sec)*1000+ms);end=m.end();
            }
            String text=row.substring(end).trim();
            if(times.isEmpty()){if(!text.isEmpty())parsed.add(new LyricLine(-1,text));}
            else for(Long time:times)parsed.add(new LyricLine(time,text));
        }
        Collections.sort(parsed,new Comparator<LyricLine>(){public int compare(LyricLine a,LyricLine b){return Long.compare(a.timeMs,b.timeMs);}});
        return parsed;
    }

    private void renderLyrics(String raw){
        lyricList.removeAllViews();lines.clear();lines.addAll(parse(raw));activeIndex=-1;
        if(raw.trim().isEmpty()){
            TextView empty=label("NO LYRICS SAVED",18,muted,true);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(12),dp(60),dp(12),dp(60));lyricList.addView(empty);
            syncLabel.setText("NO LYRICS  •  TAP EDIT LYRICS TO ADD THEM");return;
        }
        boolean synced=!lines.isEmpty()&&lines.get(0).timeMs>=0;
        syncLabel.setText(synced?"TIMED LYRICS  •  HIGHLIGHT FOLLOWS PLAYBACK":"PLAIN LYRICS  •  MANUAL SCROLL");
        for(int i=0;i<lines.size();i++){
            LyricLine line=lines.get(i);TextView v=label(line.text.isEmpty()?" ":" "+line.text+" ",20,muted,false);
            v.setGravity(Gravity.CENTER);v.setPadding(dp(7),dp(10),dp(7),dp(10));v.setLineSpacing(dp(5),1f);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(5);lyricList.addView(v,lp);line.view=v;
        }
        if(!synced)syncLabel.setText("PLAIN LYRICS  •  TIMESTAMPS CAN BE ADDED IN EDIT");
    }

    private final Runnable ticker=new Runnable(){@Override public void run(){
        PcmPlaybackEngine player=PlaybackService.getPlayer();
        if(player!=null&&PlaybackService.isPrepared()&&!lines.isEmpty()&&lines.get(0).timeMs>=0){
            try{long pos=player.getCurrentPosition();int active=-1;
                for(int i=0;i<lines.size();i++){if(lines.get(i).timeMs<=pos)active=i;else break;}
                if(active>=0&&active!=activeIndex){activeIndex=active;for(int i=0;i<lines.size();i++){
                    TextView v=lines.get(i).view;if(v==null)continue;
                    boolean selected=i==active;v.setTextColor(selected?green:(i<active?Color.rgb(65,105,110):muted));
                    v.setTypeface(selected?Typeface.DEFAULT_BOLD:Typeface.DEFAULT,selected?Typeface.BOLD:Typeface.NORMAL);
                    v.setTextSize(selected?24:20);
                    if(selected&&autoFollow){final View target=v;scroll.post(()->scroll.smoothScrollTo(0,Math.max(0,target.getTop()-scroll.getHeight()/2+target.getHeight()/2)));}
                }}
            }catch(Exception ignored){}
        }
        handler.postDelayed(this,150);
    }};
    @Override protected void onResume(){super.onResume();handler.post(ticker);}
    @Override protected void onPause(){handler.removeCallbacks(ticker);super.onPause();}
}
