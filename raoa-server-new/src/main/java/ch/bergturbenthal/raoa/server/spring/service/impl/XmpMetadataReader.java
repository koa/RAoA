package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;

import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryMetadata;
import lombok.extern.slf4j.Slf4j;

@Service
@Order(100)
@Slf4j
public class XmpMetadataReader extends AbstractDrewMetadataReader {

	@Override
	public Collection<String> metadataFileOf(final String filename) {
		return Collections.singleton(filename + ".xmp");
	}

	@Override
	public void readMetadata(final ObjectLoader objectLoader, final AlbumEntryMetadata metadata) throws IOException {
		final long startTime = System.nanoTime();
		try (final ObjectStream stream = objectLoader.openStream()) {
			final XMPMeta xmpMeta = XMPMetaFactory.parse(stream);
			final String caption = readCaption(xmpMeta);
			if (caption != null) {
				metadata.setCaption(caption);
			}
			final Collection<String> keywords = readKeywords(xmpMeta);
			if (keywords != null && !keywords.isEmpty()) {
				metadata.appendKeywords(keywords);
			}
			final Integer rating = readRating(xmpMeta);
			if (rating != null) {
				metadata.setRating(rating);
			}
		} catch (final XMPException e) {
			throw new RuntimeException("Cannot parse xmp file", e);
		} finally {
			log.info("Load time: " + ((System.nanoTime() - startTime) * 1.0 / TimeUnit.SECONDS.toNanos(1)));
		}

		// TODO Auto-generated method stub

	}

}
