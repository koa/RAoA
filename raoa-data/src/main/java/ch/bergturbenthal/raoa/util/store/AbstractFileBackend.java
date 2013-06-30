package ch.bergturbenthal.raoa.util.store;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public abstract class AbstractFileBackend<T> implements FileBackend<T> {
	protected interface FileSerializer<T> {
		public void writeToFile(final File f, final T value) throws IOException;

		public T readFromFile(final File f) throws IOException;
	}

	protected final File basePath;

	private final int cacheWeight;
	private final FileSerializer<T> serializer;

	private final String suffix;

	private static void mkdirIfNotExists(final File dir) {
		if (!dir.exists()) {
			dir.mkdirs();
		}
	}

	public AbstractFileBackend(final File basePath, final String suffix, final FileSerializer<T> serializer, final int cacheWeight) {
		this.basePath = basePath;
		this.suffix = suffix;
		this.serializer = serializer;
		this.cacheWeight = cacheWeight;
	}

	@Override
	public int cacheWeight() {
		return cacheWeight;
	}

	@Override
	public Date getLastModified(final String relativePath) {
		final File file = new File(basePath, relativePath);
		if (file.exists()) {
			return new Date(file.lastModified());
		} else {
			return null;
		}
	}

	@Override
	public Collection<String> listRelativePath(final List<Pattern> pathPatterns) {
		final ArrayList<String> ret = new ArrayList<String>();
		if (pathPatterns.size() > 0) {
			collectRelativePath(ret, basePath, pathPatterns);
		}
		return ret;
	}

	@Override
	public T load(final String relativePath) {
		final long start = System.currentTimeMillis();
		try {
			final File file = resolveFilePath(relativePath);
			if (!file.exists()) {
				return null;
			}
			return serializer.readFromFile(file);
		} catch (final IOException e) {
			throw new RuntimeException("Cannot read file " + relativePath, e);
		} finally {
			final long time = System.currentTimeMillis() - start;
			if (time > 50) {
				// Log.i("Performance Read", "read of " + relativePath + " took " + time + " ms");
			}
		}
	}

	@Override
	public CommitExecutor save(final String relativePath, final T value) {
		final File targetFile = resolveFilePath(relativePath);
		final File tempTargetFile = resolveTempFilePath(relativePath);
		return new CommitExecutor() {

			@Override
			public void abort() {
				tempTargetFile.delete();
			}

			@Override
			public void commit() {
				if (tempTargetFile.exists()) {
					tempTargetFile.renameTo(targetFile);
				}
				if (value == null) {
					targetFile.delete();
				} else {
					assert targetFile.exists();
				}
			}

			@Override
			public boolean prepare() {
				try {
					if (tempTargetFile.exists()) {
						tempTargetFile.delete();
					}
					if (value == null) {
						return true;
					}
					mkdirIfNotExists(tempTargetFile.getParentFile());
					mkdirIfNotExists(targetFile.getParentFile());
					serializer.writeToFile(tempTargetFile, value);
					if (!tempTargetFile.exists()) {
						return false;
					}
					boolean equals = targetFile.exists();
					if (equals) {
						final InputStream newIs = new BufferedInputStream(new FileInputStream(tempTargetFile));
						try {
							final InputStream oldIs = new BufferedInputStream(new FileInputStream(targetFile));
							try {
								while (equals) {
									final int newByte = newIs.read();
									final int oldByte = oldIs.read();
									equals &= newByte == oldByte;
									if (newByte < 0) {
										break;
									}
								}
							} finally {
								oldIs.close();
							}
						} finally {
							newIs.close();
						}
					}
					if (equals) {
						// no update needed
						tempTargetFile.delete();
					}

					return true;
				} catch (final IOException e) {
					throw new RuntimeException("Cannot write File " + relativePath + " to " + tempTargetFile, e);
				}
			}
		};
	}

	protected File resolveFilePath(final String relativePath) {
		return new File(basePath, relativePath + suffix);
	}

	protected File resolveTempFilePath(final String relativePath) {
		return new File(basePath, relativePath + suffix + "-temp");
	}

	private void collectRelativePath(final Collection<String> result, final File currentBasePath, final List<Pattern> remainingPatterns) {
		final Pattern pattern = remainingPatterns.get(0);
		if (remainingPatterns.size() == 1) {
			final int prefixLength = basePath.getAbsolutePath().length() + 1;
			for (final File file : currentBasePath.listFiles(new FileFilter() {
				@Override
				public boolean accept(final File candidate) {
					if (!(candidate.isFile() && candidate.canRead())) {
						return false;
					}
					final String name = candidate.getName();
					if (!name.endsWith(suffix)) {
						return false;
					}
					return pattern.matcher(name.substring(0, name.length() - suffix.length())).matches();
				}
			})) {
				final String absolutePath = file.getAbsolutePath();
				result.add(absolutePath.substring(prefixLength, absolutePath.length() - suffix.length()));
			}
		} else {
			final List<Pattern> subPatterns = remainingPatterns.subList(1, remainingPatterns.size());
			for (final File file : currentBasePath.listFiles(new FileFilter() {
				@Override
				public boolean accept(final File candidate) {
					return candidate.isDirectory() && pattern.matcher(candidate.getName()).matches();
				}
			})) {
				collectRelativePath(result, file, subPatterns);
			}
		}
	}
}