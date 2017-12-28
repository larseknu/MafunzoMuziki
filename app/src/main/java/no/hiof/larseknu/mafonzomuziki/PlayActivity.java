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
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
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
    private static final String TAG = "PlayActivity";

    private IntervalMusicService intervalMusicService;
    private boolean isBound = false;

    private TextView artistTextView;
    private TextView statusTextView;
    private TextView timeTextView;
    private ImageView coverArtImageView;
    private ImageView statusImageView;
    private ImageButton playPauseButton;
    private ConstraintLayout container;

    private PlaylistSimple currentPlaylist;
    private String accessToken;

    private IntervalMusicReceiver intervalMusicReceiver;

    private static DecimalFormat timerFormat = new DecimalFormat("00.00");

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            IntervalMusicService.MyLocalBinder myLocalBinder = (IntervalMusicService.MyLocalBinder) iBinder;

            intervalMusicService = myLocalBinder.getService();
            isBound = true;
            intervalMusicService.setResultReceiver(intervalMusicReceiver);

            IntervalMusicService.IntervalPlaybackState currentState = intervalMusicService.getCurrentState();
            if (currentState != null) {
                if (currentPlaylist != null && !currentPlaylist.uri.equals(intervalMusicService.getCurrentPlaylist().uri)) {
                    intervalMusicService.startNewPlayback(currentPlaylist);
                }
                else {
                    intervalMusicServiceStateChanged(currentState);
                    updateUIWithCurrentSong(intervalMusicService.getCurrentMetadata());
                    currentPlaylist = intervalMusicService.getCurrentPlaylist();

                    Long timeStamp = intervalMusicService.getCurrentTimeStamp();
                    timeTextView.setText(timerFormat.format(timeStamp / 1000.0));
                }
            }
            else {
                intervalMusicService.startNewPlayback(currentPlaylist);
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
        statusImageView = findViewById(R.id.statusImageView);
        playPauseButton = findViewById(R.id.playPauseButton);
        container = findViewById(R.id.container);

        playPauseButton.setOnClickListener(playPausedButtonClickedWhileStateIsPlaying);

        intervalMusicReceiver = new IntervalMusicReceiver(null);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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
                case IntervalMusicService.RESULT_CODE_METADATA_CHANGED:
                    Metadata metadata = resultData.getParcelable(IntervalMusicService.RESULT_DATA_KEY_METADATA);
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
                statusImageView.setImageDrawable(getResources().getDrawable(R.drawable.interval_relax));
                container.setBackgroundColor(ContextCompat.getColor(PlayActivity.this, R.color.backgroundPaused));
                playPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_circle_outline));
                playPauseButton.setOnClickListener(playPausedButtonClickedWhileStateIsPaused);
                break;
            case PLAYBACK_INTERVAL_PLAYING:
                statusTextView.setText(R.string.playing);
                statusImageView.setImageDrawable(getResources().getDrawable(R.drawable.interval_train));
                container.setBackgroundColor(ContextCompat.getColor(PlayActivity.this, R.color.backgroundPlaying));
                playPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_circle_outline));
                playPauseButton.setOnClickListener(playPausedButtonClickedWhileStateIsPlaying);
                break;
            case PAUSED_INTERVAL_PAUSED:
                statusTextView.setText(R.string.paused);
                statusImageView.setImageDrawable(getResources().getDrawable(R.drawable.interval_relax));
                playPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_circle_outline));
                playPauseButton.setOnClickListener(playPausedButtonClickedWhileStateIsPaused);
                break;
            case PAUSED_INTERVAL_PLAYING:
                container.setBackgroundColor(ContextCompat.getColor(PlayActivity.this, R.color.backgroundPaused));
                statusTextView.setText(R.string.paused);
                statusImageView.setImageDrawable(getResources().getDrawable(R.drawable.interval_relax));
                playPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_circle_outline));
                playPauseButton.setOnClickListener(playPausedButtonClickedWhileStateIsPlaying);
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                /*Intent upIntent = NavUtils.getParentActivityIntent(this);
                if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                    // This activity is NOT part of this app's task, so create a new task
                    // when navigating up, with a synthesized back stack.
                    TaskStackBuilder.create(this)
                            // Add all of this activity's parents to the back stack
                            .addNextIntentWithParentStack(upIntent)
                            // Navigate up to the closest parent
                            .startActivities();
                } else {
                    // This activity is part of this app's task, so simply
                    // navigate up to the logical parent activity.
                    NavUtils.navigateUpTo(this, upIntent);
                }*/
                return true;
        }
        return super.onOptionsItemSelected(item);
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
