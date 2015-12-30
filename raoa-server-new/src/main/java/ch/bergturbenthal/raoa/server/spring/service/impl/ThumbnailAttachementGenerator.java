package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.File;

import org.springframework.stereotype.Service;

import ch.bergturbenthal.raoa.server.spring.service.ThumbnailMaker;

@Service
public class ThumbnailAttachementGenerator extends AbstractThumbnailAttachementGenerator {
	@Override
	public String attachementType() {
		return "thumbnail";
	}

	@Override
	protected void processFile(final ThumbnailMaker maker, final File tempInFile, final File tempOutFile) {
		maker.makeThumbnailImage(tempInFile, tempOutFile, 512, tempOutFile.getParentFile());
	}

}
