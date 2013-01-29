package ch.bergturbenthal.image.provider.state;

import android.app.Activity;
import android.app.ListFragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.CursorAdapter;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import ch.bergturbenthal.image.provider.Client;
import ch.bergturbenthal.image.provider.R;

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
        ((TextView) view.findViewById(R.id.issue_item_album_entry_name)).setText(readStringColumn(cursor, Client.IssueEntry.ALBUM_ENTRY_NAME));
      }

      private String readStringColumn(final Cursor cursor, final String column) {
        return cursor.getString(cursor.getColumnIndex(column));
      }

    };
    setListAdapter(adapter);
    final String serverId = getArguments() == null ? null : getArguments().getString(Client.ServerEntry.SERVER_ID);
    if (serverId != null)
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
          if (isResumed())
            setListShown(true);
          else
            setListShownNoAnimation(true);
        }
      });

  }

}
