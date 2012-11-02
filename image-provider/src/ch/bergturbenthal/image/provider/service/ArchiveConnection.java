package ch.bergturbenthal.image.provider.service;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import ch.bergturbenthal.image.data.model.PingResponse;

public class ArchiveConnection {
  private final String archiveId;
  private final AtomicReference<Map<String, ServerConnecion>> serverConnections =
                                                                                  new AtomicReference<Map<String, ServerConnecion>>(
                                                                                                                                    Collections.<String, ServerConnecion> emptyMap());
  private final ExecutorService executorService;

  public ArchiveConnection(final String archiveId, final ExecutorService executorService) {
    this.archiveId = archiveId;
    this.executorService = executorService;
  }

  public Map<String, AlbumConnection> listAlbums() {
    final Map<String, Future<Map<String, String>>> results = new HashMap<String, Future<Map<String, String>>>();
    // submit all queries
    for (final Entry<String, ServerConnecion> connectionEntry : serverConnections.get().entrySet()) {
      results.put(connectionEntry.getKey(), executorService.submit(new Callable<Map<String, String>>() {
        @Override
        public Map<String, String> call() throws Exception {
          return connectionEntry.getValue().listAlbums();
        }
      }));
    }
    // collect all results
    final Map<String, Map<String, String>> collectedResults = collect(results);
    // reorder results per album
    final Map<String, Map<String, String>> perAlbumResults = new HashMap<String, Map<String, String>>();
    for (final Entry<String, Map<String, String>> serverResultEntry : collectedResults.entrySet()) {
      final String serverId = serverResultEntry.getKey();
      for (final Entry<String, String> albumEntry : serverResultEntry.getValue().entrySet()) {
        final String albumName = albumEntry.getKey();
        final String albumId = albumEntry.getValue();

        if (!perAlbumResults.containsKey(albumName)) {
          perAlbumResults.put(albumName, new HashMap<String, String>());
        }
        perAlbumResults.get(albumName).put(serverId, albumId);
      }
    }
    final Map<String, AlbumConnection> albumConnections = new HashMap<String, AlbumConnection>();
    // and make Album-Connections
    for (final Entry<String, Map<String, String>> perAlbumEntry : perAlbumResults.entrySet()) {
      final Map<String, String> connections = perAlbumEntry.getValue();
      albumConnections.put(perAlbumEntry.getKey(), new AlbumConnection() {

        @Override
        public Collection<String> connectedServers() {
          return connections.values();
        }
      });
    }
    return albumConnections;
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

    final HashMap<String, ServerConnecion> newConnections = new HashMap<String, ServerConnecion>();
    final Map<String, ServerConnecion> oldConnections = serverConnections.get();
    for (final Entry<String, Collection<URL>> connectionEntry : connectionsByServer.entrySet()) {
      final String serverId = connectionEntry.getKey();
      final ServerConnecion connection = oldConnections.containsKey(serverId) ? oldConnections.get(serverId) : new ServerConnecion();
      connection.updateServerConnections(connectionEntry.getValue());
      newConnections.put(serverId, connection);
    }
    serverConnections.set(Collections.unmodifiableMap(newConnections));
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
}
