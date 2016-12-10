package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.File;

import org.springframework.stereotype.Service;

import ch.bergturbenthal.raoa.server.spring.service.ThumbnailMaker;

@Service
public class ThumbnailAttachementGenerator extends AbstractThumbnailAttachementGenerator {
	public static final String ATTACHEMENT_TYPE = "thumbnail";

	@Override
	public String attachementType() {
		return ATTACHEMENT_TYPE;
	}

	@Override
	protected void processFile(final ThumbnailMaker maker, final File tempInFile, final File tempOutFile) {
		maker.makeThumbnailImage(tempInFile, tempOutFile, 512, tempOutFile.getParentFile());
	}

}
