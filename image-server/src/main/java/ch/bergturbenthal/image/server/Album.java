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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ch.bergturbenthal.image.data.util.StringUtil;

public class Album {
  private static String CACHE_DIR = ".servercache";
  private static String CLIENT_FILE = ".clientlist";
  private static String AUTOADD_FILE = ".autoadd";
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

  public synchronized void addClient(final String client) {
    final Collection<String> clients = listClients();
    clients.add(client);
    saveClientList(clients);
  }

  public String getName() {
    return name;
  }

  public synchronized Collection<String> listClients() {
    final File file = new File(baseDir, CLIENT_FILE);
    if (file.exists() && file.canRead()) {
      try {
        final Collection<String> clients = new HashSet<String>();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
        try {
          while (true) {
            final String line = reader.readLine();
            if (line == null)
              return clients;
            clients.add(line);
          }
        } finally {
          reader.close();
        }
      } catch (final IOException e) {
        throw new RuntimeException("Cannot read client-list: " + file, e);
      }
    } else
      return new HashSet<String>();
  }

  public synchronized List<AlbumImage> listImages() {
    return new ArrayList<AlbumImage>(loadImages());
  }

  public synchronized void removeClient(final String client) {
    final Collection<String> clients = listClients();
    clients.remove(client);
    saveClientList(clients);
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
    return images;
  }

  private void prepareGitignore() {
    try {
      final File gitignore = new File(baseDir, ".gitignore");
      final Set<String> ignoreEntries = new HashSet<String>(Arrays.asList(CACHE_DIR, AUTOADD_FILE));
      if (gitignore.exists()) {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(gitignore), "utf-8"));
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
        return;
      final PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(gitignore, true), "utf-8"));
      try {
        for (final String entry : ignoreEntries) {
          writer.println(entry);
        }
      } finally {
        writer.close();
      }
    } catch (final IOException e) {
      throw new RuntimeException("Cannot prepare .gitignore-file", e);
    }
  }

  private void saveClientList(final Collection<String> clients) {
    final File file = new File(baseDir, CLIENT_FILE);
    try {
      final PrintWriter writer = new PrintWriter(file, "utf-8");
      try {
        for (final String clientId : clients) {
          writer.println(StringUtil.filterClientIdString(clientId));
        }
      } finally {
        writer.close();
      }
    } catch (final IOException e) {
      throw new RuntimeException("Cannot write client-list to " + file, e);
    }
  }
}
