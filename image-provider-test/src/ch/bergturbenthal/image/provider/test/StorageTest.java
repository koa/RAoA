package ch.bergturbenthal.image.provider.test;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Callable;

import android.test.AndroidTestCase;
import android.util.Log;
import ch.bergturbenthal.image.provider.model.AlbumEntity;
import ch.bergturbenthal.image.provider.model.ClientEntity;
import ch.bergturbenthal.image.provider.orm.DatabaseHelper;
import ch.bergturbenthal.image.provider.orm.SessionDao;
import ch.bergturbenthal.image.provider.orm.SessionManager;

import com.j256.ormlite.android.AndroidConnectionSource;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.dao.RuntimeExceptionDao;

public class StorageTest extends AndroidTestCase {
  private AndroidConnectionSource connectionSource;

  public void testHelloWorld() {
    System.out.println("Hello world");
  }

  public void testStoreAlbum() throws SQLException {
    final SessionManager manager = new SessionManager(connectionSource);
    manager.executeInTransaction(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        assertEquals(0, manager.getDao(AlbumEntity.class).queryForAll().size());
        return null;
      }
    });
    manager.executeInTransaction(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        final SessionDao<AlbumEntity, String> albumDao = manager.getDao(AlbumEntity.class);
        final SessionDao<ClientEntity, UUID> clientDao = manager.getDao(ClientEntity.class);
        final AlbumEntity album = new AlbumEntity(UUID.randomUUID().toString());
        final ClientEntity client = new ClientEntity(album, "test-client");
        album.getInterestingClients().add(client);
        album.setName("TestAlbum");
        albumDao.save(album);
        clientDao.save(client);
        assertEquals(1, manager.getDao(AlbumEntity.class).queryForAll().size());
        return null;
      }
    });
    manager.executeInTransaction(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        assertEquals(1, manager.getDao(AlbumEntity.class).queryForAll().size());
        return null;
      }
    });
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final DatabaseHelper databaseHelper = new DatabaseHelper(getContext());
    connectionSource = new AndroidConnectionSource(databaseHelper);
    final RuntimeExceptionDao<AlbumEntity, String> albumDao =
                                                              new RuntimeExceptionDao<AlbumEntity, String>(
                                                                                                           (Dao<AlbumEntity, String>) DaoManager.createDao(connectionSource,
                                                                                                                                                           AlbumEntity.class));
    final RuntimeExceptionDao<ClientEntity, String> clientDao =
                                                                new RuntimeExceptionDao<ClientEntity, String>(
                                                                                                              (Dao<ClientEntity, String>) DaoManager.createDao(connectionSource,
                                                                                                                                                               ClientEntity.class));
    clear(albumDao);
    clear(clientDao);
  }

  private <T> void clear(final RuntimeExceptionDao<T, ?> dao) {
    for (final CloseableIterator<T> albumIter = dao.iterator(); albumIter.hasNext();) {
      final T next = albumIter.next();
      Log.i("CLEAR", "remove " + next);
      dao.delete(next);
    }
  }
}
