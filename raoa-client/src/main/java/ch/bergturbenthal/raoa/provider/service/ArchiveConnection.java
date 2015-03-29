package ch.bergturbenthal.raoa.provider.service;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import android.util.Log;
import ch.bergturbenthal.raoa.data.model.AlbumDetail;
import ch.bergturbenthal.raoa.data.model.AlbumEntry;
import ch.bergturbenthal.raoa.data.model.AlbumImageEntry;
import ch.bergturbenthal.raoa.data.model.AlbumList;
import ch.bergturbenthal.raoa.data.model.ArchiveMeta;
import ch.bergturbenthal.raoa.data.model.PingResponse;
import ch.bergturbenthal.raoa.data.model.UpdateMetadataRequest;
import ch.bergturbenthal.raoa.data.model.mutation.Mutation;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumDto;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumDto.AlbumDtoBuilder;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumEntryDto;
import ch.bergturbenthal.raoa.provider.model.dto.ServerStateDto;

public class ArchiveConnection {
	private static interface ServerConnectionCallable<V> {
		V callServer(final ServerConnection connection, final String albumId);
	}

	/**
	 * Ids per Server, per Album, per Filename
	 */
	private final String	                                       archiveId;
	private final AtomicReference<Map<String, AlbumConnection>>	 cachedAlbums	      = new AtomicReference<Map<String, AlbumConnection>>();
	private final ExecutorService	                               executorService;
	private final AtomicReference<Map<String, ServerConnection>>	serverConnections	= new AtomicReference<Map<String, ServerConnection>>(Collections.<String, ServerConnection> emptyMap());

	public ArchiveConnection(final String archiveId, final ExecutorService executorService) {
		this.archiveId = archiveId;
		this.executorService = executorService;
	}

	private <K, V> Map<K, V> collect(final Map<K, Future<V>> results) {
		final HashMap<K, V> ret = new HashMap<K, V>();
		for (final Entry<K, Future<V>> entries : results.entrySet()) {
			try {
				ret.put(entries.getKey(), entries.getValue().get());
			} catch (final InterruptedException e) {
				throw new RuntimeException("Cannot get Value from Future for key " + entries.getKey(), e);
			} catch (final ExecutionException e) {
				throw new RuntimeException("Cannot get Value from Future for key " + entries.getKey(), e);
			}
		}
		return ret;
	}

	public Collection<ServerStateDto> collectServerStates() {
		final ArrayList<ServerStateDto> ret = new ArrayList<ServerStateDto>();
		for (final ServerConnection connection : serverConnections.get().values()) {
			if (connection == null) {
				continue;
			}
			try {
				ret.add(ServerStateDto.builder().serverName(connection.getServerName()).serverState(connection.getServerState()).build());
			} catch (final Throwable t) {
				Log.w("ARCHIVE_CONNECTION", "Cannot query state from " + connection.getServerName(), t);
			}
		}
		return ret;
	}

	public Map<String, AlbumConnection> getAlbums() {
		final Map<String, AlbumConnection> cached = cachedAlbums.get();
		if (cached != null)
			return cached;
		return listAlbums();
	}

	public Map<String, AlbumConnection> listAlbums() {
		final Map<String, Future<AlbumList>> results = new HashMap<String, Future<AlbumList>>();
		// submit all queries
		for (final Entry<String, ServerConnection> connectionEntry : serverConnections.get().entrySet()) {
			final ServerConnection connection = connectionEntry.getValue();
			if (connection == null) {
				continue;
			}
			results.put(connectionEntry.getKey(), executorService.submit(new Callable<AlbumList>() {
				@Override
				public AlbumList call() throws Exception {
					try {
						return connection.listAlbums();
					} catch (final Throwable t) {
						Log.w("ARCHIVE_CONNECTION", "Cannot list data from server " + connection.getServerName(), t);
						return null;
					}
				}
			}));
		}
		// collect all results
		final Map<String, AlbumList> collectedResults = collect(results);
		// reorder results per album
		final Map<String, Set<String>> serverPerAlbum = new HashMap<String, Set<String>>();
		final Map<String, AlbumEntry> mostCurrentAlbumEntries = new HashMap<String, AlbumEntry>();
		for (final Entry<String, AlbumList> serverEntry : collectedResults.entrySet()) {
			final String serverId = serverEntry.getKey();
			final AlbumList albumList = serverEntry.getValue();
			if (albumList == null) {
				continue;
			}
			for (final AlbumEntry albumEntry : albumList.getAlbumNames()) {
				final String albumName = albumEntry.getName();
				// skip albums without modification time
				if (albumEntry.getLastModified() == null) {
					continue;
				}
				final AlbumEntry alreadyFoundAlbumEntry = mostCurrentAlbumEntries.get(albumName);
				if (alreadyFoundAlbumEntry == null || alreadyFoundAlbumEntry.getLastModified().before(albumEntry.getLastModified())) {
					serverPerAlbum.put(albumName, new HashSet<String>(Arrays.asList(serverId)));
					mostCurrentAlbumEntries.put(albumName, albumEntry);
				} else if (alreadyFoundAlbumEntry.getLastModified().equals(albumEntry.getLastModified())) {
					serverPerAlbum.get(albumName).add(serverId);
				}
			}
		}

		final Map<String, AlbumConnection> albumConnections = new HashMap<String, AlbumConnection>();
		// and make Album-Connections
		for (final Entry<String, Set<String>> perAlbumEntry : serverPerAlbum.entrySet()) {
			final String albumName = perAlbumEntry.getKey();
			final AlbumEntry albumEntry = mostCurrentAlbumEntries.get(albumName);
			final String albumId = albumEntry.getId();
			final Date lastModified = albumEntry.getLastModified();
			final Set<String> servers = new HashSet<String>(perAlbumEntry.getValue());
			albumConnections.put(perAlbumEntry.getKey(), new AlbumConnection() {

				@Override
				public Collection<String> connectedServers() {
					return servers;
				}

				@Override
				public AlbumDto getAlbumDetail() {
					final AlbumDtoBuilder albumDtoBuilder = AlbumDto.builder();
					final Map<String, AlbumEntryDto> entries = new HashMap<String, AlbumEntryDto>();
					for (final String serverId : servers) {
						final ServerConnection serverConnection = serverConnections.get().get(serverId);
						if (serverConnection == null) {
							continue;
						}
						final AlbumDetail albumDetail = serverConnection.getAlbumDetail(albumId);
						if (albumDetail.getAutoAddDate() != null) {
							albumDtoBuilder.autoAddDate(albumDetail.getAutoAddDate());
						}
						albumDtoBuilder.albumTitle(albumDetail.getTitle());
						albumDtoBuilder.albumTitleEntry(albumDetail.getTitleEntry());
						albumDtoBuilder.lastModified(albumDetail.getLastModified());
						for (final AlbumImageEntry entry : albumDetail.getImages()) {
							final String key = entry.getId();
							if (entries.containsKey(key) && entries.get(key).getLastModified().getTime() > entry.getLastModified().getTime()) {
								continue;
							}
							entries.put(key, AlbumEntryDto.fromServer(entry));
						}
						albumDtoBuilder.entries(entries);
					}
					return albumDtoBuilder.build();
				}

				@Override
				public String getCommId() {
					return albumId;
				}

				@Override
				public Date lastModified() {
					return lastModified;
				}

				@Override
				public boolean readThumbnail(final String fileId, final File tempFile, final File targetFile) {
					for (final String serverId : servers) {
						final ServerConnection serverConnection = serverConnections.get().get(serverId);
						if (serverConnection == null) {
							continue;
						}
						if (serverConnection.readThumbnail(albumId, fileId, tempFile, targetFile))
							return true;
					}
					return false;
				}

				@Override
				public void updateMetadata(final Collection<Mutation> updateEntries) {
					for (final String serverId : servers) {
						final ServerConnection serverConnection = serverConnections.get().get(serverId);
						if (serverConnection == null) {
							continue;
						}
						try {
							final UpdateMetadataRequest request = new UpdateMetadataRequest();
							request.getMutationEntries().addAll(updateEntries);
							serverConnection.updateMetadata(albumId, request);
						} catch (final Exception ex) {
							Log.w("ArchiveConnection", "cannot update metadata on server " + serverConnection.getServerName(), ex);
						}
					}

				}
			});
		}
		cachedAlbums.set(albumConnections);
		return albumConnections;
	}

	public Map<String, ServerConnection> listServers() {
		return serverConnections.get();
	}

	public ArchiveMeta listStorages() {
		ArchiveMeta ret = null;
		Date lastModified = new Date(Long.MIN_VALUE);
		for (final ServerConnection connection : serverConnections.get().values()) {
			final ArchiveMeta storages = connection.listStorages();
			if (storages != null && storages.getLastModified().after(lastModified)) {
				lastModified = storages.getLastModified();
				ret = storages;
			}
		}
		return ret;
	}

	public void updateServerConnections(final Map<URL, PingResponse> connections) {
		final Map<String, Collection<URL>> connectionsByServer = new HashMap<String, Collection<URL>>();
		for (final Entry<URL, PingResponse> connectionEntry : connections.entrySet()) {
			final String serverId = connectionEntry.getValue().getServerId();
			if (connectionsByServer.containsKey(serverId)) {
				connectionsByServer.get(serverId).add(connectionEntry.getKey());
			} else {
				connectionsByServer.put(serverId, new ArrayList<URL>(Arrays.asList(connectionEntry.getKey())));
			}
		}

		final HashMap<String, ServerConnection> newConnections = new HashMap<String, ServerConnection>();
		final Map<String, ServerConnection> oldConnections = serverConnections.get();
		for (final Entry<String, Collection<URL>> connectionEntry : connectionsByServer.entrySet()) {
			final String serverId = connectionEntry.getKey();
			final ServerConnection connection = oldConnections.containsKey(serverId) ? oldConnections.get(serverId) : new ServerConnection(serverId);
			connection.updateServerConnections(connectionEntry.getValue());
			for (final URL serverUrl : connectionEntry.getValue()) {
				final PingResponse pingResponse = connections.get(serverUrl);
				if (pingResponse == null) {
					continue;
				}
				final String serverName = pingResponse.getServerName();
				if (serverName == null) {
					continue;
				}
				connection.setServerName(serverName);
				break;
			}
			newConnections.put(serverId, connection);
		}
		serverConnections.set(Collections.unmodifiableMap(newConnections));
	}
}
