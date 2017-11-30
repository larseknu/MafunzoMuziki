package no.hiof.larseknu.mafonzomuziki;

import android.content.Intent;
import android.graphics.Color;
import android.os.CountDownTimer;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Metadata;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;

import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity implements SpotifyPlayer.NotificationCallback, ConnectionStateCallback {
    private static final String TAG = "Mafonzo";

    // region top secret
    private static final String CLIENT_ID = "0f835bef4ef44912ac05055c3d099b1b";

    private static final String REDIRECT_URI = "mafonzomuziki://callback";
    // endregion

    private static final int REQUEST_CODE = 42;

    private TextView artistTextView;
    private TextView statusTextView;
    private TextView timeTextView;
    private ImageButton playPauseButton;
    private ConstraintLayout container;

    private Player player;
    private CountDownTimer countDownTimer;
    private boolean paused = false;

    long remainingTime = 0;
    private static long PAUSE_TIME = 10000;
    private static long PLAY_TIME = 10000;
    private static DecimalFormat timerFormat = new DecimalFormat("00.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming", "playlist-read-private",
                "playlist-modify-private", "playlist-modify-public"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);

        artistTextView = findViewById(R.id.artistTextView);
        statusTextView = findViewById(R.id.statusTextView);
        timeTextView = findViewById(R.id.timeTextView);
        playPauseButton = findViewById(R.id.playPauseButton);
        container = findViewById(R.id.container);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Config playerConfig = new Config(this, response.getAccessToken(), CLIENT_ID);
                Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver() {
                    @Override
                    public void onInitialized(SpotifyPlayer spotifyPlayer) {
                        player = spotifyPlayer;
                        player.addConnectionStateCallback(MainActivity.this);
                        player.addNotificationCallback(MainActivity.this);
                        //player.setRepeat(null, true);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
                    }
                });
            }
        }
    }

    private void startPlayCountdown(long playTime) {
        countDownTimer = new CountDownTimer(playTime, 10) {

            public void onTick(long millisUntilFinished) {
                //Log.d("MainActivity", "Play seconds remaining " + millisUntilFinished / 1000);
                remainingTime = millisUntilFinished;
                timeTextView.setText(timerFormat.format(remainingTime / 1000.0));
            }

            public void onFinish() {
                Log.d("MainActivity", "Play done, pausing music for: " + PAUSE_TIME / 1000);
                player.pause(null);
                statusTextView.setText(R.string.paused);
                startPauseCountDown(PAUSE_TIME);
            }
        };
        countDownTimer.start();
    }

    private void startPauseCountDown(long pauseTime) {
        countDownTimer = new CountDownTimer(pauseTime, 10) {

            public void onTick(long millisUntilFinished) {
                //Log.d("MainActivity", "Pause seconds remaining " + millisUntilFinished / 1000);
                remainingTime = millisUntilFinished;
                timeTextView.setText(timerFormat.format(remainingTime / 1000.0));
            }

            public void onFinish() {
                Log.d("MainActivity", "Pause done, playing music for "+ PLAY_TIME / 1000);
                player.skipToNext(null);
                statusTextView.setText(R.string.playing);
                startPlayCountdown(PLAY_TIME);
            }
        };
        countDownTimer.start();
    }

    @Override
    protected void onDestroy() {
        Spotify.destroyPlayer(this);
        super.onDestroy();
    }

    @Override
    public void onPlaybackEvent(PlayerEvent playerEvent) {
        Log.d("MainActivity", "Playback event received: " + playerEvent.name());

        switch (playerEvent) {
            case kSpPlaybackNotifyPlay:
                UpdateUIWithCurrentSong(player.getMetadata());
            case kSpPlaybackNotifyNext:
                UpdateUIWithCurrentSong(player.getMetadata());
            case kSpPlaybackNotifyPrev:
                UpdateUIWithCurrentSong(player.getMetadata());
            default:
                break;
        }
    }

    private void UpdateUIWithCurrentSong(Metadata metadata) {
        Metadata.Track currentTrack = metadata.currentTrack;

        artistTextView.setText(currentTrack.artistName + " - "+ currentTrack.name);
    }

    @Override
    public void onPlaybackError(Error error) {
        Log.d("MainActivity", "Playback error received: " + error.name());
        switch (error) {
            // Handle error type as necessary
            default:
                break;
        }
    }

    @Override
    public void onLoggedIn() {
        Log.d("MainActivity", "User logged in");

        player.playUri(null, "spotify:user:johanbrook:playlist:2mtlhuFVOFMn6Ho3JmrLc2", 0, 0);
        startPlayCountdown(PLAY_TIME);
        statusTextView.setText(R.string.playing);
    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Error error) {
        Log.d("MainActivity", "Login failed: " + error.name());
    }

    @Override
    public void onTemporaryError() {
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    public void playButtonClicked(View view) {
        if (player.getPlaybackState().isPlaying) {
            player.pause(defaultOperationCallback);
            countDownTimer.cancel();
            playPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_circle_outline));
            statusTextView.setText(R.string.paused);
            paused = true;
            container.setBackgroundColor(Color.RED);
        }
        else {
            playPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_circle_outline));
            player.resume(defaultOperationCallback);
            startPlayCountdown(remainingTime);
            statusTextView.setText(R.string.playing);
            paused = false;
            container.setBackgroundColor(Color.GREEN);
        }
    }

    public void skipNextButtonClicked(View view) {
        player.skipToNext(defaultOperationCallback);
        if (paused)
            player.pause(defaultOperationCallback);
    }

    public void skipPreviousButtonClicked(View view) {
        player.skipToPrevious(defaultOperationCallback);
        if (paused)
            player.pause(defaultOperationCallback);
    }

    Player.OperationCallback defaultOperationCallback = new Player.OperationCallback() {
        @Override
        public void onSuccess() {

        }

        @Override
        public void onError(Error error) {
            Log.d(TAG, error.toString());
        }
    };
}
