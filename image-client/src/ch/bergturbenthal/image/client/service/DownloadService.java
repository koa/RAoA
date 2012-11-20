package ch.bergturbenthal.image.client.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;
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
  private final ExecutorService executorService = Executors.newFixedThreadPool(1);
  private NotificationManager notificationManager;

  private long lastUpdate = System.currentTimeMillis();
  private Notification notification;

  public DownloadService() {
    super("Image Download Service");
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
        try {
          final Collection<Runnable> downloadRunnables = new ArrayList<Runnable>();
          final AtomicInteger totalImageCount = new AtomicInteger(0);
          final AtomicInteger doneImageCount = new AtomicInteger(0);
          final Collection<String> addedFiles = new ConcurrentLinkedQueue<String>();
          final AlbumService albumService = new AlbumService(foundUrl, getApplicationContext());
          for (final AlbumEntry album : albumService.listAlbums().getAlbumNames()) {
            if (!album.getClients().contains(clientId))
              continue;

            final File albumDirectory = new File(imagesDirectory, album.getName());
            if (!albumDirectory.exists())
              albumDirectory.mkdirs();
            final AlbumDetail albumContent = albumService.listAlbumContent(album.getId());
            for (final AlbumImageEntry image : albumContent.getImages()) {
              final File imageFile;
              if (image.isVideo())
                imageFile = new File(albumDirectory, image.getName() + ".mp4");
              else
                imageFile = new File(albumDirectory, image.getName() + ".jpg");
              final Date ifModifiedSince;
              if (imageFile.exists())
                ifModifiedSince = new Date(imageFile.lastModified());
              else
                ifModifiedSince = new Date(0);
              // no request for already up to date entries
              if (image.getLastModified().before(ifModifiedSince))
                continue;
              totalImageCount.incrementAndGet();
              final Runnable downloadRunnable = new Runnable() {
                @Override
                public void run() {
                  final ImageResult imageResult = albumService.readImage(album.getId(), image.getId(), ifModifiedSince);
                  final Date lastModified = imageResult.getLastModified();
                  if (lastModified == null) {
                    updateProgress(doneImageCount.incrementAndGet(), totalImageCount.intValue(), imageFile);
                    addedFiles.add(imageFile.getAbsolutePath());
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
                    addedFiles.add(imageFile.getAbsolutePath());
                  } catch (final IOException e) {
                    updateProgress(doneImageCount.incrementAndGet(), totalImageCount.intValue(), null);
                    throw new RuntimeException("Cannot read " + album.getName() + ":" + image.getName(), e);
                  }
                  updateProgress(doneImageCount.incrementAndGet(), totalImageCount.intValue(), imageFile);
                }
              };
              downloadRunnables.add(downloadRunnable);
              // executorService.submit(downloadRunnable);
            }
          }
          for (final Runnable runnable : downloadRunnables) {
            runnable.run();
          }
          try {
            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.HOURS);
          } catch (final InterruptedException e) {
            Log.i(TAG, "Service cancelled");
          }
          // notifyStartScan();
          // MediaScannerConnection.scanFile(context, addedFiles.toArray(new
          // String[addedFiles.size()]), null,
          // new MediaScannerConnection.OnScanCompletedListener() {
          // @Override
          // public void onScanCompleted(final String path, final Uri uri) {
          // }
          // });
        } finally {
          cancelProgress();
        }
      }

      @Override
      public void notifyConnectionNotEstablished() {
        Log.w(TAG, "Could not extablish connection to server");
      }

      @Override
      public void notifyStartConnection() {
        // TODO Auto-generated method stub

      }
    });

  }

  private void cancelProgress() {
    notificationManager.cancel(PROGRESS_INDICATION);
  }

  private void notifyStartScan() {
    final Builder builder = new Notification.Builder(this).setOngoing(true).setSmallIcon(R.drawable.icon);
    builder.setContentTitle(getResources().getText(R.string.progress_indication_title));
    notificationManager.notify(PROGRESS_INDICATION, builder.getNotification());
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
    // if (lastImage != null) {
    // final Bitmap largeIcon =
    // BitmapFactory.decodeFile(lastImage.getAbsolutePath(), new
    // BitmapFactory.Options());
    // notification.largeIcon = largeIcon;
    // }

    notificationManager.notify(PROGRESS_INDICATION, notification);
    lastUpdate = now;
  }

}
