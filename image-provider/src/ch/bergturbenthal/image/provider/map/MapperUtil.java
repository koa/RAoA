package ch.bergturbenthal.image.provider.map;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.database.Cursor;
import android.util.Log;

public class MapperUtil {
  private static Map<Class<?>, Class<?>> primitiveToBoxed = new HashMap<Class<?>, Class<?>>();
  static {
    primitiveToBoxed.put(Integer.TYPE, Integer.class);
    primitiveToBoxed.put(Double.TYPE, Double.class);
    primitiveToBoxed.put(Float.TYPE, Float.class);
    primitiveToBoxed.put(Short.TYPE, Short.class);
    primitiveToBoxed.put(Long.TYPE, Long.class);
    primitiveToBoxed.put(Boolean.TYPE, Boolean.class);
  }

  public static <V> Map<String, FieldReader<V>> makeAnnotaedFieldReaders(final Class<V> type) {
    final HashMap<String, FieldReader<V>> ret = new HashMap<String, FieldReader<V>>();
    for (final Method method : type.getMethods()) {
      final CursorField annotation = method.getAnnotation(CursorField.class);
      if (annotation == null)
        continue;
      if (method.getParameterTypes().length > 0) {
        Log.e("MapperUtil", "cannot query method " + method);
        continue;
      }
      final Class<?> returnType = toBoxedType(method.getReturnType());
      if (returnType == Void.class) {
        Log.e("MapperUtil", "cannot query method " + method);
        continue;
      }
      final String fieldName = annotation.value();
      if (CharSequence.class.isAssignableFrom(returnType)) {
        ret.put(fieldName, new StringFieldReader<V>() {

          @Override
          public String getString(final V value) {
            try {
              return ((CharSequence) method.invoke(value)).toString();
            } catch (final Throwable e) {
              throw new RuntimeException("cannot call method " + method, e);
            }
          }
        });
      } else if (Number.class.isAssignableFrom(returnType)) {
        final int fieldType =
                              returnType == Double.class || returnType == Float.class || BigDecimal.class.isAssignableFrom(returnType)
                                                                                                                                      ? Cursor.FIELD_TYPE_FLOAT
                                                                                                                                      : Cursor.FIELD_TYPE_INTEGER;
        ret.put(fieldName, new NumericFieldReader<V>(fieldType) {
          @Override
          public Number getNumber(final V value) {
            try {
              return ((Number) method.invoke(value));
            } catch (final Throwable e) {
              throw new RuntimeException("cannot call method " + method, e);
            }
          }
        });
      } else if (returnType == Boolean.class) {
        ret.put(fieldName, new BooleanFieldReader<V>() {
          @Override
          public Boolean getBooleanValue(final V value) {
            try {
              return ((Boolean) method.invoke(value));
            } catch (final Throwable e) {
              throw new RuntimeException("cannot call method " + method, e);
            }
          }
        });
      } else if (returnType == Date.class) {
        ret.put(fieldName, new NumericFieldReader<V>(Cursor.FIELD_TYPE_INTEGER) {
          @Override
          public Number getNumber(final V value) {
            final Date dateValue = readDateValue(method, value);
            if (dateValue == null)
              return null;
            return Long.valueOf(dateValue.getTime());
          }

          @Override
          public String getString(final V value) {
            final Date dateValue = readDateValue(method, value);
            if (dateValue == null)
              return null;
            return dateValue.toString();
          }

          private <V> Date readDateValue(final Method method, final V value) {
            try {
              return (Date) method.invoke(value);
            } catch (final Throwable e) {
              throw new RuntimeException("cannot call method " + method, e);
            }
          }
        });
      } else if (returnType.isEnum()) {
        ret.put(fieldName, new StringFieldReader<V>() {

          @Override
          public String getString(final V value) {
            try {
              final Enum<?> enumValue = (Enum<?>) method.invoke(value);
              return enumValue.name();
            } catch (final Throwable e) {
              throw new RuntimeException("cannot call method " + method, e);
            }
          }
        });
      } else
        throw new RuntimeException("Unknown Datatype " + returnType + " for field " + fieldName + ", " + method);
    }
    return ret;
  }

  private static Class<?> toBoxedType(final Class<?> type) {
    final Class<?> boxed = primitiveToBoxed.get(type);
    if (boxed != null)
      return boxed;
    return type;
  }
}
