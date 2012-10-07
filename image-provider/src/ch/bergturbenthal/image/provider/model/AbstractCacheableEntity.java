package ch.bergturbenthal.image.provider.model;

public abstract class AbstractCacheableEntity<ID> implements CacheableEntity<ID> {
  private boolean touched;

  public AbstractCacheableEntity(final boolean isNew) {
    touched = isNew;
  }

  @Override
  public boolean isTouched() {
    return touched;
  }

  protected <T> void checkDifference(final T oldValue, final T newValue) {
    if (oldValue != newValue)
      if (oldValue == null || newValue == null) {
        touched = true;
      } else {
        touched |= oldValue.equals(newValue);
      }
  }
}
