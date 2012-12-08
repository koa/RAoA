package ch.bergturbenthal.image.server;

public interface ArchiveConfiguration {
  String getArchiveName();

  String getInstanceName();

  void setArchiveName(String archiveName);

  void setInstanceName(String instanceName);
}
