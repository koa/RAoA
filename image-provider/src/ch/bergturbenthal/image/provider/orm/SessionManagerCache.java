package ch.bergturbenthal.image.provider.orm;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import android.util.Log;
import ch.bergturbenthal.image.provider.model.CacheableEntity;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.DatabaseTableConfig;

final class SessionManagerCache {

  private class TypeData<T extends CacheableEntity, ID> implements SessionDao<T, ID> {
    private final Dao<T, ID> delegateDao;
    private final Collection<ID> deletedEntries = new HashSet<ID>();
    private final Collection<ID> oldEntries = new HashSet<ID>();
    private final Map<ID, T> entries = new HashMap<ID, T>();
    private boolean fullyLoaded = false;
    private DatabaseTableConfig<T> tableConfig;

    public TypeData(final Class<T> type) {
      try {
        delegateDao = DaoManager.createDao(connectionSource, type);
        tableConfig = DatabaseTableConfig.fromClass(connectionSource, type);
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public Collection<T> queryForAll() {
      loadFully();
      final ArrayList<T> ret = new ArrayList<T>();
      for (final Entry<ID, T> cacheEntry : entries.entrySet()) {
        if (deletedEntries.contains(cacheEntry.getKey()))
          continue;
        ret.add(cacheEntry.getValue());
      }
      return ret;
    }

    @Override
    public T read(final ID key) {
      final T cachedValue = entries.get(key);
      if (cachedValue != null)
        return cachedValue;
      try {
        synchronized (this) {
          final T loadedData = delegateDao.queryForId(key);
          if (loadedData != null)
            oldEntries.add(key);
          entries.put(key, loadedData);
          return loadedData;
        }
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
    }

    public synchronized void remove(final ID id) {
      entries.remove(id);
      deletedEntries.add(id);
    }

    @Override
    public void save(final T value) {
      try {
        final ID id = delegateDao.extractId(value);
        if (!entries.containsKey(id) && delegateDao.idExists(id)) {
          oldEntries.add(id);
        }
        deletedEntries.remove(id);
        entries.put(id, value);
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
    }

    public synchronized T updateId(final ID oldId, final ID newId) {
      final T value = read(oldId);
      entries.put(newId, value);
      deletedEntries.add(oldId);
      deletedEntries.remove(newId);
      return value;
    }

    private void flush() {
      for (final Entry<ID, T> cacheEntry : entries.entrySet()) {
        final T value = cacheEntry.getValue();
        if (value.isTouched()) {
          try {
            if (oldEntries.contains(value.getId())) {
              Log.i("FLUSH", "update: " + tableConfig.getTableName() + ":" + value);
              delegateDao.update(value);
            } else {
              Log.i("FLUSH", "create: " + tableConfig.getTableName() + ":" + value);
              delegateDao.create(value);
            }
          } catch (final SQLException e) {
            throw new RuntimeException("Cannot write " + value, e);
          }
        } else
          Log.i("FLUSH", "untouched: " + tableConfig.getTableName() + ":" + value);
      }
      try {
        delegateDao.deleteIds(deletedEntries);
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
      reset();
    }

    private synchronized void loadFully() {
      if (!fullyLoaded) {
        try {
          final List<T> allEntries = delegateDao.queryForAll();
          for (final T t : allEntries) {
            final ID id = delegateDao.extractId(t);
            oldEntries.add(id);
            if (!entries.containsKey(id)) {
              entries.put(id, t);
            }
          }
          fullyLoaded = true;
        } catch (final SQLException e) {
          throw new RuntimeException(e);
        }
      }
    }

    private void reset() {
      entries.clear();
      deletedEntries.clear();
      oldEntries.clear();
      fullyLoaded = false;
    }
  }

  public ConnectionSource connectionSource;
  private final Map<Class, TypeData> types = new HashMap<Class, TypeData>();

  /**
   * @param connectionSource
   */
  SessionManagerCache(final ConnectionSource connectionSource) {
    this.connectionSource = connectionSource;
  }

  public <R> R executeInTransaction(final Callable<R> callable) {
    try {
      return TransactionManager.callInTransaction(connectionSource, new Callable<R>() {

        @Override
        public R call() throws Exception {
          final R ret = callable.call();
          flushTransaction();
          return ret;
        }
      });
    } catch (final Exception e) {
      throw new RuntimeException(e);
    } finally {
      closeTransaction();
    }
  }

  public <T extends CacheableEntity, ID> SessionDao<T, ID> getSessionDao(final Class<T> type) {
    return (SessionDao<T, ID>) getData(type);

  }

  private void closeTransaction() {
    for (final TypeData data : types.values()) {
      data.reset();
    }
  }

  private void flushTransaction() {
    for (final TypeData data : types.values()) {
      data.flush();
    }

  }

  private <T extends CacheableEntity, ID> TypeData<T, ID> getData(final Class<T> type) {
    final TypeData<T, ID> savedData = types.get(type);
    if (savedData != null)
      return savedData;
    final TypeData<T, ID> newTypeData = new TypeData<T, ID>(type);
    types.put(type, newTypeData);
    return newTypeData;
  }

}