package ch.bergturbenthal.raoa.server.spring.model;

import java.lang.ref.WeakReference;
import java.util.Date;

import lombok.Data;

import org.eclipse.jgit.lib.Repository;

@Data
public class AlbumData {
	private Repository albumRepository;
	private WeakReference<AlbumState> albumStateReference;
	private Date lastRefreshTime;
	private WeakReference<ThumbnailState> thumbnailStateReference;
}
