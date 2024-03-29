package ch.bergturbenthal.raoa.provider.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import android.util.Log;
import ch.bergturbenthal.raoa.data.model.AlbumDetail;
import ch.bergturbenthal.raoa.data.model.AlbumEntry;
import ch.bergturbenthal.raoa.data.model.AlbumImageEntry;
import ch.bergturbenthal.raoa.data.model.AlbumList;
import ch.bergturbenthal.raoa.data.model.ArchiveMeta;
import ch.bergturbenthal.raoa.data.model.CreateAlbumRequest;
import ch.bergturbenthal.raoa.data.model.ImportFileRequest;
import ch.bergturbenthal.raoa.data.model.UpdateMetadataRequest;
import ch.bergturbenthal.raoa.data.model.state.IssueResolveAction;
import ch.bergturbenthal.raoa.data.model.state.ServerState;

public class ServerConnection {
	private static interface ConnectionCallable<V> {
		ResponseEntity<V> call(final URL baseUrl) throws Exception;
	}

	private static final String[]														DATE_FORMATS			= new String[] { "EEE, dd MMM yyyy HH:mm:ss zzz",
	                                                                                           "EEE, dd-MMM-yy HH:mm:ss zzz",
	                                                                                           "EEE MMM dd HH:mm:ss yyyy" };
	private static TimeZone																	GMT								= TimeZone.getTimeZone("GMT");
	private static ObjectMapper															mapper						= new ObjectMapper();
	private static Set<HttpStatus>													okStates					= new HashSet<HttpStatus>(Arrays.asList(HttpStatus.OK,
	                                                                                                                  HttpStatus.CREATED,
	                                                                                                                  HttpStatus.ACCEPTED,
	                                                                                                                  HttpStatus.NOT_MODIFIED));
	private final Map<String, SoftReference<AlbumDetail>>		albumDetailCache	= new HashMap<String, SoftReference<AlbumDetail>>();

	private final AtomicReference<SoftReference<AlbumList>>	albumIds					= new AtomicReference<SoftReference<AlbumList>>();
	private final AtomicReference<Collection<URL>>					connections				= new AtomicReference<Collection<URL>>(Collections.<URL> emptyList());

	private final String																		instanceId;
	private final RestTemplate															restTemplate			= new RestTemplate(false);

	private String																					serverName;

	public ServerConnection(final String instanceId) {
		this.instanceId = instanceId;
		final MappingJackson2HttpMessageConverter mappingJacksonHttpMessageConverter = new MappingJackson2HttpMessageConverter();
		mappingJacksonHttpMessageConverter.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		restTemplate.setMessageConverters((List<HttpMessageConverter<?>>) (List<?>) Collections.singletonList((HttpMessageConverter<?>) mappingJacksonHttpMessageConverter));
		final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(3));
		requestFactory.setReadTimeout((int) TimeUnit.SECONDS.toMillis(120));
		restTemplate.setRequestFactory(requestFactory);
	}

	private <V> V callOne(final ConnectionCallable<V> callable) {
		Throwable t = null;
		for (final URL connection : connections.get()) {
			try {
				// Log.d("CONNECTION", "Start connect to " + connection);
				// final long startTime = System.currentTimeMillis();
				final ResponseEntity<V> response = callable.call(connection);
				// final long endTime = System.currentTimeMillis();
				// Log.i("CONNECTION", "connected to " + connection + ", time: " +
				// (endTime - startTime) + " ms");
				if (response != null && okStates.contains(response.getStatusCode())) {
					return response.getBody();
				}
			} catch (final HttpServerErrorException ex) {
				throw new RuntimeException("Cannot connect to server " + serverName, ex);
			} catch (final Throwable ex) {
				moveConnectionToEnd(connection);
				if (t != null) {
					Log.w("Server-connection", "Exception while calling server " + serverName, t);
				}
				t = ex;
			}
		}
		if (t != null) {
			throw new RuntimeException("Cannot connect to server " + serverName, t);
		} else {
			throw new RuntimeException("Cannot connect to server " + serverName + ", no valid connection found");
		}
	}

	public AlbumEntry createAlbum(final String albumName, final Date autoaddDate) {
		final String[] albumComps = albumName.split("/");
		final CreateAlbumRequest request = new CreateAlbumRequest(albumComps, autoaddDate);
		return callOne(new ConnectionCallable<AlbumEntry>() {
			@Override
			public ResponseEntity<AlbumEntry> call(final URL baseUrl) throws Exception {
				return restTemplate.postForEntity(baseUrl.toExternalForm() + "/albums", request, AlbumEntry.class);
			}
		});
	}

	private List<URL> createList(final Collection<URL> collection) {
		if (collection instanceof List) {
			return (List<URL>) collection;
		}
		return new ArrayList<URL>(collection);
	}

	private ResponseEntity<Void> executePut(final String url, final Object data, final Object... urlVariables) {

		final HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		headers.setContentType(new MediaType("application", "json", Charset.defaultCharset()));

		return restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<Object>(data, headers), Void.class, urlVariables);
	}

	public AlbumDetail getAlbumDetail(final String albumId) {
		final SoftReference<AlbumDetail> cachedValue = albumDetailCache.get(albumId);
		if (cachedValue != null) {
			final AlbumDetail albumDetail = cachedValue.get();
			if (albumDetail != null) {
				return albumDetail;
			}
		}
		final AlbumDetail albumDetail = callOne(new ConnectionCallable<AlbumDetail>() {

			@Override
			public ResponseEntity<AlbumDetail> call(final URL baseUrl) throws Exception {
				return restTemplate.getForEntity(baseUrl.toExternalForm() + "/albums/{albumId}.json", AlbumDetail.class, albumId);
			}
		});
		final Map<String, String> entryIdMap = new HashMap<String, String>();
		for (final AlbumImageEntry entry : albumDetail.getImages()) {
			entryIdMap.put(entry.getName(), entry.getId());
		}
		albumDetailCache.put(albumId, new SoftReference<AlbumDetail>(albumDetail));
		return albumDetail;
	}

	public String getInstanceId() {
		return instanceId;
	}

	public String getServerName() {
		return serverName;
	}

	public ServerState getServerState() {
		return callOne(new ConnectionCallable<ServerState>() {
			@Override
			public ResponseEntity<ServerState> call(final URL baseUrl) throws Exception {
				return restTemplate.getForEntity(baseUrl.toExternalForm() + "/state.json", ServerState.class);
			}
		});
	}

	public void importFile(final String filename, final byte[] data) {
		final ImportFileRequest request = new ImportFileRequest(null, data, filename);
		callOne(new ConnectionCallable<Void>() {

			@Override
			public ResponseEntity<Void> call(final URL baseUrl) throws Exception {
				return executePut(baseUrl.toExternalForm() + "/albums/import", request);
			}
		});
	}

	/**
	 * Album-Keys by album-names
	 *
	 * @return
	 */
	public AlbumList listAlbums() {
		return readAlbumList();
	}

	public ArchiveMeta listStorages() {
		return callOne(new ConnectionCallable<ArchiveMeta>() {

			@Override
			public ResponseEntity<ArchiveMeta> call(final URL baseUrl) throws Exception {
				return restTemplate.getForEntity(baseUrl.toExternalForm() + "/storages.json", ArchiveMeta.class);
			}
		});
	}

	private void moveConnectionToEnd(final URL connection) {
		final Collection<URL> collection = connections.get();
		final ArrayList<URL> newList = new ArrayList<URL>(collection);
		// move to end
		if (newList.remove(connection)) {
			newList.add(connection);
		}
		// update
		connections.compareAndSet(collection, newList);
	}

	private AlbumList readAlbumList() {

		final AlbumList albums = callOne(new ConnectionCallable<AlbumList>() {

			@Override
			public ResponseEntity<AlbumList> call(final URL baseUrl) throws Exception {
				return restTemplate.getForEntity(baseUrl.toExternalForm() + "/albums.json", AlbumList.class);
			}
		});
		albumIds.set(new SoftReference<AlbumList>(albums));
		return albums;
	}

	public boolean readThumbnail(final String albumId, final String fileId, final File tempFile, final File targetFile) {
		return callOne(new ConnectionCallable<Boolean>() {

			@Override
			public ResponseEntity<Boolean> call(final URL baseUrl) throws Exception {

				return restTemplate.execute(baseUrl.toExternalForm() + "/albums/{albumId}/image/{imageId}.jpg", HttpMethod.GET, new RequestCallback() {
					@Override
					public void doWithRequest(final ClientHttpRequest request) throws IOException {
						if (targetFile.exists()) {
							request.getHeaders().setIfModifiedSince(targetFile.lastModified());
						}
					}
				}, new ResponseExtractor<ResponseEntity<Boolean>>() {
					@Override
					public ResponseEntity<Boolean> extractData(final ClientHttpResponse response) throws IOException {
						if (response.getStatusCode() == HttpStatus.NOT_MODIFIED) {
							return new ResponseEntity<Boolean>(Boolean.TRUE, response.getStatusCode());
						}
						if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
							return new ResponseEntity<Boolean>(Boolean.FALSE, response.getStatusCode());
						}
						final HttpHeaders headers = response.getHeaders();
						final String mimeType = headers.getContentType().toString();
						final long lastModified = headers.getLastModified();
						final Date lastModifiedDate;
						if (lastModified > 0) {
							lastModifiedDate = new Date(lastModified);
						} else {
							lastModifiedDate = null;
						}
						Date createDate = null;
						final String createDateString = headers.getFirst("created-at");
						if (createDateString != null) {
							for (final String dateFormat : DATE_FORMATS) {
								final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat, Locale.US);
								simpleDateFormat.setTimeZone(GMT);
								try {
									createDate = simpleDateFormat.parse(createDateString);
									break;
								} catch (final ParseException e) {
									// ignore
								}
							}
							if (createDate == null) {
								throw new IllegalArgumentException("Cannot parse date value \"" + createDateString + "\" for \"created-at\" header");
							}
						}
						final OutputStream arrayOutputStream = new FileOutputStream(tempFile);
						try {
							final InputStream inputStream = response.getBody();
							final byte[] buffer = new byte[8192];
							while (true) {
								final int read = inputStream.read(buffer);
								if (read < 0) {
									break;
								}
								arrayOutputStream.write(buffer, 0, read);
							}
						} finally {
							arrayOutputStream.close();
						}
						final boolean renameOk = tempFile.renameTo(targetFile);
						if (lastModifiedDate != null) {
							targetFile.setLastModified(lastModifiedDate.getTime());
						}

						return new ResponseEntity<Boolean>(Boolean.valueOf(renameOk), response.getStatusCode());
					}
				}, albumId, fileId);
			}
		}).booleanValue();

	}

	/**
	 * Try to resolve a Issue
	 *
	 * @param issueId
	 *          Issue to resolve
	 * @param action
	 *          action to execute on issue
	 * @return false: exection failed
	 */
	public boolean resolveIssue(final String issueId, final IssueResolveAction action) {
		try {
			callOne(new ConnectionCallable<Void>() {
				@Override
				public ResponseEntity<Void> call(final URL baseUrl) throws Exception {
					return executePut(baseUrl.toExternalForm() + "/state/issue/{issueId}/resolve", action.name(), URLEncoder.encode(issueId, "utf-8"));
				}
			});
		} catch (final RuntimeException ex) {
			Log.e("ServerConnection", "Cannot resolve issue " + issueId, ex);
			return false;
		}
		return true;
	}

	public void setServerName(final String serverName) {
		this.serverName = serverName;
	}

	public void updateMetadata(final String albumId, final UpdateMetadataRequest request) {
		callOne(new ConnectionCallable<Void>() {

			@Override
			public ResponseEntity<Void> call(final URL baseUrl) throws Exception {
				return executePut(baseUrl.toExternalForm() + "/albums/{albumId}/updateMeta", request, albumId);
			}
		});
	}

	public void updateServerConnections(final Collection<URL> value) {
		final Collection<URL> oldList = connections.get();
		if (oldList != null) {
			final ArrayList<URL> newList = new ArrayList<URL>(value);
			final Map<URL, Integer> oldOrder = new HashMap<URL, Integer>();
			int index = 0;
			// initialize all entries with default-value
			for (final URL url : newList) {
				oldOrder.put(url, Integer.valueOf(-1));
			}
			// override all entries from old list
			for (final URL url : oldList) {
				oldOrder.put(url, Integer.valueOf(index++));
			}
			// change order of new list
			Collections.sort(newList, new Comparator<URL>() {

				@Override
				public int compare(final URL lhs, final URL rhs) {
					return oldOrder.get(lhs).compareTo(oldOrder.get(rhs));
				}

			});
			connections.set(newList);
		} else {
			connections.set(value);
		}
	}
}
