package no.hiof.larseknu.mafonzomuziki;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Metadata;
import com.spotify.sdk.android.player.PlaybackState;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;
import com.squareup.picasso.Picasso;

import java.text.DecimalFormat;

import kaaes.spotify.webapi.android.models.PlaylistSimple;

public class PlayActivity extends AppCompatActivity  {
    private static final String TAG = "Mafunzo";



    private IntervalMusicService intervalMusicService;
    private boolean isBound = false;

    private static final int REQUEST_CODE = 42;

    private TextView artistTextView;
    private TextView statusTextView;
    private TextView timeTextView;
    private ImageView coverArtImageView;
    private ImageButton playPauseButton;
    private ImageButton skipNextButton;
    private ImageButton skipPreviousButton;
    private ConstraintLayout container;

    private boolean paused = false;
    private PlaylistSimple currentPlaylist;
    private String accessToken;

    private boolean isManuallyPaused = false;


    private static int DEFAULT_PAUSE_TIME = 10;
    private static int DEFAULT_PLAY_TIME = 50;
    private long userDefinedPlayTime;
    private long userDefinedPauseTime;
    private static DecimalFormat timerFormat = new DecimalFormat("00.00");

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            IntervalMusicService.MyLocalBinder myLocalBinder = (IntervalMusicService.MyLocalBinder) iBinder;

            intervalMusicService = myLocalBinder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        currentPlaylist = getIntent().getParcelableExtra("playlist");
        accessToken = getIntent().getStringExtra("accessToken");

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        userDefinedPlayTime = sharedPreferences.getInt("pref_interval_play", DEFAULT_PLAY_TIME) * 1000;
        userDefinedPauseTime = sharedPreferences.getInt("pref_interval_pause", DEFAULT_PAUSE_TIME) * 1000;

        artistTextView = findViewById(R.id.artistTextView);
        statusTextView = findViewById(R.id.statusTextView);
        timeTextView = findViewById(R.id.timeTextView);
        coverArtImageView = findViewById(R.id.coverArtImageView);
        playPauseButton = findViewById(R.id.playPauseButton);
        skipNextButton = findViewById(R.id.skipNextButton);
        skipPreviousButton = findViewById(R.id.skipPreviousButton);
        container = findViewById(R.id.container);

        playPauseButton.setOnClickListener(PlayPausedButtonClickedWhileIntervalStateIsPlaying);

        IntervalMusicReceiver intervalMusicReceiver = new IntervalMusicReceiver(null);

        Intent intent = new Intent(this, IntervalMusicService.class);
        intent.putExtra("accessToken", accessToken);
        intent.putExtra(IntervalMusicService.EXTRA_RESULT_RECEIVER, intervalMusicReceiver);
        startService(intent);

        //if (!accessToken.isEmpty())
        //    initializePlayerOnSuccessfullAuthentication(accessToken);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = new Intent(this, IntervalMusicService.class);
        bindService(intent, connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, 	"References: " + Spotify.getReferenceCount());
        super.onDestroy();
    }







    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if ((keyCode == KeyEvent.KEYCODE_BACK))
        {
            //countDownTimer.cancel();
        }
        return super.onKeyDown(keyCode, event);
    }



    /*@Override
    public void onLoggedIn() {
        Log.d("PlayActivity", "User logged in");
    }

    private void startPlaylist(String uri) {
        player.playUri(null, uri, 0, 0);
        startPlayCountdown(userDefinedPlayTime);
        statusTextView.setText(R.string.playing);
    }*/


    OnClickListener PlayPausedButtonClickedWhileIntervalStateIsPlaying = new OnClickListener() {
        @Override
        public void onClick(View view) {
            intervalMusicService.playMusic(currentPlaylist.uri);

            /*
            if (player.getPlaybackState().isPlaying) {
                player.pause(defaultOperationCallback);
                countDownTimer.cancel();
                playPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_circle_outline));
                statusTextView.setText(R.string.paused);
                paused = true;
                int pauseColor = ContextCompat.getColor(view.getContext(), R.color.backgroundPaused);
                container.setBackgroundColor(pauseColor);
            }
            else {
                playPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_circle_outline));
                player.resume(defaultOperationCallback);
                startPlayCountdown(remainingTime);
                statusTextView.setText(R.string.playing);
                paused = false;
                container.setBackgroundColor(ContextCompat.getColor(view.getContext(), R.color.backgroundPlaying));
            }*/
        }
    };

    /*OnClickListener playPausedButtonClickedWhileIntervalStateIsPaused = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (!isManuallyPaused) {
                countDownTimer.cancel();
                playPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_circle_outline));
                isManuallyPaused = true;
            }
            else {
                startPauseCountDown(remainingTime);
                playPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_circle_outline));
                isManuallyPaused = false;
            }
        }
    };

    public void skipNextButtonClicked(View view) {
        if (player.getMetadata().nextTrack != null)
            player.skipToNext(defaultOperationCallback);
        else
            player.playUri(defaultOperationCallback, currentPlaylist.uri, 0, 0);
        if (paused)
            player.pause(defaultOperationCallback);
    }

    public void skipPreviousButtonClicked(View view) {
        if (player.getMetadata().prevTrack != null) {
            player.skipToPrevious(defaultOperationCallback);
        }
        else
            player.seekToPosition(defaultOperationCallback, 0);

        if (paused)
            player.pause(defaultOperationCallback);
    }*/

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


    private class IntervalMusicReceiver extends ResultReceiver {
        public IntervalMusicReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);

            Log.i("MyResultReceiver", "Thread: " + Thread.currentThread().getName());

            switch (resultCode) {
                case IntervalMusicService.RESULT_CODE_ARTIST:
                    final Metadata metadata = resultData.getParcelable(IntervalMusicService.RESULT_DATA_KEY_ARTIST);

                    artistTextView.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.i("IntervalMusicReceiver", "Thread: " + Thread.currentThread().getName());
                            UpdateUIWithCurrentSong(metadata);
                        }
                    });
                    break;
                case IntervalMusicService.RESULT_CODE_PLAYBACK_PAUSED:
                    statusTextView.post(new Runnable() {
                        @Override
                        public void run() {
                            statusTextView.setText(R.string.paused);
                        }
                    });
                    break;
                case IntervalMusicService.RESULT_CODE_TIMESTAMP_UPDATED:
                    final Long timeStamp = resultData.getLong(IntervalMusicService.RESULT_DATA_KEY_TIMESTAMP);

                    timeTextView.post(new Runnable() {
                        @Override
                        public void run() {
                            timeTextView.setText(timerFormat.format(timeStamp / 1000.0));
                        }
                    });
            }
        }
    }

    private void UpdateUIWithCurrentSong(Metadata metadata) {
        Metadata.Track currentTrack = metadata.currentTrack;

        if (currentTrack != null) {
            if (currentTrack.artistName != null && currentTrack.name != null)
                artistTextView.setText(currentTrack.artistName + " - " + currentTrack.name);

            if (currentTrack.albumCoverWebUrl != null) {
                Picasso.with(this)
                        .load(currentTrack.albumCoverWebUrl)
                        .into(coverArtImageView);
            }
        }
    }
}
