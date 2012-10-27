package ch.bergturbenthal.image.provider.orm;

import java.sql.SQLException;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import ch.bergturbenthal.image.provider.model.AlbumEntity;
import ch.bergturbenthal.image.provider.model.ArchiveEntity;
import ch.bergturbenthal.image.provider.model.ClientEntity;

import com.j256.ormlite.android.AndroidConnectionSource;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {

  private static final String DATABASE_NAME = "images";
  private static final int DATABASE_VERSION = 1;

  private static Class<?>[] entities = new Class[] { ArchiveEntity.class, AlbumEntity.class, ClientEntity.class };

  public static AndroidConnectionSource makeConnectionSource(final Context context) {
    return new AndroidConnectionSource(new DatabaseHelper(context));
  }

  public DatabaseHelper(final Context context) {
    super(context, DATABASE_NAME, null, DATABASE_VERSION);
  }

  @Override
  public void onCreate(final SQLiteDatabase database, final ConnectionSource connectionSource) {
    try {
      for (final Class<?> entityType : entities) {
        TableUtils.createTable(connectionSource, entityType);
      }
    } catch (final SQLException e) {
      Log.e("ORM", "Can't create database", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void onUpgrade(final SQLiteDatabase database, final ConnectionSource connectionSource, final int oldVersion, final int newVersion) {
    try {
      for (final Class<?> entityType : entities) {
        TableUtils.dropTable(connectionSource, entityType, false);
      }
      onCreate(database, connectionSource);
    } catch (final SQLException e) {
      Log.e("ORM", "Can't update database", e);
      throw new RuntimeException(e);
    }
  }
}
