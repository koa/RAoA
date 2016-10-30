package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.File;

import org.springframework.stereotype.Service;

import ch.bergturbenthal.raoa.server.spring.service.ThumbnailMaker;

@Service
public class MobileAttachementGenerator extends AbstractThumbnailAttachementGenerator {
	public static final String ATTACHEMENT_TYPE = "mobile-version";

	@Override
	public String attachementType() {
		return ATTACHEMENT_TYPE;
	}

	@Override
	protected void processFile(final ThumbnailMaker maker, final File tempInFile, final File tempOutFile) {
		maker.makeThumbnail(tempInFile, tempOutFile, tempOutFile.getParentFile());
	}

}
