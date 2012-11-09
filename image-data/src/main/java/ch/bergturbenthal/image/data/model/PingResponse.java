package ch.bergturbenthal.image.data.model;

public class PingResponse {
  private String version = "1";
  private String serverId;
  private String archiveId;

  public String getArchiveId() {
    return archiveId;
  }

  public String getServerId() {
    return serverId;
  }

  public String getVersion() {
    return version;
  }

  public void setArchiveId(final String collectionId) {
    this.archiveId = collectionId;
  }

  public void setServerId(final String serverId) {
    this.serverId = serverId;
  }

  public void setVersion(final String version) {
    this.version = version;
  }

  @Override
  public String toString() {
    return "PingResponse [version=" + version + ", collectionId=" + archiveId + ", serverId=" + serverId + "]";
  }

}
