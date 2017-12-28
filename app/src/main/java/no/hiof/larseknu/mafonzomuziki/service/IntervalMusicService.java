package no.hiof.larseknu.mafonzomuziki.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.app.NotificationCompat.MediaStyle;
import android.util.Log;

import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Metadata;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import kaaes.spotify.webapi.android.models.PlaylistSimple;
import no.hiof.larseknu.mafonzomuziki.PlayActivity;
import no.hiof.larseknu.mafonzomuziki.R;
import no.hiof.larseknu.mafonzomuziki.util.NotificationUtil;

public class IntervalMusicService extends Service implements ConnectionStateCallback, Player.NotificationCallback {
    public static final String EXTRA_RESULT_RECEIVER = "no.hiof.larseknu.mafonzomuziki.extra.RESULT_RECEIVER";

    public enum IntervalPlaybackState {
        PLAYBACK_INTERVAL_PAUSED,
        PLAYBACK_INTERVAL_PLAYING,
        PAUSED_INTERVAL_PAUSED,
        PAUSED_INTERVAL_PLAYING
    }

    private IntervalPlaybackState currentState;

    public static final int RESULT_CODE_METADATA_CHANGED = 1;
    public static final int RESULT_CODE_TIMESTAMP_UPDATED = 2;
    public static final int RESULT_CODE_STATE_CHANGED = 3;
    public static final String RESULT_DATA_KEY_METADATA = "no.hiof.larseknu.mafonzomuziki.intentservice.RESULT_DATA_ARTIST";
    public static final String RESULT_DATA_KEY_TIMESTAMP = "no.hiof.larseknu.mafonzomuziki.intentservice.RESULT_DATA_TIMESTAMP";

    private static int DEFAULT_PAUSE_TIME = 10;
    private static int DEFAULT_PLAY_TIME = 50;
    private long userDefinedPlayTime;
    private long userDefinedPauseTime;

    private String TAG = "IntervalMusicService";

    private SpotifyPlayer player;
    private PlaylistSimple currentPlaylist;

    private MyLocalBinder myLocalBinder = new MyLocalBinder();
    private ResultReceiver resultReceiver;
    private boolean initialized = false;

    private CountDownTimer countDownTimer;
    private long remainingTime = 0;

    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    private SoundPool soundPool;
    private int intervalChangeSound;

    // region top secret
    private static final String CLIENT_ID = "0f835bef4ef44912ac05055c3d099b1b";

    private static final String REDIRECT_URI = "mafonzomuziki://callback";

    // endregion

    public IntervalMusicService() {

    }

    public IntervalPlaybackState getCurrentState() {
        return currentState;
    }

    public Metadata getCurrentMetadata() {
        return player.getMetadata();
    }

    public PlaylistSimple getCurrentPlaylist() {
        return currentPlaylist;
    }

    public Long getCurrentTimeStamp() {
        return remainingTime;
    }

    public void setResultReceiver(ResultReceiver resultReceiver) {
        this.resultReceiver = resultReceiver;
    }

    @Override
    public void onCreate() {
        notificationManager =  (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        setUpSoundPool();

        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_STICKY;
        String action = intent.getAction();
        if (action == null)
            return START_STICKY;

        if (action.equals("SKIP_NEXT"))
            skipNext();
        else if (action.equals("SKIP_PREVIOUS"))
            skipPrevious();
        else if (action.equals("PLAY_PAUSE")) {
            if (currentState == IntervalPlaybackState.PAUSED_INTERVAL_PAUSED || currentState == IntervalPlaybackState.PLAYBACK_INTERVAL_PAUSED)
                pause();
            else
                play();
        }

        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        Log.d(TAG, "Destroying IntervalMusicService");
        notificationManager.cancel(1);

        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (!initialized)
            initialize(intent);

        return myLocalBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    private void setUpSoundPool() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder().setAudioAttributes(audioAttributes).setMaxStreams(5).build();
        }
        else {
            soundPool = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
        }

        intervalChangeSound = soundPool.load(getApplicationContext(), R.raw.interval_change, 1);
    }

    private void initializeNotification() {
        NotificationUtil.createNotificationChannel(this);

        Intent showPlayingActivity = new Intent(this, PlayActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                showPlayingActivity,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Intent skipNextIntent = new Intent(this, IntervalMusicService.class);
        skipNextIntent.setAction("SKIP_NEXT");
        PendingIntent skipNextPendingIntent = PendingIntent.getService(this, 2, skipNextIntent, 0);
        Intent skipPreviousIntent = new Intent(this, IntervalMusicService.class);
        skipPreviousIntent.setAction("SKIP_PREVIOUS");
        PendingIntent skipPreviousPendingIntent = PendingIntent.getService(this, 3, skipPreviousIntent, 0);
        Intent playPauseIntent = new Intent(this, IntervalMusicService.class);
        playPauseIntent.setAction("PLAY_PAUSE");
        PendingIntent playPausePendingIntent = PendingIntent.getService(this, 4, playPauseIntent, 0);

        notificationBuilder = new NotificationCompat.Builder(this, "MafunzoMuzikiMusic")
                .setSmallIcon(R.drawable.ic_library_music)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(R.drawable.ic_skip_previous, "SkipPrevious", skipPreviousPendingIntent)
                .addAction(R.drawable.ic_pause_circle_outline, "Pause", playPausePendingIntent)
                .addAction(R.drawable.ic_skip_next, "SkipNext", skipNextPendingIntent);

        if (Build.VERSION.SDK_INT >= 26)
            notificationBuilder.setStyle(new MediaStyle());

        updateNotification(getCurrentMetadata(), true);
    }

    private void updateNotification(Metadata metadata) {
        updateNotification(metadata, false);
    }

    private void updateNotification(Metadata metadata, boolean firstNotification) {
        Metadata.Track currentTrack = metadata.currentTrack;

        if (currentTrack != null) {

            Intent showPlayingActivity = new Intent(this, PlayActivity.class);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    showPlayingActivity,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            Intent skipNextIntent = new Intent(this, IntervalMusicService.class);
            skipNextIntent.setAction("SKIP_NEXT");
            PendingIntent skipNextPendingIntent = PendingIntent.getService(this, 2, skipNextIntent, 0);
            Intent skipPreviousIntent = new Intent(this, IntervalMusicService.class);
            skipPreviousIntent.setAction("SKIP_PREVIOUS");
            PendingIntent skipPreviousPendingIntent = PendingIntent.getService(this, 3, skipPreviousIntent, 0);
            Intent playPauseIntent = new Intent(this, IntervalMusicService.class);
            playPauseIntent.setAction("PLAY_PAUSE");
            PendingIntent playPausePendingIntent = PendingIntent.getService(this, 4, playPauseIntent, 0);

            notificationBuilder = new NotificationCompat.Builder(this, "MafunzoMuzikiMusic")
                    .setSmallIcon(R.drawable.ic_library_music)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .addAction(R.drawable.ic_skip_previous, "SkipPrevious", skipPreviousPendingIntent)
                    .addAction(R.drawable.ic_pause_circle_outline, "Pause", playPausePendingIntent)
                    .addAction(R.drawable.ic_skip_next, "SkipNext", skipNextPendingIntent)
                    .setContentTitle(currentTrack.name)
                    .setContentText(currentTrack.artistName + " - " + currentTrack.albumName);

            if (firstNotification)
                startForeground(1, notificationBuilder.build());
            else
                notificationManager.notify(1, notificationBuilder.build());

            Log.d(TAG, currentTrack.albumCoverWebUrl);

            Picasso.with(this)
                    .load(currentTrack.albumCoverWebUrl)
                    .resize(250, 250)
                    .placeholder(R.drawable.ic_library_music)
                    .into(new Target() {
                        @Override
                        public void onBitmapLoaded(final Bitmap bitmap, final Picasso.LoadedFrom from) {
                            notificationBuilder.setLargeIcon(bitmap);
                            notificationManager.notify(1, notificationBuilder.build());
                        }

                        @Override
                        public void onBitmapFailed(final Drawable errorDrawable) {
                            // Do nothing
                            Log.d(TAG, "Loading bitmap into notification failed");
                        }

                        @Override
                        public void onPrepareLoad(final Drawable placeHolderDrawable) {
                            // Do nothing
                            notificationBuilder.setLargeIcon(((BitmapDrawable)placeHolderDrawable).getBitmap());
                            notificationManager.notify(1, notificationBuilder.build());
                        }
                    });
        }
    }

    private void updateNotificationWithRemainingTime(int maxTime, int millisecondsUntilFinished) {
        notificationBuilder.setProgress(maxTime, millisecondsUntilFinished, false);

        notificationManager.notify(1, notificationBuilder.build());
    }


    public void startNewPlayback(PlaylistSimple currentPlaylist) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        userDefinedPlayTime = sharedPreferences.getInt("pref_interval_play", DEFAULT_PLAY_TIME) * 1000;
        userDefinedPauseTime = sharedPreferences.getInt("pref_interval_pause", DEFAULT_PAUSE_TIME) * 1000;

        this.currentPlaylist = currentPlaylist;
        player.playUri(null, currentPlaylist.uri, 0, 0);

        currentState = IntervalPlaybackState.PLAYBACK_INTERVAL_PLAYING;
        resultReceiver.send(RESULT_CODE_STATE_CHANGED, null);

        if (countDownTimer != null)
            countDownTimer.cancel();

        startPlayCountdown(userDefinedPlayTime);
    }

    private void initialize(Intent intent) {
        initializePlayerOnSuccessfulAuthentication(intent.getStringExtra("accessToken"));
        resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);
        initialized = true;
    }

    public class MyLocalBinder extends Binder {
        public IntervalMusicService getService() {
            return IntervalMusicService.this;
        }
    }

    public void play() {
        switch (currentState) {
            case PLAYBACK_INTERVAL_PAUSED:
                player.resume(defaultOperationCallback);
                startPlayCountdown(remainingTime);
                currentState = IntervalPlaybackState.PLAYBACK_INTERVAL_PLAYING;
                resultReceiver.send(RESULT_CODE_STATE_CHANGED, null);
                break;
            case PAUSED_INTERVAL_PAUSED:
                startPauseCountDown(remainingTime);
                currentState = IntervalPlaybackState.PAUSED_INTERVAL_PLAYING;
                resultReceiver.send(RESULT_CODE_STATE_CHANGED, null);
                break;
        }
    }

    public void pause() {
        switch (currentState) {
            case PLAYBACK_INTERVAL_PLAYING:
                player.pause(defaultOperationCallback);
                currentState = IntervalPlaybackState.PLAYBACK_INTERVAL_PAUSED;
                resultReceiver.send(RESULT_CODE_STATE_CHANGED, null);
                break;
            case PAUSED_INTERVAL_PLAYING:
                currentState = IntervalPlaybackState.PAUSED_INTERVAL_PAUSED;
                resultReceiver.send(RESULT_CODE_STATE_CHANGED, null);
                break;
        }

        countDownTimer.cancel();
    }

    public void skipNext() {
        if (player.getMetadata().nextTrack != null)
            player.skipToNext(defaultOperationCallback);
        else
            player.playUri(defaultOperationCallback, currentPlaylist.uri, 0, 0);
        if (!currentState.equals(IntervalPlaybackState.PLAYBACK_INTERVAL_PLAYING))
            player.pause(defaultOperationCallback);
    }

    public void skipPrevious() {
        if (player.getMetadata().prevTrack != null) {
            player.skipToPrevious(defaultOperationCallback);
        }
        else
            player.seekToPosition(defaultOperationCallback, 0);
        if (!currentState.equals(IntervalPlaybackState.PLAYBACK_INTERVAL_PLAYING))
            player.pause(defaultOperationCallback);
    }

    private void initializePlayerOnSuccessfulAuthentication(String accessToken) {
        Config playerConfig = new Config(this, accessToken, CLIENT_ID);
        Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver() {
            @Override
            public void onInitialized(SpotifyPlayer spotifyPlayer) {
                player = spotifyPlayer;
                player.addConnectionStateCallback(IntervalMusicService.this);
                player.addNotificationCallback(IntervalMusicService.this);

                initializeNotification();
            }

            @Override
            public void onError(Throwable throwable) {
                Log.e("PlayActivity", "Could not initialize player: " + throwable.getMessage());
            }
        });
    }


    private void startPlayCountdown(long playTime) {
        countDownTimer = new CountDownTimer(playTime, 100) {
            public void onTick(long millisUntilFinished) {
                if (2951 < millisUntilFinished && millisUntilFinished < 3050)
                    soundPool.play(intervalChangeSound, 1, 1, 1,0,1);
                else if (1951 < millisUntilFinished && millisUntilFinished < 2050)
                    soundPool.play(intervalChangeSound, 1, 1, 1,0,1);
                else if (951 < millisUntilFinished && millisUntilFinished < 1050)
                    soundPool.play(intervalChangeSound, 1, 1, 1,0,1);

                updateNotificationWithRemainingTime((int)userDefinedPlayTime, (int)millisUntilFinished);
                sendTimeStamp(millisUntilFinished);
            }

            public void onFinish() {
                Log.d(TAG, "Play done, pausing music for: " + userDefinedPauseTime / 1000);
                player.pause(defaultOperationCallback);
                currentState = IntervalPlaybackState.PAUSED_INTERVAL_PLAYING;
                resultReceiver.send(RESULT_CODE_STATE_CHANGED, null);
                startPauseCountDown(userDefinedPauseTime);
                soundPool.play(intervalChangeSound, 1, 1, 1,0,1);
            }
        };
        countDownTimer.start();
    }

    private void startPauseCountDown(long pauseTime) {
        countDownTimer = new CountDownTimer(pauseTime, 100) {

            public void onTick(long millisUntilFinished) {
                if (2951 < millisUntilFinished && millisUntilFinished < 3050)
                    soundPool.play(intervalChangeSound, 1, 1, 1,0,1);
                else if (1951 < millisUntilFinished && millisUntilFinished < 2050)
                    soundPool.play(intervalChangeSound, 1, 1, 1,0,1);
                else if (951 < millisUntilFinished && millisUntilFinished < 1050)
                    soundPool.play(intervalChangeSound, 1, 1, 1,0,1);

                updateNotificationWithRemainingTime((int)userDefinedPauseTime, (int)millisUntilFinished);

                sendTimeStamp(millisUntilFinished);
            }

            public void onFinish() {
                Log.d("PlayActivity", "Pause done, playing music for "+ userDefinedPlayTime / 1000);
                player.skipToNext(defaultOperationCallback);
                currentState = IntervalPlaybackState.PLAYBACK_INTERVAL_PLAYING;
                resultReceiver.send(RESULT_CODE_STATE_CHANGED, null);
                startPlayCountdown(userDefinedPlayTime);
                soundPool.play(intervalChangeSound, 1, 1, 1,0,1);
            }
        };
        countDownTimer.start();
    }

    private void sendTimeStamp(long remainingTime) {
        this.remainingTime = remainingTime;
        Bundle bundle = new Bundle();
        bundle.putLong(RESULT_DATA_KEY_TIMESTAMP, remainingTime);

        resultReceiver.send(RESULT_CODE_TIMESTAMP_UPDATED, bundle);
    }

    @Override
    public void onPlaybackEvent(PlayerEvent playerEvent) {
        Log.d(TAG, "Playback event received: " + playerEvent.name());

        switch (playerEvent) {
            case kSpPlaybackNotifyMetadataChanged:
                Bundle bundle = new Bundle();
                bundle.putParcelable(RESULT_DATA_KEY_METADATA, player.getMetadata());
                resultReceiver.send(RESULT_CODE_METADATA_CHANGED, bundle);
                updateNotification(player.getMetadata());
                break;
            default:
                break;
        }
    }

    @Override
    public void onPlaybackError(Error error) {
        Log.d("PlayActivity", "Playback error received: " + error.name());
        switch (error) {
            // Handle error type as necessary
            default:
                break;
        }
    }

    @Override
    public void onLoggedIn() {

    }

    @Override
    public void onLoggedOut() {

    }

    @Override
    public void onLoginFailed(Error error) {

    }

    @Override
    public void onTemporaryError() {

    }

    @Override
    public void onConnectionMessage(String s) {

    }

    Player.OperationCallback defaultOperationCallback = new Player.OperationCallback() {
        @Override
        public void onSuccess() {
            Log.d(TAG, "Success: " + this.toString());
        }

        @Override
        public void onError(Error error) {
            Log.d(TAG, error.toString());
        }
    };
}
