package ch.bergturbenthal.image.provider.map;

public abstract class NumericFieldReader<V> implements FieldReader<V> {
  final int type;

  public NumericFieldReader(final int type) {
    this.type = type;
  }

  @Override
  public String getString(final V value) {
    return getNumber(value).toString();
  }

  @Override
  public int getType() {
    return type;
  }

  @Override
  public Object getValue(final V value) {
    return getNumber(value);
  }

  @Override
  public boolean isNull(final V value) {
    return getNumber(value) == null;
  }
}