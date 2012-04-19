package ch.bergturbenthal.image.server;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;

public class FileAlbumAccess implements AlbumAccess {
  private final Logger logger = LoggerFactory.getLogger(FileAlbumAccess.class);
  private Resource baseDir;
  private Resource importBaseDir;
  private Map<String, Album> loadedAlbums = null;

  @Override
  public Album getAlbum(final String albumId) {
    return listAlbums().get(albumId);
  }

  public Resource getBaseDir() {
    return baseDir;
  }

  public Resource getImportBaseDir() {
    return importBaseDir;
  }

  @Override
  public void importFiles(final File importDir) {

    try {
      if (!importDir.getAbsolutePath().startsWith(importBaseDir.getFile().getAbsolutePath())) {
        logger.error("Secutity-Error: Not allowed to read Images from " + importDir + " (Import-Path is " + importBaseDir + ")");
        return;
      }
      final SortedMap<Date, Album> importAlbums = new TreeMap<Date, Album>();
      for (final Album album : listAlbums().values()) {
        final Date beginDate = album.autoAddBeginDate();
        if (beginDate != null)
          importAlbums.put(beginDate, album);
      }
      final Collection<File> importCandicates = collectImportFiles(importDir);
      for (final File file : importCandicates) {
        try {
          // logger.info("Read: " + file.getName());
          final Metadata metadata = ImageMetadataReader.readMetadata(file);
          final Date createDate = MetadataUtil.readCreateDate(metadata);
          if (createDate == null)
            // skip images without creation date
            continue;
          final SortedMap<Date, Album> entriesBeforeDate = importAlbums.headMap(createDate);
          if (entriesBeforeDate.isEmpty())
            // no matching album found
            continue;
          final Album album = entriesBeforeDate.get(entriesBeforeDate.lastKey());
          // logger.info(" ->" + album.getName());
          if (album.importImage(file, createDate)) {
            logger.debug("image " + file + " imported successfully to " + album.getName());
            file.delete();
          } else {
            logger.warn("Could not import image " + file);
          }
        } catch (final ImageProcessingException e) {
          throw new RuntimeException("Cannot import file " + file, e);
        }
      }
    } catch (final IOException e) {
      throw new RuntimeException("Cannot import from " + importDir, e);
    }
  }

  @Override
  public Map<String, Album> listAlbums() {
    if (loadedAlbums == null) {
      synchronized (this) {
        try {
          final Map<String, Album> ret = new HashMap<String, Album>();
          final File basePath = baseDir.getFile().getAbsoluteFile();
          logger.debug("Base-Path: " + basePath);
          final int basePathLength = basePath.getAbsolutePath().length();
          for (final File albumDir : findAlbums(basePath)) {
            final String name = albumDir.getAbsolutePath().substring(basePathLength);
            ret.put(Util.sha1(albumDir.getAbsolutePath()), new Album(albumDir, name));
          }
          loadedAlbums = ret;
        } catch (final IOException e) {
          throw new RuntimeException("Troubles while accessing resource " + baseDir, e);
        }
      }
    }
    return loadedAlbums;
  }

  public synchronized void setBaseDir(final Resource baseDir) {
    this.baseDir = baseDir;
    loadedAlbums = null;
  }

  public void setImportBaseDir(final Resource importBaseDir) {
    this.importBaseDir = importBaseDir;
  }

  private Collection<File> collectImportFiles(final File importDir) {
    if (!importDir.isDirectory())
      return Collections.emptyList();
    final ArrayList<File> ret = new ArrayList<File>();
    ret.addAll(Arrays.asList(importDir.listFiles(new FileFilter() {
      @Override
      public boolean accept(final File pathname) {
        if (!pathname.canRead())
          return false;
        if (!pathname.isFile())
          return false;
        final String lowerFilename = pathname.getName().toLowerCase();
        return lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".nef") || lowerFilename.endsWith(".jpeg");
      }
    })));
    for (final File dir : importDir.listFiles(new FileFilter() {
      @Override
      public boolean accept(final File pathname) {
        return pathname.isDirectory();
      }
    })) {
      ret.addAll(collectImportFiles(dir));
    }
    return ret;
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
    if (foundFiles != null) {
      Arrays.sort(foundFiles);
      for (final File subDir : foundFiles) {
        ret.addAll(findAlbums(subDir));
      }
    }
    return ret;
  }
}
