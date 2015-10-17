package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import lombok.Cleanup;

import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.springframework.stereotype.Service;

import ch.bergturbenthal.raoa.server.spring.service.MetadataReader;

import com.adobe.xmp.XMPMeta;
import com.coremedia.iso.IsoFile;
import com.googlecode.mp4parser.DataSource;

@Service
public class Mp4MetadataReader implements MetadataReader {

	@Override
	public void readMetadata(final ObjectLoader objectLoader, final XMPMeta metadataBuilder) throws IOException {
		@Cleanup
		final ObjectStream stream = objectLoader.openStream();
		final BufferedInputStream bufferedInputStream = new BufferedInputStream(stream);
		final DataSource dataSource = new DataSource() {

			@Override
			public void close() throws IOException {
				stream.close();
			}

			@Override
			public ByteBuffer map(final long startPosition, final long size) throws IOException {

				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public long position() throws IOException {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public void position(final long nuPos) throws IOException {
				// TODO Auto-generated method stub

			}

			@Override
			public int read(final ByteBuffer byteBuffer) throws IOException {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public long size() throws IOException {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public long transferTo(final long position, final long count, final WritableByteChannel target) throws IOException {
				// TODO Auto-generated method stub
				return 0;
			}
		};
		new IsoFile(dataSource);
		// TODO Auto-generated method stub

	}

}
