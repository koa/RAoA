package ch.bergturbenthal.raoa.server.spring.model;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.lib.Repository;

import lombok.Data;

@Data
public class AlbumData {
	private Repository albumRepository;
	private WeakReference<AlbumState> albumStateReference;
	private Map<String, WeakReference<AttachementState>> attachementStates = new HashMap<String, WeakReference<AttachementState>>();
	private String fullAlbumName;
	private Date lastRefreshTime;
}
