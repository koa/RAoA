/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.store;

import java.io.File;
import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;

/**
 * TODO: add type comment.
 * 
 * @param <T>
 * 
 */
public class JacksonBackend<T> extends AbstractFileBackend<T> {

	private final static ObjectMapper mapper = new ObjectMapper();
	private static final String SUFFIX = ".json";
	private final Class<T> type;

	/**
	 * @param basePath
	 * @param suffix
	 * @param serializer
	 */
	public JacksonBackend(final File basePath, final Class<T> type) {
		super(basePath, SUFFIX, new AbstractFileBackend.FileSerializer<T>() {

			@Override
			public T readFromFile(final File f) throws IOException {
				return mapper.reader(type).readValue(f);
			}

			@Override
			public void writeToFile(final File f, final T value) throws IOException {
				mapper.writer().writeValue(f, value);
			}
		});
		this.type = type;
	}

	@Override
	public Class<T> getType() {
		return type;
	}
}