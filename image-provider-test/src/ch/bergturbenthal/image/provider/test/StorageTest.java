package ch.bergturbenthal.image.provider.test;

import java.sql.SQLException;
import java.util.UUID;

import android.test.AndroidTestCase;
import ch.bergturbenthal.image.provider.model.Album;
import ch.bergturbenthal.image.provider.orm.DatabaseHelper;

import com.j256.ormlite.android.AndroidConnectionSource;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.dao.RuntimeExceptionDao;

public class StorageTest extends AndroidTestCase {
  private RuntimeExceptionDao<Album, String> albumDao;

  public void testHelloWorld() {
    System.out.println("Hello world");
  }

  public void testStoreAlbum() throws SQLException {
    final Album album = new Album(UUID.randomUUID().toString());
    album.setName("TestAlbum");
    albumDao.create(album);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final DatabaseHelper databaseHelper = new DatabaseHelper(getContext());
    final AndroidConnectionSource connectionSource = new AndroidConnectionSource(databaseHelper);
    albumDao = new RuntimeExceptionDao(DaoManager.createDao(connectionSource, Album.class));
  }
}
