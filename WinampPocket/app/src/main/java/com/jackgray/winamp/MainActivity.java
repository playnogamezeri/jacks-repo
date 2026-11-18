package com.jackgray.winamp;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
import android.media.audiofx.Equalizer;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONArray;

public class MainActivity extends Activity {
    private final int bg = Color.rgb(12, 12, 19), panel = Color.rgb(30, 30, 48), edge = Color.rgb(145, 139, 111);
    private final int green = Color.rgb(34, 255, 38), white = Color.rgb(232, 232, 232), muted = Color.rgb(160, 160, 170);
    private final ArrayList<Uri> tracks = new ArrayList<>();
    private final ArrayList<String> names = new ArrayList<>();
    private final ArrayList<Long> addedAt = new ArrayList<>();
    private final ArrayList<String> artists = new ArrayList<>();
    private final ArrayList<String> trackTitles = new ArrayList<>();
    private int sortMode = 0;
    private Button sortButton;
    private LinearLayout menuPanel;
    private TextView playlistHeading;
    private String playlistName = "MY MUSIC LIST";
    private MediaPlayer player;
    private Equalizer equalizer;
    private int current = -1;
    private boolean prepared = false, seeking = false, shuffle = false, repeat = false;
    private TextView trackTitle, elapsed, duration, status;
    private SeekBar progress;
    private ListView playlist;
    private ArrayAdapter<String> adapter;
    private LinearLayout eqBands;
    private TextView presetName;
    private short currentPreset = -1;
    private boolean eqEnabled = true;
    private LinearLayout root;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        buildUi();
        if(android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 200);
        }
        loadPlaylist();
        adapter.notifyDataSetChanged();
        player = PlaybackService.getPlayer();
        prepared = PlaybackService.isPrepared();
        PlaybackService.setTransportCallbacks(()->{if(current>0)playIndex(current-1);else if(current==0)playIndex(0);},()->next());
        if(player != null) { current = tracks.indexOf(PlaybackService.getCurrentUri()); if(current >= 0) trackTitle.setText(cleanTrackName(names.get(current))); }
        if(!tracks.isEmpty()) status.setText("PLAYLIST RESTORED     "+tracks.size()+" TRACKS");
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(10),dp(8),dp(10),dp(8)); root.setBackgroundColor(bg);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0,0,0,dp(12));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);

        LinearLayout topBar = new LinearLayout(this); topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("JACK TUNES", 17, white, true); brand.setGravity(Gravity.CENTER); brand.setPadding(0,dp(5),0,dp(8));
        topBar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        addControl(topBar,"MENU ▼",()->toggleMenu());
        content.addView(topBar);
        menuPanel = new LinearLayout(this); menuPanel.setOrientation(LinearLayout.VERTICAL); menuPanel.setPadding(dp(6),dp(5),dp(6),dp(5)); menuPanel.setBackgroundColor(panel); menuPanel.setVisibility(View.GONE);
        LinearLayout menuRow1 = new LinearLayout(this); menuRow1.setGravity(Gravity.CENTER);
        addControl(menuRow1,"SAVE PLAYLIST",()->savePlaylistAsFile());
        addControl(menuRow1,"OPEN PLAYLIST",()->openPlaylistFile());
        addControl(menuRow1,"PLAY YOUTUBE LINK",()->playYoutubeLink());
        menuPanel.addView(menuRow1);
        LinearLayout menuRow2 = new LinearLayout(this); menuRow2.setGravity(Gravity.CENTER);
        addControl(menuRow2,"RENAME PLAYLIST",()->renamePlaylist());
        menuPanel.addView(menuRow2);
        content.addView(menuPanel);
        LinearLayout main = panel("PLAYER"); content.addView(main);
        LinearLayout screen = new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL); screen.setPadding(dp(10),dp(8),dp(10),dp(8)); screen.setBackgroundColor(Color.BLACK);
        main.addView(screen, matchWrap());
        trackTitle = text("NO TRACK LOADED", 17, green, true); trackTitle.setSingleLine(true); trackTitle.setGravity(Gravity.CENTER_VERTICAL); screen.addView(trackTitle);
        status = text("STEREO     44 kHz     READY", 11, muted, true); screen.addView(status);
        LinearLayout timeRow = new LinearLayout(this); timeRow.setGravity(Gravity.CENTER_VERTICAL);
        elapsed = text("0:00",12,green,true); duration = text("0:00",12,green,true);
        timeRow.addView(elapsed); progress = new SeekBar(this); progress.setMax(1000); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(green)); progress.setThumbTintList(android.content.res.ColorStateList.valueOf(white));
        timeRow.addView(progress,new LinearLayout.LayoutParams(0,dp(28),1)); timeRow.addView(duration); screen.addView(timeRow);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar b,int v,boolean u){} public void onStartTrackingTouch(SeekBar b){seeking=true;} public void onStopTrackingTouch(SeekBar b){ if(player!=null&&prepared) player.seekTo((int)(player.getDuration()*b.getProgress()/1000f)); seeking=false; }});

        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER); controls.setPadding(0,dp(7),0,dp(3));
        addControl(controls,"|◀",()->playIndex(Math.max(0,current-1)));
        addControl(controls,"▶",()->playOrResume()); addControl(controls,"Ⅱ",()->pause()); addControl(controls,"■",()->stop()); addControl(controls,"▶|",()->next());
        main.addView(controls);
        LinearLayout modes = new LinearLayout(this); modes.setGravity(Gravity.CENTER); addControl(modes,"SHUFFLE",()->{shuffle=!shuffle; toast("Shuffle "+(shuffle?"ON":"OFF"));}); addControl(modes,"REPEAT",()->{repeat=!repeat; toast("Repeat "+(repeat?"ON":"OFF"));});
        main.addView(modes);
        LinearLayout volRow = new LinearLayout(this); volRow.setGravity(Gravity.CENTER_VERTICAL); volRow.setPadding(dp(8),dp(2),dp(8),dp(2));
        TextView volLabel = text("VOL",10,muted,true); volRow.addView(volLabel);
        SeekBar volume = new SeekBar(this); volume.setMax(100); volume.setProgress(80); volume.setProgressTintList(android.content.res.ColorStateList.valueOf(green)); volume.setThumbTintList(android.content.res.ColorStateList.valueOf(white));
        volRow.addView(volume,new LinearLayout.LayoutParams(0,dp(30),1)); main.addView(volRow);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int v,boolean user){if(player!=null)player.setVolume(v/100f,v/100f);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});

        LinearLayout eq = panel("JACK TUNERS"); content.addView(eq);
        LinearLayout eqButtons = new LinearLayout(this); eqButtons.setGravity(Gravity.CENTER_VERTICAL);
        addControl(eqButtons,"ON",()->setEqualizerEnabled(true));
        addControl(eqButtons,"OFF",()->setEqualizerEnabled(false));
        addControl(eqButtons,"PRESET",()->cycleEqualizerPreset());
        presetName = text("PRESET: DEFAULT",10,green,true);
        presetName.setGravity(Gravity.CENTER_VERTICAL);
        presetName.setPadding(dp(8),0,0,0);
        eqButtons.addView(presetName,new LinearLayout.LayoutParams(0,dp(40),1));
        eq.addView(eqButtons);
        eqBands = new LinearLayout(this);
        eqBands.setOrientation(LinearLayout.VERTICAL);
        eqBands.setPadding(dp(5),dp(4),dp(5),dp(4));
        eq.addView(eqBands);
        renderEqualizerBands();

        LinearLayout pl = panel(playlistName); content.addView(pl); playlistHeading = (TextView)pl.getChildAt(0);
        playlist = new ListView(this); playlist.setBackgroundColor(Color.BLACK); playlist.setDividerHeight(dp(1)); playlist.setDivider(new android.graphics.drawable.ColorDrawable(Color.rgb(45,45,45)));
        adapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,names){@Override public View getView(int p,View c,ViewGroup parent){TextView v=(TextView)super.getView(p,c,parent);v.setText(displayTrack(p));v.setTextColor(p==current?white:green);v.setTextSize(15);v.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);v.setSingleLine(true);v.setPadding(dp(8),dp(7),dp(8),dp(7));v.setBackgroundColor(Color.BLACK);return v;}};
        playlist.setAdapter(adapter); playlist.setOnItemClickListener((a,v,pos,id)->playIndex(pos));
        playlist.setOnItemLongClickListener((parent,view,position,id)->{
            android.content.ClipData clip=android.content.ClipData.newPlainText("playlist-position",String.valueOf(position));
            View.DragShadowBuilder shadow=new View.DragShadowBuilder(view);
            if(android.os.Build.VERSION.SDK_INT>=24) view.startDragAndDrop(clip,shadow,position,0);
            else view.startDrag(clip,shadow,position,0);
            return true;
        });
        playlist.setOnDragListener((view,event)->{
            switch(event.getAction()){
                case android.view.DragEvent.ACTION_DRAG_STARTED: return event.getLocalState() instanceof Integer;
                case android.view.DragEvent.ACTION_DRAG_ENTERED: return true;
                case android.view.DragEvent.ACTION_DRAG_LOCATION: return true;
                case android.view.DragEvent.ACTION_DROP:
                    int from=(Integer)event.getLocalState();
                    int child=playlist.pointToPosition((int)event.getX(),(int)event.getY());
                    if(child==ListView.INVALID_POSITION) child=tracks.size()-1;
                    if(from>=0&&from<tracks.size()&&child>=0&&child<tracks.size()) moveTrack(from,child);
                    return true;
                case android.view.DragEvent.ACTION_DRAG_ENDED: return true;
            }
            return true;
        });
        playlist.setNestedScrollingEnabled(true);
        playlist.setOnTouchListener((v,event)->{
            if(event.getAction()==android.view.MotionEvent.ACTION_DOWN || event.getAction()==android.view.MotionEvent.ACTION_MOVE){
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if(event.getAction()==android.view.MotionEvent.ACTION_UP || event.getAction()==android.view.MotionEvent.ACTION_CANCEL){
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        pl.addView(playlist,new LinearLayout.LayoutParams(-1,dp(235)));
        LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.CENTER); addControl(actions,"ADD FILES",()->pickFiles()); addControl(actions,"REMOVE",()->removeCurrent()); sortButton=addControl(actions,sortLabel(),()->toggleSort()); addControl(actions,"CLEAR",()->clearPlaylist()); addControl(actions,"EXIT",()->exitApp()); pl.addView(actions);
        TextView foot=text("LOCAL AUDIO PLAYER  •  WINAMP-INSPIRED",10,muted,false); foot.setGravity(Gravity.CENTER); foot.setPadding(0,dp(8),0,0); content.addView(foot);
    }

    private LinearLayout panel(String title){ LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(5),dp(4),dp(5),dp(5));box.setBackground(new android.graphics.drawable.GradientDrawable(){ {setColor(panel);setStroke(dp(1),edge);setCornerRadius(dp(3));} }); TextView h=text(title,13,white,true);h.setGravity(Gravity.CENTER);h.setPadding(0,dp(3),0,dp(5));box.addView(h); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(9);box.setLayoutParams(lp);return box; }
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(bold?Typeface.MONOSPACE:Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);return t;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(-1,-2);}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private Button addControl(LinearLayout row,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(10);b.setTextColor(white);b.setAllCaps(false);b.setPadding(dp(5),0,dp(5),0);b.setMinWidth(0);b.setMinimumWidth(dp(42));b.setMinHeight(dp(38));b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(66,67,85)));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(40));lp.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(b,lp);return b;}

    private void toggleMenu(){ if(menuPanel!=null) menuPanel.setVisibility(menuPanel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE); }

    private void renamePlaylist(){
        android.widget.EditText field=new android.widget.EditText(this);
        field.setSingleLine(true); field.setText(playlistName); field.setSelection(field.getText().length());
        new android.app.AlertDialog.Builder(this).setTitle("Rename playlist").setView(field)
            .setNegativeButton("CANCEL",null).setPositiveButton("SAVE",(d,w)->{
                String value=field.getText().toString().trim();
                if(!value.isEmpty()){playlistName=value; if(playlistHeading!=null)playlistHeading.setText(playlistName); savePlaylist(); toast("Playlist renamed");}
            }).show();
    }

    private void savePlaylistAsFile(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,playlistName.replaceAll("[^a-zA-Z0-9 _-]","").trim()+".json");
        i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,101);
    }

    private void openPlaylistFile(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,102);
    }

    private void playYoutubeLink(){
        android.widget.EditText field=new android.widget.EditText(this);
        field.setSingleLine(true); field.setHint("Paste a YouTube video link");
        new android.app.AlertDialog.Builder(this).setTitle("Play YouTube link").setView(field)
            .setNegativeButton("CANCEL",null).setPositiveButton("OPEN",(d,w)->{
                String link=field.getText().toString().trim();
                if(!link.startsWith("https://")&&!link.startsWith("http://")) link="https://"+link;
                if(link.matches("https?://(www\\.)?(m\\.)?(youtube\\.com|youtu\\.be)/.*")){
                    try{Intent playerIntent=new Intent(this,YoutubePlayerActivity.class);playerIntent.putExtra("youtube_url",link);startActivity(playerIntent);}catch(Exception e){toast("Could not open YouTube player");}
                }else toast("Enter a valid YouTube URL");
            }).show();
    }

    private void writePlaylistFile(Uri uri){
        try{
            JSONArray items=new JSONArray();
            for(int i=0;i<tracks.size();i++){
                org.json.JSONObject item=new org.json.JSONObject();
                item.put("uri",tracks.get(i).toString()); item.put("name",cleanTrackName(names.get(i)));
                item.put("addedAt",addedAt.get(i)); item.put("artist",artists.get(i)); item.put("title",trackTitles.get(i)); items.put(item);
            }
            org.json.JSONObject root=new org.json.JSONObject(); root.put("format","JackTunesPlaylist"); root.put("version",1); root.put("name",playlistName); root.put("tracks",items);
            OutputStream out=getContentResolver().openOutputStream(uri);
            if(out==null) throw new IOException("Cannot open destination");
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8)); out.close(); toast("Playlist exported");
        }catch(Exception e){toast("Could not save playlist");}
    }

    private void readPlaylistFile(Uri uri){
        try{
            InputStream in=getContentResolver().openInputStream(uri); if(in==null)throw new IOException("Cannot open playlist");
            java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream(); byte[] data=new byte[4096]; int n;
            while((n=in.read(data))!=-1)buffer.write(data,0,n); in.close();
            org.json.JSONObject root=new org.json.JSONObject(new String(buffer.toByteArray(),StandardCharsets.UTF_8));
            JSONArray items=root.getJSONArray("tracks");
            tracks.clear(); names.clear(); addedAt.clear(); artists.clear(); trackTitles.clear(); current=-1;
            for(int i=0;i<items.length();i++){
                org.json.JSONObject item=items.getJSONObject(i); Uri u=Uri.parse(item.getString("uri"));
                try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
                String name=item.optString("name","Audio track"); String[] meta=readMetadata(u,name);
                tracks.add(u); names.add((names.size()+1)+".  "+name); addedAt.add(item.optLong("addedAt",System.currentTimeMillis()));
                artists.add(item.optString("artist",meta[0])); trackTitles.add(item.optString("title",meta[1]));
            }
            playlistName=root.optString("name","MY MUSIC LIST"); if(playlistHeading!=null)playlistHeading.setText(playlistName);
            releasePlayer(); stopService(new Intent(this,PlaybackService.class)); savePlaylist(); adapter.notifyDataSetChanged();
            trackTitle.setText("NO TRACK LOADED"); status.setText("PLAYLIST OPENED"); elapsed.setText("0:00"); duration.setText("0:00"); progress.setProgress(0);
            toast("Opened "+playlistName+" ("+tracks.size()+" tracks)");
        }catch(Exception e){toast("Could not open playlist. Check that its audio files are still accessible.");}
    }

    private void pickFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,100);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result==RESULT_OK&&data!=null&&request==101){writePlaylistFile(data.getData());return;}if(result==RESULT_OK&&data!=null&&request==102){readPlaylistFile(data.getData());return;}if(request==100&&result==RESULT_OK&&data!=null){if(data.getClipData()!=null){for(int n=0;n<data.getClipData().getItemCount();n++)addUri(data.getClipData().getItemAt(n).getUri());}else if(data.getData()!=null)addUri(data.getData());adapter.notifyDataSetChanged();savePlaylist();if(current<0&&!tracks.isEmpty())playIndex(0);}}
    private void addUri(Uri u){
        if(u==null || tracks.contains(u)) return;
        try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        String name="Audio track";
        Cursor c=getContentResolver().query(u,null,null,null,null);
        if(c!=null){if(c.moveToFirst()){int k=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(k>=0)name=c.getString(k);}c.close();}
        String[] meta=readMetadata(u,name);tracks.add(u);names.add((names.size()+1)+".  "+name);addedAt.add(System.currentTimeMillis());artists.add(meta[0]);trackTitles.add(meta[1]);
    }

    // The playlist is saved automatically and restored when the app is reopened.
    private void savePlaylist(){
        try{
            JSONArray items=new JSONArray();
            for(int i=0;i<tracks.size();i++){
                org.json.JSONObject item=new org.json.JSONObject();
                item.put("uri",tracks.get(i).toString());
                String display=cleanTrackName(names.get(i));
                item.put("name",display);
                item.put("addedAt",addedAt.get(i));item.put("artist",artists.get(i));item.put("title",trackTitles.get(i));
                items.put(item);
            }
            getSharedPreferences("winamp_playlist",MODE_PRIVATE).edit().putString("tracks",items.toString()).putInt("sortMode",sortMode).putString("playlistName",playlistName).apply();
        }catch(Exception e){toast("Could not save playlist");}
    }

    private void loadPlaylist(){
        try{
            SharedPreferences prefs=getSharedPreferences("winamp_playlist",MODE_PRIVATE);
            JSONArray items=new JSONArray(prefs.getString("tracks","[]"));
            sortMode=prefs.getInt("sortMode",0);
            playlistName=prefs.getString("playlistName","MY MUSIC LIST");
            if(playlistHeading!=null) playlistHeading.setText(playlistName);
            for(int i=0;i<items.length();i++){
                org.json.JSONObject item=items.getJSONObject(i);
                Uri uri=Uri.parse(item.getString("uri"));
                String name=item.optString("name","Audio track");
                try{
                    getContentResolver().openFileDescriptor(uri,"r").close();
                    tracks.add(uri);
                    names.add((names.size()+1)+".  "+name);
                    addedAt.add(item.optLong("addedAt",0L));String[] meta=readMetadata(uri,name);artists.add(item.optString("artist",meta[0]));trackTitles.add(item.optString("title",meta[1]));
                }catch(Exception unavailable){
                    // Skip files whose original document permission or file is no longer available.
                }
            }
            if(tracks.size()!=items.length()) savePlaylist();if(sortMode!=0)applySort(false);if(sortButton!=null)sortButton.setText(sortLabel());
        }catch(Exception ignored){}
    }
    private void moveTrack(int from,int to){
        if(from==to)return;
        Uri active=current>=0&&current<tracks.size()?tracks.get(current):null;
        Uri uri=tracks.remove(from); tracks.add(to,uri);
        String name=names.remove(from); names.add(to,name);
        Long date=addedAt.remove(from); addedAt.add(to,date);String artist=artists.remove(from);artists.add(to,artist);String title=trackTitles.remove(from);trackTitles.add(to,title);
        if(active!=null) current=tracks.indexOf(active);
        renumberPlaylist(); savePlaylist(); adapter.notifyDataSetChanged();
    }

    private String sortLabel(){return sortMode==0?"SORT: DATE":sortMode==1?"SORT: ARTIST":"SORT: TRACK";}
    private void toggleSort(){sortMode=(sortMode+1)%3;applySort(true);}
    private void applySort(boolean announce){
        Uri active=current>=0&&current<tracks.size()?tracks.get(current):null;
        ArrayList<Integer> order=new ArrayList<>();for(int i=0;i<tracks.size();i++)order.add(i);
        java.util.Collections.sort(order,(a,b)->sortMode==1?artists.get(a).compareToIgnoreCase(artists.get(b)):sortMode==2?trackTitles.get(a).compareToIgnoreCase(trackTitles.get(b)):Long.compare(addedAt.get(b),addedAt.get(a)));
        ArrayList<Uri> t=new ArrayList<>();ArrayList<String> n=new ArrayList<>(),ar=new ArrayList<>(),ti=new ArrayList<>();ArrayList<Long>d=new ArrayList<>();
        for(int i:order){t.add(tracks.get(i));n.add(names.get(i));ar.add(artists.get(i));ti.add(trackTitles.get(i));d.add(addedAt.get(i));}
        tracks.clear();tracks.addAll(t);names.clear();names.addAll(n);artists.clear();artists.addAll(ar);trackTitles.clear();trackTitles.addAll(ti);addedAt.clear();addedAt.addAll(d);
        if(active!=null)current=tracks.indexOf(active);renumberPlaylist();savePlaylist();adapter.notifyDataSetChanged();if(sortButton!=null)sortButton.setText(sortLabel());
        if(announce)toast(sortMode==0?"Sorted by date added (newest first)":sortMode==1?"Sorted by artist A–Z":"Sorted by track title A–Z");
    }
    private String[] readMetadata(Uri uri,String fallback){
        String artist="Unknown artist",title=fallback;int dot=title.lastIndexOf('.');if(dot>0)title=title.substring(0,dot);int split=title.indexOf(" - ");if(split>0){artist=title.substring(0,split).trim();title=title.substring(split+3).trim();}
        MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(this,uri);String x=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),y=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);if(x!=null&&!x.trim().isEmpty())artist=x.trim();if(y!=null&&!y.trim().isEmpty())title=y.trim();}catch(Exception ignored){}finally{try{m.release();}catch(Exception ignored){}}
        return new String[]{artist,title};
    }
    private String displayTrack(int i){return (i+1)+".  "+(artists.get(i).equals("Unknown artist")?trackTitles.get(i):artists.get(i)+" - "+trackTitles.get(i));}

    private void playIndex(int index){if(index<0||index>=tracks.size())return;releasePlayer();current=index;prepared=false;adapter.notifyDataSetChanged();trackTitle.setText(displayTrack(index).replaceFirst("^\\d+\\.  ",""));status.setText("LOADING AUDIO...");PlaybackService.setTrackInfo(artists.get(index),trackTitles.get(index));player=new MediaPlayer(); player.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK); PlaybackService.attachPlayer(player,tracks.get(index)); PlaybackService.startService(this); try{player.setDataSource(this,tracks.get(index));player.setOnPreparedListener(mp->{prepared=true;PlaybackService.setPrepared(true);mp.start();PlaybackService.setTrackInfo(artists.get(current),trackTitles.get(current));PlaybackService.refreshNotification();duration.setText(time(mp.getDuration()));status.setText("PLAYING     STEREO");initEqualizer(mp.getAudioSessionId());});player.setOnCompletionListener(mp->{if(repeat)playIndex(current);else next();});player.setOnErrorListener((mp,what,extra)->{toast("Unable to play this audio file");status.setText("PLAYBACK ERROR");return true;});player.prepareAsync();}catch(IOException e){toast("Could not open audio file");}}
    private void initEqualizer(int session){
        try {
            if(equalizer!=null) equalizer.release();
            equalizer=new Equalizer(0,session);
            equalizer.setEnabled(eqEnabled);
            currentPreset=-1;
            if(presetName!=null) presetName.setText("PRESET: CUSTOM");
            renderEqualizerBands();
        } catch(Exception ignored) {
            equalizer=null;
            if(eqBands!=null) {
                eqBands.removeAllViews();
                eqBands.addView(text("EQUALIZER NOT AVAILABLE",11,muted,true));
            }
        }
    }

    private void setEqualizerEnabled(boolean enabled) {
        eqEnabled=enabled;
        if(equalizer!=null) {
            try { equalizer.setEnabled(enabled); } catch(Exception ignored) {}
        } else {
            toast("Start a track to activate equalizer");
            return;
        }
        toast("Equalizer "+(enabled?"ON":"OFF"));
    }

    private void cycleEqualizerPreset() {
        if(equalizer==null) { toast("Start a track to activate equalizer"); return; }
        try {
            short count=equalizer.getNumberOfPresets();
            if(count<=0) { toast("No equalizer presets available"); return; }
            currentPreset=(short)((currentPreset+1)%count);
            equalizer.usePreset(currentPreset);
            String name=equalizer.getPresetName(currentPreset);
            presetName.setText("PRESET: "+name.toUpperCase(Locale.US));
            renderEqualizerBands();
        } catch(Exception e) { toast("Could not apply preset"); }
    }

    private void renderEqualizerBands() {
        if(eqBands==null) return;
        eqBands.removeAllViews();
        if(equalizer==null) {
            eqBands.addView(text("Load a track to enable frequency controls",10,muted,false));
            return;
        }
        try {
            short count=equalizer.getNumberOfBands();
            short[] range=equalizer.getBandLevelRange();
            for(short i=0;i<count;i++) {
                final short band=i;
                int center=equalizer.getCenterFreq(band)/1000;
                String frequency=center>=1000?String.format(Locale.US,"%.1f kHz",center/1000f):center+" Hz";
                LinearLayout row=new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView label=text(frequency,10,white,true);
                row.addView(label,new LinearLayout.LayoutParams(dp(62),dp(36)));
                SeekBar slider=new SeekBar(this);
                slider.setMax(100);
                short actual=equalizer.getBandLevel(band);
                int initial=(int)(((actual-range[0])*100f)/(range[1]-range[0]));
                slider.setProgress(Math.max(0,Math.min(100,initial)));
                slider.setEnabled(eqEnabled);
                slider.setProgressTintList(android.content.res.ColorStateList.valueOf(green));
                slider.setThumbTintList(android.content.res.ColorStateList.valueOf(white));
                row.addView(slider,new LinearLayout.LayoutParams(0,dp(36),1));
                TextView value=text(String.format(Locale.US,"%+.1f",actual/100f),10,green,true);
                value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
                row.addView(value,new LinearLayout.LayoutParams(dp(45),dp(36)));
                slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    public void onProgressChanged(SeekBar seek,int progress,boolean fromUser) {
                        if(fromUser && equalizer!=null) {
                            short level=(short)(range[0]+(range[1]-range[0])*progress/100);
                            try {
                                equalizer.setBandLevel(band,level);
                                value.setText(String.format(Locale.US,"%+.1f",level/100f));
                                currentPreset=-1;
                                if(presetName!=null) presetName.setText("PRESET: CUSTOM");
                            } catch(Exception ignored) {}
                        }
                    }
                    public void onStartTrackingTouch(SeekBar seek) {}
                    public void onStopTrackingTouch(SeekBar seek) {}
                });
                eqBands.addView(row);
            }
        } catch(Exception ignored) {
            eqBands.addView(text("Could not read equalizer bands",10,muted,false));
        }
    }
    private void playOrResume(){if(player==null){if(!tracks.isEmpty())playIndex(current<0?0:current);else pickFiles();}else if(prepared&&!player.isPlaying()){player.start();PlaybackService.refreshNotification();status.setText("PLAYING     STEREO");}}
    private void pause(){if(player!=null&&prepared&&player.isPlaying()){player.pause();PlaybackService.refreshNotification();status.setText("PAUSED");}}
    private void stop(){if(player!=null&&prepared){player.pause();PlaybackService.refreshNotification();player.seekTo(0);status.setText("STOPPED");elapsed.setText("0:00");progress.setProgress(0);}}
    private void next(){if(tracks.isEmpty())return;int n=shuffle?(int)(Math.random()*tracks.size()):(current+1)%tracks.size();playIndex(n);}
    private void removeCurrent(){if(current<0||current>=tracks.size())return;releasePlayer();tracks.remove(current);names.remove(current);addedAt.remove(current);artists.remove(current);trackTitles.remove(current);current=-1;stopService(new Intent(this,PlaybackService.class));renumberPlaylist();savePlaylist();adapter.notifyDataSetChanged();trackTitle.setText("NO TRACK LOADED");status.setText("READY");elapsed.setText("0:00");duration.setText("0:00");progress.setProgress(0);}
    private void exitApp(){
        releasePlayer();
        stopService(new Intent(this,PlaybackService.class));
        finishAndRemoveTask();
    }
    private void clearPlaylist(){releasePlayer();stopService(new Intent(this,PlaybackService.class));tracks.clear();names.clear();addedAt.clear();artists.clear();trackTitles.clear();current=-1;savePlaylist();adapter.notifyDataSetChanged();trackTitle.setText("NO TRACK LOADED");status.setText("READY");elapsed.setText("0:00");duration.setText("0:00");progress.setProgress(0);}
    private String cleanTrackName(String value){
        int marker=value.indexOf(".  ");
        return marker>=0?value.substring(marker+3):value;
    }

    private void renumberPlaylist(){
        for(int i=0;i<names.size();i++) names.set(i,(i+1)+".  "+cleanTrackName(names.get(i)));
    }

    private void releasePlayer(){
        if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}
        prepared=false;
        if(equalizer!=null){equalizer.release();equalizer=null;}
        PlaybackService.detachPlayer();
    }

    private String time(int ms){int sec=Math.max(0,ms/1000);return String.format(Locale.US,"%d:%02d",sec/60,sec%60);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onResume(){super.onResume();root.post(tick);}
    private final Runnable tick=new Runnable(){public void run(){if(player!=null&&prepared){try{int pos=player.getCurrentPosition(),len=player.getDuration();if(!seeking){progress.setProgress(len>0?(int)(pos*1000f/len):0);elapsed.setText(time(pos));duration.setText(time(len));}}catch(Exception ignored){}}if(root!=null)root.postDelayed(this,400);}};
    @Override protected void onDestroy(){if(root!=null)root.removeCallbacks(tick);if(equalizer!=null){equalizer.release();equalizer=null;}super.onDestroy();}
}
