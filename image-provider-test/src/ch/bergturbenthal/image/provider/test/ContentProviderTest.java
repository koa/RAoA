package ch.bergturbenthal.image.provider.test;

import android.content.ContentResolver;
import android.database.Cursor;
import android.test.AndroidTestCase;
import android.util.Log;
import ch.bergturbenthal.image.provider.Data;

public class ContentProviderTest extends AndroidTestCase {
  public void testContentProvider() {
    final ContentResolver resolver = getContext().getContentResolver();
    final Cursor cursor = resolver.query(Data.ALBUM_URI, null, null, null, null);
    Log.i("Test", "Result: " + cursor);
    while (cursor.moveToNext()) {
      final int count = cursor.getColumnCount();
      final StringBuilder builder = new StringBuilder();
      for (int i = 0; i < count; i++) {
        if (i > 0)
          builder.append(", ");
        builder.append(cursor.getColumnName(i));
        builder.append(": ");
        switch (cursor.getType(i)) {
        case Cursor.FIELD_TYPE_BLOB:
          builder.append("Blob");
          break;
        case Cursor.FIELD_TYPE_FLOAT:
          builder.append(cursor.getFloat(i));
          break;
        case Cursor.FIELD_TYPE_INTEGER:
          builder.append(cursor.getInt(i));
          break;
        case Cursor.FIELD_TYPE_NULL:
          builder.append("null");
          break;
        case Cursor.FIELD_TYPE_STRING:
          builder.append(cursor.getString(i));
          break;
        }
      }
      Log.i("Test", "Row[" + cursor.getPosition() + "]: " + builder.toString());
    }
  }
}
