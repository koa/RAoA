package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryData;
import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryMetadata;
import ch.bergturbenthal.raoa.server.spring.service.AttachementGenerator;
import ch.bergturbenthal.raoa.server.spring.service.MetadataReader;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MetadataAttachementGenerator implements AttachementGenerator {

	private final ObjectMapper mapper = new ObjectMapper();
	@Autowired
	private List<MetadataReader> metadataReader;

	@Override
	public String attachementType() {
		return "metadata";
	}

	@Override
	public String createAttachementFilename(final AlbumEntryData entry) {
		final ObjectId originalFileId = entry.getOriginalFileId();
		final ObjectId metadataSidecarId = entry.getMetadataSidecarId();
		if (metadataSidecarId == null) {
			return originalFileId.name();
		}
		return originalFileId.name() + "-" + metadataSidecarId.name();
	}

	@Override
	public Future<ObjectId> generateAttachement(final String filename,
																							final Callable<ObjectLoader> entryLoader,
																							final Callable<ObjectLoader> sidecarLoader,
																							final ObjectInserter inserter) {
		try {
			final ObjectLoader entryObjectLoader = entryLoader.call();
			final AlbumEntryMetadata.AlbumEntryMetadataBuilder metadata = AlbumEntryMetadata.builder();
			if (entryLoader != null) {
				for (final MetadataReader currentReader : metadataReader) {
					currentReader.readMetadata(entryObjectLoader, metadata);
				}
			}
			final byte[] jsonData = mapper.writeValueAsBytes(metadata.build());
			return new CompletedFuture<ObjectId>(inserter.insert(Constants.OBJ_BLOB, jsonData));
		} catch (final Exception e) {
			throw new RuntimeException("Cannot generate attachement for " + filename, e);
		}
	}

}
