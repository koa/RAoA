package ch.bergturbenthal.image.data.model;

public class PingResponse {
  private String version = "1";
  private String serverId;
  private String collectionId;

  public String getCollectionId() {
    return collectionId;
  }

  public String getServerId() {
    return serverId;
  }

  public String getVersion() {
    return version;
  }

  public void setCollectionId(final String collectionId) {
    this.collectionId = collectionId;
  }

  public void setServerId(final String serverId) {
    this.serverId = serverId;
  }

  public void setVersion(final String version) {
    this.version = version;
  }

  @Override
  public String toString() {
    return "PingResponse [version=" + version + ", collectionId=" + collectionId + ", serverId=" + serverId + "]";
  }

}
