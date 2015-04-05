package ch.bergturbenthal.image.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.lib.Ref;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import ch.bergturbenthal.raoa.server.util.RepositoryService;
import ch.bergturbenthal.raoa.server.util.RepositoryServiceImpl;

@Slf4j
public class TestGitRepository {
	private final RepositoryService service = new RepositoryServiceImpl();
	@Rule
	public TemporaryFolder testFolder = new TemporaryFolder();

	@Test
	public void testCreateRepository() throws IOException, IllegalStateException, GitAPIException {
		final File repositoryFolder = testFolder.newFolder();
		final Git git = Git.init().setDirectory(repositoryFolder).call();
		// first commit
		updateFileAndAdd("content1", "file1", git);
		git.commit().setMessage("c1").call();
		// second commit
		updateFileAndAdd("content2", "file1", git);
		git.commit().setMessage("c2").call();
		// should be 2 commits
		final int count = service.countCommits(git);
		Assert.assertEquals(2, count);
	}

	@Test
	public void testCreatRepositoryWith2Branches() throws IOException, IllegalStateException, GitAPIException {
		final File repositoryFolder = testFolder.newFolder();
		final Git git = Git.init().setDirectory(repositoryFolder).call();
		// first commit
		updateFileAndAdd("content1", "file1", git);
		git.commit().setMessage("c1").call();

		final Ref newBranchRef = git.checkout().setName("newbranch").setCreateBranch(true).call();
		log.info("new Branch: " + newBranchRef);
		// second commit
		updateFileAndAdd("content2", "file1", git);
		git.commit().setMessage("c2").call();
		// should be 2 commits
		Assert.assertEquals(2, service.countCommits(git));
		// back to master
		service.checkoutMaster(git);
		// should be 1 commits
		Assert.assertEquals(1, service.countCommits(git));

	}

	private void updateFileAndAdd(final String content, final String filename, final Git git)	throws FileNotFoundException,
																																														IOException,
																																														GitAPIException,
																																														NoFilepatternException {
		final File file1 = new File(git.getRepository().getWorkTree(), filename);
		writeToFile(content, file1);
		git.add().addFilepattern(file1.getName()).call();
	}

	private void writeToFile(final String content, final File file) throws FileNotFoundException, IOException {
		@Cleanup
		final OutputStreamWriter output = new OutputStreamWriter(new FileOutputStream(file));
		IOUtils.write(content, output);
	}
}
