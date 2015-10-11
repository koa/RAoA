package ch.bergturbenthal.raoa.server.spring.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.io.DirectoryWalker;

public class FindGitDirWalker extends DirectoryWalker<File> {
	public Collection<File> findGitDirs(final File start) throws IOException {
		final ArrayList<File> foundDirs = new ArrayList<File>();
		walk(start, foundDirs);
		return foundDirs;
	}

	@Override
	protected boolean handleDirectory(final File directory, final int depth, final Collection<File> results) throws IOException {
		final String name = directory.getName();
		if (name.endsWith(".git") && name.length() > 5) {
			results.add(directory);
			return false;
		}
		return true;
	}

}