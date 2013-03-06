/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.provider.util;

/**
 * TODO: add type comment.
 * 
 */
public class ObjectUtils {
  public static boolean objectEquals(final Object v1, final Object v2) {
    if (v1 == null && v2 == null)
      return true;
    if (v1 == null || v2 == null)
      return false;
    return v1.equals(v2);
  }
}
