package ch.bergturbenthal.image.provider.test;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Callable;

import android.test.AndroidTestCase;
import android.util.Log;
import ch.bergturbenthal.image.provider.model.AlbumEntity;
import ch.bergturbenthal.image.provider.model.ClientEntity;
import ch.bergturbenthal.image.provider.orm.DatabaseHelper;

import com.j256.ormlite.android.AndroidConnectionSource;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.misc.TransactionManager;

public class StorageTest extends AndroidTestCase {
  private AndroidConnectionSource connectionSource;

  private <T> void clear(final RuntimeExceptionDao<T, ?> dao) {
    for (final CloseableIterator<T> albumIter = dao.iterator(); albumIter.hasNext();) {
      final T next = albumIter.next();
      Log.i("CLEAR", "remove " + next);
      dao.delete(next);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    connectionSource = DatabaseHelper.makeConnectionSource(getContext());
    final RuntimeExceptionDao<AlbumEntity, String> albumDao = RuntimeExceptionDao.createDao(connectionSource, AlbumEntity.class);
    final RuntimeExceptionDao<ClientEntity, String> clientDao = RuntimeExceptionDao.createDao(connectionSource, ClientEntity.class);
    clear(albumDao);
    clear(clientDao);
  }

  public void testHelloWorld() {
    System.out.println("Hello world");
  }

  public void testStoreAlbum() throws SQLException {

    final RuntimeExceptionDao<AlbumEntity, String> albumDao = RuntimeExceptionDao.createDao(connectionSource, AlbumEntity.class);
    final RuntimeExceptionDao<ClientEntity, String> clientDao = RuntimeExceptionDao.createDao(connectionSource, ClientEntity.class);

    TransactionManager.callInTransaction(connectionSource, new Callable<Void>() {

      @Override
      public Void call() throws Exception {
        assertEquals(0, albumDao.queryForAll().size());
        return null;
      }
    });

    TransactionManager.callInTransaction(connectionSource, new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        final AlbumEntity album = new AlbumEntity(UUID.randomUUID().toString());
        final ClientEntity client = new ClientEntity(album, "test-client");
        album.getInterestingClients().add(client);
        album.setName("TestAlbum");
        albumDao.create(album);
        clientDao.create(client);
        assertEquals(1, albumDao.queryForAll().size());
        return null;
      }
    });
    TransactionManager.callInTransaction(connectionSource, new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        assertEquals(1, albumDao.queryForAll().size());
        return null;
      }
    });
  }
}
