package ch.bergturbenthal.image.provider;

import android.net.Uri;

public class Data {
  public static class Album {
    public static String ID = "_ID";
    public static String NAME = "name";
    public static String AUTOADD_DATE = "autoAddDate";
    public static String SHOULD_SYNC = "shouldSync";
    public static String SYNCED = "synced";
  }

  public static final String AUTHORITY = "ch.bergturbenthal.image.provider";
  public static final Uri ALBUM_URI = Uri.parse("content://" + AUTHORITY + "/albums");
}
