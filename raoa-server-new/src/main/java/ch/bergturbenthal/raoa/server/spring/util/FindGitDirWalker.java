package ch.bergturbenthal.raoa.server.spring.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.io.DirectoryWalker;

import reactor.core.publisher.Flux;

public class FindGitDirWalker extends DirectoryWalker<File> {
	public Flux<File> findGitDirs(final File start) {
		try {
			final ArrayList<File> foundDirs = new ArrayList<File>();
			walk(start, foundDirs);
			return Flux.fromIterable(foundDirs);
		} catch (final IOException ex) {
			return Flux.error(ex);
		}
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