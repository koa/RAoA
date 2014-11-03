package ch.bergturbenthal.raoa.data.model;

import java.io.Serializable;
import java.util.Set;
import java.util.TreeSet;

import lombok.Data;

@Data
public class StorageEntry implements Serializable {
	private final Set<String> albumList = new TreeSet<String>();
	private Long gBytesAvailable;
	private String storageId;
	private String storageName;
	private boolean takeAllRepositories;
}
