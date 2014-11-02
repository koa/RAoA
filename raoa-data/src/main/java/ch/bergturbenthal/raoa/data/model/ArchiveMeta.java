package ch.bergturbenthal.raoa.data.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import lombok.Data;

@Data
public class ArchiveMeta implements Serializable {
	private final Collection<StorageEntry> clients = new ArrayList<StorageEntry>();
	private Date lastModified;
	private String version;
}
