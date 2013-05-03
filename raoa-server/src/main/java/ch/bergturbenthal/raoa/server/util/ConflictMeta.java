package ch.bergturbenthal.raoa.server.util;

import java.util.Date;

import lombok.Data;

/**
 * Metadata about a conflict which will be as notes at a conflict branch
 * 
 */
@Data
public class ConflictMeta {
	private Date conflictDate;
	private String remoteUri;
	private String server;
}