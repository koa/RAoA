package ch.bergturbenthal.raoa.provider.util;

import java.util.List;

import android.net.Uri;

public class ThumbnailUriParser {
	public static interface ThumbnailUriReceiver<V> {
		V execute(final String archiveName, final String albumId, final String thumbnailId);
	}

	public static <V> V parseUri(final Uri uri, final ThumbnailUriReceiver<V> receiver) {
		final List<String> segments = uri.getPathSegments();
		final String archive = segments.get(1);
		final String albumId = segments.get(2);
		final String image = segments.get(4);
		return receiver.execute(archive, albumId, image);
	}
}
