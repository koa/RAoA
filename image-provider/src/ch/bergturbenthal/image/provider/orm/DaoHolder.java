package ch.bergturbenthal.image.provider.orm;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.support.ConnectionSource;

public class DaoHolder {
  private final Map<Class<?>, RuntimeExceptionDao<?, ?>> existingDaos = new HashMap<Class<?>, RuntimeExceptionDao<?, ?>>();
  private final TransactionManager transactionManager;
  private final ConnectionSource connectionSource;

  public DaoHolder(final ConnectionSource connectionSource) {
    this.connectionSource = connectionSource;
    transactionManager = new TransactionManager(connectionSource);
  }

  public <V> V callInTransaction(final Callable<V> callable) {
    synchronized (transactionManager) {
      try {
        return transactionManager.callInTransaction(callable);
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public synchronized <T, ID> RuntimeExceptionDao<T, ID> getDao(final Class<T> type) {
    if (existingDaos.containsKey(type))
      return (RuntimeExceptionDao<T, ID>) existingDaos.get(type);
    try {
      final RuntimeExceptionDao<T, ID> dao = RuntimeExceptionDao.createDao(connectionSource, type);
      existingDaos.put(type, dao);
      return dao;
    } catch (final SQLException e) {
      throw new RuntimeException("Cannot initialize Dao for " + type.getName(), e);
    }
  }

  public TransactionManager getTransactionManager() {
    return transactionManager;
  }
}
