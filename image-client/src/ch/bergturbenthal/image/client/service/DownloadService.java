package ch.bergturbenthal.image.client.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;
import ch.bergturbenthal.image.client.R;
import ch.bergturbenthal.image.client.resolver.AlbumService;
import ch.bergturbenthal.image.client.resolver.Resolver;
import ch.bergturbenthal.image.client.resolver.SingleMediaScanner;
import ch.bergturbenthal.image.data.api.ImageResult;
import ch.bergturbenthal.image.data.model.AlbumDetail;
import ch.bergturbenthal.image.data.model.AlbumEntry;
import ch.bergturbenthal.image.data.model.AlbumImageEntry;

public class DownloadService extends IntentService {

  private static final String TAG = "Service";
  private static final int PROGRESS_INDICATION = 1;
  private final ExecutorService executorService = Executors.newFixedThreadPool(4);
  private NotificationManager notificationManager;
  private Notification notification;
  private RemoteViews notificationView;

  private long lastUpdate = System.currentTimeMillis();

  public DownloadService() {
    super("Image Download Service");
  }

  private void cancelProgress() {
    notificationManager.cancel(PROGRESS_INDICATION);
  }

  @Override
  protected void onHandleIntent(final Intent intent) {
    final Context context = this;
    final Resolver resolver = new Resolver(context);
    final String clientId = PreferenceManager.getDefaultSharedPreferences(context).getString("client_name", null);
    if (clientId == null)
      return;
    final File imagesDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "server");
    resolver.establishLastConnection(new Resolver.ConnectionUrlListener() {
      @Override
      public void notifyConnectionEstabilshed(final String foundUrl, final String serverName) {
        prepareProgressBar();
        final AtomicInteger totalImageCount = new AtomicInteger(0);
        final AtomicInteger doneImageCount = new AtomicInteger(0);
        final File serverDirectory = new File(imagesDirectory, serverName);
        if (!serverDirectory.exists())
          serverDirectory.mkdirs();
        final AlbumService albumService = new AlbumService(foundUrl);
        for (final AlbumEntry album : albumService.listAlbums().getAlbumNames()) {
          if (!album.getClients().contains(clientId))
            continue;
          final File albumDirectory = new File(serverDirectory, album.getName());
          if (!albumDirectory.exists())
            albumDirectory.mkdirs();
          final AlbumDetail albumContent = albumService.listAlbumContent(album.getId());
          for (final AlbumImageEntry image : albumContent.getImages()) {
            totalImageCount.incrementAndGet();
            executorService.submit(new Runnable() {
              @Override
              public void run() {
                final File imageFile = new File(albumDirectory, image.getName() + ".jpg");
                final Date ifModifiedSince;
                if (imageFile.exists())
                  ifModifiedSince = new Date(imageFile.lastModified());
                else
                  ifModifiedSince = null;
                final ImageResult imageResult = albumService.readImage(album.getId(), image.getId(), 1600, 1600, ifModifiedSince);
                final Date lastModified = imageResult.getLastModified();
                if (lastModified == null) {
                  updateProgress(doneImageCount.incrementAndGet(), totalImageCount.intValue());
                  return;
                }
                try {
                  final InputStream inputStream = imageResult.getDataStream();
                  try {
                    final File tempImageFile = new File(albumDirectory, image.getId() + ".jpg-temp");
                    final OutputStream outputStream = new FileOutputStream(tempImageFile);
                    try {
                      final byte[] buffer = new byte[8192];
                      while (true) {
                        final int read = inputStream.read(buffer);
                        if (read < 0)
                          break;
                        outputStream.write(buffer, 0, read);
                      }
                    } finally {
                      outputStream.close();
                    }
                    tempImageFile.renameTo(imageFile);
                  } finally {
                    inputStream.close();
                  }
                  imageFile.setLastModified(lastModified.getTime());
                } catch (final IOException e) {
                  updateProgress(doneImageCount.incrementAndGet(), totalImageCount.intValue());
                  throw new RuntimeException("Cannot read " + album.getName() + ":" + image.getName(), e);
                }
                updateProgress(doneImageCount.incrementAndGet(), totalImageCount.intValue());
              }
            });
          }
        }
        try {
          executorService.shutdown();
          executorService.awaitTermination(1, TimeUnit.HOURS);
        } catch (final InterruptedException e) {
          Log.i(TAG, "Service cancelled");
        }
        cancelProgress();
        new SingleMediaScanner(DownloadService.this, serverDirectory);
      }

      @Override
      public void notifyConnectionNotEstablished() {
        Log.w(TAG, "Could not extablish connection to server");
      }
    });

  }

  private void prepareProgressBar() {
    notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    notification = new Notification();
    notificationView = new RemoteViews(getPackageName(), R.layout.progress_notification);
    notification.contentView = notificationView;
    notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
    notificationView.setProgressBar(R.id.progressBar, 0, 0, true);
    notification.icon = R.drawable.logo;
    notificationManager.notify(PROGRESS_INDICATION, notification);
  }

  private synchronized void updateProgress(final int currentValue, final int maxValue) {
    final long now = System.currentTimeMillis();
    if (now - lastUpdate < 200)
      return;
    lastUpdate = now;
    notificationView.setProgressBar(R.id.progressBar, maxValue, currentValue, false);
    notificationManager.notify(PROGRESS_INDICATION, notification);
  }

}
