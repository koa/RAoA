/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.provider.service;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;

import ch.bergturbenthal.image.provider.util.IOUtil;

/**
 * TODO: add type comment.
 * 
 */
public class FileTransaction {
  private final File baseDir;
  private final Map<String, Object> parsedData = new LinkedHashMap<String, Object>();
  private final Map<String, byte[]> originalData = new HashMap<String, byte[]>();
  private final ObjectMapper mapper = new ObjectMapper();

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
        final File targetFile = new File(baseDir, filename);
        if (newData == null) {
          if (targetFile.exists())
            targetFile.delete();
          continue;
        }
        final byte[] newBytes = writer.writeValueAsBytes(newData);
        final byte[] oldBytes = originalData.get(filename);
        if (oldBytes != null && Arrays.equals(oldBytes, newBytes)) {
          // data not modified
          continue;
        }
        if (!targetFile.getParentFile().exists())
          targetFile.getParentFile().mkdirs();
        final File tempFile = new File(targetFile.getParentFile(), targetFile.getName() + "-tmp");
        final GZIPOutputStream os = new GZIPOutputStream(new FileOutputStream(tempFile));
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

  public <D> D getObject(final String relativePath, final Class<D> type) {
    if (parsedData.containsKey(relativePath))
      return (D) parsedData.get(relativePath);
    final File infile = new File(baseDir, relativePath);
    if (!infile.exists())
      return null;
    return readFile(infile, relativePath, type);
  }

  public <D> void putObject(final String relativePath, final D value) {
    parsedData.put(relativePath, value);
  }

  public <D> Collection<D> readAll(final List<Pattern> pathPatterns, final Class<D> type) {
    final ArrayList<D> ret = new ArrayList<D>();
    for (final String relativePath : findAll(pathPatterns)) {
      ret.add(getObject(relativePath, type));
    }
    return ret;
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
      final byte[] data = IOUtil.readStream(new GZIPInputStream(new FileInputStream(infile)));
      originalData.put(relativePath, data);
      final D readValue = mapper.reader(type).readValue(data);
      parsedData.put(relativePath, readValue);
      return readValue;
    } catch (final IOException e) {
      throw new RuntimeException("Cannot read file " + relativePath + " as " + type.getName(), e);
    }
  }

}
