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
import ch.bergturbenthal.raoa.provider.map.NotifyableCursor;
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
	private final Collection<WeakReference<NotifyableCursor>> allAlbumCursors = new ConcurrentLinkedQueue<WeakReference<NotifyableCursor>>();
	private final ThreadLocal<Boolean> collectingNotifications = new ModifiedFlagThreadLocal();;
	private final ThreadLocal<Collection<AlbumIndex>> singleAlbumCursorModified = new ThreadLocal<Collection<AlbumIndex>>() {
		@Override
		protected Collection<AlbumIndex> initialValue() {
			return new HashSet<AlbumIndex>();
		}
	};
	private final ConcurrentMap<AlbumIndex, Collection<WeakReference<NotifyableCursor>>> singleAlbumCursors = new ConcurrentHashMap<AlbumIndex, Collection<WeakReference<NotifyableCursor>>>();
	private final Collection<WeakReference<NotifyableCursor>> stateCursors = new ConcurrentLinkedQueue<WeakReference<NotifyableCursor>>();
	private final Collection<WeakReference<NotifyableCursor>> storageCursors = new ConcurrentLinkedQueue<WeakReference<NotifyableCursor>>();

	private final ThreadLocal<Boolean> storagesCursorModified = new ModifiedFlagThreadLocal();

	public NotifyableCursor addAllAlbumCursor(final NotifyableCursor cursor) {
		allAlbumCursors.add(new WeakReference<NotifyableCursor>(cursor));
		return cursor;
	}

	public NotifyableCursor addSingleAlbumCursor(final AlbumIndex albumIndex, final NotifyableCursor cursor) {
		final AlbumIndex albumKey = albumIndex;
		if (!singleAlbumCursors.containsKey(albumKey)) {
			singleAlbumCursors.putIfAbsent(albumKey, new ConcurrentLinkedQueue<WeakReference<NotifyableCursor>>());
		}
		singleAlbumCursors.get(albumKey).add(new WeakReference<NotifyableCursor>(cursor));
		return cursor;
	}

	public NotifyableCursor addStateCursor(final NotifyableCursor cursor) {
		stateCursors.add(new WeakReference<NotifyableCursor>(cursor));
		return cursor;
	}

	/**
	 * @param cursor
	 * @return
	 */
	public Cursor addStorageCursor(final NotifyableCursor cursor) {
		storageCursors.add(new WeakReference<NotifyableCursor>(cursor));
		return cursor;
	}

	public <V> V doWithNotify(final Callable<V> callable) {
		allAlbumCursorModified.set(Boolean.FALSE);
		storagesCursorModified.set(Boolean.FALSE);
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
			for (final Collection<WeakReference<NotifyableCursor>> cursors : singleAlbumCursors.values()) {
				notifyCursors(cursors);
			}
		}
	}

	private void notifyCursors(final Collection<WeakReference<NotifyableCursor>> cursors) {
		for (final Iterator<WeakReference<NotifyableCursor>> i = cursors.iterator(); i.hasNext();) {
			final WeakReference<NotifyableCursor> reference = i.next();
			final NotifyableCursor cursor = reference.get();
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
				final Collection<WeakReference<NotifyableCursor>> registeresCursors = singleAlbumCursors.get(albumId);
				if (registeresCursors != null) {
					notifyCursors(registeresCursors);
				}
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

}
