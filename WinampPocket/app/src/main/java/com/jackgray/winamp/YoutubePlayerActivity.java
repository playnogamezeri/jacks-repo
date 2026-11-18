package com.jackgray.winamp;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubePlayerActivity extends Activity {
    private WebView webView;
    private String videoId;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        String url=getIntent().getStringExtra("youtube_url");
        videoId=extractVideoId(url);
        if(videoId==null){Toast.makeText(this,"Could not read YouTube video link",Toast.LENGTH_LONG).show();finish();return;}

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12,12,19));
        TextView heading=new TextView(this);
        heading.setText("JACK TUNES  •  YOUTUBE PLAYER");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(14);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(8,18,8,18);
        root.addView(heading,new LinearLayout.LayoutParams(-1,-2));

        webView=new WebView(this);
        WebSettings settings=webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        root.addView(webView,new LinearLayout.LayoutParams(-1,0,1));

        TextView note=new TextView(this);
        note.setText("Keep this player visible during playback. YouTube playback pauses when you leave this screen.");
        note.setTextColor(Color.LTGRAY);
        note.setTextSize(11);
        note.setGravity(Gravity.CENTER);
        note.setPadding(12,10,12,14);
        root.addView(note,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
        webView.loadDataWithBaseURL("https://www.youtube.com/",buildPlayerHtml(videoId),"text/html","UTF-8",null);
    }

    private String extractVideoId(String raw) {
        if(raw==null)return null;
        try {
            Uri uri=Uri.parse(raw);
            String host=uri.getHost();
            if(host==null || !(host.equals("youtu.be") || host.endsWith("youtube.com")))return null;
            String id=uri.getQueryParameter("v");
            if(id==null || id.isEmpty()) {
                String path=uri.getPath();
                if(path!=null) {
                    Matcher m=Pattern.compile("^/(?:embed/|shorts/|live/)?([A-Za-z0-9_-]{11})(?:/.*)?$").matcher(path);
                    if(m.matches())id=m.group(1);
                }
            }
            return id!=null && id.matches("[A-Za-z0-9_-]{11}")?id:null;
        } catch(Exception e){return null;}
    }

    private String buildPlayerHtml(String id) {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1, maximum-scale=1'></head>"
            +"<body style='margin:0;background:#000;color:#fff;font-family:sans-serif'>"
            +"<div id='player' style='width:100%;aspect-ratio:16/9;min-height:200px'></div>"
            +"<script>var ytPlayer;function onYouTubeIframeAPIReady(){ytPlayer=new YT.Player('player',{width:'100%',height:'100%',videoId:'"+id+"',playerVars:{playsinline:1,autoplay:0},events:{onReady:function(e){},onError:function(e){document.getElementById('message').innerText='This video cannot be embedded. Open it on YouTube.';}}});}"
            +"function pauseJackTunesYoutube(){if(ytPlayer&&ytPlayer.pauseVideo)ytPlayer.pauseVideo();}</script>"
            +"<script src='https://www.youtube.com/iframe_api'></script>"
            +"<div id='message' style='padding:12px;text-align:center;font-size:13px'></div></body></html>";
    }

    @Override protected void onPause() {
        if(webView!=null) {
            webView.evaluateJavascript("pauseJackTunesYoutube()",null);
            webView.onPause();
        }
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if(webView!=null)webView.onResume();
    }

    @Override public void onBackPressed() {
        if(webView!=null)webView.destroy();
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if(webView!=null){webView.stopLoading();webView.destroy();webView=null;}
        super.onDestroy();
    }
}