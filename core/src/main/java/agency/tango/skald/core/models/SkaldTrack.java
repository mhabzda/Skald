package agency.tango.skald.core.models;

import android.net.Uri;

public class SkaldTrack extends SkaldPlayableEntity {
  public static final String PATH = "track";

  private final String artistName;
  private final String title;

  public SkaldTrack(Uri uri) {
    super(uri);
    artistName = "";
    title = "";
  }

  public SkaldTrack(Uri uri, String imageUrl, String artistName, String title) {
    super(uri, imageUrl);
    this.artistName = artistName;
    this.title = title;
  }

  public String getArtistName() {
    return artistName;
  }

  public String getTitle() {
    return title;
  }

  @Override
  public String toString() {
    return String.format("%s - %s", artistName, title);
  }
}
