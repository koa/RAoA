package ch.bergturbenthal.image.provider.service;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import ch.bergturbenthal.image.provider.Data;

public class NetworkActivity extends Activity {

  private NetworkReceiver receiver;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Registers BroadcastReceiver to track network connection changes.
    final IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    receiver = new NetworkReceiver();
    this.registerReceiver(receiver, filter);

  }

  @Override
  protected void onDestroy() {
    // TODO Auto-generated method stub
    super.onDestroy();
    // Unregisters BroadcastReceiver when app is destroyed.
    if (receiver != null) {
      this.unregisterReceiver(receiver);
    }
  }

  @Override
  protected void onResume() {
    testReadContentProvider();
  }

  @Override
  protected void onStart() {
    // TODO Auto-generated method stub
    super.onStart();
  }

  private void testReadContentProvider() {
    final ContentResolver resolver = getContentResolver();
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
