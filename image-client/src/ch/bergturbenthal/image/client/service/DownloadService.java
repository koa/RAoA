package ch.bergturbenthal.image.client.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.IntentService;
import android.content.Intent;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import ch.bergturbenthal.image.client.resolver.AlbumService;
import ch.bergturbenthal.image.client.resolver.Resolver;
import ch.bergturbenthal.image.client.resolver.SingleMediaScanner;
import ch.bergturbenthal.image.data.api.ImageResult;
import ch.bergturbenthal.image.data.model.AlbumDetail;
import ch.bergturbenthal.image.data.model.AlbumEntry;
import ch.bergturbenthal.image.data.model.AlbumImageEntry;

public class DownloadService extends IntentService {

  private static final String TAG = "Service";
  private final ExecutorService executorService = Executors.newFixedThreadPool(4);

  public DownloadService() {
    super("Image Download Service");
  }

  @Override
  protected void onHandleIntent(final Intent intent) {
    final Resolver resolver = new Resolver(this);
    final String clientId = PreferenceManager.getDefaultSharedPreferences(this).getString("client_name", null);
    if (clientId == null)
      return;
    final File imagesDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "server");
    resolver.establishLastConnection(new Resolver.ConnectionUrlListener() {
      @Override
      public void notifyConnectionEstabilshed(final String foundUrl, final String serverName) {
        final File serverDirectory = new File(imagesDirectory, serverName);
        if (!serverDirectory.exists())
          serverDirectory.mkdirs();
        final AlbumService albumService = new AlbumService(foundUrl, DownloadService.this);
        for (final AlbumEntry album : albumService.listAlbums().getAlbumNames()) {
          if (!album.getClients().contains(clientId))
            continue;
          executorService.submit(new Runnable() {
            @Override
            public void run() {
              final File albumDirectory = new File(serverDirectory, album.getName());
              if (!albumDirectory.exists())
                albumDirectory.mkdirs();
              else
                new SingleMediaScanner(DownloadService.this, albumDirectory);
              final AlbumDetail albumContent = albumService.listAlbumContent(album.getId());
              for (final AlbumImageEntry image : albumContent.getImages()) {
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
                      throw new RuntimeException("Cannot read " + album.getName() + ":" + image.getName(), e);
                    }
                  }
                });
              }
            }
          });
        }
      }

      @Override
      public void notifyConnectionNotEstablished() {
        Log.w(TAG, "Could not extablish connection to server");
      }
    });

  }
}
