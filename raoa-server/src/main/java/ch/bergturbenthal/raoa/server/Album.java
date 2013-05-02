package ch.bergturbenthal.raoa.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.ref.SoftReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectReader;
import org.codehaus.jackson.map.type.MapType;
import org.codehaus.jackson.map.type.SimpleType;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import ch.bergturbenthal.raoa.data.model.CaptionMutationEntry;
import ch.bergturbenthal.raoa.data.model.KeywordMutationEntry;
import ch.bergturbenthal.raoa.data.model.MutationEntry;
import ch.bergturbenthal.raoa.data.model.RatingMutationEntry;
import ch.bergturbenthal.raoa.server.cache.AlbumManager;
import ch.bergturbenthal.raoa.server.metadata.PicasaIniData;
import ch.bergturbenthal.raoa.server.model.AlbumEntryData;
import ch.bergturbenthal.raoa.server.state.StateManager;
import ch.bergturbenthal.raoa.server.util.ConflictEntry;
import ch.bergturbenthal.raoa.server.util.RepositoryService;

public class Album implements ApplicationContextAware {
  private static class ImportEntry {

    private final String filename;

    private final String hash;

    static ImportEntry parseLine(final String line) {
      final String[] comps = line.split(";", 2);
      return new ImportEntry(comps[0], comps[1]);
    }

    public ImportEntry(final String filename, final String hash) {
      super();
      this.filename = filename;
      this.hash = hash;
    }

    public String getFilename() {
      return filename;
    }

    public String getHash() {
      return hash;
    }

    public String makeString() {
      return filename + ";" + hash;
    }

  }

  private static String AUTOADD_FILE = ".autoadd";
  private static String CACHE_DIR = ".servercache";
  private static String INDEX_FILE = ".index";
  private static Logger logger = LoggerFactory.getLogger(Album.class);
  private static ObjectMapper mapper = new ObjectMapper();

  private final ConcurrentMap<String, AlbumEntryData> albumMetadataCache = new ConcurrentHashMap<String, AlbumEntryData>();

  private final File baseDir;
  private long cachedImages = 0;
  private File cacheDir;
  private Git git;
  private SoftReference<Map<String, AlbumImage>> images = null;
  private Collection<ImportEntry> importEntries = null;
  private final AtomicBoolean metadataModified = new AtomicBoolean(false);
  private final String[] nameComps;
  @Autowired
  private RepositoryService repositoryService;
  private Long repositorySize = null;

  private final Semaphore writeAlbumEntryCacheSemaphore = new Semaphore(1);
  private final String initRemoteUri;
  private final String initRemoteServerName;

  private ApplicationContext applicationContext;
  @Autowired
  private StateManager stateManager;

  public static Album createAlbum(final File baseDir, final String[] nameComps, final String remoteUri, final String serverName) {
    return new Album(baseDir, nameComps, remoteUri, serverName);
  }

  private Album(final File baseDir, final String[] nameComps, final String remoteUri, final String serverName) {
    this.baseDir = baseDir;
    this.nameComps = nameComps;
    // this.repositoryService = repositoryService;
    this.initRemoteUri = remoteUri;
    this.initRemoteServerName = serverName;
  }

  public synchronized void commit(final String message) {
    try {
      final Status status = git.status().call();
      if (!status.isClean()) {
        git.commit().setMessage(message).call();
      }
    } catch (final GitAPIException e) {
      throw new RuntimeException("Cannot execute commit on " + getName(), e);
    } catch (final RuntimeException e) {
      throw new RuntimeException("Cannot execute commit on " + getName(), e);
    }
  }

  public synchronized Date getAutoAddBeginDate() {
    final File file = autoaddFile();
    if (!file.exists())
      return null;
    try {
      final BufferedReader reader = bufferedReader(file);
      try {
        final String line = reader.readLine();
        if (line == null)
          return null;
        return ISODateTimeFormat.dateTimeParser().parseDateTime(line).toDate();
      } finally {
        reader.close();
      }
    } catch (final IOException e) {
      throw new RuntimeException("Cannot read " + file, e);
    }
  }

  public AlbumImage getImage(final String imageId) {
    return loadImages().get(imageId);
  }

  public Date getLastModified() {
    try {
      if (!isMaster())
        return new Date(1);
      final LogCommand log = git.log().setMaxCount(1);
      final Iterable<RevCommit> commitIterable = log.call();
      final Iterator<RevCommit> iterator = commitIterable.iterator();
      if (iterator.hasNext()) {
        final RevCommit lastCommit = iterator.next();
        return new Date(lastCommit.getCommitTime() * 1000l);
      }
    } catch (final NoHeadException e) {
      // no head -> return zero date
    } catch (final GitAPIException e) {
      logger.warn("Cannot query log from " + git.getRepository(), e);
    }
    return new Date(0);
  }

  public String getName() {
    final StringBuilder ret = new StringBuilder();
    for (final String comp : nameComps) {
      if (ret.length() != 0)
        ret.append("/");
      if (comp.length() > 0)
        ret.append(comp);
    }
    return ret.toString();
  }

  public List<String> getNameComps() {
    return Collections.unmodifiableList(Arrays.asList(nameComps));
  }

  public Repository getRepository() {
    return git.getRepository();
  }

  public long getRepositorySize() {
    while (true) {
      final Long size = repositorySize;
      if (size != null) {
        return size.longValue();
      }
      synchronized (this) {
        if (repositorySize == null)
          repositorySize = Long.valueOf(FileUtils.sizeOfDirectory(git.getRepository().getDirectory()));
      }
    }
  }

  public boolean importImage(final File imageFile, final Date createDate) {
    if (!imageFile.exists())
      return false;
    final long length = imageFile.length();
    if (length == 0)
      return false;
    if (imageFile.getParent().equals(baseDir))
      // points to a already imported file
      return true;
    final String sha1OfFile = makeSha1(imageFile);
    synchronized (this) {
      final ImportEntry existingImportEntry = findExistingImportEntry(sha1OfFile);
      if (existingImportEntry != null) {
        final File file = new File(baseDir, existingImportEntry.getFilename());
        if (file.exists() && file.length() == length)
          // already full imported
          return true;
      }
      for (int i = 0; true; i++) {
        final File targetFile = new File(baseDir, makeFilename(imageFile.getName(), i, createDate));
        if (targetFile.exists()) {
          final ImportEntry entry = findOrMakeImportEntryForExisting(targetFile);
          if (entry.getHash().equals(sha1OfFile))
            // File already imported
            return true;
        } else {
          // new Filename found -> import file
          final File tempFile = new File(baseDir, targetFile.getName() + "-temp");
          try {
            FileUtils.copyFile(imageFile, tempFile);
            if (tempFile.renameTo(targetFile)) {
              final ImportEntry loadedEntry = findOrMakeImportEntryForExisting(targetFile);
              final boolean importOk = loadedEntry.getHash().equals(sha1OfFile);
              if (importOk)
                git.add().addFilepattern(targetFile.getName()).call();
              return importOk;
            } else
              return false;
          } catch (final IOException ex) {
            throw new RuntimeException("Cannot copy file " + imageFile, ex);
          } catch (final NoFilepatternException e) {
            throw new RuntimeException("Cannot add File to git-repository", e);
          } catch (final GitAPIException e) {
            throw new RuntimeException("Cannot add File to git-repository", e);
          } finally {
            // clear cache
            cachedImages = 0;
          }
        }
      }
    }
  }

  @PostConstruct
  public void init() {
    if (new File(baseDir, ".git").exists()) {
      try {
        git = Git.open(baseDir);
      } catch (final IOException e) {
        throw new RuntimeException("Cannot access to git-repository of " + baseDir, e);
      }
    } else {
      try {
        git = Git.init().setDirectory(baseDir).call();
      } catch (final GitAPIException e) {
        throw new RuntimeException("Cannot create Album", e);
      }
    }
    if (initRemoteUri != null)
      pull(initRemoteUri, initRemoteServerName);

    final boolean modified = checkup();
    cacheDir = new File(baseDir, CACHE_DIR);
    if (!cacheDir.exists())
      cacheDir.mkdirs();
    if (autoaddFile().exists()) {
      loadImportEntries();
    }
    if (metadataCacheFile().exists())
      loadMetadataCache();
    if (modified)
      commit("initialized repository for image-server");
  }

  public long lastModified() {
    long lastModified = 0;
    for (final File imageFile : listImageFiles()) {
      final long currentLastModified = imageFile.lastModified();
      if (currentLastModified > lastModified)
        lastModified = currentLastModified;
    }
    return lastModified;
  }

  public Map<String, AlbumImage> listImages() {
    return loadImages();
  }

  public synchronized void pull(final String remoteUri, final String serverName) {
    repositoryService.pull(git, remoteUri, serverName);
    repositorySize = null;
    updateConflictStatus();
  }

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  public synchronized void setAutoAddBeginDate(final Date date) {
    final File file = autoaddFile();
    try {
      final PrintWriter writer = new PrintWriter(file, "utf-8");
      try {
        writer.println(ISODateTimeFormat.dateTime().print(date.getTime()));
      } finally {
        writer.close();
      }
    } catch (final IOException ex) {
      throw new RuntimeException("Cannot write new date to " + file, ex);
    }
  }

  public synchronized void sync(final File remoteDir, final String localName, final String remoteName, final boolean bare) {
    repositoryService.sync(git, remoteDir, localName, remoteName, bare);
    repositorySize = null;
    updateConflictStatus();
  }

  @Override
  public String toString() {
    return "Album [" + getName() + "]";
  }

  public synchronized void updateMetadata(final Collection<MutationEntry> updateEntries) {
    final Map<String, AlbumImage> loadedImages = loadImages();
    final Set<String> modifiedImages = new HashSet<>();
    for (final MutationEntry mutationEntry : updateEntries) {
      final AlbumImage albumImage = loadedImages.get(mutationEntry.getAlbumEntryId());
      if (albumImage == null)
        continue;
      final AlbumEntryData oldMetadata = albumImage.getAlbumEntryData();
      if (!StringUtils.equals(oldMetadata.getEditableMetadataHash(), mutationEntry.getBaseVersion()))
        continue;
      modifiedImages.add(mutationEntry.getAlbumEntryId());
      try {
        if (mutationEntry instanceof RatingMutationEntry) {
          final RatingMutationEntry ratingMutationEntry = (RatingMutationEntry) mutationEntry;
          albumImage.setRating(ratingMutationEntry.getRating());
        }
        if (mutationEntry instanceof CaptionMutationEntry) {
          final CaptionMutationEntry captionMutationEntry = (CaptionMutationEntry) mutationEntry;
          albumImage.setCaption(captionMutationEntry.getCaption());
        }
        if (mutationEntry instanceof KeywordMutationEntry) {
          final KeywordMutationEntry keywordMutationEntry = (KeywordMutationEntry) mutationEntry;
          switch (keywordMutationEntry.getMutation()) {
          case ADD:
            albumImage.addKeyword(keywordMutationEntry.getKeyword());
            break;
          case REMOVE:
            albumImage.removeKeyword(keywordMutationEntry.getKeyword());
            break;
          }
        }
      } catch (final Exception e) {
        logger.error("Cannot execute update " + mutationEntry + " at album " + getName(), e);
      }
    }
    if (!modifiedImages.isEmpty())
      try {
        final AddCommand addCommand = git.add();
        for (final String imageId : modifiedImages) {
          addCommand.addFilepattern(loadedImages.get(imageId).getXmpSideFile().getName());
        }
        addCommand.call();
        final CommitCommand commitCommand = git.commit();
        commitCommand.setMessage("Metadata updated");
        commitCommand.call();
      } catch (final GitAPIException e) {
        logger.error("Cannot update git", e);
      }
  }

  /**
   * evaluates current version of album
   * 
   * @return version, null if there is no commit
   */
  public String version() {
    try {
      for (final RevCommit revCommit : git.log().call()) {
        return revCommit.getName();
      }
      return null;
    } catch (final NoHeadException e) {
      return null;
    } catch (final JGitInternalException e) {
      throw new RuntimeException("Cannot read log", e);
    } catch (final GitAPIException e) {
      throw new RuntimeException("Cannot read log", e);
    }

  }

  private void appendImportEntry(final ImportEntry newEntry) {
    if (importEntries == null)
      loadImportEntries();
    try {
      final PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(indexFile(), true), "utf-8"));
      try {
        writer.println(newEntry.makeString());
      } finally {
        writer.close();
      }
      importEntries.add(newEntry);
    } catch (final IOException e) {
      throw new RuntimeException("Cannot save import index", e);
    }
  }

  private File autoaddFile() {
    return new File(baseDir, AUTOADD_FILE);
  }

  private BufferedReader bufferedReader(final File file) throws UnsupportedEncodingException, FileNotFoundException {
    final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
    return reader;
  }

  /**
   * Checks the configuration of this repository and disables deltacompression
   * if not elsewhere set
   * 
   * @throws IOException
   */
  private boolean checkup() {
    boolean modified = false;
    modified |= prepareGitignore();
    // disables delta-compression by config for speedup synchronisation
    final StoredConfig config = git.getRepository().getConfig();
    final Set<String> packConfigs = config.getNames("pack");
    if (!packConfigs.contains("deltacompression")) {
      config.setBoolean("pack", null, "deltacompression", false);
      try {
        config.save();
      } catch (final IOException e) {
        throw new RuntimeException("Cannot update config", e);
      }
    }

    repositoryService.cleanOldConflicts(git);

    // commit changes from outside the server
    try {
      final Status status = git.status().call();
      if (!status.isClean()) {
        git.add().addFilepattern(".").call();
        modified = true;
      }
    } catch (final GitAPIException e) {
      throw new RuntimeException("Cannot make initial commit", e);
    }
    updateConflictStatus();
    return modified;
  }

  private synchronized ImportEntry findExistingImportEntry(final String sha1OfFile) {
    if (importEntries == null)
      loadImportEntries();
    for (final ImportEntry entry : importEntries) {
      if (entry.getHash().equals(sha1OfFile))
        return entry;
    }
    return null;
  }

  private synchronized ImportEntry findOrMakeImportEntryForExisting(final File existingFile) {
    if (importEntries == null)
      loadImportEntries();
    for (final ImportEntry entry : importEntries) {
      if (entry.getFilename().equals(existingFile.getName()))
        return entry;
    }
    final ImportEntry newEntry = new ImportEntry(existingFile.getName(), makeSha1(existingFile));
    appendImportEntry(newEntry);
    return newEntry;
  }

  private File indexFile() {
    return new File(cacheDir, INDEX_FILE);
  }

  private synchronized boolean isMaster() throws GitAPIException {
    try {
      return git.getRepository().getBranch().equals("master");
    } catch (final IOException e) {
      return false;
    }
  }

  private File[] listImageFiles() {
    final File[] foundFiles = baseDir.listFiles(new FileFilter() {
      @Override
      public boolean accept(final File file) {
        if (!file.isFile() || !file.canRead())
          return false;
        final String lowerFilename = file.getName().toLowerCase();
        return lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg") || lowerFilename.endsWith(".nef") || lowerFilename.endsWith(".mkv");
      }
    });
    return foundFiles;
  }

  private synchronized Map<String, AlbumImage> loadImages() {
    final Date lastModified = getLastModified();
    final long dirLastModified = lastModified.getTime();
    if (images != null) {
      final Map<String, AlbumImage> cachedImageMap = images.get();
      if (cachedImageMap != null) {
        if (dirLastModified == cachedImages)
          return cachedImageMap;
      }
    }
    final Map<String, PicasaIniData> picasaData = PicasaIniData.parseIniFile(baseDir);
    final Map<String, AlbumImage> ret = new HashMap<String, AlbumImage>();
    for (final File file : listImageFiles()) {
      final String filename = file.getName();
      final PicasaIniData picasaIniData = picasaData.get(filename);
      final AlbumManager cacheManager = new AlbumManager() {

        @Override
        public void clearThumbnailException(final String image) {
          stateManager.clearThumbnailException(getName(), image);
        }

        @Override
        public AlbumEntryData getCachedData() {
          return readAlbumEntryDataFromCache(filename);
        }

        @Override
        public PicasaIniData getPicasaData() {
          return picasaIniData;
        }

        @Override
        public void recordThumbnailException(final String image, final Throwable ex) {
          stateManager.recordThumbnailException(getName(), image, ex);
        }

        @Override
        public void updateCache(final AlbumEntryData entryData) {
          updateAlbumEntryInCache(filename, entryData);
        }
      };
      ret.put(Util.encodeStringForUrl(filename), (AlbumImage) applicationContext.getBean("albumImage", file, cacheDir, lastModified, cacheManager));
    }
    cachedImages = dirLastModified;
    images = new SoftReference<Map<String, AlbumImage>>(ret);
    return ret;
  }

  private synchronized void loadImportEntries() {
    importEntries = new ArrayList<Album.ImportEntry>();
    final File file = indexFile();
    if (file.exists()) {
      try {
        final BufferedReader reader = bufferedReader(file);
        try {
          while (true) {
            final String line = reader.readLine();
            if (line == null)
              break;
            importEntries.add(ImportEntry.parseLine(line));
          }
        } finally {
          reader.close();
        }
      } catch (final IOException e) {
        throw new RuntimeException("Cannot read " + file, e);
      }
    }
  }

  private void loadMetadataCache() {
    albumMetadataCache.clear();
    final ObjectReader reader =
                                mapper.reader(MapType.construct(Map.class, SimpleType.construct(String.class),
                                                                SimpleType.construct(AlbumEntryData.class)));
    final File metadataCacheFile = metadataCacheFile();
    try {
      albumMetadataCache.putAll(reader.<Map<String, AlbumEntryData>> readValue(metadataCacheFile));
    } catch (final IOException e) {
      logger.warn("Cannot read " + metadataCacheFile, e);
      metadataCacheFile.delete();
    }
  }

  private String makeFilename(final String name, final int i, final Date timestamp) {
    if (i == 0)
      return MessageFormat.format("{1,date,yyyy-MM-dd-HH-mm-ss}-{0}", name, timestamp);
    final int lastPt = name.lastIndexOf(".");
    return MessageFormat.format("{3,date,yyyy-MM-dd-HH-mm-ss}-{0}-{1}{2}", name.substring(0, lastPt), i, name.substring(lastPt), timestamp);
  }

  private String makeSha1(final File file) {
    try {
      final MessageDigest md = MessageDigest.getInstance("SHA-1");
      final FileInputStream fileInputStream = new FileInputStream(file);
      try {
        final byte[] buffer = new byte[8192];
        while (true) {
          final int read = fileInputStream.read(buffer);
          if (read < 0)
            break;
          md.update(buffer, 0, read);
        }
        final Base32 base32 = new Base32();
        return base32.encodeToString(md.digest()).toLowerCase();
      } finally {
        fileInputStream.close();
      }
    } catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException("Cannot make sha1 of " + file, e);
    } catch (final IOException e) {
      throw new RuntimeException("Cannot make sha1 of " + file, e);
    }
  }

  private File metadataCacheFile() {
    return new File(cacheDir, "metadata.json");
  }

  private boolean prepareGitignore() {
    try {
      final File gitignore = new File(baseDir, ".gitignore");
      final Set<String> ignoreEntries = new HashSet<String>(Arrays.asList(CACHE_DIR));
      if (gitignore.exists()) {
        final BufferedReader reader = bufferedReader(gitignore);
        try {
          while (ignoreEntries.size() > 0) {
            final String line = reader.readLine();
            if (line == null)
              break;
            ignoreEntries.remove(line);
          }
        } finally {
          reader.close();
        }
      }
      if (ignoreEntries.size() == 0)
        return false;
      final PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(gitignore, true), "utf-8"));
      try {
        for (final String entry : ignoreEntries) {
          writer.println(entry);
        }
      } finally {
        writer.close();
      }
      git.add().addFilepattern(gitignore.getName()).call();
    } catch (final IOException e) {
      throw new RuntimeException("Cannot prepare .gitignore-file", e);
    } catch (final NoFilepatternException e) {
      throw new RuntimeException("Error while executing git-command", e);
    } catch (final GitAPIException e) {
      throw new RuntimeException("Error while executing git-command", e);
    }
    return true;
  }

  private AlbumEntryData readAlbumEntryDataFromCache(final String filename) {
    return albumMetadataCache.get(filename);
  }

  private void updateAlbumEntryInCache(final String filename, final AlbumEntryData entryData) {
    final AlbumEntryData oldValue = albumMetadataCache.putIfAbsent(filename, entryData);
    if (entryData.equals(oldValue))
      return;
    metadataModified.set(true);
    final boolean hasLock = writeAlbumEntryCacheSemaphore.tryAcquire();
    if (hasLock) {
      try {
        while (metadataModified.getAndSet(false)) {
          final File metadataCacheFile = metadataCacheFile();
          final File tempFile = new File(metadataCacheFile.getParentFile(), "mtadata.tmp");
          try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(tempFile, albumMetadataCache);
            tempFile.renameTo(metadataCacheFile);
          } catch (final IOException e) {
            logger.warn("Cannot write to " + tempFile, e);
          }
        }
      } finally {
        writeAlbumEntryCacheSemaphore.release();
      }
    }
  }

  private void updateConflictStatus() {
    final Collection<ConflictEntry> conflicts = repositoryService.describeConflicts(git);
    stateManager.reportConflict(getName(), conflicts);
  }

}
