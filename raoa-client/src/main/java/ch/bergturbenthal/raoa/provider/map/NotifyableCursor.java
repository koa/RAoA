package ch.bergturbenthal.raoa.provider.map;

import android.database.Cursor;

public interface NotifyableCursor extends Cursor {
	void onChange(final boolean selfChange);
}
