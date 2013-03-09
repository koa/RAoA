package ch.bergturbenthal.image.provider.map;

import android.database.MatrixCursor;

public class NotifyableMatrixCursor extends MatrixCursor {

  public NotifyableMatrixCursor(final String[] columnNames) {
    super(columnNames);
  }

  @Override
  public void onChange(final boolean selfChange) {
    super.onChange(selfChange);
  }

}
