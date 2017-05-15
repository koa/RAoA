/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.util.store;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.InputDecorator;
import com.fasterxml.jackson.core.io.OutputDecorator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

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
	 * @param cacheWeight
	 *          TODO
	 * @param compression
	 *          TODO
	 * @param suffix
	 * @param serializer
	 */
	public JacksonBackend(final File basePath, final Class<T> type, final int cacheWeight, final boolean compression) {
		super(basePath, SUFFIX, new AbstractFileBackend.FileSerializer<T>() {

			private final ObjectReader reader;
			private final ObjectWriter writer;
			{
				mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
				if (compression) {
					final JsonFactory jsonFactory = mapper.getJsonFactory();
					jsonFactory.setOutputDecorator(new OutputDecorator() {

						@Override
						public OutputStream decorate(final IOContext ctxt, final OutputStream out) throws IOException {
							return new GZIPOutputStream(out);
						}

						@Override
						public Writer decorate(final IOContext ctxt, final Writer w) throws IOException {
							return w;
						}
					});
					jsonFactory.setInputDecorator(new InputDecorator() {

						@Override
						public InputStream decorate(final IOContext ctxt, final byte[] src, final int offset, final int length) throws IOException {
							return null;
						}

						@Override
						public InputStream decorate(final IOContext ctxt, final InputStream in) throws IOException {
							return new GZIPInputStream(in);
						}

						@Override
						public Reader decorate(final IOContext ctxt, final Reader src) throws IOException {
							return src;
						}
					});
					writer = mapper.writer().withDefaultPrettyPrinter();
					reader = mapper.readerFor(type);
				} else {
					writer = mapper.writer().withDefaultPrettyPrinter();
					reader = mapper.readerFor(type);
				}
			}

			@Override
			public T readFromFile(final File f) throws IOException {
				return reader.readValue(f);
			}

			@Override
			public void writeToFile(final File f, final T value) throws IOException {
				writer.writeValue(f, value);
			}
		}, cacheWeight);

		this.type = type;
	}

	@Override
	public Class<T> getType() {
		return type;
	}
}
