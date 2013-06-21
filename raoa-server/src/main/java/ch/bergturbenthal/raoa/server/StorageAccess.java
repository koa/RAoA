package ch.bergturbenthal.raoa.server;

import ch.bergturbenthal.raoa.data.model.ArchiveMeta;

public interface StorageAccess {

	ArchiveMeta listKnownStorage();

}
