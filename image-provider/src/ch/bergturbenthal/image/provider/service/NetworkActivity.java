package ch.bergturbenthal.image.provider.service;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import ch.bergturbenthal.image.provider.R;

public class NetworkActivity extends Activity {

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.teststarter);
    registerServiceCall(R.id.start, ServiceCommand.START);
    registerServiceCall(R.id.stop, ServiceCommand.STOP);
    registerServiceCall(R.id.poll, ServiceCommand.POLL);
  }

  private void dumpCursor(final Cursor cursor) {
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
        case Cursor.FIELD_TYPE_NULL:
          builder.append("null");
          break;
        case Cursor.FIELD_TYPE_INTEGER:
        case Cursor.FIELD_TYPE_FLOAT:
        case Cursor.FIELD_TYPE_STRING:
          builder.append(cursor.getString(i));
          break;
        }
      }
      Log.i("Test", "Row[" + cursor.getPosition() + "]: " + builder.toString());
    }
  }

  private void registerServiceCall(final int id, final ServiceCommand cmd) {
    findViewById(id).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(final View v) {
        final Intent intent = new Intent(NetworkActivity.this, SynchronisationServiceImpl.class);
        intent.putExtra("command", (Parcelable) cmd);
        startService(intent);
      }
    });
  }
}
