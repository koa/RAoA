package ch.bergturbenthal.image.provider.map;

public interface FieldReader<V> {
  Number getNumber(V value);

  String getString(V value);

  int getType();

  boolean isNull(V value);
}