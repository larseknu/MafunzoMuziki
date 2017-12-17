package no.hiof.larseknu.mafonzomuziki;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Album;
import kaaes.spotify.webapi.android.models.Pager;
import kaaes.spotify.webapi.android.models.PlaylistSimple;
import no.hiof.larseknu.mafonzomuziki.adapter.PlaylistRecyclerAdapter;
import no.hiof.larseknu.mafonzomuziki.service.IntervalMusicService;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class MainActivity extends AppCompatActivity implements SpotifyPlayer.NotificationCallback, ConnectionStateCallback {

    // region top secret
    // Request code will be used to verify if result comes from the login activity. Can be set to any integer.
    private static final int REQUEST_CODE = 43;
    private static final String CLIENT_ID = "0f835bef4ef44912ac05055c3d099b1b";

    private static final String REDIRECT_URI = "mafonzomuziki://callback";
    // endregion

    Player player;
    SpotifyService spotifyService;

    RecyclerView playlistView;
    ArrayList<PlaylistSimple> playlists;
    PlaylistRecyclerAdapter playlistAdapter;

    String accessToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();

        //AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);

        AuthenticationClient.openLoginInBrowser(this, request);

        playlists = new ArrayList<>();

        setUpRecyclerView();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri uri = intent.getData();
        if (uri != null) {
            AuthenticationResponse response = AuthenticationResponse.fromUri(uri);
            switch (response.getType()) {
                // Response was successful and contains auth token
                case TOKEN:
                    // Handle successful response
                    initializePlayerOnSuccessfullLogin(response.getAccessToken());
                    break;

                // Auth flow returned an error
                case ERROR:
                    // Handle error response
                    break;

                // Most likely auth flow was cancelled
                default:
                    // Handle other cases
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                initializePlayerOnSuccessfullLogin(response.getAccessToken());
            }
        }
    }


    private void initializePlayerOnSuccessfullLogin(String accessToken) {
        this.accessToken = accessToken;

        final Config playerConfig = new Config(this, accessToken, CLIENT_ID);

        SpotifyApi api = new SpotifyApi();

        api.setAccessToken(accessToken);

        spotifyService = api.getService();

        Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver() {
            @Override
            public void onInitialized(SpotifyPlayer spotifyPlayer) {
                player = spotifyPlayer;
                player.addConnectionStateCallback(MainActivity.this);
                player.addNotificationCallback(MainActivity.this);
            }

            @Override
            public void onError(Throwable throwable) {
                Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
            }
        });

        if (!this.accessToken.isEmpty()) {
            Intent intent = new Intent(this, IntervalMusicService.class);
            startService(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

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
            // Handle event type as necessary
            default:
                break;
        }
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

        spotifyService.getMyPlaylists(getAllMyPlaylists);
    }

    Callback<Pager<PlaylistSimple>> getAllMyPlaylists = new Callback<Pager<PlaylistSimple>>() {
        @Override
        public void success(Pager<PlaylistSimple> playlistSimplePager, Response response) {
            Log.d("MainActivity", "Number of playlists: " + playlistSimplePager.total);

            for (PlaylistSimple playlistSimple : playlistSimplePager.items) {
                //Log.d("MainActivity", playlistSimple.name);

                playlists.add(playlistSimple);
            }

            Log.d("MainActivity", "Next: " + playlistSimplePager.next + " Previous: " + playlistSimplePager.previous + " Offset: " + playlistSimplePager.offset);


            Map<String, Object> options = new HashMap<>();
            options.put(SpotifyService.OFFSET, playlistSimplePager.offset+20);
            options.put(SpotifyService.LIMIT, 20);

            playlistAdapter.notifyDataSetChanged();
            if (playlistSimplePager.next != null)
                spotifyService.getMyPlaylists(options, getAllMyPlaylists);
            else {

            }
        }

        @Override
        public void failure(RetrofitError error) {

        }
    };

    private void setUpRecyclerView() {

        playlistView = findViewById(R.id.collectionRecyclerView);
        playlistAdapter = new PlaylistRecyclerAdapter(MainActivity.this, playlists);
        playlistView.setAdapter(playlistAdapter);

        playlistAdapter.setOnItemClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = playlistView.getChildAdapterPosition(v);

                PlaylistSimple playlist = playlists.get(position);

                Intent intent = new Intent(MainActivity.this, PlayActivity.class);
                intent.putExtra("playlist", playlist);
                intent.putExtra("accessToken", accessToken);

                player.removeConnectionStateCallback(MainActivity.this);
                player.removeNotificationCallback(MainActivity.this);
                Spotify.destroyPlayer(this);

                startActivity(intent);
            }
        });

        GridLayoutManager mGridLayoutManager = new GridLayoutManager(this, 2); // (Context context, int spanCount)
        playlistView.setLayoutManager(mGridLayoutManager);

    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Error error) {
        Log.d("MainActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settingsMenuItem:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            case R.id.playActivityMenuItem:
                Intent playActivityIntent = new Intent(this, PlayActivity.class);
                playActivityIntent.putExtra("accessToken", accessToken);
                startActivity(playActivityIntent);
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
