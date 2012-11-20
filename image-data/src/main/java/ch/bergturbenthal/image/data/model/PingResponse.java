package ch.bergturbenthal.image.data.model;

public class PingResponse {
  private String version = "1";
  private String serverId;
  private String archiveId;
  private int gitPort;

  public String getArchiveId() {
    return archiveId;
  }

  /**
   * Returns the gitPort.
   * 
   * @return the gitPort
   */
  public int getGitPort() {
    return gitPort;
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

  /**
   * Sets the gitPort.
   * 
   * @param gitPort
   *          the gitPort to set
   */
  public void setGitPort(final int gitPort) {
    this.gitPort = gitPort;
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
