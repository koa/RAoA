/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.util;

/**
 * TODO: add type comment.
 *
 */
public class ObjectUtils {
	public static <T extends Comparable<T>> int compare(final T v1, final T v2) {
		if (v1 == v2)
			return 0;
		if (v1 == null)
			return 1;
		if (v2 == null)
			return -1;
		return v1.compareTo(v2);
	}

	public static boolean objectEquals(final Object v1, final Object v2) {
		if (v1 == null && v2 == null)
			return true;
		if (v1 == null || v2 == null)
			return false;
		return v1.equals(v2);
	}
}
