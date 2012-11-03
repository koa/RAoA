package ch.bergturbenthal.image.provider.test;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Callable;

import android.test.AndroidTestCase;
import android.util.Log;
import ch.bergturbenthal.image.provider.model.AlbumEntity;
import ch.bergturbenthal.image.provider.model.ArchiveEntity;
import ch.bergturbenthal.image.provider.model.ClientEntity;
import ch.bergturbenthal.image.provider.orm.DatabaseHelper;

import com.j256.ormlite.android.AndroidConnectionSource;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.misc.TransactionManager;

public class StorageTest extends AndroidTestCase {
  private AndroidConnectionSource connectionSource;

  public void testStoreAlbum() throws SQLException {

    final RuntimeExceptionDao<ArchiveEntity, String> archiveDao = RuntimeExceptionDao.createDao(connectionSource, ArchiveEntity.class);
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
        final ArchiveEntity archive = new ArchiveEntity("dummy-archive");
        archiveDao.create(archive);
        final AlbumEntity album = new AlbumEntity(archive, UUID.randomUUID().toString());
        final ClientEntity client = new ClientEntity(album, "test-client");
        album.getInterestingClients().add(client);
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

  @SuppressWarnings("unchecked")
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    connectionSource = DatabaseHelper.makeConnectionSource(getContext());
    clear(RuntimeExceptionDao.<ArchiveEntity, String> createDao(connectionSource, ArchiveEntity.class));
    clear(RuntimeExceptionDao.<AlbumEntity, String> createDao(connectionSource, AlbumEntity.class));
    clear(RuntimeExceptionDao.<ClientEntity, String> createDao(connectionSource, ClientEntity.class));
  }

  private <T> void clear(final RuntimeExceptionDao<T, ?> dao) {
    for (final CloseableIterator<T> albumIter = dao.iterator(); albumIter.hasNext();) {
      final T next = albumIter.next();
      Log.i("CLEAR", "remove " + next);
      dao.delete(next);
    }
  }
}
