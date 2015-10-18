package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import lombok.Cleanup;

import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.springframework.stereotype.Service;

import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryMetadata;
import ch.bergturbenthal.raoa.server.spring.service.MetadataReader;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.MovieHeaderBox;
import com.googlecode.mp4parser.DataSource;
import com.googlecode.mp4parser.util.Path;

@Service
public class Mp4MetadataReader implements MetadataReader {

	@Override
	public void readMetadata(final ObjectLoader objectLoader, final AlbumEntryMetadata.AlbumEntryMetadataBuilder metadataBuilder) throws IOException {
		@Cleanup
		final DataSource dataSource = new DataSource() {
			long currentPos = 0;
			ObjectStream stream = objectLoader.openStream();

			@Override
			public void close() throws IOException {
				stream.close();
			}

			@Override
			public ByteBuffer map(final long startPosition, final long size) throws IOException {
				position(startPosition);
				final int retBufferSize = (int) Math.min(size, size() - startPosition);
				final byte[] retBuffer = new byte[retBufferSize];
				final int readCount = stream.read(retBuffer);
				currentPos += readCount;
				return ByteBuffer.wrap(retBuffer, 0, readCount);
			}

			@Override
			public long position() throws IOException {
				return currentPos;
			}

			@Override
			public void position(final long nuPos) throws IOException {
				if (nuPos < currentPos) {
					resetStream();
				}
				if (nuPos > currentPos) {
					final long skipped = stream.skip(nuPos - currentPos);
					if (skipped < nuPos - currentPos) {
						throw new IOException("Cannot move to " + nuPos);
					}
				}
			}

			@Override
			public int read(final ByteBuffer byteBuffer) throws IOException {
				final int readCount = stream.read(byteBuffer.array());
				currentPos += readCount;
				return readCount;
			}

			private void resetStream() throws IOException {
				stream.close();
				stream = objectLoader.openStream();
				currentPos = 0;
			}

			@Override
			public long size() throws IOException {
				return stream.getSize();
			}

			@Override
			public long transferTo(final long position, final long count, final WritableByteChannel target) throws IOException {
				position(position);
				final long endPosition = position + count;
				final byte[] buffer = new byte[64 * 1024];
				while (currentPos < endPosition) {
					final int readCount = Math.min(buffer.length, (int) (endPosition - currentPos));
					final int loadedCount = stream.read(buffer, 0, readCount);
					if (loadedCount < 0) {
						break;
					}
					currentPos += loadedCount;
					target.write(ByteBuffer.wrap(buffer, 0, loadedCount));
				}
				return currentPos - position;
			}
		};
		final IsoFile isoFile = new IsoFile(dataSource);
		final MovieHeaderBox header = Path.getPath(isoFile, "moov[0]/mvhd[0]");
		if (header != null) {
			metadataBuilder.captureDate(header.getCreationTime());
		}
	}
}
