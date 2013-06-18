package ch.bergturbenthal.raoa.data.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import lombok.Data;

@Data
public class StorageList {
	private final Collection<StorageEntry> clients = new ArrayList<StorageEntry>();
	private Date lastModified;
	private String version;

	public boolean updateFrom(final StorageList other) {
		if (other.version == null) {
			return false;
		}
		if (other.version.equals(version)) {
			return false;
		}
		clients.clear();
		clients.addAll(other.getClients());
		lastModified = other.lastModified;
		version = other.version;
		return true;
	}
}
