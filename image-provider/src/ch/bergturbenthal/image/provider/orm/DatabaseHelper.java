package ch.bergturbenthal.image.provider.orm;

import java.sql.SQLException;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import ch.bergturbenthal.image.provider.model.Album;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {

  private static final String DATABASE_NAME = "images";
  private static final int DATABASE_VERSION = 1;

  public DatabaseHelper(final Context context) {
    super(context, DATABASE_NAME, null, DATABASE_VERSION);
  }

  @Override
  public void onCreate(final SQLiteDatabase database, final ConnectionSource connectionSource) {
    try {
      TableUtils.createTable(connectionSource, Album.class);
    } catch (final SQLException e) {
      Log.e("ORM", "Can't create database", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void onUpgrade(final SQLiteDatabase database, final ConnectionSource connectionSource, final int oldVersion, final int newVersion) {
    try {
      TableUtils.dropTable(connectionSource, Album.class, false);
      onCreate(database, connectionSource);
    } catch (final SQLException e) {
      Log.e("ORM", "Can't update database", e);
      throw new RuntimeException(e);
    }
  }

}
