package ch.bergturbenthal.raoa.client.document;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdManager.DiscoveryListener;
import android.net.nsd.NsdManager.ResolveListener;
import android.net.nsd.NsdServiceInfo;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.util.Log;

public class ImageDocumentProvider extends DocumentsProvider {
	private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {	Document.COLUMN_DOCUMENT_ID,
																																							Document.COLUMN_MIME_TYPE,
																																							Document.COLUMN_DISPLAY_NAME,
																																							Document.COLUMN_LAST_MODIFIED,
																																							Document.COLUMN_FLAGS,
																																							Document.COLUMN_SIZE, };
	private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {	Root.COLUMN_ROOT_ID,
																																					Root.COLUMN_MIME_TYPES,
																																					Root.COLUMN_FLAGS,
																																					Root.COLUMN_ICON,
																																					Root.COLUMN_TITLE,
																																					Root.COLUMN_SUMMARY,
																																					Root.COLUMN_DOCUMENT_ID,
																																					Root.COLUMN_AVAILABLE_BYTES, };
	private static final String TAG = "content-provider";
	private DiscoveryListener mDiscoveryListener;
	private NsdManager mNsdManager;
	private final Queue<NsdServiceInfo> pendingResolveQueue = new LinkedList<NsdServiceInfo>();
	private final Semaphore pendingResolveSemaphore = new Semaphore(1);

	public void initializeDiscoveryListener() {

		mNsdManager = getContext().getSystemService(NsdManager.class);

		mDiscoveryListener = new NsdManager.DiscoveryListener() {

			// Called as soon as service discovery begins.
			@Override
			public void onDiscoveryStarted(final String regType) {
				Log.d(TAG, "Service discovery started");
			}

			@Override
			public void onDiscoveryStopped(final String serviceType) {
				Log.i(TAG, "Discovery stopped: " + serviceType);
			}

			@Override
			public void onServiceFound(final NsdServiceInfo service) {
				// A service was found! Do something with it.
				// Log.d(TAG, "Service discovery success" + service);
				// Log.d(TAG, "Service-Name: " + service.getServiceName());
				// Log.d(TAG, "Service-Type: " + service.getServiceType());
				// Log.d(TAG, "Attributes: " + service.getAttributes());
				// Log.d(TAG, "Host: " + service.getHost());
				pendingResolveQueue.add(service);
				trySubmitResolve();
				// if (!service.getServiceType().equals(SERVICE_TYPE)) {
				// // Service type is the string containing the protocol and
				// // transport layer for this service.
				// Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
				// } else if (service.getServiceName().contains("NsdChat")) {
				// //mNsdManager.resolveService(service, mResolveListener);
				// }
			}

			@Override
			public void onServiceLost(final NsdServiceInfo service) {
				// When the network service is no longer available.
				// Internal bookkeeping code goes here.
				Log.e(TAG, "service lost" + service);
			}

			@Override
			public void onStartDiscoveryFailed(final String serviceType, final int errorCode) {
				Log.e(TAG, "Discovery failed: Error code:" + errorCode);
				mNsdManager.stopServiceDiscovery(this);
			}

			@Override
			public void onStopDiscoveryFailed(final String serviceType, final int errorCode) {
				Log.e(TAG, "Discovery failed: Error code:" + errorCode);
				mNsdManager.stopServiceDiscovery(this);
			}

			private void trySubmitResolve() {
				final boolean hasLock = pendingResolveSemaphore.tryAcquire();
				if (hasLock) {
					final NsdServiceInfo service = pendingResolveQueue.poll();
					if (service != null) {
						Log.i(TAG, "Request Resolve " + hasLock + ", " + pendingResolveSemaphore);
						mNsdManager.resolveService(service, new ResolveListener() {

							@Override
							public void onResolveFailed(final NsdServiceInfo serviceInfo, final int errorCode) {
								switch (errorCode) {
								case NsdManager.FAILURE_ALREADY_ACTIVE:
									Log.i(TAG, "Resolve failed: already Active, " + serviceInfo);
									// pendingResolveQueue.add(service);
									break;
								default:
									Log.i(TAG, "Resolve failed: " + errorCode + ", " + serviceInfo);
									break;
								}
								pendingResolveSemaphore.release();
								trySubmitResolve();
							}

							@Override
							public void onServiceResolved(final NsdServiceInfo serviceInfo) {
								Log.d(TAG, "Resolved: " + serviceInfo);
								Log.d(TAG, "Resolved-Attributes: " + serviceInfo.getAttributes());
								Log.d(TAG, "Resolved-Path: " + serviceInfo.getAttributes().get("path"));
								final InetAddress host = serviceInfo.getHost();
								Log.d(TAG, "Resolved-Host: " + host);
								if (host != null && host instanceof Inet6Address) {
									Log.d(TAG, "Interface: " + ((Inet6Address) host).getScopedInterface());
								}
								if (host != null) {
									try {
										Log.i(TAG, "Resolved: Ping-result: " + host.isReachable(200));
									} catch (final IOException e) {
										Log.e(TAG, "Cannot Ping", e);
									}
								}
								pendingResolveSemaphore.release();
								trySubmitResolve();
							}
						});
					} else {
						pendingResolveSemaphore.release();
						Log.i(TAG, "All Resolved");
					}
				} else {
					Log.i(TAG, "Cannot take lock");
				}
			}
		};
		mNsdManager.discoverServices("_http._tcp", NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
	}

	@Override
	public boolean onCreate() {
		initializeDiscoveryListener();
		Log.i(TAG, "Created");
		return true;
	}

	@Override
	public ParcelFileDescriptor openDocument(final String documentId, final String mode, final CancellationSignal signal) throws FileNotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Cursor queryChildDocuments(final String parentDocumentId, final String[] projection, final String sortOrder) throws FileNotFoundException {
		Log.i(TAG, "Query child documents" + parentDocumentId);
		// TODO Auto-generated method stub
		return new MatrixCursor(DEFAULT_DOCUMENT_PROJECTION);
	}

	@Override
	public Cursor queryDocument(final String documentId, final String[] projection) throws FileNotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Cursor queryRoots(final String[] projection) throws FileNotFoundException {
		final StringBuilder projDescription = new StringBuilder();
		if (projection != null) {
			for (final String string : projection) {
				if (projDescription.length() != 0) {
					projDescription.append(", ");
				}
				projDescription.append(string);
			}
		} else {
			projDescription.append("no projection");
		}
		Log.i(TAG, "Query Roots: " + projDescription.toString());
		final MatrixCursor ret = new MatrixCursor(DEFAULT_ROOT_PROJECTION);
		final RowBuilder row = ret.newRow();
		row.add(Root.COLUMN_ROOT_ID, "root ");
		row.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_SEARCH);
		row.add(Root.COLUMN_TITLE, "RAoA Test Root");
		row.add(Root.COLUMN_DOCUMENT_ID, "Test-Entry-ID");
		row.add(Root.COLUMN_MIME_TYPES, "image/jpeg");
		row.add(Root.COLUMN_ICON, R.drawable.ic_launcher);
		return ret;
	}

	@Override
	public void shutdown() {
		mNsdManager.stopServiceDiscovery(mDiscoveryListener);
		super.shutdown();
		Log.i(TAG, "Stopped");
	}

}
