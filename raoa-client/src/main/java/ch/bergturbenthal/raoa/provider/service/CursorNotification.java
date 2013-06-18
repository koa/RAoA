package ch.bergturbenthal.raoa.provider.service;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import android.database.Cursor;
import ch.bergturbenthal.raoa.provider.map.NotifyableMatrixCursor;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumIndex;

public class CursorNotification {
	/**
	 * TODO: add type comment.
	 * 
	 */
	private static class ModifiedFlagThreadLocal extends ThreadLocal<Boolean> {
		@Override
		protected Boolean initialValue() {
			return Boolean.FALSE;
		}
	}

	private final ThreadLocal<Boolean> allAlbumCursorModified = new ModifiedFlagThreadLocal();
	private final Collection<WeakReference<NotifyableMatrixCursor>> allAlbumCursors = new ConcurrentLinkedQueue<WeakReference<NotifyableMatrixCursor>>();
	private final ThreadLocal<Boolean> collectingNotifications = new ModifiedFlagThreadLocal();;
	private final ThreadLocal<Collection<AlbumIndex>> singleAlbumCursorModified = new ThreadLocal<Collection<AlbumIndex>>() {
		@Override
		protected Collection<AlbumIndex> initialValue() {
			return new HashSet<AlbumIndex>();
		}
	};
	private final ConcurrentMap<AlbumIndex, Collection<WeakReference<NotifyableMatrixCursor>>> singleAlbumCursors = new ConcurrentHashMap<AlbumIndex, Collection<WeakReference<NotifyableMatrixCursor>>>();
	private final Collection<WeakReference<NotifyableMatrixCursor>> stateCursors = new ConcurrentLinkedQueue<WeakReference<NotifyableMatrixCursor>>();
	private final Collection<WeakReference<NotifyableMatrixCursor>> storageCursors = new ConcurrentLinkedQueue<WeakReference<NotifyableMatrixCursor>>();

	private final ThreadLocal<Boolean> storagesCursorModified = new ModifiedFlagThreadLocal();

	public NotifyableMatrixCursor addAllAlbumCursor(final NotifyableMatrixCursor cursor) {
		allAlbumCursors.add(new WeakReference<NotifyableMatrixCursor>(cursor));
		return cursor;
	}

	public NotifyableMatrixCursor addSingleAlbumCursor(final AlbumIndex albumIndex, final NotifyableMatrixCursor cursor) {
		final AlbumIndex albumKey = albumIndex;
		if (!singleAlbumCursors.containsKey(albumKey)) {
			singleAlbumCursors.putIfAbsent(albumKey, new ConcurrentLinkedQueue<WeakReference<NotifyableMatrixCursor>>());
		}
		singleAlbumCursors.get(albumKey).add(new WeakReference<NotifyableMatrixCursor>(cursor));
		return cursor;
	}

	public NotifyableMatrixCursor addStateCursor(final NotifyableMatrixCursor cursor) {
		stateCursors.add(new WeakReference<NotifyableMatrixCursor>(cursor));
		return cursor;
	}

	/**
	 * @param cursor
	 * @return
	 */
	public Cursor addStorageCursor(final NotifyableMatrixCursor cursor) {
		storageCursors.add(new WeakReference<NotifyableMatrixCursor>(cursor));
		return cursor;
	}

	public <V> V doWithNotify(final Callable<V> callable) {
		allAlbumCursorModified.set(Boolean.FALSE);
		final Collection<AlbumIndex> modifiedCursors = singleAlbumCursorModified.get();
		modifiedCursors.clear();
		collectingNotifications.set(Boolean.TRUE);
		try {
			return callable.call();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		} finally {
			collectingNotifications.set(Boolean.FALSE);
			if (allAlbumCursorModified.get().booleanValue()) {
				notifyAllAlbumCursorsChanged();
			} else {
				notifyModifiedAlbums(modifiedCursors);
			}
			if (storagesCursorModified.get().booleanValue()) {
				notifyStoragesModified();
			}
		}
	}

	public void notifyAllAlbumCursorsChanged() {
		if (collectingNotifications.get().booleanValue()) {
			allAlbumCursorModified.set(Boolean.TRUE);
		} else {
			notifyCursors(allAlbumCursors);
			for (final Collection<WeakReference<NotifyableMatrixCursor>> cursors : singleAlbumCursors.values()) {
				notifyCursors(cursors);
			}
		}
	}

	public void notifyServerStateModified() {
		notifyCursors(stateCursors);
	}

	public void notifySingleAlbumCursorChanged(final AlbumIndex albumId) {
		if (collectingNotifications.get().booleanValue()) {
			singleAlbumCursorModified.get().add(albumId);
		} else {
			notifyModifiedAlbums(Arrays.asList(albumId));
		}
	}

	public void notifyStoragesModified() {
		if (collectingNotifications.get().booleanValue()) {
			storagesCursorModified.set(Boolean.TRUE);
		} else {
			notifyCursors(storageCursors);
		}
	}

	private void notifyCursors(final Collection<WeakReference<NotifyableMatrixCursor>> cursors) {
		for (final Iterator<WeakReference<NotifyableMatrixCursor>> i = cursors.iterator(); i.hasNext();) {
			final WeakReference<NotifyableMatrixCursor> reference = i.next();
			final NotifyableMatrixCursor cursor = reference.get();
			if (cursor != null) {
				cursor.onChange(false);
			} else {
				i.remove();
			}
		}
	}

	private void notifyModifiedAlbums(final Collection<AlbumIndex> modifiedCursors) {
		if (modifiedCursors.size() > 0) {
			notifyCursors(allAlbumCursors);
			for (final AlbumIndex albumId : modifiedCursors) {
				final Collection<WeakReference<NotifyableMatrixCursor>> registeresCursors = singleAlbumCursors.get(albumId);
				if (registeresCursors != null) {
					notifyCursors(registeresCursors);
				}
			}
		}
	}

}
