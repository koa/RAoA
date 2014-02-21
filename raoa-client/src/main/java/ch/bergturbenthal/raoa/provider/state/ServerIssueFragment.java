package ch.bergturbenthal.raoa.provider.state;

import android.app.Activity;
import android.app.ListFragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.provider.Client;

public class ServerIssueFragment extends ListFragment {
	private CursorAdapter adapter;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Activity activity = getActivity();
		adapter = new ResourceCursorAdapter(activity, R.layout.activity_server_list_issues_item, null, true) {

			@Override
			public void bindView(final View view, final Context context, final Cursor cursor) {
				((TextView) view.findViewById(R.id.issue_item_album_name)).setText(readStringColumn(cursor, Client.IssueEntry.ALBUM_NAME));
				((TextView) view.findViewById(R.id.issue_item_album_entry_name)).setText(readStringColumn(cursor, Client.IssueEntry.ALBUM_DETAIL_NAME));
			}

			private String readStringColumn(final Cursor cursor, final String column) {
				return cursor.getString(cursor.getColumnIndex(column));
			}

		};
		setListAdapter(adapter);
		final String serverId = getArguments() == null ? null : getArguments().getString(Client.ServerEntry.SERVER_ID);
		if (serverId != null) {
			getLoaderManager().initLoader(0, null, new LoaderCallbacks<Cursor>() {

				@Override
				public Loader<Cursor> onCreateLoader(final int arg0, final Bundle arg1) {
					return new CursorLoader(activity, Client.makeServerIssueUri(serverId), null, null, null, null);
				}

				@Override
				public void onLoaderReset(final Loader<Cursor> arg0) {
					adapter.swapCursor(null);
				}

				@Override
				public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
					adapter.swapCursor(data);
					if (isResumed()) {
						setListShown(true);
					} else {
						setListShownNoAnimation(true);
					}
				}
			});
		}
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id) {
		final Cursor cursor = (Cursor) adapter.getItem(position);
		final Intent intent = new Intent(getActivity(), ShowIssueActivity.class);
		for (final String column : new String[] { Client.IssueEntry.ALBUM_NAME, Client.IssueEntry.ALBUM_DETAIL_NAME, Client.IssueEntry.ISSUE_TYPE, Client.IssueEntry.DETAILS }) {
			final int index = cursor.getColumnIndexOrThrow(column);
			final String value = cursor.getString(index);
			intent.putExtra(column, value);
		}
		final int timeColumnIndex = cursor.getColumnIndexOrThrow(Client.IssueEntry.ISSUE_TIME);
		intent.putExtra(Client.IssueEntry.ISSUE_TIME, cursor.getLong(timeColumnIndex));
		startActivity(intent);
	}

}
