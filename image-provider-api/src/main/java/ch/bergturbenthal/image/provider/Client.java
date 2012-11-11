package ch.bergturbenthal.image.provider;

import android.net.Uri;

public class Client {
  public static class Album {
    public static final String ARCHIVE_NAME = "archiveName";
    public static final String ID = "_ID";
    public static final String NAME = "name";
    public static final String AUTOADD_DATE = "autoAddDate";
    public static final String SHOULD_SYNC = "shouldSync";
    public static final String SYNCED = "synced";
    public static final String[] ALL_COLUMNS = new String[] { ARCHIVE_NAME, ID, NAME, AUTOADD_DATE, SHOULD_SYNC, SYNCED };
  }

  public static final String AUTHORITY = "ch.bergturbenthal.image.provider";
  public static final Uri ALBUM_URI = Uri.parse("content://" + AUTHORITY + "/albums");
}
