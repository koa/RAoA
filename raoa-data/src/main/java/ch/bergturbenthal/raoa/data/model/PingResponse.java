package ch.bergturbenthal.raoa.data.model;

import lombok.Data;

@Data
public class PingResponse {
  private String version = "1";
  private String serverId;
  private String serverName;
  private String archiveId;
  private int gitPort;

}
