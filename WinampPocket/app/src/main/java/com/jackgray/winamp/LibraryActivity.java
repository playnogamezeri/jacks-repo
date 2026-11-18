package com.jackgray.winamp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Search and stream the Jamendo catalog. */
public class LibraryActivity extends Activity {
    private static final String CLIENT_ID = "bbf83937";
    private static final String API = "https://api.jamendo.com/v3.0/tracks/";
    private final int bg=Color.rgb(3,8,15),panel=Color.rgb(7,20,31),cyan=Color.rgb(0,190,210);
    private final int white=Color.rgb(220,250,255),muted=Color.rgb(105,160,175),green=Color.rgb(0,235,220);
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<JSONObject> tracks=new ArrayList<>();
    private final ArrayList<String> rows=new ArrayList<>();
    private EditText search;
    private TextView status;
    private ArrayAdapter<String> adapter;
    private Button searchButton;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(10));root.setBackgroundColor(bg);setContentView(root);
        TextView heading=label("JAMENDO // ONLINE MUSIC LIBRARY",16,white,true);
        heading.setGravity(Gravity.CENTER);heading.setPadding(0,dp(8),0,dp(12));root.addView(heading);
        TextView note=label("INDEPENDENT MUSIC • STREAMING CATALOG",10,muted,true);
        note.setGravity(Gravity.CENTER);root.addView(note);
        LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(white);
        search.setHintTextColor(muted);search.setHint("Search tracks or artists");
        search.setTextSize(14);search.setPadding(dp(9),0,dp(9),0);
        search.setBackgroundColor(panel);
        searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
        searchButton=button("SEARCH",()->loadTracks(search.getText().toString().trim()));
        searchRow.addView(searchButton);root.addView(searchRow);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);
        actions.addView(button("POPULAR TRACKS",()->{search.setText("");loadTracks("");}));
        root.addView(actions);
        status=label("CONNECTING TO JAMENDO...",10,green,true);status.setPadding(dp(3),dp(10),dp(3),dp(8));root.addView(status);
        ListView list=new ListView(this);list.setBackgroundColor(Color.BLACK);
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.rgb(30,65,75)));list.setDividerHeight(dp(1));
        adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,rows){
            @Override public View getView(int position,View convert,ViewGroup parent){
                LinearLayout item=new LinearLayout(LibraryActivity.this);item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(dp(10),dp(10),dp(8),dp(10));item.setBackgroundColor(Color.BLACK);
                String[] parts=rows.get(position).split("\\n",2);
                TextView title=label(parts[0],13,green,true);item.addView(title);
                if(parts.length>1){TextView artist=label(parts[1],10,muted,false);item.addView(artist);}
                return item;
            }
        };
        list.setAdapter(adapter);list.setOnItemClickListener((parent,view,position,id)->chooseTrack(position));
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(button("CLOSE LIBRARY",this::finish));
        loadTracks("");
    }

    private void loadTracks(String query){
        searchButton.setEnabled(false);status.setText("SEARCHING JAMENDO...");
        executor.execute(()->{
            HttpURLConnection connection=null;
            try{
                StringBuilder endpoint=new StringBuilder(API)
                    .append("?client_id=").append(CLIENT_ID)
                    .append("&format=json&limit=30&audioformat=mp31&order=popularity_total")
                    .append("&include=musicinfo");
                if(!query.isEmpty())endpoint.append("&search=").append(URLEncoder.encode(query,"UTF-8"));
                connection=(HttpURLConnection)new URL(endpoint.toString()).openConnection();
                connection.setRequestMethod("GET");connection.setConnectTimeout(12000);connection.setReadTimeout(15000);
                connection.setRequestProperty("Accept","application/json");
                int code=connection.getResponseCode();
                InputStream stream=code>=200&&code<300?connection.getInputStream():connection.getErrorStream();
                if(stream==null)throw new Exception("HTTP "+code);
                BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8));
                StringBuilder body=new StringBuilder();String line;
                while((line=reader.readLine())!=null)body.append(line);
                reader.close();
                if(code<200||code>=300)throw new Exception("Jamendo returned HTTP "+code);
                JSONObject response=new JSONObject(body.toString());
                JSONArray results=response.optJSONArray("results");
                ArrayList<JSONObject> found=new ArrayList<>();
                if(results!=null)for(int i=0;i<results.length();i++){
                    JSONObject track=results.optJSONObject(i);
                    if(track!=null&&!track.optString("audio","").isEmpty())found.add(track);
                }
                main.post(()->{
                    tracks.clear();tracks.addAll(found);rows.clear();
                    for(JSONObject track:tracks){
                        rows.add(track.optString("name","Untitled track")+"\n"+
                            track.optString("artist_name","Unknown artist")+"  •  "+
                            formatDuration(track.optInt("duration",0)));
                    }
                    adapter.notifyDataSetChanged();searchButton.setEnabled(true);
                    status.setText(tracks.size()+" STREAMABLE TRACKS  •  TAP A TRACK TO ADD & PLAY");
                    if(tracks.isEmpty())status.setText("NO STREAMABLE TRACKS FOUND");
                });
            }catch(Exception e){
                main.post(()->{searchButton.setEnabled(true);status.setText("CATALOG ERROR: "+e.getMessage());});
            }finally{if(connection!=null)connection.disconnect();}
        });
    }

    private void chooseTrack(int position){
        if(position<0||position>=tracks.size())return;
        JSONObject track=tracks.get(position);
        String title=track.optString("name","Untitled track");
        String artist=track.optString("artist_name","Unknown artist");
        String license=track.optString("license_ccurl","");
        String message=artist+"\nDuration: "+formatDuration(track.optInt("duration",0))+
            (license.isEmpty()?"":"\nLicense: "+license);
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton("CANCEL",null)
            .setPositiveButton("ADD & PLAY",(dialog,which)->{
                Intent result=new Intent();
                result.putExtra("stream_url",track.optString("audio",""));
                result.putExtra("track_title",title);
                result.putExtra("artist_name",artist);
                setResult(RESULT_OK,result);finish();
            }).show();
    }

    private String formatDuration(int seconds){return String.format(java.util.Locale.US,"%d:%02d",seconds/60,seconds%60);}
    private TextView label(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.MONOSPACE,bold?Typeface.BOLD:Typeface.NORMAL);return t;
    }
    private Button button(String text,Runnable action){
        Button b=new Button(this);b.setText(text);b.setTextColor(white);b.setTextSize(10);b.setAllCaps(false);
        b.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(8,55,72)));
        b.setOnClickListener(v->action.run());return b;
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    @Override protected void onDestroy(){executor.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
