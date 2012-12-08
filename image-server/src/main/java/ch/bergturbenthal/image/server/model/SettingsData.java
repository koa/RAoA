package ch.bergturbenthal.image.server.model;

import java.io.File;

public class SettingsData {
  private File albumPath;
  private File importBasePath;
  private String instanceName;
  private String archiveName;

  public File getAlbumPath() {
    return albumPath;
  }

  public File getImportBasePath() {
    return importBasePath;
  }

  public String getInstanceName() {
    return instanceName;
  }

  public void setAlbumPath(final File albumPath) {
    this.albumPath = albumPath;
  }

  public void setImportBasePath(final File importBasePath) {
    this.importBasePath = importBasePath;
  }

  public void setInstanceName(final String instanceName) {
    this.instanceName = instanceName;
  }

  public String getArchiveName() {
    return archiveName;
  }

  public void setArchiveName(String archiveName) {
    this.archiveName = archiveName;
  }
}
