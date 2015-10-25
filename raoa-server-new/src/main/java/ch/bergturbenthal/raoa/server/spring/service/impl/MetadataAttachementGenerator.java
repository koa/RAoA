package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryData;
import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryMetadata;
import ch.bergturbenthal.raoa.server.spring.service.AttachementGenerator;
import ch.bergturbenthal.raoa.server.spring.service.MetadataReader;

@Service
public class MetadataAttachementGenerator implements AttachementGenerator {

	private final ExecutorService executorService = new ThreadPoolExecutor(	0,
																																					1,
																																					30,
																																					TimeUnit.SECONDS,
																																					new LinkedBlockingQueue<Runnable>(30000),
																																					new CustomizableThreadFactory("metadata-pool-"));
	private final ObjectMapper mapper = new ObjectMapper();
	@Autowired
	private List<MetadataReader> metadataReader;

	@Override
	public String attachementType() {
		return "metadata";
	}

	@Override
	public String createAttachementFilename(final AlbumEntryData entry) {
		final SortedSet<ObjectId> dependingObjects = new TreeSet<ObjectId>();
		for (final MetadataReader metadataReader : metadataReader) {
			final Set<ObjectId> objectId = entry.getAttachedFiles().get(metadataReader.getClass());
			if (objectId == null || objectId.isEmpty()) {
				continue;
			}
			dependingObjects.addAll(objectId);
		}
		if (dependingObjects.isEmpty()) {
			return null;
		}
		final StringBuffer sb = new StringBuffer();
		for (final ObjectId objectId : dependingObjects) {
			if (sb.length() > 0) {
				sb.append("-");
			}
			sb.append(objectId.name());
		}
		return sb.toString();
	}

	@Override
	public Map<Class<? extends Object>, Set<ObjectId>> findAdditionalFiles(	final AlbumEntryData entry,
																																					final Map<String, ObjectId> filenames,
																																					final ObjectLoaderLookup lookup) {
		final Map<Class<? extends Object>, Set<ObjectId>> ret = new HashMap<>();
		for (final MetadataReader reader : metadataReader) {
			final Collection<String> candidateFiles = reader.metadataFileOf(entry.getFilename());
			if (candidateFiles == null || candidateFiles.isEmpty()) {
				continue;
			}

			final Set<ObjectId> foundObjects = new HashSet<ObjectId>();
			for (final String candidateFile : candidateFiles) {
				final ObjectId candidateObject = filenames.get(candidateFile);
				if (candidateObject == null) {
					continue;
				}
				foundObjects.add(candidateObject);
			}
			ret.put(reader.getClass(), foundObjects);
		}
		return ret;
	}

	@Override
	public Future<ObjectId> generateAttachement(final AlbumEntryData entryData, final ObjectLoaderLookup lookup, final ObjectInserter inserter) {
		return executorService.submit(new Callable<ObjectId>() {

			@Override
			public ObjectId call() throws Exception {
				try {
					final AlbumEntryMetadata metadata = new AlbumEntryMetadata();
					for (final MetadataReader currentReader : metadataReader) {
						final Set<ObjectId> attachementOfReader = entryData.getAttachedFiles().get(currentReader.getClass());
						if (attachementOfReader == null) {
							continue;
						}
						for (final ObjectId objectId : attachementOfReader) {
							currentReader.readMetadata(lookup.createLoader(objectId), metadata);
						}
					}
					final byte[] jsonData = mapper.writeValueAsBytes(metadata);
					return inserter.insert(Constants.OBJ_BLOB, jsonData);
				} catch (final Exception e) {
					throw new RuntimeException("Cannot generate attachement for " + entryData.getFilename(), e);
				}
			}
		});
	}

}
