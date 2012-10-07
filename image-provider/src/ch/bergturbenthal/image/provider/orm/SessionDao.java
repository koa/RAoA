package ch.bergturbenthal.image.provider.orm;

import java.util.Collection;

public interface SessionDao<T, ID> {
  Collection<T> queryForAll();

  T read(ID key);

  void save(T value);
}
