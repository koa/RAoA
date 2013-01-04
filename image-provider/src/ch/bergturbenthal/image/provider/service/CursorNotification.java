package ch.bergturbenthal.image.provider.service;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import ch.bergturbenthal.image.provider.map.NotifyableMatrixCursor;

public class CursorNotification {
  private final ThreadLocal<Boolean> allAlbumCursorModified = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };
  private final ThreadLocal<Collection<Integer>> singleAlbumCursorModified = new ThreadLocal<Collection<Integer>>() {

    @Override
    protected Collection<Integer> initialValue() {
      return new HashSet<Integer>();
    }

  };

  private final Collection<WeakReference<NotifyableMatrixCursor>> allAlbumCursors =
                                                                                    new ConcurrentLinkedQueue<WeakReference<NotifyableMatrixCursor>>();

  private final Collection<WeakReference<NotifyableMatrixCursor>> stateCursors = new ConcurrentLinkedQueue<WeakReference<NotifyableMatrixCursor>>();

  private final ConcurrentMap<Integer, Collection<WeakReference<NotifyableMatrixCursor>>> singleAlbumCursors =
                                                                                                               new ConcurrentHashMap<Integer, Collection<WeakReference<NotifyableMatrixCursor>>>();

  public NotifyableMatrixCursor addAllAlbumCursor(final NotifyableMatrixCursor cursor) {
    allAlbumCursors.add(new WeakReference<NotifyableMatrixCursor>(cursor));
    return cursor;
  }

  public NotifyableMatrixCursor addSingleAlbumCursor(final int album, final NotifyableMatrixCursor cursor) {
    final Integer albumKey = Integer.valueOf(album);
    if (!singleAlbumCursors.containsKey(albumKey))
      singleAlbumCursors.putIfAbsent(albumKey, new ConcurrentLinkedQueue<WeakReference<NotifyableMatrixCursor>>());
    singleAlbumCursors.get(albumKey).add(new WeakReference<NotifyableMatrixCursor>(cursor));
    return cursor;
  }

  public NotifyableMatrixCursor addStateCursor(final NotifyableMatrixCursor cursor) {
    stateCursors.add(new WeakReference<NotifyableMatrixCursor>(cursor));
    return cursor;
  }

  public <V> V doWithNotify(final Callable<V> callable) {
    allAlbumCursorModified.set(Boolean.FALSE);
    singleAlbumCursorModified.get().clear();
    try {
      return callable.call();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    } finally {
      if (allAlbumCursorModified.get().booleanValue()) {
        notifyCursors(allAlbumCursors);
        for (final Collection<WeakReference<NotifyableMatrixCursor>> cursors : singleAlbumCursors.values()) {
          notifyCursors(cursors);
        }
      } else {
        if (singleAlbumCursorModified.get().size() > 0) {
          notifyCursors(allAlbumCursors);
          for (final Integer albumId : singleAlbumCursorModified.get()) {
            final Collection<WeakReference<NotifyableMatrixCursor>> registeresCursors = singleAlbumCursors.get(albumId);
            if (registeresCursors != null)
              notifyCursors(registeresCursors);
          }
        }
      }
    }
  }

  public void notifyAllAlbumCursorsChanged() {
    allAlbumCursorModified.set(Boolean.TRUE);
  }

  public void notifyServerStateModifies() {
    notifyCursors(stateCursors);
  }

  public void notifySingleAlbumCursorChanged(final int albumId) {
    singleAlbumCursorModified.get().add(Integer.valueOf(albumId));
  }

  private void notifyCursors(final Collection<WeakReference<NotifyableMatrixCursor>> cursors) {
    for (final Iterator<WeakReference<NotifyableMatrixCursor>> i = cursors.iterator(); i.hasNext();) {
      final WeakReference<NotifyableMatrixCursor> reference = i.next();
      final NotifyableMatrixCursor cursor = reference.get();
      if (cursor != null)
        cursor.onChange(true);
      else
        i.remove();
    }
  }

}
