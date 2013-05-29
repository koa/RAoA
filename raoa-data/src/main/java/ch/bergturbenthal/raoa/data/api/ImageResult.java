package ch.bergturbenthal.raoa.data.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class ImageResult {
	public static enum ResultCode {
		NOT_MODIFIED, OK, TRY_LATER
	}

	public static interface StreamSource {
		InputStream getInputStream() throws IOException;
	}

	public static ImageResult makeModifiedResult(final Date lastModified, final Date created, final StreamSource dataStream, final String mimeType) {
		return new ImageResult(lastModified, created, dataStream, mimeType, ResultCode.OK);
	}

	public static ImageResult makeNotModifiedResult() {
		return new ImageResult(null, null, null, null, ResultCode.NOT_MODIFIED);
	}

	public static ImageResult makeTryLaterResult() {
		return new ImageResult(null, null, null, null, ResultCode.TRY_LATER);
	}

	private final Date created;
	private final StreamSource dataStream;
	private final Date lastModified;

	private final String mimeType;

	private final ResultCode status;

	private ImageResult(final Date lastModified, final Date created, final StreamSource dataStream, final String mimeType, final ResultCode status) {
		super();
		this.lastModified = lastModified;
		this.created = created;
		this.dataStream = dataStream;
		this.mimeType = mimeType;
		this.status = status;
	}

	public Date getCreated() {
		return created;
	}

	public InputStream getDataStream() {
		try {
			return dataStream.getInputStream();
		} catch (final IOException e) {
			throw new RuntimeException("Cannot make new Stream", e);
		}
	}

	public Date getLastModified() {
		return lastModified;
	}

	/**
	 * Returns the mimeType.
	 * 
	 * @return the mimeType
	 */
	public String getMimeType() {
		return mimeType;
	}

	/**
	 * Returns the status.
	 * 
	 * @return the status
	 */
	public ResultCode getStatus() {
		return status;
	}

}
