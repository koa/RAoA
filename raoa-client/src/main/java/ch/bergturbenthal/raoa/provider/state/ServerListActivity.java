package ch.bergturbenthal.raoa.provider.state;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.provider.Client;

/**
 * An activity representing a list of Servers. This activity has different presentations for handset and tablet-size devices. On handsets, the
 * activity presents a list of items, which when touched, lead to a {@link ServerDetailActivity} representing item details. On tablets, the activity
 * presents the list of items and item details side-by-side using two vertical panes.
 * <p>
 * The activity makes heavy use of fragments. The list of items is a {@link ServerListFragment} and the item details (if present) is a
 * {@link ServerDetailFragment}.
 * <p>
 * This activity also implements the required {@link ServerListFragment.Callbacks} interface to listen for item selections.
 */
public class ServerListActivity extends Activity implements ServerListFragment.Callbacks {

	/**
	 * Whether or not the activity is in two-pane mode, i.e. running on a tablet device.
	 */
	private boolean mTwoPane;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_server_list);

		if (findViewById(R.id.server_detail_fragment) != null) {
			// The detail container view will be present only in the
			// large-screen layouts (res/values-large and
			// res/values-sw600dp). If this view is present, then the
			// activity should be in two-pane mode.
			mTwoPane = true;

			// In two-pane mode, list items should be given the
			// 'activated' state when touched.
			((ServerListFragment) getFragmentManager().findFragmentById(R.id.server_list)).setActivateOnItemClick(true);
		}

		// TODO: If exposing deep links into your app, handle intents here.
	}

	/**
	 * Callback method from {@link ServerListFragment.Callbacks} indicating that the item with the given ID was selected.
	 */
	@Override
	public void onItemSelected(final String id) {
		if (mTwoPane) {
			// In two-pane mode, show the detail view in this activity by
			// adding or replacing the detail fragment using a
			// fragment transaction.
			final Bundle arguments = new Bundle();
			arguments.putString(Client.ServerEntry.SERVER_ID, id);

			TabListener.initTabs(this, arguments);

		} else {
			// In single-pane mode, simply start the detail activity
			// for the selected item ID.
			final Intent detailIntent = new Intent(this, ServerDetailActivity.class);
			detailIntent.putExtra(Client.ServerEntry.SERVER_ID, id);
			startActivity(detailIntent);
		}
	}
}
