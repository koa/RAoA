package ch.bergturbenthal.raoa.server.spring.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;

import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryData;

public interface AttachementGenerator {
	public static interface ObjectLoaderLookup {
		ObjectLoader createLoader(final ObjectId object) throws IOException;
	}

	String attachementType();

	String createAttachementFilename(final AlbumEntryData entry);

	Map<Class<? extends Object>, Set<ObjectId>> findAdditionalFiles(final AlbumEntryData entry, final Map<String, ObjectId> filenames, final ObjectLoaderLookup lookup);

	Future<ObjectId> generateAttachement(final AlbumEntryData entryData, final ObjectLoaderLookup lookup, final ObjectInserter inserter);
}
