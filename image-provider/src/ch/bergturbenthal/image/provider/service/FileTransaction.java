/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.provider.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;

import android.os.Parcelable;
import ch.bergturbenthal.image.provider.util.IOUtil;
import ch.bergturbenthal.image.provider.util.ObjectUtils;

/**
 * TODO: add type comment.
 * 
 */
public class FileTransaction {
  private final File baseDir;
  private final Map<String, Object> parsedData = new LinkedHashMap<String, Object>();
  private final Map<String, Date> originalData = new HashMap<String, Date>();
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final Map<String, WeakReference<Object>> readOnlyCache = new ConcurrentHashMap<String, WeakReference<Object>>();

  private static <D> D readFileContent(final File infile, final Class<D> type) throws IOException, FileNotFoundException, JsonProcessingException {
    final GZIPInputStream is = new GZIPInputStream(new FileInputStream(infile));
    final D readValue;
    try {
      readValue = mapper.reader(type).readValue(is);
    } finally {
      is.close();
    }
    return readValue;
  }

  public FileTransaction(final File baseDir) {
    this.baseDir = baseDir;
  }

  public void commitTransaction() {
    if (parsedData.isEmpty())
      return;
    final ObjectWriter writer = mapper.writer();
    for (final Entry<String, Object> fileEntry : parsedData.entrySet()) {
      final String filename = fileEntry.getKey();
      try {
        final Object newData = fileEntry.getValue();
        final File targetFile = evaluateFile(filename);
        if (newData == null) {
          if (targetFile.exists())
            targetFile.delete();
          continue;
        }
        final Date oldDate = originalData.get(filename);
        final Date currentDate = targetFile.exists() ? new Date(targetFile.lastModified()) : null;
        if (!ObjectUtils.objectEquals(oldDate, currentDate)) {
          throw new ConcurrentTransactionException(filename);
        }
        final ByteArrayOutputStream tempOs = new ByteArrayOutputStream();
        final GZIPOutputStream gzipOs = new GZIPOutputStream(tempOs);
        try {
          writer.writeValue(gzipOs, newData);
        } finally {
          gzipOs.close();
        }
        final byte[] newBytes = tempOs.toByteArray();
        final byte[] oldBytes = readIfExists(targetFile);
        if (oldBytes != null && Arrays.equals(oldBytes, newBytes)) {
          // data not modified
          continue;
        }
        if (!targetFile.getParentFile().exists())
          targetFile.getParentFile().mkdirs();
        final File tempFile = new File(targetFile.getParentFile(), targetFile.getName() + "-tmp");
        final OutputStream os = new FileOutputStream(tempFile);
        try {
          os.write(newBytes);
        } finally {
          os.close();
        }
        tempFile.renameTo(targetFile);
      } catch (final Throwable t) {
        throw new RuntimeException("Cannot save " + filename, t);
      }
    }
    for (final Entry<String, Object> updatedEntry : parsedData.entrySet()) {
      readOnlyCache.put(updatedEntry.getKey(), new WeakReference<Object>(updatedEntry.getValue()));
    }
    parsedData.clear();
    originalData.clear();
  }

  public void evict(final String relativePath) {
    parsedData.remove(relativePath);
    originalData.remove(relativePath);
  }

  public Collection<String> findAll(final List<Pattern> pathPatterns) {
    return findAll(baseDir, "", pathPatterns);
  }

  public <D extends Parcelable> D getObject(final String relativePath, final Class<D> type) {
    if (parsedData.containsKey(relativePath))
      return (D) parsedData.get(relativePath);
    final File infile = evaluateFile(relativePath);
    if (!infile.exists())
      return null;
    return readFile(infile, relativePath, type);
  }

  public <D extends Parcelable> D getObjectReadOnly(final String relativePath, final Class<D> type) {
    {
      final WeakReference<Object> cachedValue = readOnlyCache.get(relativePath);
      if (cachedValue != null) {
        final D cachedResult = (D) cachedValue.get();
        if (cachedResult != null)
          return cachedResult;
      }
    }
    synchronized (readOnlyCache) {
      final WeakReference<Object> cachedValue = readOnlyCache.get(relativePath);
      if (cachedValue != null) {
        final D cachedResult = (D) cachedValue.get();
        if (cachedResult != null)
          return cachedResult;
      }
      final File inFile = evaluateFile(relativePath);
      if (!inFile.exists())
        return null;
      try {
        final D readValue = readFileContent(inFile, type);
        readOnlyCache.put(relativePath, new WeakReference<Object>(readValue));
        return readValue;
      } catch (final IOException e) {
        throw new RuntimeException("Cannot read " + relativePath + " readonly", e);
      }
    }
  }

  public <D extends Parcelable> void putObject(final String relativePath, final D value) {
    parsedData.put(relativePath, value);
  }

  public <D extends Parcelable> Collection<D> readAll(final List<Pattern> pathPatterns, final Class<D> type) {
    final ArrayList<D> ret = new ArrayList<D>();
    for (final String relativePath : findAll(pathPatterns)) {
      ret.add(getObject(relativePath, type));
    }
    return ret;
  }

  private File evaluateFile(final String relativePath) {
    return new File(baseDir, relativePath);
  }

  private Collection<String> findAll(final File dir, final String relativePath, final List<Pattern> pathPatterns) {
    if (pathPatterns.isEmpty())
      return Collections.emptyList();

    final ArrayList<String> ret = new ArrayList<String>();
    final Pattern pattern = pathPatterns.get(0);
    if (pathPatterns.size() == 1) {
      for (final File file : dir.listFiles(new FileFilter() {
        @Override
        public boolean accept(final File pathname) {
          return pathname.isFile() && pattern.matcher(pathname.getName()).matches();
        }
      })) {
        ret.add((relativePath + "/" + file.getName()).substring(1));
      }
    } else {
      final List<Pattern> remainingPatterns = pathPatterns.subList(1, pathPatterns.size());
      for (final File file : dir.listFiles(new FileFilter() {
        @Override
        public boolean accept(final File pathname) {
          return pathname.isDirectory() && pattern.matcher(pathname.getName()).matches();
        }
      })) {
        ret.addAll(findAll(file, relativePath + "/" + file.getName(), remainingPatterns));
      }
    }
    return ret;

  }

  private <D> D readFile(final File infile, final String relativePath, final Class<D> type) {
    try {
      originalData.put(relativePath, new Date(infile.lastModified()));
      final D readValue = readFileContent(infile, type);
      parsedData.put(relativePath, readValue);
      return readValue;
    } catch (final IOException e) {
      throw new RuntimeException("Cannot read file " + relativePath + " as " + type.getName(), e);
    }
  }

  private byte[] readIfExists(final File file) throws IOException {
    if (!file.exists())
      return null;
    final FileInputStream fis = new FileInputStream(file);
    try {
      return IOUtil.readStream(fis);
    } finally {
      fis.close();
    }
  }

}
