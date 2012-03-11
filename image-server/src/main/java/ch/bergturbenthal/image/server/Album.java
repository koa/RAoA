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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Album {
  private static String CACHE_DIR = ".servercache";
  private final File baseDir;
  private final long cachedImages = 0;
  private List<AlbumImage> images = null;
  private final File cacheDir;
  private final String name;

  public Album(final File baseDir, final String name) {
    this.baseDir = baseDir;
    this.name = name;
    prepareGitignore();
    cacheDir = new File(baseDir, CACHE_DIR);
    if (!cacheDir.exists())
      cacheDir.mkdir();
  }

  public String getName() {
    return name;
  }

  public synchronized List<AlbumImage> listImages() {
    return new ArrayList<AlbumImage>(loadImages());
  }

  @Override
  public String toString() {
    return "Album [" + getName() + "]";
  }

  public synchronized long totalSize() {
    long size = 0;
    for (final AlbumImage image : loadImages()) {
      size += image.readSize();
    }
    return size;
  }

  private synchronized Collection<AlbumImage> loadImages() {
    if (baseDir.lastModified() == cachedImages)
      return images;
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
    Collections.sort(images, new Comparator<AlbumImage>() {
      @Override
      public int compare(final AlbumImage o1, final AlbumImage o2) {
        final int cmp = o1.captureDate().compareTo(o2.captureDate());
        return cmp;
      }
    });
    return images;
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
