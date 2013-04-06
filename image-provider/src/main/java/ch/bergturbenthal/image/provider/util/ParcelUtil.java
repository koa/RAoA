package ch.bergturbenthal.image.provider.util;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import android.os.Parcel;

public class ParcelUtil {
  private static final Map<Class<? extends Enum<?>>, WeakReference<Enum<? extends Enum<?>>[]>> enumCache =
                                                                                                           new ConcurrentHashMap<Class<? extends Enum<?>>, WeakReference<Enum<? extends Enum<?>>[]>>();

  public static boolean readBoolean(final Parcel source) {
    return source.readByte() == 1;
  }

  public static Date readDate(final Parcel dest) {
    final long l = dest.readLong();
    if (l == Long.MIN_VALUE)
      return null;
    return new Date(l);
  }

  public static <E extends Enum<E>> E readEnum(final Parcel source, final Class<E> type) {
    final int ordinal = source.readInt();
    if (ordinal < 0)
      return null;
    return valuesOf(type)[ordinal];
  }

  public static void writeBoolean(final Parcel dest, final boolean value) {
    dest.writeByte((byte) (value ? 1 : 0));
  }

  public static void writeDate(final Date value, final Parcel dest) {
    if (value == null)
      dest.writeLong(Long.MIN_VALUE);
    else
      dest.writeLong(value.getTime());
  }

  public static void writeEnum(final Enum<?> e, final Parcel dest) {
    if (e == null)
      dest.writeInt(-1);
    else
      dest.writeInt(e.ordinal());
  }

  private static <E extends Enum<E>> E[] valuesOf(final Class<E> type) {
    {
      final WeakReference<Enum<? extends Enum<?>>[]> savedValue = enumCache.get(type);
      if (savedValue != null) {
        final Enum<? extends Enum<?>>[] enums = savedValue.get();
        if (enums != null)
          return (E[]) enums;
      }
    }
    synchronized (enumCache) {
      final WeakReference<Enum<? extends Enum<?>>[]> savedValue = enumCache.get(type);
      if (savedValue != null) {
        final Enum<? extends Enum<?>>[] enums = savedValue.get();
        if (enums != null)
          return (E[]) enums;
      }
      try {
        final Method method = type.getMethod("values");
        final E[] values = (E[]) method.invoke((Object[]) null);
        enumCache.put(type, new WeakReference<Enum<? extends Enum<?>>[]>(values));
        return values;
      } catch (final IllegalAccessException impossible) {
        throw new AssertionError();
      } catch (final InvocationTargetException impossible) {
        throw new AssertionError();
      } catch (final NoSuchMethodException e) {
        throw new AssertionError();
      }
    }
  }
}
