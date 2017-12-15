package no.hiof.larseknu.mafonzomuziki;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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

import com.spotify.sdk.android.player.Metadata;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Spotify;
import com.squareup.picasso.Picasso;

import java.text.DecimalFormat;

import kaaes.spotify.webapi.android.models.PlaylistSimple;
import no.hiof.larseknu.mafonzomuziki.service.IntervalMusicService;

public class PlayActivity extends AppCompatActivity  {
    private static final String TAG = "Mafunzo";

    private IntervalMusicService intervalMusicService;
    private boolean isBound = false;

    private TextView artistTextView;
    private TextView statusTextView;
    private TextView timeTextView;
    private ImageView coverArtImageView;
    private ImageButton playPauseButton;
    private ConstraintLayout container;

    private PlaylistSimple currentPlaylist;
    private String accessToken;

    private static DecimalFormat timerFormat = new DecimalFormat("00.00");

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            IntervalMusicService.MyLocalBinder myLocalBinder = (IntervalMusicService.MyLocalBinder) iBinder;

            intervalMusicService = myLocalBinder.getService();
            isBound = true;

            IntervalMusicService.IntervalPlaybackState currentState = intervalMusicService.getCurrentState();
            if (currentState != null) {
                intervalMusicServiceStateChanged(currentState);

                updateUIWithCurrentSong(intervalMusicService.getCurrentMetadata());
            }
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

        artistTextView = findViewById(R.id.artistTextView);
        statusTextView = findViewById(R.id.statusTextView);
        timeTextView = findViewById(R.id.timeTextView);
        coverArtImageView = findViewById(R.id.coverArtImageView);
        playPauseButton = findViewById(R.id.playPauseButton);
        container = findViewById(R.id.container);

        playPauseButton.setOnClickListener(playPausedButtonClickedWhileStateIsPlaying);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!isBound) {
            Intent intent = new Intent(this, IntervalMusicService.class);
            intent.putExtra(IntervalMusicService.EXTRA_RESULT_RECEIVER, new IntervalMusicReceiver(null));
            intent.putExtra("accessToken", accessToken);
            if (currentPlaylist != null)
                intent.putExtra("playlist", currentPlaylist);
            bindService(intent, connection, BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
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


    OnClickListener playPausedButtonClickedWhileStateIsPlaying = new OnClickListener() {
        @Override
        public void onClick(View view) {
            intervalMusicService.pause();
        }
    };

    OnClickListener playPausedButtonClickedWhileStateIsPaused = new OnClickListener() {
        @Override
        public void onClick(View view) {
            intervalMusicService.play();
        }
    };

    public void skipNextButtonClicked(View view) {
        intervalMusicService.skipNext();
    }

    public void skipPreviousButtonClicked(View view) {
        intervalMusicService.skipPrevious();
    }


    private class IntervalMusicReceiver extends ResultReceiver {
        public IntervalMusicReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);

            switch (resultCode) {
                case IntervalMusicService.RESULT_CODE_TIMESTAMP_UPDATED:
                    Long timeStamp = resultData.getLong(IntervalMusicService.RESULT_DATA_KEY_TIMESTAMP);
                    timeTextView.setText(timerFormat.format(timeStamp / 1000.0));
                    break;
                case IntervalMusicService.RESULT_CODE_ARTIST:
                    Metadata metadata = resultData.getParcelable(IntervalMusicService.RESULT_DATA_KEY_ARTIST);
                    updateUIWithCurrentSong(metadata);
                    break;
                case IntervalMusicService.RESULT_CODE_STATE_CHANGED:
                    intervalMusicServiceStateChanged(intervalMusicService.getCurrentState());
                    break;
            }
        }
    }


    public void intervalMusicServiceStateChanged(IntervalMusicService.IntervalPlaybackState currentState) {
        switch (currentState) {
            case PLAYBACK_INTERVAL_PAUSED:
                statusTextView.setText(R.string.paused);
                container.setBackgroundColor(ContextCompat.getColor(PlayActivity.this, R.color.backgroundPaused));
                playPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_circle_outline));
                playPauseButton.setOnClickListener(playPausedButtonClickedWhileStateIsPaused);
                break;
            case PLAYBACK_INTERVAL_PLAYING:
                statusTextView.setText(R.string.playing);
                container.setBackgroundColor(ContextCompat.getColor(PlayActivity.this, R.color.backgroundPlaying));
                playPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_circle_outline));
                playPauseButton.setOnClickListener(playPausedButtonClickedWhileStateIsPlaying);
                break;
            case PAUSED_INTERVAL_PAUSED:
                playPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_circle_outline));
                playPauseButton.setOnClickListener(playPausedButtonClickedWhileStateIsPaused);
                break;
            case PAUSED_INTERVAL_PLAYING:
                container.setBackgroundColor(ContextCompat.getColor(PlayActivity.this, R.color.backgroundPaused));
                statusTextView.setText(R.string.paused);
                playPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_circle_outline));
                playPauseButton.setOnClickListener(playPausedButtonClickedWhileStateIsPlaying);
                break;
        }
    }

    private void updateUIWithCurrentSong(Metadata metadata) {
        Metadata.Track currentTrack = metadata.currentTrack;

        if (currentTrack != null) {
            if (currentTrack.artistName != null && currentTrack.name != null)
                artistTextView.setText(currentTrack.artistName + " - " + currentTrack.name);

            if (currentTrack.albumCoverWebUrl != null) {
                Picasso.with(PlayActivity.this)
                        .load(currentTrack.albumCoverWebUrl)
                        .into(coverArtImageView);
            }
        }
    }
}
