package ch.bergturbenthal.raoa.server;

import java.io.File;

public interface AlbumFactory {
	public Album createAlbum(final File baseDir, final String[] nameComps, final String remoteUri, final String serverName);
}
