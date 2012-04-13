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
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import ch.bergturbenthal.image.client.R;
import ch.bergturbenthal.image.client.resolver.AlbumService;
import ch.bergturbenthal.image.client.resolver.Resolver;
import ch.bergturbenthal.image.data.api.ImageResult;
import ch.bergturbenthal.image.data.model.AlbumDetail;
import ch.bergturbenthal.image.data.model.AlbumEntry;
import ch.bergturbenthal.image.data.model.AlbumImageEntry;

public class DownloadService extends IntentService {

  private static final String TAG = "Service";
  private static final int PROGRESS_INDICATION = 1;
  private final ExecutorService executorService = Executors.newFixedThreadPool(4);
  private NotificationManager notificationManager;

  private long lastUpdate = System.currentTimeMillis();
  private Notification notification;

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
                  updateProgress(doneImageCount.incrementAndGet(), totalImageCount.intValue(), null);
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
                  updateProgress(doneImageCount.incrementAndGet(), totalImageCount.intValue(), null);
                  throw new RuntimeException("Cannot read " + album.getName() + ":" + image.getName(), e);
                }
                updateProgress(doneImageCount.incrementAndGet(), totalImageCount.intValue(), imageFile);
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

        final Intent scanIntent = new Intent(Intent.ACTION_MEDIA_MOUNTED);
        scanIntent.setData(Uri.fromFile(serverDirectory));
        sendBroadcast(scanIntent);
        // startActivity(scanIntent);
      }

      @Override
      public void notifyConnectionNotEstablished() {
        Log.w(TAG, "Could not extablish connection to server");
      }
    });

  }

  private void prepareProgressBar() {
    notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    final Notification notification =
                                      new Notification.Builder(this).setOngoing(true).setProgress(0, 0, true).setSmallIcon(R.drawable.icon)
                                                                    .getNotification();

    notification.flags |= Notification.FLAG_NO_CLEAR;
    notificationManager.notify(PROGRESS_INDICATION, notification);
  }

  private synchronized void updateProgress(final int currentValue, final int maxValue, final File lastImage) {
    final long now = System.currentTimeMillis();
    if (now - lastUpdate < 300)
      return;
    if (notification == null) {
      final Builder builder =
                              new Notification.Builder(this).setOngoing(true).setProgress(maxValue, currentValue, false)
                                                            .setSmallIcon(R.drawable.icon);
      builder.setContentTitle(getResources().getText(R.string.progress_indication_title));
      notification = builder.getNotification();
      notification.flags |= Notification.FLAG_NO_CLEAR;
    }
    notification.contentView.setProgressBar(android.R.id.progress, maxValue, currentValue, false);
    if (lastImage != null) {
      final Bitmap largeIcon = BitmapFactory.decodeFile(lastImage.getAbsolutePath());
      notification.largeIcon = largeIcon;
    }

    notificationManager.notify(PROGRESS_INDICATION, notification);
    lastUpdate = now;
  }

}
