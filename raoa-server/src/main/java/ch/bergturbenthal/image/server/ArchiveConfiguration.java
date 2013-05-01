package ch.bergturbenthal.image.server;

public interface ArchiveConfiguration {
  String getArchiveName();

  String getInstanceName();

  void setArchiveName(final String archiveName);

  void setInstanceName(final String instanceName);
}
