package ch.bergturbenthal.image.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;

public class Album {
  private static String CACHE_DIR = ".servercache";
  private final File baseDir;
  private final long cachedImages = 0;
  private Collection<AlbumImage> images = null;
  private final File cacheDir;

  public Album(final File baseDir) {
    this.baseDir = baseDir;
    prepareGitignore();
    cacheDir = new File(baseDir, CACHE_DIR);
    if (!cacheDir.exists())
      cacheDir.mkdir();
  }

  public String getName() {
    return baseDir.getName();
  }

  public synchronized Collection<AlbumImage> listImages() {
    loadImagesIfNeeded();
    return new ArrayList<AlbumImage>(images);
  }

  @Override
  public String toString() {
    return "Album [" + getName() + "]";
  }

  public synchronized long totalSize() {
    loadImagesIfNeeded();
    long size = 0;
    for (final AlbumImage image : images) {
      size += image.readSize();
    }
    return size;
  }

  private synchronized void loadImagesIfNeeded() {
    if (baseDir.lastModified() == cachedImages)
      return;
    final File[] foundFiles = baseDir.listFiles(new FileFilter() {
      @Override
      public boolean accept(final File file) {
        if (!file.isFile() || !file.canRead())
          return false;
        final String lowerFilename = file.getName().toLowerCase();
        return lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg") || lowerFilename.endsWith(".nef");
      }
    });
    images = new ArrayList<AlbumImage>(foundFiles.length);
    for (final File file : foundFiles) {
      images.add(new AlbumImage(file, cacheDir));
    }
  }

  private void prepareGitignore() {
    try {
      final File gitignore = new File(baseDir, ".gitignore");
      if (gitignore.exists()) {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(gitignore), "utf-8"));
        try {
          while (true) {
            final String line = reader.readLine();
            if (line == null)
              break;
            if (line.equals(CACHE_DIR))
              // already added
              return;
          }
        } finally {
          reader.close();
        }
      }
      final PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(gitignore, true), "utf-8"));
      try {
        writer.println(CACHE_DIR);
      } finally {
        writer.close();
      }
    } catch (final IOException e) {
      throw new RuntimeException("Cannot prepare .gitignore-file", e);
    }
  }
}
