package ch.bergturbenthal.image.provider.service;

import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import ch.bergturbenthal.image.provider.Client;
import ch.bergturbenthal.image.provider.R;

public class NetworkActivity extends Activity {

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.teststarter);
    registerServiceCall(R.id.start, ServiceCommand.START);
    registerServiceCall(R.id.stop, ServiceCommand.STOP);
    registerServiceCall(R.id.poll, ServiceCommand.POLL);
    findViewById(R.id.list_albums).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(final View v) {
        testReadContentProvider();
      }
    });
    findViewById(R.id.dump_thumbnails).setOnClickListener(new OnClickListener() {

      @Override
      public void onClick(final View v) {
        dumpThumbnails();
      }

    });
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

  private void dumpThumbnails() {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(final Void... params) {
        final ContentResolver resolver = getContentResolver();
        for (int i = 1; i < 10; i++) {
          try {
            final InputStream stream = resolver.openInputStream(Client.makeThumbnailUri(0, i));
            try {

            } finally {
              stream.close();
            }
          } catch (final IOException e) {
            Log.w("Activity", "Cannot open Stream " + i, e);
          }
        }
        return null;
      }
    }.execute();
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

  private void testReadContentProvider() {
    final ContentResolver resolver = getContentResolver();
    final Cursor albumCursor = resolver.query(Client.ALBUM_URI, null, null, null, null);
    dumpCursor(albumCursor);
    if (albumCursor.moveToPosition(1)) {
      final int albumId = albumCursor.getInt(albumCursor.getColumnIndexOrThrow(Client.Album.ID));
      final Cursor entryCursor = resolver.query(Client.makeAlbumEntriesUri(albumId), null, null, null, null);
      dumpCursor(entryCursor);
      entryCursor.close();
    }
    albumCursor.close();

  }
}
