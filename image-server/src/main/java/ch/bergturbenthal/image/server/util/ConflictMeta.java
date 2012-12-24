package ch.bergturbenthal.image.server.util;

import java.util.Date;

import lombok.Data;

@Data
public class ConflictMeta {
  private String remoteUri;
  private String server;
  private Date conflictDate;
}