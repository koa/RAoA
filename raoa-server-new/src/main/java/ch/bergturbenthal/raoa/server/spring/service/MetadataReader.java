package ch.bergturbenthal.raoa.server.spring.service;

import java.io.IOException;

import org.eclipse.jgit.lib.ObjectLoader;

import com.adobe.xmp.XMPMeta;

public interface MetadataReader {
	void readMetadata(final ObjectLoader objectLoader, final XMPMeta metadata) throws IOException;
}
