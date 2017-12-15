package no.hiof.larseknu.mafonzomuziki.service;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Metadata;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;

import kaaes.spotify.webapi.android.models.PlaylistSimple;
import no.hiof.larseknu.mafonzomuziki.R;

public class IntervalMusicService extends Service implements ConnectionStateCallback, Player.NotificationCallback {
    public static final String EXTRA_RESULT_RECEIVER = "no.hiof.larseknu.mafonzomuziki.extra.RESULT_RECEIVER";

    public enum IntervalPlaybackState {
        PLAYBACK_INTERVAL_PAUSED,
        PLAYBACK_INTERVAL_PLAYING,
        PAUSED_INTERVAL_PAUSED,
        PAUSED_INTERVAL_PLAYING
    }

    private IntervalPlaybackState currentState;

    public static final int RESULT_CODE_ARTIST = 1;
    public static final int RESULT_CODE_TIMESTAMP_UPDATED = 2;
    public static final int RESULT_CODE_STATE_CHANGED = 3;
    public static final String RESULT_DATA_KEY_ARTIST = "no.hiof.larseknu.mafonzomuziki.intentservice.RESULT_DATA_ARTIST";
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

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (!initialized)
            initialize(intent);

        resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);
        return myLocalBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);

        resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    private void initialize(Intent intent) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        userDefinedPlayTime = sharedPreferences.getInt("pref_interval_play", DEFAULT_PLAY_TIME) * 1000;
        userDefinedPauseTime = sharedPreferences.getInt("pref_interval_pause", DEFAULT_PAUSE_TIME) * 1000;

        initializePlayerOnSuccessfulAuthentication(intent.getStringExtra("accessToken"));
        currentPlaylist = intent.getParcelableExtra("playlist");
        player.playUri(null, currentPlaylist.uri, 0, 0);
        currentState = IntervalPlaybackState.PLAYBACK_INTERVAL_PLAYING;
        startPlayCountdown(userDefinedPlayTime);

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
                sendTimeStamp(millisUntilFinished);
            }

            public void onFinish() {
                Log.d(TAG, "Play done, pausing music for: " + userDefinedPauseTime / 1000);
                player.pause(defaultOperationCallback);
                currentState = IntervalPlaybackState.PAUSED_INTERVAL_PLAYING;
                resultReceiver.send(RESULT_CODE_STATE_CHANGED, null);
                startPauseCountDown(userDefinedPauseTime);
            }
        };
        countDownTimer.start();
    }

    private void startPauseCountDown(long pauseTime) {
        countDownTimer = new CountDownTimer(pauseTime, 100) {

            public void onTick(long millisUntilFinished) {
                sendTimeStamp(millisUntilFinished);
            }

            public void onFinish() {
                Log.d("PlayActivity", "Pause done, playing music for "+ userDefinedPlayTime / 1000);
                player.skipToNext(defaultOperationCallback);
                currentState = IntervalPlaybackState.PLAYBACK_INTERVAL_PLAYING;
                resultReceiver.send(RESULT_CODE_STATE_CHANGED, null);
                startPlayCountdown(userDefinedPlayTime);
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
                bundle.putParcelable(RESULT_DATA_KEY_ARTIST, player.getMetadata());
                resultReceiver.send(RESULT_CODE_ARTIST, bundle);
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
