package ch.bergturbenthal.raoa.server.spring.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import ch.bergturbenthal.raoa.server.spring.service.impl.FfmpegVideoThumbnailMaker;
import ch.bergturbenthal.raoa.server.spring.service.impl.ImageMagickImageThumbnailMaker;
import ch.bergturbenthal.raoa.server.spring.util.FindGitDirWalker;

@Slf4j
public class TestCreateThumbnail {
	public static void main(final String[] args) throws IOException {
		final ImageMagickImageThumbnailMaker imageThumbnailMaker = new ImageMagickImageThumbnailMaker();
		imageThumbnailMaker.init();
		final FfmpegVideoThumbnailMaker ffmpegVideoThumbnailMaker = new FfmpegVideoThumbnailMaker();
		ffmpegVideoThumbnailMaker.init();
		final Collection<File> gitDirs = new FindGitDirWalker().findGitDirs(new File("/tmp/testdata"));
		for (final File file : gitDirs) {
			log.info("Repository" + file);
			final FileRepositoryBuilder builder = new FileRepositoryBuilder();
			final Repository repository = builder.readEnvironment().setGitDir(file).build();
			@Cleanup
			final RevWalk revWalk = new RevWalk(repository);
			final Map<String, ObjectId> existingThumbnails = new HashMap<>();
			final ObjectId thumbnailsRev = repository.resolve("refs/heads/thumbnails");
			@Cleanup
			final TreeWalk treeWalk = new TreeWalk(repository);
			String foundCommitId = null;
			if (thumbnailsRev != null) {
				final RevCommit thumbnailsCommit = revWalk.parseCommit(thumbnailsRev);
				treeWalk.addTree(thumbnailsCommit.getTree());
				while (treeWalk.next()) {
					final ObjectId objectId = treeWalk.getObjectId(0);
					final String pathString = treeWalk.getPathString();
					if (pathString.equals("commit-id")) {
						final ObjectLoader commitIdLoader = repository.open(objectId);
						if (commitIdLoader.getSize() > 100) {
							continue;
						}
						foundCommitId = new String(commitIdLoader.getBytes());
						continue;
					}
					existingThumbnails.put(pathString, objectId);
				}
				revWalk.reset();
				treeWalk.reset();
			}
			// treeWalk.reset();
			final ObjectId masterRef = repository.resolve("refs/heads/master");
			if (masterRef == null) {
				log.error("No Master in " + file);
				continue;
			}
			if (foundCommitId != null && masterRef.getName().equals(foundCommitId)) {
				continue;
			}
			final RevCommit commit = revWalk.parseCommit(masterRef);
			log.info("Commit: " + commit);
			final RevTree tree = commit.getTree();
			log.info("Tree: " + tree);
			treeWalk.addTree(tree);
			final Map<String, ObjectId> masterFiles = new HashMap<String, ObjectId>();
			final ObjectInserter inserter = repository.getObjectDatabase().newInserter();
			final TreeFormatter thumbnailTreeFormatter = new TreeFormatter();
			while (treeWalk.next()) {
				if (treeWalk.isSubtree()) {
					continue;
				}
				final ObjectId objectId = treeWalk.getObjectId(0);
				final String pathString = treeWalk.getPathString();
				final ObjectId existingThumbnailId = existingThumbnails.get(objectId.getName());
				if (existingThumbnailId == null) {
					final int positionOfPoint = pathString.lastIndexOf('.');
					if (positionOfPoint < 2) {
						continue;
					}
					final String suffix = pathString.substring(positionOfPoint);
					final File tempInFile = File.createTempFile("in-tmp", suffix);
					final File tempOutFile = File.createTempFile("out-tmp", suffix);
					log.info("create thumbnail for " + pathString + " tmp: " + tempOutFile);

					try {
						final ObjectLoader objectLoader = repository.open(objectId);
						{
							@Cleanup
							final FileOutputStream fos = new FileOutputStream(tempInFile);
							objectLoader.copyTo(fos);
						}
						if (suffix.toLowerCase().equals(".jpg")) {
							imageThumbnailMaker.makeThumbnail(tempInFile, tempOutFile, new File("/tmp"));
						} else if (suffix.toLowerCase().equals(".mp4")) {
							ffmpegVideoThumbnailMaker.makeThumbnail(tempInFile, tempOutFile, new File("/tmp"));
						} else {
							continue;
						}
						tempInFile.delete();
						{
							@Cleanup
							final FileInputStream tempFileStream = new FileInputStream(tempOutFile);
							final ObjectId thumbnailObjectId = inserter.insert(Constants.OBJ_BLOB, tempOutFile.length(), tempFileStream);
							thumbnailTreeFormatter.append(objectId.getName(), FileMode.REGULAR_FILE, thumbnailObjectId);
						}
					} finally {
						tempOutFile.delete();
						tempInFile.delete();
					}
				} else {
					thumbnailTreeFormatter.append(objectId.getName(), FileMode.REGULAR_FILE, existingThumbnailId);
				}
				masterFiles.put(pathString, objectId);
			}
			final ObjectId commitIdFile = inserter.insert(Constants.OBJ_BLOB, commit.getName().getBytes());
			thumbnailTreeFormatter.append("commit-id", FileMode.REGULAR_FILE, commitIdFile);
			final ObjectId treeId = inserter.insert(thumbnailTreeFormatter);
			final CommitBuilder commitBuilder = new CommitBuilder();
			if (thumbnailsRev != null) {
				commitBuilder.setParentId(thumbnailsRev);
			}
			commitBuilder.setTreeId(treeId);
			final PersonIdent author = new PersonIdent("Server ", "dummy@none");
			commitBuilder.setAuthor(author);
			commitBuilder.setCommitter(author);
			final ObjectId commitId = inserter.insert(commitBuilder);
			inserter.flush();
			final RefUpdate ru = repository.updateRef("refs/heads/thumbnails");
			ru.setNewObjectId(commitId);
			ru.setRefLogMessage("thumbnails added", false);
			if (thumbnailsRev != null) {
				ru.setExpectedOldObjectId(thumbnailsRev);
			} else {
				ru.setExpectedOldObjectId(ObjectId.zeroId());
			}
			final Result result = ru.update();
			System.out.println(result);
		}

		// TODO Auto-generated method stub

	}
}
