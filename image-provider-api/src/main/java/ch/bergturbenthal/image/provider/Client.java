package ch.bergturbenthal.image.provider;

import android.net.Uri;
import android.net.Uri.Builder;

public class Client {
  public static class Album {
    public static final String ALBUM_CAPTURE_DATE = "albumCaptureDate";
    public static final String ARCHIVE_NAME = "archiveName";
    public static final String AUTOADD_DATE = "autoAddDate";
    public static final String ENTRY_COUNT = "entryCount";
    public static final String FULL_NAME = "fullName";
    public static final String ID = "_ID";
    public static final String NAME = "name";
    public static final String SHOULD_SYNC = "shouldSync";
    public static final String SYNCED = "synced";
    public static final String THUMBNAIL = "thumbnail";
  }

  public static class AlbumEntry {
    public static final String CAPTURE_DATE = "captureDate";
    public static final String ENTRY_TYPE = "entryType";
    public static final String ID = "_ID";
    public static final String LAST_MODIFIED = "lastModified";
    public static final String NAME = "fileName";
    public static final String THUMBNAIL = "thumbnail";
  }

  public static class ProgressEntry {
    public static final String ID = "_id";
    public static final String PROGRESS_ID = "progressId";
    public static final String STEP_COUNT = "stepCount";
    public static final String CURRENT_STEP_NR = "currentStepNr";
    public static final String PROGRESS_DESCRIPTION = "progressDescription";
    public static final String CURRENT_STATE_DESCRIPTION = "currentStateDescription";
    public static final String PROGRESS_TYPE = "progressType";
  }

  public static class ServerEntry {
    public static final String ID = "_id";
    public static final String SERVER_ID = "serverId";
    public static final String ARCHIVE_NAME = "archiveName";
    public static final String SERVER_NAME = "serverName";
  }

  public static final String AUTHORITY = "ch.bergturbenthal.image.provider";
  public static final Uri ALBUM_URI = Uri.parse("content://" + AUTHORITY + "/albums");
  public static final Uri SERVER_URI = Uri.parse("content://" + AUTHORITY + "/servers");

  public static Uri makeAlbumUri(final int albumId) {
    final Builder builder = ALBUM_URI.buildUpon();
    builder.appendPath(Integer.toString(albumId));
    builder.appendPath("entries");
    return builder.build();
  }

  public static Uri makeServerProgressUri(final String serverId) {
    final Builder builder = SERVER_URI.buildUpon();
    builder.appendPath(serverId);
    builder.appendPath("progress");
    return builder.build();
  }

  /**
   * Build a Content-Provider-URI for reading a given Thumbnail from
   * Content-Provider
   * 
   * @param albumId
   *          id of album
   * @param entryId
   *          id of image
   * @return built URI
   */
  public static Uri makeThumbnailUri(final int albumId, final int entryId) {
    final Builder builder = ALBUM_URI.buildUpon();
    builder.appendPath(Integer.toString(albumId));
    builder.appendPath("entries");
    builder.appendPath(Integer.toString(entryId));
    builder.appendPath("thumbnail");
    return builder.build();
  }
}
