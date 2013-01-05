package ch.bergturbenthal.image.provider.state;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.app.ListFragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import ch.bergturbenthal.image.data.model.state.ProgressType;
import ch.bergturbenthal.image.provider.Client;
import ch.bergturbenthal.image.provider.R;

public class ServerStateFragment extends ListFragment {

  private CursorAdapter adapter;

  private static final Map<String, Integer> iconMap = new HashMap<String, Integer>();
  static {
    iconMap.put(ProgressType.REFRESH_ALBUM.name(), android.R.drawable.ic_popup_sync);
  }

  @Override
  public void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final Activity activity = getActivity();
    // adapter =
    // new SimpleCursorAdapter(activity,
    // R.layout.activity_server_list_progress_item, null,
    // new String[] { Client.ProgressEntry.PROGRESS_TYPE,
    // Client.ProgressEntry.PROGRESS_DESCRIPTION },
    // new int[] { R.id.progress_item_title, R.id.progress_item_description },
    // 0);
    adapter = new ResourceCursorAdapter(getActivity(), R.layout.activity_server_list_progress_item, null, true) {

      @Override
      public void bindView(final View view, final Context context, final Cursor cursor) {
        ((TextView) view.findViewById(R.id.progress_item_title)).setText(cursor.getString(cursor.getColumnIndex(Client.ProgressEntry.PROGRESS_DESCRIPTION)));
        ((TextView) view.findViewById(R.id.progress_item_description)).setText(cursor.getString(cursor.getColumnIndex(Client.ProgressEntry.CURRENT_STATE_DESCRIPTION)));
        final ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.progress_item_progress);
        progressBar.setProgress(cursor.getInt(cursor.getColumnIndex(Client.ProgressEntry.CURRENT_STEP_NR)));
        progressBar.setMax(cursor.getInt(cursor.getColumnIndex(Client.ProgressEntry.STEP_COUNT)));
        final Integer alternateIcon = iconMap.get(cursor.getString(cursor.getColumnIndex(Client.ProgressEntry.PROGRESS_TYPE)));
        if (alternateIcon != null)
          ((ImageView) view.findViewById(R.id.progress_item_icon)).setImageResource(alternateIcon.intValue());
      }

    };
    setListAdapter(adapter);
    final String serverId = getArguments() == null ? null : getArguments().getString(Client.ServerEntry.SERVER_ID);
    if (serverId != null)
      getLoaderManager().initLoader(0, null, new LoaderCallbacks<Cursor>() {

        @Override
        public Loader<Cursor> onCreateLoader(final int arg0, final Bundle arg1) {
          return new CursorLoader(activity, Client.makeServerProgressUri(serverId), null, null, null, null);
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

  @Override
  public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
    // TODO Auto-generated method stub
    return super.onCreateView(inflater, container, savedInstanceState);
  }

}
