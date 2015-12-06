package ch.bergturbenthal.raoa.server.spring.model;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.lib.Repository;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@Builder
@RequiredArgsConstructor
public class AlbumData {
	private final AtomicReference<WeakReference<AlbumCache>> albumCacheReference = new AtomicReference<WeakReference<AlbumCache>>(null);
	private final Repository albumRepository;
	private final Map<String, WeakReference<AttachementCache>> attachementCaches = new ConcurrentHashMap<String, WeakReference<AttachementCache>>();
	private final AtomicReference<BranchState> currentState = new AtomicReference<BranchState>();
	private final String fullAlbumName;
}
