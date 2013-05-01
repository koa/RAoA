package ch.bergturbenthal.image.provider.util;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import android.content.UriMatcher;
import android.net.Uri;

public class EnumUriMatcher<E extends Enum<E>> {
  private final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
  private final Map<Integer, E> ordinalMap = new HashMap<Integer, E>();

  public EnumUriMatcher(final String authority, final Class<E> type) {
    for (final Field field : type.getFields()) {
      if (!field.isEnumConstant())
        continue;
      final Path annotation = field.getAnnotation(Path.class);
      if (annotation != null) {
        try {
          @SuppressWarnings("unchecked")
          final E value = (E) field.get(null);
          final int ordinal = value.ordinal();
          ordinalMap.put(Integer.valueOf(ordinal), value);
          matcher.addURI(authority, annotation.value(), ordinal);
        } catch (final IllegalArgumentException e) {
          throw new RuntimeException("cannot read field " + field, e);
        } catch (final IllegalAccessException e) {
          throw new RuntimeException("cannot read field " + field, e);
        }
      }
    }
  }

  public E match(final Uri uri) {
    return ordinalMap.get(Integer.valueOf(matcher.match(uri)));
  }
}
