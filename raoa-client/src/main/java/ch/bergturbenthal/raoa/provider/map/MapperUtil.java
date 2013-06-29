package ch.bergturbenthal.raoa.provider.map;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import android.database.Cursor;
import android.util.Log;
import ch.bergturbenthal.raoa.provider.SortOrder;
import ch.bergturbenthal.raoa.provider.SortOrderEntry;
import ch.bergturbenthal.raoa.provider.SortOrderEntry.Order;
import ch.bergturbenthal.raoa.provider.criterium.Compare;
import ch.bergturbenthal.raoa.provider.criterium.Constant;
import ch.bergturbenthal.raoa.provider.criterium.Criterium;
import ch.bergturbenthal.raoa.provider.criterium.Value;
import ch.bergturbenthal.raoa.provider.util.LazyLoader;
import ch.bergturbenthal.raoa.provider.util.LazyLoader.Lookup;

public class MapperUtil {
	private static class IndexedOderEntry {
		private int index;
		private boolean nullFirst;
		private SortOrderEntry.Order order;
	}

	private interface RawFieldReader<V> {
		Object read(final V value) throws Exception;
	}

	private static Map<Class<?>, Class<?>> primitiveToBoxed = new HashMap<Class<?>, Class<?>>();

	private static String TAG = "MapperUtil";
	static {
		primitiveToBoxed.put(Integer.TYPE, Integer.class);
		primitiveToBoxed.put(Double.TYPE, Double.class);
		primitiveToBoxed.put(Float.TYPE, Float.class);
		primitiveToBoxed.put(Short.TYPE, Short.class);
		primitiveToBoxed.put(Long.TYPE, Long.class);
		primitiveToBoxed.put(Boolean.TYPE, Boolean.class);
	}

	public static <I, O> Map<String, FieldReader<O>> delegateFieldReaders(final Map<String, FieldReader<I>> delegatingReaders, final Lookup<O, I> lookup) {
		final HashMap<String, FieldReader<O>> ret = new HashMap<String, FieldReader<O>>();
		for (final Entry<String, FieldReader<I>> dtoFieldReader : delegatingReaders.entrySet()) {
			final FieldReader<I> delegatingFieldReader = dtoFieldReader.getValue();

			ret.put(dtoFieldReader.getKey(), new FieldReader<O>() {

				@Override
				public Number getNumber(final O value) {
					return delegatingFieldReader.getNumber(lookup.get(value));
				}

				@Override
				public String getString(final O value) {
					return delegatingFieldReader.getString(lookup.get(value));
				}

				@Override
				public int getType() {
					return delegatingFieldReader.getType();
				}

				@Override
				public Object getValue(final O value) {
					return delegatingFieldReader.getValue(lookup.get(value));
				}

				@Override
				public boolean isNull(final O value) {
					return delegatingFieldReader.isNull(lookup.get(value));
				}
			});
		}
		return ret;
	}

	public static <E> NotifyableMatrixCursor loadCollectionIntoCursor(final Iterable<E> collection,
																																		final String[] projection,
																																		final Map<String, FieldReader<E>> fieldReaders,
																																		final Criterium criterium,
																																		final SortOrder order) {

		final List<String> columnNames = projection != null ? new ArrayList<String>(Arrays.asList(projection)) : new ArrayList<String>(fieldReaders.keySet());
		final int outputColumns = columnNames.size();

		final List<IndexedOderEntry> sortEntries = new ArrayList<MapperUtil.IndexedOderEntry>();
		if (order != null) {
			for (final SortOrderEntry orderEntry : order.getEntries()) {
				final Order orderEntryOrder = orderEntry.getOrder();
				if (orderEntryOrder == null) {
					continue;
				}
				final boolean nullFirst = orderEntry.isNullFirst();
				final String columnName = orderEntry.getColumnName();
				final int foundColumn = columnNames.indexOf(columnName);
				final int columnIndex;
				if (foundColumn < 0) {
					columnIndex = columnNames.size();
					columnNames.add(columnName);
				} else {
					columnIndex = foundColumn;
				}
				final IndexedOderEntry entry = new IndexedOderEntry();
				entry.index = columnIndex;
				entry.order = orderEntryOrder;
				entry.nullFirst = nullFirst;
				sortEntries.add(entry);
			}
		}
		final List<Object[]> entries = new ArrayList<Object[]>();
		Log.i(TAG, "Start collection rows");
		long selectionTime = 0;
		long takeRestTime = 0;
		for (final E entry : collection) {

			final Lookup<String, Object> columnLookup = LazyLoader.loadLazy(new Lookup<String, Object>() {

				@Override
				public Object get(final String key) {
					return fieldReaders.get(key).getValue(entry);
				}
			});
			if (criterium != null) {
				selectionTime -= System.currentTimeMillis();
				final boolean columnOk = columnOk(criterium, columnLookup);
				selectionTime += System.currentTimeMillis();
				if (!columnOk) {
					continue;
				}
			}

			final Object[] row = new Object[columnNames.size()];
			takeRestTime -= System.currentTimeMillis();
			for (int j = 0; j < row.length; j++) {
				row[j] = columnLookup.get(columnNames.get(j));
			}
			takeRestTime += System.currentTimeMillis();

			entries.add(row);
		}
		long orderTime = 0;
		Log.i(TAG, "End collection " + entries.size() + " rows");
		if (sortEntries.size() > 0) {
			orderTime -= System.currentTimeMillis();
			Collections.sort(entries, new Comparator<Object[]>() {
				@Override
				public int compare(final Object[] lhs, final Object[] rhs) {
					for (final IndexedOderEntry sortColumn : sortEntries) {
						final Comparable<Object> leftValue = (Comparable<Object>) lhs[sortColumn.index];
						final Comparable<Object> rightValue = (Comparable<Object>) rhs[sortColumn.index];
						final int cmp = compareRaw(leftValue, rightValue, sortColumn.nullFirst);
						if (cmp != 0) {
							return sortColumn.order == Order.ASC ? cmp : -cmp;
						}
					}
					return 0;
				}

			});
			orderTime += System.currentTimeMillis();
		}
		final NotifyableMatrixCursor cursor = new NotifyableMatrixCursor(columnNames.subList(0, outputColumns).toArray(new String[outputColumns]));
		if (outputColumns == columnNames.size()) {
			for (final Object[] entryValues : entries) {
				cursor.addRow(entryValues);
			}
		} else {
			for (final Object[] entryValues : entries) {
				final Object[] dataValues = new Object[outputColumns];
				System.arraycopy(entryValues, 0, dataValues, 0, outputColumns);
				cursor.addRow(dataValues);
			}
		}
		Log.i(TAG, "Returning Cursor " + entries.size() + " entries, selection-Time: " + selectionTime + ", orderTime: " + orderTime + ", rest " + takeRestTime);
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
						if (rawValue == null) {
							return null;
						}
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
					if (dateValue == null) {
						return null;
					}
					return Long.valueOf(dateValue.getTime());
				}

				@Override
				public String getString(final V value) {
					final Date dateValue = readDateValue(value);
					if (dateValue == null) {
						return null;
					}
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
		} else {
			throw new RuntimeException("Unknown Datatype " + returnType + " for field " + fieldName);
		}
	}

	private static boolean columnOk(final Criterium criterium, final Lookup<String, Object> columnLookup) {
		if (criterium instanceof Compare) {
			final Compare compare = (Compare) criterium;
			final Object v1 = readValue(compare.getOp1(), columnLookup);
			final Object v2 = readValue(compare.getOp1(), columnLookup);
			switch (compare.getOperator()) {
			case EQUALS:
				return eq(v1, v2);
			case GE:
				return compareRaw((Comparable<Object>) v1, (Comparable<Object>) v2, true) >= 0;
			case GT:
				return compareRaw((Comparable<Object>) v1, (Comparable<Object>) v2, true) > 0;
			case LE:
				return compareRaw((Comparable<Object>) v1, (Comparable<Object>) v2, true) <= 0;
			case LT:
				return compareRaw((Comparable<Object>) v1, (Comparable<Object>) v2, true) < 0;
			case MATCH:
				return match(v1, v2);
			case CONTAINS:
				return contains(v1, v2);
			}
		}
		if (criterium instanceof ch.bergturbenthal.raoa.provider.criterium.Boolean) {
			final ch.bergturbenthal.raoa.provider.criterium.Boolean boolCrit = (ch.bergturbenthal.raoa.provider.criterium.Boolean) criterium;
			switch (boolCrit.getOperator()) {
			case AND:
				return columnOk(boolCrit.getOp1(), columnLookup) && columnOk(boolCrit.getOp2(), columnLookup);
			case OR:
				return columnOk(boolCrit.getOp1(), columnLookup) || columnOk(boolCrit.getOp2(), columnLookup);
			case XOR:
				return columnOk(boolCrit.getOp1(), columnLookup) ^ columnOk(boolCrit.getOp2(), columnLookup);
			}
		}
		throw new RuntimeException("Cannot interprete criterium " + criterium);
	}

	private static int compareRaw(final Comparable<Object> leftValue, final Comparable<Object> rightValue, final boolean nullFirst) {
		if (leftValue == null) {
			return rightValue == null ? 0 : nullFirst ? -1 : 1;
		}
		if (rightValue == null) {
			return nullFirst ? 1 : -1;
		}
		return leftValue.compareTo(rightValue);
	}

	private static boolean contains(final Object value, final Object pattern) {
		final String valueStr = String.valueOf(value);
		final String patternStr = String.valueOf(pattern);
		return patternStr.indexOf(valueStr) >= 0;
	}

	private static boolean eq(final Object v1, final Object v2) {
		if (v1 == v2) {
			return true;
		}
		if (v1 == null || v2 == null) {
			return false;
		}
		return v1.equals(v2);
	}

	private static boolean match(final Object value, final Object pattern) {
		final String valueStr = String.valueOf(value);
		final String patternStr = String.valueOf(pattern);
		return Pattern.compile(patternStr).matcher(valueStr).matches();
	}

	private static Object readValue(final Value v, final Lookup<String, Object> columnLookup) {
		if (v instanceof Constant) {
			return ((Constant) v).getValue();
		}
		if (v instanceof ch.bergturbenthal.raoa.provider.criterium.Field) {
			final ch.bergturbenthal.raoa.provider.criterium.Field field = (ch.bergturbenthal.raoa.provider.criterium.Field) v;
			return columnLookup.get(field.getFieldName());
		}
		return null;
	}

	private static Class<?> toBoxedType(final Class<?> type) {
		final Class<?> boxed = primitiveToBoxed.get(type);
		if (boxed != null) {
			return boxed;
		}
		return type;
	}
}
