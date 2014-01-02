package ch.bergturbenthal.raoa.client.album;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.client.binding.AbstractViewHandler;
import ch.bergturbenthal.raoa.client.binding.ComplexCursorAdapter;
import ch.bergturbenthal.raoa.client.binding.PhotoViewHandler;
import ch.bergturbenthal.raoa.client.binding.TextViewHandler;
import ch.bergturbenthal.raoa.client.binding.ViewHandler;
import ch.bergturbenthal.raoa.client.photo.PhotoOverviewActivity;
import ch.bergturbenthal.raoa.provider.Client;

public class AlbumOverviewActivity extends Activity implements LoaderCallbacks<Cursor> {

	private static String TAG = AlbumOverviewActivity.class.getSimpleName();
	private ComplexCursorAdapter cursorAdapter;
	private ExecutorService threadPoolExecutor;

	private Collection<ViewHandler<? extends View>> makeServerViewHandlers() {
		final ArrayList<ViewHandler<? extends View>> ret = new ArrayList<ViewHandler<? extends View>>();
		ret.add(new TextViewHandler(android.R.id.text1, Client.ServerEntry.SERVER_NAME));
		ret.add(new TextViewHandler(android.R.id.text2, Client.ServerEntry.ARCHIVE_NAME));
		return ret;
	}

	private List<ViewHandler<? extends View>> makeViewHandlers() {
		final ArrayList<ViewHandler<? extends View>> ret = new ArrayList<ViewHandler<? extends View>>();
		final PhotoViewHandler photoViewHandler = new PhotoViewHandler(	this,
																																		R.id.album_item_image,
																																		Client.Album.THUMBNAIL,
																																		new PhotoViewHandler.DimensionCalculator(R.dimen.image_width),
																																		threadPoolExecutor,
																																		"album-overview");
		photoViewHandler.setIdleView(R.id.album_item_empty_layout);
		ret.add(photoViewHandler);
		ret.add(new TextViewHandler(R.id.album_item_name, Client.Album.TITLE));
		// ret.add(new TextViewHandler(R.id.album_item_size, Client.Album.ENTRY_COUNT));
		ret.add(new AbstractViewHandler<TextView>(R.id.album_item_size) {

			@Override
			public void bindView(final TextView view, final Context context, final Map<String, Object> values) {
				final Object entryCount = values.get(Client.Album.ENTRY_COUNT);
				double size = ((Number) values.get(Client.Album.THUMBNAILS_SIZE)).doubleValue();
				final String[] units = new String[] { "B", "K", "M", "G", "T" };
				int unitType = 0;
				for (; unitType < units.length && size > 500; unitType++) {
					size = size / 1024;
				}
				final NumberFormat sizeFormat;
				if (size < 10) {
					sizeFormat = new DecimalFormat("0.0");
				} else {
					sizeFormat = NumberFormat.getIntegerInstance();
				}
				final String value = entryCount + " " + sizeFormat.format(size) + units[unitType];
				view.setText(value);
			}

			@Override
			public String[] usedFields() {
				return new String[] { Client.Album.ENTRY_COUNT, Client.Album.THUMBNAILS_SIZE };
			}
		});
		ret.add(new AbstractViewHandler<ImageView>(R.id.album_item_icon_offline) {

			@Override
			public void bindView(final ImageView view, final Context context, final Map<String, Object> values) {

				final boolean shouldSync = ((Number) values.get(Client.Album.SHOULD_SYNC)).intValue() != 0;
				final boolean synced = ((Number) values.get(Client.Album.SYNCED)).intValue() != 0;
				if (shouldSync) {
					view.setImageResource(R.drawable.ic_icon_offline_online);
					if (synced) {
						view.clearAnimation();
					} else {
						final Animation animation = AnimationUtils.loadAnimation(context, R.anim.rotate_infinitely);
						view.startAnimation(animation);
					}
				} else {
					view.clearAnimation();
					view.setImageResource(R.drawable.ic_icon_offline_offline);
				}
				final String entryUri = (String) values.get(Client.Album.ENTRY_URI);
				view.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(final View v) {
						final ContentValues values = new ContentValues();
						final boolean enableSync = !shouldSync;
						values.put(Client.Album.SHOULD_SYNC, Boolean.valueOf(enableSync));
						new AsyncTask<Void, Void, Void>() {
							@Override
							protected Void doInBackground(final Void... params) {
								context.getContentResolver().update(Uri.parse(entryUri), values, null, null);
								return null;
							}

						}.execute();
						final Animation animation = AnimationUtils.loadAnimation(context, R.anim.rotate_infinitely_slow);
						view.startAnimation(animation);
						if (enableSync) {
							view.setImageResource(R.drawable.ic_icon_offline_online);
						} else {
							view.setImageResource(R.drawable.ic_icon_offline_offline);
						}
					}
				});
			}

			@Override
			public String[] usedFields() {
				return new String[] { Client.Album.SHOULD_SYNC, Client.Album.SYNCED, Client.Album.ENTRY_URI };
			}
		});
		return ret;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		threadPoolExecutor = new ThreadPoolExecutor(5, 15, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(1000), new ThreadFactory() {
			final AtomicInteger nextThreadIndex = new AtomicInteger(0);

			@Override
			public Thread newThread(final Runnable r) {
				final Thread thread = new Thread(r, "album-overview-background-photo-loader-" + nextThreadIndex.getAndIncrement());
				thread.setPriority(3);
				return thread;
			}
		});

		setContentView(R.layout.album_overview);

		cursorAdapter = ComplexCursorAdapter.registerLoaderManager(	getLoaderManager(),
																																this,
																																Client.ALBUM_URI,
																																R.layout.album_overview_item,
																																makeViewHandlers(),
																																new String[] { Client.Album.ENTRY_URI, Client.Album.ALBUM_ENTRIES_URI });
		final GridView gridview = (GridView) findViewById(R.id.album_overview);
		gridview.setEmptyView(findViewById(R.id.no_albums_visible));
		gridview.setAdapter(cursorAdapter);

		@SuppressWarnings("unchecked")
		final AdapterView<ListAdapter> serverListView = (AdapterView<ListAdapter>) findViewById(R.id.server_list);
		final ComplexCursorAdapter serverCursorAdapter = ComplexCursorAdapter.registerLoaderManager(getLoaderManager(),
																																																1,
																																																this,
																																																Client.SERVER_URI,
																																																null,
																																																null,
																																																null,
																																																android.R.layout.two_line_list_item,
																																																makeServerViewHandlers(),
																																																null);
		serverListView.setAdapter(serverCursorAdapter);
		serverListView.setEmptyView(findViewById(R.id.no_servers_visible));
		// Handle clicks on album image
		gridview.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View v, final int position, final long id) {
				final Intent intent = new Intent(AlbumOverviewActivity.this, PhotoOverviewActivity.class);
				final Object[] additionalValues = cursorAdapter.getAdditionalValues(position);
				intent.putExtra("album_entries_uri", (String) (additionalValues[1]));
				intent.putExtra("album_uri", (String) (additionalValues[0]));
				startActivity(intent);
			}
		});
	}

	@Override
	public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
		return new CursorLoader(this, Client.ALBUM_URI, null, null, null, null);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		final MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.album_overview_menu, menu);
		return true;
	}

	@Override
	protected void onDestroy() {
		if (threadPoolExecutor != null) {
			threadPoolExecutor.shutdownNow();
		}
		super.onDestroy();
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader) {
		cursorAdapter.swapCursor(null);
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
		cursorAdapter.swapCursor(data);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.createAlbumMenuItem:
			startActivity(new Intent(this, CreateAlbumActivity.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}
