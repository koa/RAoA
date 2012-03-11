package ch.bergturbenthal.image.server;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.springframework.core.io.Resource;

public class FileAlbumAccess implements AlbumAccess {
  // @Value("/data/heap/data/photos")
  // @Value("/data/heap/data/photos/old/Landschaft/Vorführung Seilbahn 2012-02-18")
  private Resource baseDir;

  public Resource getBaseDir() {
    return baseDir;
  }

  @Override
  public Collection<Album> listAlbums() {
    try {
      final ArrayList<Album> ret = new ArrayList<Album>();
      for (final File albumDir : findAlbums(baseDir.getFile())) {
        ret.add(new Album(albumDir));
      }
      return ret;
    } catch (final IOException e) {
      throw new RuntimeException("Troubles while accessing resource " + baseDir, e);
    }
  }

  public void setBaseDir(final Resource baseDir) {
    this.baseDir = baseDir;
  }

  private Collection<File> findAlbums(final File dir) {
    final File gitSubDir = new File(dir, ".git");
    if (gitSubDir.exists() && gitSubDir.isDirectory()) {
      return Collections.singleton(dir);
    }
    final File[] foundFiles = dir.listFiles(new FileFilter() {
      @Override
      public boolean accept(final File pathname) {
        return pathname.isDirectory();
      }
    });
    final ArrayList<File> ret = new ArrayList<File>();
    Arrays.sort(foundFiles);
    for (final File subDir : foundFiles) {
      ret.addAll(findAlbums(subDir));
    }
    return ret;
  }
}
