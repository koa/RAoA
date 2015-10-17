package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryData;
import ch.bergturbenthal.raoa.server.spring.service.AttachementGenerator;
import ch.bergturbenthal.raoa.server.spring.service.ThumbnailMaker;

@Service
public class ThumbnailAttachementGenerator implements AttachementGenerator {
	final Map<ThumbnailMaker, ExecutorService> executors = new HashMap<ThumbnailMaker, ExecutorService>();
	@Autowired
	private List<ThumbnailMaker> thumbnailMakers;

	@Override
	public String attachementType() {
		return "thumbnails";
	}

	@Override
	public String createAttachementFilename(final AlbumEntryData entry) {
		return entry.getOriginalFileId().name();
	}

	private ThumbnailMaker findThumbnailMaker(final String filename) {
		for (final ThumbnailMaker thumbnailMaker : thumbnailMakers) {
			if (thumbnailMaker.canMakeThumbnail(filename)) {
				return thumbnailMaker;
			}
		}
		return null;
	}

	@Override
	public Future<ObjectId> generateAttachement(final String originalFilename,
																							final Callable<ObjectLoader> entryLoader,
																							final Callable<ObjectLoader> sidecarLoader,
																							final ObjectInserter inserter) {
		final ThumbnailMaker maker = findThumbnailMaker(originalFilename);
		if (maker == null) {
			return null;
		}
		final int lastPt = originalFilename.lastIndexOf('.');
		if (lastPt < 2) {
			return null;
		}
		final String suffix = originalFilename.substring(lastPt);
		final ExecutorService executorService = executors.get(maker);

		return executorService.submit(new Callable<ObjectId>() {

			@Override
			public ObjectId call() throws Exception {
				final File tempInFile = File.createTempFile("thumbnail-in", suffix);
				final File tempOutFile = File.createTempFile("thumbnail-out", suffix);
				try {
					final ObjectLoader objectLoader = entryLoader.call();
					try (FileOutputStream inFileOS = new FileOutputStream(tempInFile)) {
						objectLoader.copyTo(inFileOS);
					}
					maker.makeThumbnail(tempInFile, tempOutFile, tempOutFile.getParentFile());
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
	public void initThreads() {
		for (final ThumbnailMaker thumbnailMaker : thumbnailMakers) {
			executors.put(thumbnailMaker, thumbnailMaker.createExecutorservice());
		}
	}

}
