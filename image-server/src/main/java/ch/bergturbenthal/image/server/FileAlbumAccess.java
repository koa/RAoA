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
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;

public class FileAlbumAccess implements AlbumAccess {
  private final Logger logger = LoggerFactory.getLogger(FileAlbumAccess.class);
  private Resource baseDir;
  private Resource importBaseDir;
  private Map<String, Album> loadedAlbums = null;
  private final AtomicLong lastLoadedDate = new AtomicLong(0);
  @Autowired
  private ScheduledExecutorService executorService;

  @Override
  public synchronized String createAlbum(final String[] pathNames) {
    final Map<String, Album> albums = listAlbums();
    final File basePath = getBasePath();
    File newAlbumPath = basePath;
    for (final String pathComp : pathNames) {
      newAlbumPath = new File(newAlbumPath, pathComp);
    }
    if (!newAlbumPath.getAbsolutePath().startsWith(basePath.getAbsolutePath())) {
      throw new RuntimeException("Cannot create Album " + pathNames);
    }
    if (newAlbumPath.exists()) {
      for (final Entry<String, Album> albumEntry : albums.entrySet()) {
        if (Arrays.asList(pathNames).equals(albumEntry.getValue().getNameComps())) {
          // album already exists
          return albumEntry.getKey();
        }
      }
      throw new RuntimeException("Directory " + newAlbumPath + " already exsists");
    }
    if (!newAlbumPath.exists()) {
      final boolean createParent = newAlbumPath.mkdirs();
      if (!createParent)
        throw new RuntimeException("Cannot create Directory " + newAlbumPath);
    }

    final String[] nameComps = newAlbumPath.getAbsolutePath().substring(getBasePath().getAbsolutePath().length() + 1).split(File.pathSeparator);
    final Album newAlbum = new Album(newAlbumPath, nameComps);
    final String albumKey = Util.sha1(newAlbumPath.getAbsolutePath());
    loadedAlbums.put(albumKey, newAlbum);
    return albumKey;
  }

  @Override
  public Album getAlbum(final String albumId) {
    return listAlbums().get(albumId);
  }

  public Resource getBaseDir() {
    return baseDir;
  }

  @Override
  public String getCollectionId() {
    // TODO read from repository-metadata
    return "Development";
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
      final HashSet<Album> modifiedAlbums = new HashSet<Album>();
      final SortedMap<Date, Album> importAlbums = new TreeMap<Date, Album>();
      for (final Album album : listAlbums().values()) {
        final Date beginDate = album.getAutoAddBeginDate();
        if (beginDate != null)
          importAlbums.put(beginDate, album);
      }
      final Collection<File> deleteFiles = new ArrayList<File>();
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
            modifiedAlbums.add(album);
            logger.debug("image " + file + " imported successfully to " + album.getName());
            deleteFiles.add(file);
          } else {
            logger.warn("Could not import image " + file);
          }
        } catch (final ImageProcessingException e) {
          throw new RuntimeException("Cannot import file " + file, e);
        }
      }
      for (final Album album : modifiedAlbums) {
        album.commit("automatically imported");
      }
      for (final File file : deleteFiles) {
        final boolean deleted = file.delete();
        if (!deleted)
          logger.error("Cannot delete File " + file);
      }
    } catch (final IOException e) {
      throw new RuntimeException("Cannot import from " + importDir, e);
    } finally {
      refreshThumbnails();
    }
  }

  @Override
  public Map<String, Album> listAlbums() {
    if (needToLoadAlbumList()) {
      synchronized (this) {
        if (needToLoadAlbumList())
          try {
            final Map<String, Album> ret = new HashMap<String, Album>();
            final File basePath = getBasePath();
            logger.debug("Load Repositories from: " + basePath);
            final int basePathLength = basePath.getAbsolutePath().length();
            for (final File albumDir : findAlbums(basePath)) {
              logger.debug("Load Repository " + albumDir);
              final String[] nameComps = albumDir.getAbsolutePath().substring(basePathLength + 1).split(File.pathSeparator);
              ret.put(Util.sha1(albumDir.getAbsolutePath()), new Album(albumDir, nameComps));
            }
            lastLoadedDate.set(System.currentTimeMillis());
            loadedAlbums = ret;
          } catch (final Throwable e) {
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

  /**
   * Start scheduling automatically after initializing
   */
  @PostConstruct
  protected void startScheduling() {
    executorService.scheduleWithFixedDelay(new Runnable() {

      @Override
      public void run() {
        try {
          refreshThumbnails();
        } catch (final Throwable t) {
          logger.warn("Exception while refreshing thumbnails", t);
        }
      }
    }, 5, 2 * 60 * 60, TimeUnit.SECONDS);
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

  private File getBasePath() {
    try {
      return baseDir.getFile().getAbsoluteFile();
    } catch (final IOException e) {
      throw new RuntimeException("Cannot read base-path from " + baseDir, e);
    }
  }

  private boolean needToLoadAlbumList() {
    return loadedAlbums == null || (System.currentTimeMillis() - lastLoadedDate.get()) > TimeUnit.MINUTES.toMillis(5);
  }

  private void refreshThumbnails() {
    for (final Album album : listAlbums().values()) {
      for (final AlbumImage image : album.listImages().values()) {
        executorService.submit(new Runnable() {

          @Override
          public void run() {
            image.getThumbnail();
          }
        });
      }
    }
  }
}
