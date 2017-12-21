package agency.tango.skald.example;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.squareup.picasso.Picasso;
import java.util.List;
import agency.tango.skald.R;
import agency.tango.skald.core.SkaldAuthService;
import agency.tango.skald.core.SkaldMusicService;
import agency.tango.skald.core.errors.AuthError;
import agency.tango.skald.core.errors.PlaybackError;
import agency.tango.skald.core.exceptions.AuthException;
import agency.tango.skald.core.listeners.OnPlaybackListener;
import agency.tango.skald.core.models.SkaldPlayableEntity;
import agency.tango.skald.core.models.SkaldPlaylist;
import agency.tango.skald.core.models.SkaldTrack;
import agency.tango.skald.core.models.SkaldUser;
import agency.tango.skald.core.models.TrackMetadata;
import agency.tango.skald.deezer.errors.DeezerAuthError;
import agency.tango.skald.deezer.provider.DeezerProvider;
import agency.tango.skald.spotify.errors.SpotifyAuthError;
import agency.tango.skald.spotify.provider.SpotifyProvider;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends Activity {
  private static final String TAG = MainActivity.class.getSimpleName();
  private static final int AUTH_SPOTIFY_REQUEST_CODE = 1334;
  private static final int AUTH_DEEZER_REQUEST_CODE = 1656;
  private static final String EMPTY = "";

  private SkaldMusicService skaldMusicService;
  private SkaldAuthService skaldAuthService;
  private ImageButton resumePauseButton;
  private ImageButton stopButton;
  private ImageView trackImage;
  private TextView artistName;
  private TextView title;
  private Button spotifyButton;
  private Button deezerButton;
  private Button tracksButton;
  private Button playlistButton;
  private TextView userName;
  private ImageView userAvatar;
  private ListView listView;

  private SkaldEntityAdapter adapter;
  private boolean isPlaying = false;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    resumePauseButton = findViewById(R.id.imageButton_play);
    stopButton = findViewById(R.id.imageButton_stop);
    trackImage = findViewById(R.id.image_cover);
    artistName = findViewById(R.id.text_artist);
    title = findViewById(R.id.text_title);
    spotifyButton = findViewById(R.id.button_login_spotify);
    deezerButton = findViewById(R.id.button_login_deezer);
    tracksButton = findViewById(R.id.button_tracks);
    playlistButton = findViewById(R.id.button_playlists);
    userName = findViewById(R.id.text_user_name);
    userAvatar = findViewById(R.id.image_user);
    adapter = new SkaldEntityAdapter(this);
    listView = findViewById(R.id.list_view_tracks);
    listView.setAdapter(adapter);
    listView.setOnItemClickListener((parent, listView, position, id) -> {
      final SkaldPlayableEntity item = (SkaldPlayableEntity) parent.getItemAtPosition(position);
      play(item);
    });

    skaldAuthService = new SkaldAuthService(getApplicationContext(), this::startAuthActivity);
    skaldMusicService = new SkaldMusicService(getApplicationContext());

    addOnErrorListener();
    addOnPlaybackListener();
    skaldMusicService.addOnLoadingListener(() -> {
      Log.d(TAG, "Loading track...");
    });

    spotifyButton.setOnClickListener(v -> {
      if (!skaldAuthService.isLoggedIn(SpotifyProvider.NAME)) {
        skaldAuthService.login(SpotifyProvider.NAME);
      } else {
        skaldAuthService.logout(SpotifyProvider.NAME);
        spotifyButton.setText(R.string.login_to_spotify);
        updateViewsAfterLoggingOut();
      }
    });
    deezerButton.setOnClickListener(v -> {
      if (!skaldAuthService.isLoggedIn(DeezerProvider.NAME)) {
        skaldAuthService.login(DeezerProvider.NAME);
      } else {
        skaldAuthService.logout(DeezerProvider.NAME);
        deezerButton.setText(R.string.login_to_deezer);
        updateViewsAfterLoggingOut();
      }
    });

    tracksButton.setOnClickListener(v -> searchTracks());

    playlistButton.setOnClickListener(v -> searchPlaylists());

    resumePauseButton.setOnClickListener(v -> {
      if (isPlaying) {
        skaldMusicService.pause()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new PlaybackEventCompletableObserver());
      } else {
        skaldMusicService.resume()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new PlaybackEventCompletableObserver());
      }
    });
    stopButton.setOnClickListener(v -> skaldMusicService.stop()
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new PlaybackEventCompletableObserver()));
  }

  @Override
  protected void onResume() {
    super.onResume();

    setSpotifyButtonText();
    setDeezerButtonText();
    getUsersAndUpdateUserViews();
  }

  @Override
  protected void onDestroy() {
    skaldMusicService.release();
    super.onDestroy();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == AUTH_SPOTIFY_REQUEST_CODE) {
      handleAuthenticationResult(resultCode, spotifyButton, R.string.logout_from_spotify);
    } else if (requestCode == AUTH_DEEZER_REQUEST_CODE) {
      handleAuthenticationResult(resultCode, deezerButton, R.string.logout_from_deezer);
    }
  }

  public void play(SkaldPlayableEntity skaldPlayableEntity) {
    skaldMusicService.play(skaldPlayableEntity)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new DisposableCompletableObserver() {
          @Override
          public void onComplete() {
            Log.d(TAG, "Played complete");
          }

          @Override
          public void onError(Throwable error) {
            Log.e(TAG, "Error during playing sth", error);
            if (error instanceof AuthException) {
              AuthError authError = ((AuthException) error).getAuthError();
              startAuthActivity(authError);
            }
          }
        });
  }

  public void searchTracks() {
    skaldMusicService.searchTracks("rock")
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new DisposableSingleObserver<List<SkaldTrack>>() {
          @Override
          public void onSuccess(List<SkaldTrack> skaldTracks) {
            adapter.clear();
            adapter.addAll(skaldTracks);
          }

          @Override
          public void onError(Throwable error) {
            Log.e(TAG, "Error during searching tracks", error);
          }
        });
  }

  public void searchPlaylists() {
    skaldMusicService.searchPlayLists("hip-hop")
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new DisposableSingleObserver<List<SkaldPlaylist>>() {
          @Override
          public void onSuccess(List<SkaldPlaylist> skaldPlaylists) {
            adapter.clear();
            adapter.addAll(skaldPlaylists);
          }

          @Override
          public void onError(Throwable error) {
            Log.e(TAG, "Error during searching playlists", error);
          }
        });
  }

  private void updateViewsAfterLoggingOut() {
    if (!isUserLoggedIn()) {
      updateUserViews(EMPTY, null);
      userName.setText(R.string.hello);
    } else {
      getUsersAndUpdateUserViews();
    }
  }

  private void getUsersAndUpdateUserViews() {
    if (isUserLoggedIn()) {
      skaldMusicService.getCurrentUsers()
          .subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(new DisposableSingleObserver<List<SkaldUser>>() {
            @Override
            public void onSuccess(List<SkaldUser> skaldUsers) {
              String message = String.format(getString(R.string.hello_user_name),
                  skaldUsers.get(0).getNickName());
              updateUserViews(message, skaldUsers.get(0).getImageUrl());
            }

            @Override
            public void onError(Throwable e) {
              Log.e(TAG, "Error during getting player");
            }
          });
    }
  }

  private boolean isUserLoggedIn() {
    return skaldAuthService.isLoggedIn(SpotifyProvider.NAME) ||
        skaldAuthService.isLoggedIn(DeezerProvider.NAME);
  }

  private void updateUserViews(String message, String imageUrl) {
    userName.setText(message);
    drawAnImage(imageUrl, userAvatar);
  }

  private void setSpotifyButtonText() {
    if (skaldAuthService.isLoggedIn(SpotifyProvider.NAME)) {
      spotifyButton.setText(R.string.logout_from_spotify);
    } else {
      spotifyButton.setText(R.string.login_to_spotify);
    }
  }

  private void setDeezerButtonText() {
    if (skaldAuthService.isLoggedIn(DeezerProvider.NAME)) {
      deezerButton.setText(R.string.logout_from_deezer);
    } else {
      deezerButton.setText(R.string.login_to_deezer);
    }
  }

  private void handleAuthenticationResult(int resultCode, Button serviceButton,
      @StringRes int buttonText) {
    if (resultCode == RESULT_OK) {
      serviceButton.setText(buttonText);
    } else {
      Log.e(TAG, "Authentication went wrong");
      Toast.makeText(this, R.string.authentication_error, Toast.LENGTH_LONG)
          .show();
    }
  }

  private void addOnErrorListener() {
    skaldMusicService.addOnErrorListener(
        exception -> Log.e(TAG, "Error in SkaldMusicService occurred", exception));
  }

  private void addOnPlaybackListener() {
    skaldMusicService.addOnPlaybackListener(new OnPlaybackListener() {
      @Override
      public void onPlayEvent(TrackMetadata trackMetadata) {
        Log.d(TAG, String.format("%s - %s", trackMetadata.getArtistsName(),
            trackMetadata.getTitle()));
        isPlaying = true;
        updateSongViews(trackMetadata);
      }

      @Override
      public void onPauseEvent() {
        Log.d(TAG, "Pause Event");
        isPlaying = false;
        updateResumePauseButton();
      }

      @Override
      public void onResumeEvent() {
        Log.d(TAG, "Resume Event");
        isPlaying = true;
        updateResumePauseButton();
      }

      @Override
      public void onStopEvent() {
        Log.d(TAG, "Stop event");
        isPlaying = false;
      }

      @Override
      public void onError(PlaybackError playbackError) {
        Log.e(TAG, "Playback error occurred", playbackError.getException());
      }
    });
  }

  private void startAuthActivity(AuthError authError) {
    if (authError.hasResolution()) {
      if (authError instanceof SpotifyAuthError) {
        startActivityForResult(authError.getResolution(), AUTH_SPOTIFY_REQUEST_CODE);
      } else if (authError instanceof DeezerAuthError) {
        startActivityForResult(authError.getResolution(), AUTH_DEEZER_REQUEST_CODE);
      }
    }
  }

  private void updateSongViews(TrackMetadata trackMetadata) {
    drawAnImage(trackMetadata.getImageUrl(), trackImage);
    artistName.setText(trackMetadata.getArtistsName());
    title.setText(trackMetadata.getTitle());
  }

  private void drawAnImage(String imageUrl, ImageView imageView) {
    Picasso
        .with(this)
        .load(imageUrl)
        .placeholder(R.drawable.ic_person_24dp_black)
        .into(imageView);
  }

  private void updateResumePauseButton() {
    if (isPlaying) {
      resumePauseButton.setImageResource(R.drawable.ic_action_pause);
    } else {
      resumePauseButton.setImageResource(R.drawable.ic_action_play);
    }
  }

  private class PlaybackEventCompletableObserver extends DisposableCompletableObserver {
    @Override
    public void onComplete() {
      Log.d(TAG, "Operation completed");
    }

    @Override
    public void onError(@NonNull Throwable e) {
      Log.e(TAG, "Error during playback operation", e);
    }
  }
}
