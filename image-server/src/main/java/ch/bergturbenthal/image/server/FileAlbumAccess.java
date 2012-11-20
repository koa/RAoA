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
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.joda.time.Duration;
import org.joda.time.format.PeriodFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;

import ch.bergturbenthal.image.server.model.ArchiveData;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;

public class FileAlbumAccess implements AlbumAccess {
  private static final String META_REPOSITORY = ".meta";
  private final String instanceId = UUID.randomUUID().toString();
  private final Logger logger = LoggerFactory.getLogger(FileAlbumAccess.class);
  private Resource baseDir;
  private Resource importBaseDir;
  private Map<String, Album> loadedAlbums = null;
  private final AtomicLong lastLoadedDate = new AtomicLong(0);
  @Autowired
  private ScheduledExecutorService executorService;

  private final ObjectMapper mapper = new ObjectMapper();
  private ArchiveData archiveData;
  private Git metaGit;

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
    return archiveData.getArchiveName();
  }

  public Resource getImportBaseDir() {
    return importBaseDir;
  }

  @Override
  public String getInstanceId() {
    return instanceId;
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
      refreshCache();
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
          refreshCache();
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

  private File getConfigFile() {
    return new File(getMetaDir(), "config.json");
  }

  private File getMetaDir() {
    final File metaDir = new File(getBasePath(), META_REPOSITORY);
    if (!metaDir.exists())
      metaDir.mkdirs();
    return metaDir;
  }

  @PostConstruct
  private void initMetaRepository() {
    final File metaDir = getMetaDir();

    if (new File(metaDir, ".git").exists()) {
      try {
        metaGit = Git.open(metaDir);
      } catch (final IOException e) {
        throw new RuntimeException("Cannot access to git-repository of " + baseDir, e);
      }
    } else {
      metaGit = Git.init().setDirectory(metaDir).call();
    }
    final File configFile = new File(metaDir, "config.json");
    try {
      if (!configFile.exists()) {
        archiveData = new ArchiveData();
        final Map<String, Collection<String>> albumPerStorage = archiveData.getAlbumPerStorage();
        archiveData.setArchiveName(UUID.randomUUID().toString());
        for (final Album album : listAlbums().values()) {
          for (final String client : album.listClients()) {
            if (albumPerStorage.containsKey(client)) {
              albumPerStorage.get(client).add(album.getName());
            } else {
              final TreeSet<String> albums = new TreeSet<String>();
              albums.add(album.getName());
              albumPerStorage.put(client, albums);
            }
          }
        }
        updateMeta("config.json built");
      } else {
        archiveData = mapper.readValue(configFile, ArchiveData.class);
      }
    } catch (final IOException e) {
      throw new RuntimeException("Cannot write config to " + configFile, e);
    } catch (final GitAPIException e) {
      throw new RuntimeException("Commit config", e);
    }
  }

  private boolean needToLoadAlbumList() {
    return loadedAlbums == null || (System.currentTimeMillis() - lastLoadedDate.get()) > TimeUnit.MINUTES.toMillis(5);
  }

  private void refreshCache() {
    try {
      // limit the queue size for take not too much memory
      final Semaphore queueLimitSemaphore = new Semaphore(100);
      final long startTime = System.currentTimeMillis();
      for (final Album album : listAlbums().values()) {
        for (final AlbumImage image : album.listImages().values()) {
          queueLimitSemaphore.acquire();
          executorService.submit(new Runnable() {

            @Override
            public void run() {
              try {
                // read Metadata
                image.captureDate();
                // read Thumbnail
                image.getThumbnail();
              } finally {
                queueLimitSemaphore.release();
              }
            }
          });
        }
      }
      // wait until the end
      queueLimitSemaphore.acquire(100);
      final long endTime = System.currentTimeMillis();
      final Duration duration = new Duration(startTime, endTime);
      final StringBuffer buf = new StringBuffer("Refresh-Time: ");
      PeriodFormat.wordBased().getPrinter().printTo(buf, duration.toPeriod(), Locale.getDefault());
      logger.info(buf.toString());
    } catch (final InterruptedException e) {
      logger.info("cache refresh interrupted");
    }
  }

  private void updateMeta(final String message) throws IOException, JsonGenerationException, JsonMappingException, NoFilepatternException,
                                               NoHeadException, NoMessageException, UnmergedPathException, ConcurrentRefUpdateException,
                                               WrongRepositoryStateException {
    mapper.writerWithDefaultPrettyPrinter().writeValue(getConfigFile(), archiveData);
    metaGit.add().addFilepattern(".").call();
    metaGit.commit().setMessage(message).call();
  }
}
