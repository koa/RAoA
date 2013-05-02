package ch.bergturbenthal.raoa.provider.store;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public interface FileBackend<T> {
	public interface CommitExecutor {
		boolean prepare();

		void commit();

		void abort();
	}

	Class<T> getType();

	T load(final String relativePath);

	Date getLastModified(final String relativePath);

	Collection<String> listRelativePath(final List<Pattern> pathPatterns);

	CommitExecutor save(final String relativePath, final T value);
}
