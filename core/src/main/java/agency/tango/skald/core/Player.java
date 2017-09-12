package agency.tango.skald.core;

import agency.tango.skald.core.listeners.OnPlayerReadyListener;
import agency.tango.skald.core.listeners.onPlaybackListener;
import agency.tango.skald.core.models.SkaldPlaylist;
import agency.tango.skald.core.models.SkaldTrack;

public interface Player {
  void play(SkaldTrack track);

  void play(SkaldPlaylist playlist);

  void stop();

  void pause();

  void resume();

  void release();

  void addPlayerReadyListener(OnPlayerReadyListener onPlayerReadyListener);

  void removePlayerReadyListener(OnPlayerReadyListener onPlayerReadyListener);

  void addOnPlabackListener(onPlaybackListener onPlaybackListener);

  void removeOnPlabackListener(onPlaybackListener onPlaybackListener);
}
