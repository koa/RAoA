package ch.bergturbenthal.image.server.util;

import java.util.Date;

import lombok.Data;

/**
 * Metadata about a conflict which will be as notes at a conflict branch
 * 
 */
@Data
public class ConflictMeta {
  private String remoteUri;
  private String server;
  private Date conflictDate;
}