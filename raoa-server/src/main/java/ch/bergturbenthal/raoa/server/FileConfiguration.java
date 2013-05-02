package ch.bergturbenthal.raoa.server;

import java.io.File;

public interface FileConfiguration {
  File getBaseDir();

  File getImportBaseDir();

  void setBaseDir(final File baseDir);

  void setImportBaseDir(final File importBaseDir);
}