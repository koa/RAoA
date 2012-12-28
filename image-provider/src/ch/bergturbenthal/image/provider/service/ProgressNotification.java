package ch.bergturbenthal.image.provider.service;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import ch.bergturbenthal.image.data.model.state.Progress;
import ch.bergturbenthal.image.provider.model.dto.ServerStateDto;

public class ProgressNotification implements Closeable {
  protected static final String SERVICE_TAG = "ProgressNotification";
  private final ScheduledExecutorService executorService;
  private final AtomicReference<Map<String, ArchiveConnection>> connectionMap;
  private ScheduledFuture<?> statePollingFuture;
  private final Handler contextHandler;

  private final Collection<String> visibleProgress = new HashSet<String>();
  private final NotificationManager notificationManager;
  private final Context context;

  public ProgressNotification(final AtomicReference<Map<String, ArchiveConnection>> connectionMap, final Context context,
                              final ScheduledExecutorService executorService) {
    this.connectionMap = connectionMap;
    this.context = context;
    this.executorService = executorService;
    contextHandler = new Handler(context.getMainLooper());
    notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
  }

  @Override
  public synchronized void close() {
    stopPolling();
  }

  public synchronized void startPolling() {
    if (statePollingFuture == null || statePollingFuture.isCancelled())
      statePollingFuture = executorService.scheduleWithFixedDelay(new Runnable() {

        @Override
        public void run() {
          try {
            pollServerStates();
          } catch (final Throwable t) {
            Log.w(SERVICE_TAG, "Exception while polling server for state", t);
          }

        }
      }, 2, 5, TimeUnit.SECONDS);

  }

  public synchronized void stopPolling() {
    if (statePollingFuture != null)
      statePollingFuture.cancel(false);

  }

  private synchronized void pollServerStates() {
    final Map<String, ArchiveConnection> connections = connectionMap.get();
    if (connections == null)
      return;
    final Collection<ServerStateDto> serverStates = new ArrayList<ServerStateDto>();
    for (final ArchiveConnection connection : connections.values()) {
      serverStates.addAll(connection.collectServerStates());
    }

    final Map<String, Notification.Builder> newBuilders = new HashMap<String, Notification.Builder>();
    for (final ServerStateDto serverStateDto : serverStates) {
      final String serverName = serverStateDto.getServerName();
      for (final Progress progress : serverStateDto.getServerState().getProgress()) {

        final Notification.Builder builder = new Notification.Builder(context);
        builder.setContentTitle(serverName + ": " + progress.getType().name() + " : " + progress.getProgressDescription());
        builder.setSmallIcon(android.R.drawable.ic_dialog_info);
        builder.setAutoCancel(false);
        builder.setContentText(progress.getCurrentStepDescription());
        builder.setProgress(progress.getStepCount(), progress.getCurrentStepNr(), progress.getStepCount() == 0);
        newBuilders.put(progress.getProgressId(), builder);
      }
    }
    contextHandler.post(new Runnable() {

      @Override
      public void run() {
        synchronized (ProgressNotification.this) {
          final Collection<String> notificationsToRemove = new HashSet<String>(visibleProgress);
          notificationsToRemove.removeAll(newBuilders.keySet());
          for (final String removeKey : notificationsToRemove) {
            notificationManager.cancel(removeKey, 0);
          }
          for (final Entry<String, Builder> builderEntry : newBuilders.entrySet()) {
            notificationManager.notify(builderEntry.getKey(), 0, builderEntry.getValue().getNotification());
          }
          visibleProgress.clear();
          visibleProgress.addAll(newBuilders.keySet());
        }
      }
    });
  }
}
