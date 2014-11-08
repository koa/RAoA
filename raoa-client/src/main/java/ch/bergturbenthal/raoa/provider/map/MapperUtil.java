package ch.bergturbenthal.raoa.provider.map;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import android.database.AbstractWindowedCursor;
import android.database.Cursor;
import android.database.CursorWindow;
import android.util.Log;
import ch.bergturbenthal.raoa.provider.SortOrder;
import ch.bergturbenthal.raoa.provider.SortOrderEntry;
import ch.bergturbenthal.raoa.provider.SortOrderEntry.Order;
import ch.bergturbenthal.raoa.provider.criterium.Compare;
import ch.bergturbenthal.raoa.provider.criterium.Constant;
import ch.bergturbenthal.raoa.provider.criterium.Criterium;
import ch.bergturbenthal.raoa.provider.criterium.PairValue;
import ch.bergturbenthal.raoa.provider.criterium.Value;
import ch.bergturbenthal.raoa.provider.util.IndexedIterable;
import ch.bergturbenthal.raoa.provider.util.LazyLoader.Lookup;
import ch.bergturbenthal.raoa.util.Pair;

public class MapperUtil {

  @lombok.Value
  @AllArgsConstructor(suppressConstructorProperties = true)
  private static class FieldReaderOderEntry {
    private FieldReader<?>       fieldReader;
    private boolean              nullFirst;
    private SortOrderEntry.Order order;
  }

  @AllArgsConstructor(suppressConstructorProperties = true)
  public static class IndexedIterableList<T> implements IndexedIterable<T> {

    private final List<T> list;

    @Override
    public T get(final int index) {
      return list.get(index);
    }

    @Override
    public Iterator<T> iterator() {
      return list.iterator();
    }

    @Override
    public int size() {
      return list.size();
    }

  }

  private static class IndexedOderEntry {
    private int                  index;
    private boolean              nullFirst;
    private SortOrderEntry.Order order;
  }

  private interface RawFieldReader<V> {
    Object read(final V value) throws Exception;
  }

  private static final class WindowedLazyLoadingCursor<E> extends AbstractWindowedCursor implements NotifyableCursor {
    private final Runnable                    closeRunnable;
    private final String[]                    columnNamesArray;
    private final Map<String, FieldReader<E>> fieldReaders;
    private int                               lastWindowSize;
    private final IndexedIterable<E>          orderedIndizes;

    public WindowedLazyLoadingCursor(final IndexedIterable<E> orderedIndizes, final String[] columnNamesArray, final Map<String, FieldReader<E>> fieldReaders,
                                     final Runnable closeRunnable) {
      this.orderedIndizes = orderedIndizes;
      this.columnNamesArray = columnNamesArray;
      this.fieldReaders = fieldReaders;
      this.closeRunnable = closeRunnable;
      lastWindowSize = 200 / columnNamesArray.length + 1;
    }

    /**
     * If there is a window, clear it. Otherwise, creates a new window.
     *
     * @param name
     *          The window name.
     * @hide
     */
    protected void clearOrCreateWindow(final String name) {
      if (mWindow == null) {
        mWindow = new CursorWindow(name);
      } else {
        mWindow.clear();
      }
    }

    @Override
    public void close() {
      if (closeRunnable != null) {
        closeRunnable.run();
      }
      super.close();
    }

    private void fillWindow(final int requiredPos, final boolean forward) {
      final int windowStart;
      final int windowEnd;
      if (forward) {
        windowStart = requiredPos;
        windowEnd = Math.min(windowStart + lastWindowSize * 5 / 4, orderedIndizes.size());
      } else {
        windowStart = Math.max((requiredPos - lastWindowSize * 4 / 5), 0);
        windowEnd = Math.min(requiredPos + 1, orderedIndizes.size());
      }

      clearOrCreateWindow(MapperUtil.class.getName());
      final CursorWindow window = mWindow;
      window.acquireReference();
      try {
        int currentWindowStart = windowStart;
        while (true) {
          window.clear();
          window.setStartPosition(currentWindowStart);
          window.setNumColumns(columnNamesArray.length);
          int i = currentWindowStart;
          for (; i < windowEnd; i++) {
            if (!window.allocRow()) {
              // storage full
              lastWindowSize = Math.min(i - currentWindowStart - 1, 500);
              break;
            }
            for (int j = 0; j < columnNamesArray.length; j++) {
              final String columnName = columnNamesArray[j];
              final E index = orderedIndizes.get(i);
              if (index == null) {
                window.putNull(i, j);
              } else {
                final FieldReader<E> fieldReader = fieldReaders.get(columnName);
                switch (fieldReader.getType()) {
                case Cursor.FIELD_TYPE_NULL:
                  window.putNull(i, j);
                  break;
                case Cursor.FIELD_TYPE_STRING:
                  window.putString(fieldReader.getString(index), i, j);
                  break;
                case Cursor.FIELD_TYPE_FLOAT: {
                  final Number number = fieldReader.getNumber(index);
                  if (number == null) {
                    window.putNull(i, j);
                  } else {
                    window.putDouble(number.doubleValue(), i, j);
                  }
                }
                  break;
                case Cursor.FIELD_TYPE_INTEGER: {
                  final Number number = fieldReader.getNumber(index);
                  if (number == null) {
                    window.putNull(i, j);
                  } else {
                    window.putLong(number.longValue(), i, j);
                  }
                }
                  break;
                default:
                  throw new RuntimeException("Unsupportet type " + fieldReader.getType());
                }
              }
            }
          }
          if (requiredPos < i) {
            // window ok
            return;
          }
          // window to short -> recalculate window start and try again
          currentWindowStart = Math.max((requiredPos - lastWindowSize * 4 / 5), 0);

        }
      } finally {
        window.releaseReference();
      }
    }

    @Override
    public String[] getColumnNames() {
      return columnNamesArray;
    }

    @Override
    public int getCount() {
      return orderedIndizes.size();
    }

    @Override
    public void onChange(final boolean selfChange) {
      super.onChange(selfChange);
    }

    @Override
    public boolean onMove(final int oldPosition, final int newPosition) {
      // Make sure the row at newPosition is present in the window
      if (mWindow == null || newPosition < mWindow.getStartPosition() || newPosition >= (mWindow.getStartPosition() + mWindow.getNumRows())) {
        fillWindow(newPosition, oldPosition < newPosition);
      }

      return true;
    }
  }

  private static final Map<Class<?>, Map<String, FieldReader<?>>> fieldReaders     = new HashMap<Class<?>, Map<String, FieldReader<?>>>();

  private static Map<Class<?>, Class<?>>                          primitiveToBoxed = new HashMap<Class<?>, Class<?>>();
  private static String                                           TAG              = "MapperUtil";

  static {
    primitiveToBoxed.put(Integer.TYPE, Integer.class);
    primitiveToBoxed.put(Double.TYPE, Double.class);
    primitiveToBoxed.put(Float.TYPE, Float.class);
    primitiveToBoxed.put(Short.TYPE, Short.class);
    primitiveToBoxed.put(Long.TYPE, Long.class);
    primitiveToBoxed.put(Boolean.TYPE, Boolean.class);
  }

  private static <V> void appendFieldReader(final HashMap<String, FieldReader<V>> ret, final String fieldName, final Field field) {
    final Class<?> returnType = toBoxedType(field.getType());
    field.setAccessible(true);
    appendReader(ret, fieldName, new RawFieldReader<V>() {

      @Override
      public Object read(final V value) throws Exception {
        if (value == null) {
          return null;
        }
        return field.get(value);
      }
    }, returnType);
  }

  private static <V> void appendMethodReader(final HashMap<String, FieldReader<V>> ret, final String fieldName, final Method method) {
    final Class<?> returnType = toBoxedType(method.getReturnType());

    appendReader(ret, fieldName, new RawFieldReader<V>() {

      @Override
      public Object read(final V value) throws Exception {
        if (value == null) {
          return null;
        }
        return method.invoke(value);
      }
    }, returnType);
  }

  private static <V> void appendReader(final HashMap<String, FieldReader<V>> ret,
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
      final Object v2 = readValue(compare.getOp2(), columnLookup);
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
      case IN:
        return in(v1, v2);
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
    final String valueStr = String.valueOf(value).toLowerCase();
    final String patternStr = String.valueOf(pattern).toLowerCase();
    return valueStr.indexOf(patternStr) >= 0;
  }

  private static <E> Lookup<String, Object> createColumnLookup(final E entry, final Map<String, FieldReader<E>> fieldReaders) {
    final Lookup<String, Object> columnLookup = new Lookup<String, Object>() {
      @Override
      public Object get(final String key) {
        return lookupValue(entry, key, fieldReaders);
      }

    };
    return columnLookup;
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

  private static boolean eq(final Object v1, final Object v2) {
    if (v1 == v2) {
      return true;
    }
    if (v1 == null || v2 == null) {
      return false;
    }
    return v1.equals(v2);
  }

  private static <E> ArrayList<String> evalProjection(final String[] projection, final Map<String, FieldReader<E>> fieldReaders) {
    return projection != null ? new ArrayList<String>(Arrays.asList(projection)) : new ArrayList<String>(fieldReaders.keySet());
  }

  /**
   * @param v1
   * @param v2
   * @return
   */
  private static boolean in(final Object v1, final Object v2) {
    if (v2 == null) {
      return false;
    }
    if (v2 instanceof Collection) {
      return ((Collection) v2).contains(v1);
    }
    if (v2.getClass().isArray()) {
      return Arrays.asList((Object[]) v2).contains(v1);
    }
    return eq(v1, v2);
  }

  public static <E> NotifyableCursor loadCollectionIntoCursor(final Iterable<E> collection,
                                                              final String[] projection,
                                                              final Map<String, FieldReader<E>> fieldReaders,
                                                              final Criterium criterium,
                                                              final SortOrder order) {

    final List<String> columnNames = evalProjection(projection, fieldReaders);
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
    final long takeRestTime = 0;
    final long[] columnTimes = new long[columnNames.size()];
    Arrays.fill(columnTimes, 0);

    final List<FieldReader<E>> orderedFieldReaders = new ArrayList<FieldReader<E>>();
    final Map<String, Integer> columnIndices = new HashMap<String, Integer>();

    for (int i = 0; i < columnNames.size(); i++) {
      final String columnName = columnNames.get(i);
      columnIndices.put(columnName, Integer.valueOf(i));
      final FieldReader<E> fieldReader = fieldReaders.get(columnName);
      orderedFieldReaders.add(fieldReader);
    }

    for (final E entry : collection) {

      final Lookup<String, Object> columnLookup = createColumnLookup(entry, fieldReaders);
      if (criterium != null) {
        selectionTime -= System.currentTimeMillis();
        final boolean columnOk = columnOk(criterium, columnLookup);
        selectionTime += System.currentTimeMillis();
        if (!columnOk) {
          continue;
        }
      }

      final Object[] columnValues = new Object[columnNames.size()];
      for (int i = 0; i < columnNames.size(); i++) {
        columnValues[i] = columnLookup.get(columnNames.get(i));
      }

      entries.add(columnValues);
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
    for (final Object[] entryValues : entries) {
      if (entryValues.length == outputColumns) {
        cursor.addRow(entryValues);
      } else {
        final Object[] dataValues = new Object[outputColumns];
        System.arraycopy(entryValues, 0, dataValues, 0, outputColumns);
        cursor.addRow(dataValues);
      }
    }
    long timeSum = 0;
    final StringBuffer columnReport = new StringBuffer();
    for (int i = 0; i < columnTimes.length; i++) {
      final String name = columnNames.get(i);
      final long time = columnTimes[i];
      if (i > 0) {
        columnReport.append(", ");
      }
      columnReport.append(name);
      columnReport.append(": ");
      columnReport.append(time);
      timeSum += time;
    }
    Log.i(TAG, "Returning Cursor " + entries.size()
               + " entries, selection-Time: "
               + selectionTime
               + ", orderTime: "
               + orderTime
               + ", rest "
               + takeRestTime
               + " "
               + columnReport
               + ", Precise: "
               + (takeRestTime - timeSum));
    return cursor;
  }

  public static <E> NotifyableCursor loadConnectionIntoWindowedCursor(final Iterable<E> collection,
                                                                      final String[] projection,
                                                                      final Map<String, FieldReader<E>> fieldReaders,
                                                                      final Criterium criterium,
                                                                      final SortOrder order,
                                                                      final Runnable closeRunnable) {
    final long startTime = System.currentTimeMillis();
    final List<String> columnNames = evalProjection(projection, fieldReaders);

    final Iterable<E> filteredIndizes;
    final boolean filteredIsPrivate;
    if (criterium == null) {
      filteredIndizes = collection;
      filteredIsPrivate = false;
    } else {
      filteredIsPrivate = true;
      filteredIndizes = new ArrayList<E>();
      for (final E e : collection) {
        final Lookup<String, Object> columnLookup = createColumnLookup(e, fieldReaders);
        if (columnOk(criterium, columnLookup)) {
          ((Collection) filteredIndizes).add(e);
        }
      }
    }
    final long startOrdeTime = System.currentTimeMillis();
    Log.i(TAG, "Filter time : " + (startOrdeTime - startTime));
    final IndexedIterable<E> orderedIndizes;
    if (order == null) {
      if (filteredIndizes instanceof IndexedIterable) {
        orderedIndizes = (IndexedIterable<E>) filteredIndizes;
      } else if (filteredIndizes instanceof List) {
        orderedIndizes = new IndexedIterableList<E>((List<E>) filteredIndizes);
      } else if (filteredIndizes instanceof Collection) {
        orderedIndizes = new IndexedIterableList<E>(new ArrayList<E>((Collection<E>) filteredIndizes));
      } else {
        final ArrayList<E> list = new ArrayList<E>();
        for (final E e : filteredIndizes) {
          list.add(e);
        }
        orderedIndizes = new IndexedIterableList<E>(list);
      }
    } else {
      final List<E> orderedIndexList;
      if (filteredIndizes instanceof Collection) {
        if (filteredIsPrivate) {
          orderedIndexList = (List<E>) filteredIndizes;
        } else {
          orderedIndexList = new ArrayList<E>((Collection<E>) filteredIndizes);
        }
      } else {
        orderedIndexList = new ArrayList<E>();
        for (final E e : filteredIndizes) {
          orderedIndexList.add(e);
        }
      }
      final List<SortOrderEntry> entries = order.getEntries();
      final FieldReaderOderEntry[] indexedEntries = new FieldReaderOderEntry[entries.size()];
      for (int i = 0; i < entries.size(); i++) {
        final SortOrderEntry sortOrderEntry = entries.get(i);
        final FieldReader<E> fieldReader = fieldReaders.get(sortOrderEntry.getColumnName());
        indexedEntries[i] = new FieldReaderOderEntry(fieldReader, sortOrderEntry.isNullFirst(), sortOrderEntry.getOrder());
      }
      Collections.sort(orderedIndexList, new Comparator<E>() {

        @Override
        public int compare(final E lhs, final E rhs) {
          for (final FieldReaderOderEntry orderRule : indexedEntries) {
            final FieldReader<E> reader = (FieldReader<E>) orderRule.getFieldReader();
            final boolean ascending = orderRule.getOrder() == Order.ASC;
            final Comparable<Object> lValue = (Comparable) reader.getValue(lhs);
            final Comparable<Object> rValue = (Comparable) reader.getValue(rhs);
            final int cmp = compareRaw(lValue, rValue, orderRule.isNullFirst());
            if (cmp != 0) {
              return ascending ? cmp : -cmp;
            }
          }
          return 0;
        }
      });
      orderedIndizes = new IndexedIterableList<E>(orderedIndexList);
    }
    final long endPrepareTime = System.currentTimeMillis();
    Log.i(TAG, "Sort time: " + (endPrepareTime - startOrdeTime));
    final String[] columnNamesArray = columnNames.toArray(new String[columnNames.size()]);
    Log.i(TAG, "prepared lazy cursor of " + orderedIndizes.size() + " in " + (endPrepareTime - startTime));
    return new WindowedLazyLoadingCursor(orderedIndizes, columnNamesArray, fieldReaders, closeRunnable);
  }

  private static <E> Object lookupValue(final E row, final String column, final Map<String, FieldReader<E>> fieldReaders) {
    final FieldReader<E> fieldReader = fieldReaders.get(column);
    final int currentFieldType = fieldReader.getType();
    switch (currentFieldType) {
    case Cursor.FIELD_TYPE_NULL:
      return null;
    case Cursor.FIELD_TYPE_STRING:
      return fieldReader.getString(row);
    case Cursor.FIELD_TYPE_FLOAT:
    case Cursor.FIELD_TYPE_INTEGER:
      return fieldReader.getNumber(row);
    default:
      throw new RuntimeException("Unsupportet type " + currentFieldType);
    }
  }

  public static <V> Map<String, FieldReader<V>> makeAnnotatedFieldReaders(final Class<V> type) {
    synchronized (fieldReaders) {
      final Map<String, FieldReader<V>> storedReader = (Map<String, FieldReader<V>>) (Map<String, ?>) fieldReaders.get(type);
      if (storedReader != null) {
        return new HashMap<String, FieldReader<V>>(storedReader);
      }
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
      for (final Field field : type.getDeclaredFields()) {
        final CursorField annotation = field.getAnnotation(CursorField.class);
        if (annotation == null) {
          continue;
        }
        final String fieldName = annotation.value();
        appendFieldReader(ret, fieldName, field);
      }
      fieldReaders.put(type, (Map<String, FieldReader<?>>) (Map<String, ?>) ret);
      if (ret.isEmpty()) {
        throw new IllegalArgumentException("Class " + type.getName() + " has no annotations");
      }
      return ret;
    }
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
    if (v instanceof PairValue) {
      final PairValue pairValue = (PairValue) v;
      return new Pair<Object, Object>(readValue(pairValue.getV1(), columnLookup), readValue(pairValue.getV2(), columnLookup));
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
