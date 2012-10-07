package ch.bergturbenthal.image.provider.model;

public interface CacheableEntity<ID> {

  ID getId();

  boolean isTouched();
}