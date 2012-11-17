package ch.bergturbenthal.image.provider.map;

public interface FieldReader<V> {
  Number getNumber(V value);

  String getString(V value);

  int getType();

  Object getValue(V value);

  boolean isNull(V value);
}