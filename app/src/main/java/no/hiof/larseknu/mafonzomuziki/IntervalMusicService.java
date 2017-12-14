package no.hiof.larseknu.mafonzomuziki;

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
import android.widget.Toast;

import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;

public class IntervalMusicService extends Service implements ConnectionStateCallback, Player.NotificationCallback {
    public static final String EXTRA_RESULT_RECEIVER = "no.hiof.larseknu.mafonzomuziki.extra.RESULT_RECEIVER";

    public static final int RESULT_CODE_ARTIST = 1;
    public static final int RESULT_CODE_PLAYBACK_PAUSED = 2;
    public static final int RESULT_CODE_PLAYBACK_PLAY = 3;
    public static final int RESULT_CODE_TIMESTAMP_UPDATED = 4;
    public static final String RESULT_DATA_KEY_ARTIST = "no.hiof.larseknu.mafonzomuziki.intentservice.RESULT_DATA_ARTIST";
    public static final String RESULT_DATA_KEY_TIMESTAMP = "no.hiof.larseknu.mafonzomuziki.intentservice.RESULT_DATA_TIMESTAMP";

    private String TAG = "IntervalMusicService";

    private SpotifyPlayer player;

    private MyLocalBinder myLocalBinder = new MyLocalBinder();
    private ResultReceiver resultReceiver;

    private CountDownTimer countDownTimer;
    private long remainingTime = 0;

    // region top secret
    private static final String CLIENT_ID = "0f835bef4ef44912ac05055c3d099b1b";

    private static final String REDIRECT_URI = "mafonzomuziki://callback";
    // endregion

    public IntervalMusicService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initializePlayerOnSuccessfulAuthentication(intent.getStringExtra("accessToken"));

        resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return myLocalBinder;
    }


    public class MyLocalBinder extends Binder {
        public IntervalMusicService getService() {
            return IntervalMusicService.this;
        }
    }

    public void playMusic(String uri) {
        player.playUri(null, uri, 0, 0);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        startPlayCountdown(sharedPreferences.getInt("pref_interval_play", 20) * 1000);
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
                //Log.d("PlayActivity", "Play seconds remaining " + millisUntilFinished / 1000);
                remainingTime = millisUntilFinished;
                Bundle bundle = new Bundle();
                bundle.putLong(RESULT_DATA_KEY_TIMESTAMP, remainingTime);

                resultReceiver.send(RESULT_CODE_TIMESTAMP_UPDATED, bundle);
                //timeTextView.setText(timerFormat.format(remainingTime / 1000.0));
            }

            public void onFinish() {
                //Log.d("PlayActivity", "Play done, pausing music for: " + userDefinedPauseTime / 1000);
                player.pause(defaultOperationCallback);

                resultReceiver.send(RESULT_CODE_PLAYBACK_PAUSED, null);

                /*statusTextView.setText(R.string.paused);
                container.setBackgroundColor(ContextCompat.getColor(PlayActivity.this, R.color.backgroundPaused));
                startPauseCountDown(userDefinedPauseTime);
                playPauseButton.setOnClickListener(playPausedButtonClickedWhileIntervalStateIsPaused);
                skipNextButton.setEnabled(false);
                skipPreviousButton.setEnabled(false);*/
            }
        };
        countDownTimer.start();
    }

    /*private void startPauseCountDown(long pauseTime) {
        countDownTimer = new CountDownTimer(pauseTime, 10) {

            public void onTick(long millisUntilFinished) {
                remainingTime = millisUntilFinished;
                timeTextView.setText(timerFormat.format(remainingTime / 1000.0));
            }

            public void onFinish() {
                Log.d("PlayActivity", "Pause done, playing music for "+ userDefinedPlayTime / 1000);
                player.skipToNext(null);
                statusTextView.setText(R.string.playing);
                container.setBackgroundColor(ContextCompat.getColor(PlayActivity.this, R.color.backgroundPlaying));
                startPlayCountdown(userDefinedPlayTime);
                playPauseButton.setOnClickListener(PlayPausedButtonClickedWhileIntervalStateIsPlaying);
                skipNextButton.setEnabled(true);
                skipPreviousButton.setEnabled(true);
            }
        };
        countDownTimer.start();
    }*/

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
