package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.springframework.beans.factory.annotation.Autowired;

import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryData;
import ch.bergturbenthal.raoa.server.spring.service.AttachementGenerator;
import ch.bergturbenthal.raoa.server.spring.service.ThumbnailMaker;

public abstract class AbstractThumbnailAttachementGenerator implements AttachementGenerator {

	private File tempDir;

	@Autowired
	private List<ThumbnailMaker> thumbnailMakers;

	public AbstractThumbnailAttachementGenerator() {
		super();
	}

	@Override
	public String createAttachementFilename(final AlbumEntryData entry) {
		return entry.getOriginalFileId().name();
	}

	@Override
	public Map<Class<? extends Object>, Set<ObjectId>> findAdditionalFiles(	final AlbumEntryData entry,
																																					final Map<String, ObjectId> filenames,
																																					final ObjectLoaderLookup lookup) {
		return Collections.emptyMap();
	}

	protected ThumbnailMaker findThumbnailMaker(final String filename) {
		for (final ThumbnailMaker thumbnailMaker : thumbnailMakers) {
			if (thumbnailMaker.canMakeThumbnail(filename)) {
				return thumbnailMaker;
			}
		}
		return null;
	}

	@Override
	public Future<ObjectId> generateAttachement(final AlbumEntryData entryData, final ObjectLoaderLookup lookup, final ObjectInserter inserter) {
		final String originalFilename = entryData.getFilename();
		final ThumbnailMaker maker = findThumbnailMaker(originalFilename);
		if (maker == null) {
			return null;
		}
		final int lastPt = originalFilename.lastIndexOf('.');
		if (lastPt < 2) {
			return null;
		}
		final String suffix = originalFilename.substring(lastPt);

		return maker.submit(new Callable<ObjectId>() {

			@Override
			public ObjectId call() throws Exception {
				final File tempInFile = File.createTempFile("thumbnail-in", suffix, tempDir);
				tempInFile.deleteOnExit();
				final File tempOutFile = File.createTempFile("thumbnail-out", suffix, tempDir);
				tempOutFile.deleteOnExit();
				try {
					final ObjectLoader objectLoader = lookup.createLoader(entryData.getOriginalFileId());
					try (FileOutputStream inFileOS = new FileOutputStream(tempInFile)) {
						objectLoader.copyTo(inFileOS);
					}
					processFile(maker, tempInFile, tempOutFile);
					try (FileInputStream outFileIs = new FileInputStream(tempOutFile)) {
						return inserter.insert(Constants.OBJ_BLOB, tempOutFile.length(), outFileIs);
					} finally {
						inserter.flush();
					}
				} finally {
					tempInFile.delete();
					tempOutFile.delete();
				}
			}
		});
	}

	@PostConstruct
	public void initTempDir() throws IOException {
		tempDir = File.createTempFile("raoa", "thumbnail");
		tempDir.delete();
		if (!tempDir.mkdirs()) {
			throw new IOException("Cannot create directory " + tempDir);
		}
	}

	protected abstract void processFile(ThumbnailMaker maker, File tempInFile, File tempOutFile);

	@PreDestroy
	public void removeTempDir() {
		FileUtils.deleteQuietly(tempDir);
	}

}