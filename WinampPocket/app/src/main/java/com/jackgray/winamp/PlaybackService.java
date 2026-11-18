package com.jackgray.winamp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

public class PlaybackService extends Service {
    private static final String CHANNEL_ID = "jack_tunes_playback";
    private static final int NOTIFICATION_ID = 4101;
    private static final String ACTION_TOGGLE = "com.jackgray.winamp.TOGGLE";
    private static final String ACTION_PREVIOUS = "com.jackgray.winamp.PREVIOUS";
    private static final String ACTION_NEXT = "com.jackgray.winamp.NEXT";
    private static PlaybackService instance;
    private static MediaPlayer sharedPlayer;
    private static Uri currentUri;
    private static boolean prepared;
    private static String artist = "Unknown artist", trackTitle = "Jack Tunes";
    private static Runnable previousCallback, nextCallback;
    private MediaSession mediaSession;

    public static void attachPlayer(MediaPlayer player, Uri uri) {
        sharedPlayer = player;
        currentUri = uri;
        prepared = false;
    }

    public static void setPrepared(boolean value) { prepared = value; }
    public static void setTrackInfo(String newArtist,String newTitle) {
        artist = newArtist == null ? "Unknown artist" : newArtist;
        trackTitle = newTitle == null ? "Jack Tunes" : newTitle;
        if(instance != null) instance.updateNotification();
    }
    public static void setTransportCallbacks(Runnable previous,Runnable next) {
        previousCallback = previous; nextCallback = next;
    }
    public static boolean isPrepared() { return prepared; }
    public static MediaPlayer getPlayer() { return sharedPlayer; }
    public static Uri getCurrentUri() { return currentUri; }

    public static void startService(Context context) {
        Intent intent = new Intent(context, PlaybackService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void detachPlayer() {
        sharedPlayer = null;
        currentUri = null;
        prepared = false;
        if (instance != null) instance.updateNotification();
    }

    public static void refreshNotification() {
        if (instance != null) instance.updateNotification();
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel();
        mediaSession = new MediaSession(this, "JackTunesSession");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { toggleTo(true); }
            @Override public void onPause() { toggleTo(false); }
            @Override public void onSkipToNext() { if(nextCallback != null) nextCallback.run(); }
            @Override public void onSkipToPrevious() { if(previousCallback != null) previousCallback.run(); }
        });
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TOGGLE.equals(intent.getAction())) {
            togglePlayback();
        }
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    private void toggleTo(boolean play) {
        try {
            if(sharedPlayer != null && prepared) {
                if(play && !sharedPlayer.isPlaying()) sharedPlayer.start();
                else if(!play && sharedPlayer.isPlaying()) sharedPlayer.pause();
            }
        } catch(Exception ignored) {}
        updateNotification();
    }

    private void togglePlayback() {
        try {
            if (sharedPlayer != null && prepared) {
                if (sharedPlayer.isPlaying()) sharedPlayer.pause();
                else sharedPlayer.start();
            }
        } catch (Exception ignored) {}
        updateNotification();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Jack Tunes playback", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Playback controls for Jack Tunes");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        boolean playing = false;
        try { playing = sharedPlayer != null && prepared && sharedPlayer.isPlaying(); }
        catch (Exception ignored) {}

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 1, open,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        Intent toggle = new Intent(this, PlaybackService.class).setAction(ACTION_TOGGLE);
        PendingIntent action = PendingIntent.getService(this, 2, toggle,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        String title = "Jack Tunes";
        String text = currentUri == null ? "Ready" : (playing ? "Playing audio" : "Playback paused");
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);

        PendingIntent prevIntent = PendingIntent.getService(this, 3,
            new Intent(this, PlaybackService.class).setAction(ACTION_PREVIOUS),
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        PendingIntent nextIntent = PendingIntent.getService(this, 4,
            new Intent(this, PlaybackService.class).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        builder.setSmallIcon(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(content)
            .setOngoing(sharedPlayer != null)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "Pause" : "Play", action);
        return builder.build();
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }

    @Override public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}