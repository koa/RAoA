package ch.bergturbenthal.raoa.server.spring.service;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;

import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryData;

public interface AttachementGenerator {
	String attachementType();

	String createAttachementFilename(final AlbumEntryData entry);

	Future<ObjectId> generateAttachement(	final String filename,
																				final Callable<ObjectLoader> entryLoader,
																				final Callable<ObjectLoader> sidecarLoader,
																				final ObjectInserter inserter);
}
