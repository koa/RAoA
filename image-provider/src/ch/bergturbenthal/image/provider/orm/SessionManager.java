package ch.bergturbenthal.image.provider.orm;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import ch.bergturbenthal.image.provider.model.CacheableEntity;

import com.j256.ormlite.android.AndroidConnectionSource;
import com.j256.ormlite.dao.RuntimeExceptionDao;

public class SessionManager {

  private final AndroidConnectionSource connectionSource;

  private final ThreadLocal<SessionManagerCache> objectCache = new ThreadLocal<SessionManagerCache>() {

    @Override
    protected SessionManagerCache initialValue() {
      return new SessionManagerCache(connectionSource);
    }

  };

  private final ThreadLocal<Map<Class<?>, SessionDao<?, ?>>> sessionDao = new ThreadLocal<Map<Class<?>, SessionDao<?, ?>>>() {

    @Override
    protected Map<Class<?>, SessionDao<?, ?>> initialValue() {
      return new HashMap<Class<?>, SessionDao<?, ?>>();
    }
  };

  public SessionManager(final AndroidConnectionSource connectionSource) {
    this.connectionSource = connectionSource;
  }

  public <R> R executeInTransaction(final Callable<R> callable) {
    return objectCache.get().executeInTransaction(callable);
  }

  public <T extends CacheableEntity<ID>, ID> SessionDao<T, ID> getDao(final Class<T> type) {
    try {
      final SessionDao<T, ID> savedDao = (SessionDao<T, ID>) sessionDao.get().get(type);
      if (savedDao != null)
        return savedDao;
      final RuntimeExceptionDao<T, Object> delegateDao = RuntimeExceptionDao.createDao(connectionSource, type);
      final SessionManagerCache cache = objectCache.get();
      // delegateDao.setObjectCache(cache);
      final SessionDao<T, ID> createdDao = (SessionDao<T, ID>) cache.getSessionDao(type);
      sessionDao.get().put(type, createdDao);
      return createdDao;
    } catch (final SQLException e) {
      throw new RuntimeException(e);
    }
  }

}
