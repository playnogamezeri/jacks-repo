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
    private static String artist = "Unknown artist";
    private static String trackTitle = "Jack Tunes";
    private static Runnable previousCallback;
    private static Runnable nextCallback;
    private MediaSession mediaSession;

    public static void attachPlayer(MediaPlayer player, Uri uri) {
        sharedPlayer = player;
        currentUri = uri;
        prepared = false;
    }

    public static void setPrepared(boolean value) { prepared = value; }
    public static boolean isPrepared() { return prepared; }
    public static MediaPlayer getPlayer() { return sharedPlayer; }
    public static Uri getCurrentUri() { return currentUri; }

    public static void setTrackInfo(String newArtist, String newTitle) {
        artist = newArtist == null ? "Unknown artist" : newArtist;
        trackTitle = newTitle == null ? "Jack Tunes" : newTitle;
        if (instance != null) instance.updateNotification();
    }

    public static void setTransportCallbacks(Runnable previous, Runnable next) {
        previousCallback = previous;
        nextCallback = next;
    }

    public static void startService(Context context) {
        Intent intent = new Intent(context, PlaybackService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static boolean attachAuxEffect(int effectId) {
        try {
            if (sharedPlayer == null || !prepared) return false;
            sharedPlayer.attachAuxEffect(effectId);
            sharedPlayer.setAuxEffectSendLevel(1.0f);
            return true;
        } catch (Exception e) { return false; }
    }

    public static void detachAuxEffect() {
        try { if (sharedPlayer != null) sharedPlayer.setAuxEffectSendLevel(0.0f); } catch (Exception ignored) {}
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
            @Override public void onPlay() { setPlaying(true); }
            @Override public void onPause() { setPlaying(false); }
            @Override public void onSkipToNext() { if (nextCallback != null) nextCallback.run(); }
            @Override public void onSkipToPrevious() { if (previousCallback != null) previousCallback.run(); }
        });
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_TOGGLE.equals(action)) togglePlayback();
            else if (ACTION_PREVIOUS.equals(action) && previousCallback != null) previousCallback.run();
            else if (ACTION_NEXT.equals(action) && nextCallback != null) nextCallback.run();
        }
        updateNotification();
        return START_STICKY;
    }

    private void setPlaying(boolean play) {
        try {
            if (sharedPlayer != null && prepared) {
                if (play && !sharedPlayer.isPlaying()) sharedPlayer.start();
                else if (!play && sharedPlayer.isPlaying()) sharedPlayer.pause();
            }
        } catch (Exception ignored) {}
        updateNotification();
    }

    private void togglePlayback() {
        try {
            if (sharedPlayer != null && prepared) {
                if (sharedPlayer.isPlaying()) sharedPlayer.pause();
                else sharedPlayer.start();
            }
        } catch (Exception ignored) {}
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

    private PendingIntent serviceAction(String action, int requestCode) {
        Intent intent = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
    }

    private Notification buildNotification() {
        boolean playing = false;
        try { playing = sharedPlayer != null && prepared && sharedPlayer.isPlaying(); }
        catch (Exception ignored) {}

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        long sessionActions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
                PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT |
                PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if (mediaSession != null) {
            mediaSession.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, trackTitle)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, "Jack Tunes")
                    .build());
            mediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setActions(sessionActions)
                    .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                            0, playing ? 1f : 0f)
                    .build());
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setSmallIcon(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play)
                .setContentTitle(trackTitle)
                .setContentText(artist + " • " + (playing ? "Playing" : "Paused"))
                .setContentIntent(content)
                .setOngoing(sharedPlayer != null)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_media_previous, "Previous",
                        serviceAction(ACTION_PREVIOUS, 3))
                .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Pause" : "Play", serviceAction(ACTION_TOGGLE, 2))
                .addAction(android.R.drawable.ic_media_next, "Next",
                        serviceAction(ACTION_NEXT, 4));

        if (Build.VERSION.SDK_INT >= 21 && mediaSession != null) {
            builder.setStyle(new Notification.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2));
        }
        return builder.build();
    }

    private void updateNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }

    @Override public void onDestroy() {
        instance = null;
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}