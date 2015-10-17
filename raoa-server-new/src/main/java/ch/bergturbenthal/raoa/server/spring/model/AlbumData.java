package ch.bergturbenthal.raoa.server.spring.model;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import lombok.Data;

import org.eclipse.jgit.lib.Repository;

@Data
public class AlbumData {
	private Repository albumRepository;
	private WeakReference<AlbumState> albumStateReference;
	private Map<String, WeakReference<AttachementState>> attachementStates = new HashMap<String, WeakReference<AttachementState>>();
	private Date lastRefreshTime;
}
