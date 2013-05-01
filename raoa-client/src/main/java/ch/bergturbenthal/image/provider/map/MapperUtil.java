package ch.bergturbenthal.image.provider.map;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import android.database.Cursor;
import android.util.Log;

public class MapperUtil {
	private interface RawFieldReader<V> {
		Object read(final V value) throws Exception;
	}

	private static Map<Class<?>, Class<?>> primitiveToBoxed = new HashMap<Class<?>, Class<?>>();
	static {
		primitiveToBoxed.put(Integer.TYPE, Integer.class);
		primitiveToBoxed.put(Double.TYPE, Double.class);
		primitiveToBoxed.put(Float.TYPE, Float.class);
		primitiveToBoxed.put(Short.TYPE, Short.class);
		primitiveToBoxed.put(Long.TYPE, Long.class);
		primitiveToBoxed.put(Boolean.TYPE, Boolean.class);
	}

	public static <E> NotifyableMatrixCursor loadCollectionIntoCursor(final Iterable<E> collection,
																																		final String[] projection,
																																		final Map<String, FieldReader<E>> fieldReaders) {
		final String[] columnNames = projection != null ? projection : fieldReaders.keySet().toArray(new String[0]);
		final NotifyableMatrixCursor cursor = new NotifyableMatrixCursor(columnNames);
		for (final E entry : collection) {
			cursor.addRow(makeRow(entry, columnNames, fieldReaders));
		}
		return cursor;
	}

	public static <V> Map<String, FieldReader<V>> makeAnnotaedFieldReaders(final Class<V> type) {
		final HashMap<String, FieldReader<V>> ret = new HashMap<String, FieldReader<V>>();
		for (final Method method : type.getMethods()) {
			final CursorField annotation = method.getAnnotation(CursorField.class);
			if (annotation == null) {
				continue;
			}
			if (method.getParameterTypes().length > 0) {
				Log.e("MapperUtil", "cannot query method " + method);
				continue;
			}
			final String fieldName = annotation.value();
			appendMethodReader(ret, fieldName, method);
		}
		for (final Field field : type.getFields()) {
			final CursorField annotation = field.getAnnotation(CursorField.class);
			if (annotation == null) {
				continue;
			}
			final String fieldName = annotation.value();
			appendFieldReader(ret, fieldName, field);
		}
		return ret;
	}

	public static <V> Map<String, FieldReader<V>> makeNamedFieldReaders(final Class<V> type, final Map<String, String> mappedFields) {
		final HashMap<String, FieldReader<V>> ret = new HashMap<String, FieldReader<V>>();
		for (final Entry<String, String> entry : mappedFields.entrySet()) {
			final String objectFieldName = entry.getValue();
			final String targetFieldName = entry.getKey();
			final String getterName = "get" + Character.toUpperCase(objectFieldName.charAt(0)) + objectFieldName.substring(1);
			try {
				appendMethodReader(ret, targetFieldName, type.getMethod(getterName));
			} catch (final NoSuchMethodException e1) {
				final String isGetterName = "is" + Character.toUpperCase(objectFieldName.charAt(0)) + objectFieldName.substring(1);
				try {
					appendMethodReader(ret, targetFieldName, type.getMethod(isGetterName));
				} catch (final NoSuchMethodException e2) {
					// no getter -> try direct field access
					try {
						final Field field = type.getField(objectFieldName);
						appendFieldReader(ret, targetFieldName, field);
					} catch (final NoSuchFieldException e3) {
						throw new RuntimeException("No getter or field found for " + objectFieldName, e3);
					}
				}
			}
		}
		return ret;
	}

	private static <V> void appendFieldReader(final HashMap<String, FieldReader<V>> ret, final String fieldName, final Field field) {
		final Class<?> returnType = toBoxedType(field.getType());
		field.setAccessible(true);
		appendReader(ret, fieldName, new RawFieldReader<V>() {

			@Override
			public Object read(final V value) throws Exception {
				return field.get(value);
			}
		}, returnType);
	}

	private static <V> void appendMethodReader(final HashMap<String, FieldReader<V>> ret, final String fieldName, final Method method) {
		final Class<?> returnType = toBoxedType(method.getReturnType());

		appendReader(ret, fieldName, new RawFieldReader<V>() {

			@Override
			public Object read(final V value) throws Exception {
				return method.invoke(value);
			}
		}, returnType);
	}

	private static <V> void appendReader(	final HashMap<String, FieldReader<V>> ret,
																				final String fieldName,
																				final RawFieldReader<V> rawFieldReader,
																				final Class<?> returnType) {
		if (CharSequence.class.isAssignableFrom(returnType)) {
			ret.put(fieldName, new StringFieldReader<V>() {

				@Override
				public String getString(final V value) {
					try {
						final CharSequence rawValue = (CharSequence) rawFieldReader.read(value);
						if (rawValue == null)
							return null;
						return rawValue.toString();
					} catch (final Throwable e) {
						throw new RuntimeException("cannot query field " + fieldName, e);
					}
				}
			});
		} else if (Number.class.isAssignableFrom(returnType)) {
			final int fieldType = returnType == Double.class || returnType == Float.class || BigDecimal.class.isAssignableFrom(returnType) ? Cursor.FIELD_TYPE_FLOAT
					: Cursor.FIELD_TYPE_INTEGER;
			ret.put(fieldName, new NumericFieldReader<V>(fieldType) {
				@Override
				public Number getNumber(final V value) {
					try {
						return ((Number) rawFieldReader.read(value));
					} catch (final Throwable e) {
						throw new RuntimeException("cannot query field " + fieldName, e);
					}
				}
			});
		} else if (returnType == Boolean.class) {
			ret.put(fieldName, new BooleanFieldReader<V>() {
				@Override
				public Boolean getBooleanValue(final V value) {
					try {
						return ((Boolean) rawFieldReader.read(value));
					} catch (final Throwable e) {
						throw new RuntimeException("cannot query field " + fieldName, e);
					}
				}
			});
		} else if (returnType == Date.class) {
			ret.put(fieldName, new NumericFieldReader<V>(Cursor.FIELD_TYPE_INTEGER) {
				@Override
				public Number getNumber(final V value) {
					final Date dateValue = readDateValue(value);
					if (dateValue == null)
						return null;
					return Long.valueOf(dateValue.getTime());
				}

				@Override
				public String getString(final V value) {
					final Date dateValue = readDateValue(value);
					if (dateValue == null)
						return null;
					return dateValue.toString();
				}

				private Date readDateValue(final V value) {
					try {
						return (Date) rawFieldReader.read(value);
					} catch (final Throwable e) {
						throw new RuntimeException("cannot query field " + fieldName, e);
					}
				}
			});
		} else if (returnType.isEnum()) {
			ret.put(fieldName, new StringFieldReader<V>() {

				@Override
				public String getString(final V value) {
					try {
						final Enum<?> enumValue = (Enum<?>) rawFieldReader.read(value);
						return enumValue.name();
					} catch (final Throwable e) {
						throw new RuntimeException("cannot query field " + fieldName, e);
					}
				}
			});
		} else
			throw new RuntimeException("Unknown Datatype " + returnType + " for field " + fieldName);
	}

	private static <E> Object[] makeRow(final E entry, final String[] columnNames, final Map<String, FieldReader<E>> fieldReaders) {
		final Object[] row = new Object[columnNames.length];
		for (int j = 0; j < row.length; j++) {
			row[j] = fieldReaders.get(columnNames[j]).getValue(entry);
		}
		return row;
	}

	private static Class<?> toBoxedType(final Class<?> type) {
		final Class<?> boxed = primitiveToBoxed.get(type);
		if (boxed != null)
			return boxed;
		return type;
	}
}
