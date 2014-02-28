package ch.bergturbenthal.raoa.provider.state;

import java.util.Collection;
import java.util.Date;

import android.app.Activity;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.TextView;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.data.model.state.IssueResolveAction;
import ch.bergturbenthal.raoa.provider.Client;

public class ShowIssueActivity extends Activity {

	private String formatName(final IssueResolveAction action) {
		final Resources resources = getResources();
		switch (action) {
		case ACKNOWLEDGE:
			return resources.getString(R.string.resolve_action_acknowledge);
		case IGNORE_OTHER:
			return resources.getString(R.string.resolve_action_ignore_other);
		case IGNORE_THIS:
			return resources.getString(R.string.resolve_action_ignore_this);
		default:
			return action.name();
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_issue_details);
		final Bundle bundle = getIntent().getExtras();
		showOnView(bundle, Client.IssueEntry.ALBUM_NAME, R.id.issue_album_name_value);
		showOnView(bundle, Client.IssueEntry.ALBUM_DETAIL_NAME, R.id.issue_detail_name_value);
		showOnView(bundle, Client.IssueEntry.DETAILS, R.id.issue_details_value);
		showOnView(bundle, Client.IssueEntry.ISSUE_TYPE, R.id.issue_type_value);
		final Date issueDate = new Date(bundle.getLong(Client.IssueEntry.ISSUE_TIME));
		final String issueTimeFormatted = DateFormat.getDateFormat(this).format(issueDate) + " : " + DateFormat.getTimeFormat(this).format(issueDate);
		showOnView(R.id.issue_time_value, issueTimeFormatted);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		final Bundle bundle = getIntent().getExtras();
		final String issueActionId = bundle.getString(Client.IssueEntry.ISSUE_ACTION_ID);
		final String serverId = bundle.getString(Client.ServerEntry.SERVER_ID);
		final Collection<IssueResolveAction> actions = Client.IssueEntry.decodeActions(bundle.getString(Client.IssueEntry.AVAILABLE_ACTIONS));
		for (final IssueResolveAction action : actions) {
			final MenuItem menuItem = menu.add(formatName(action));
			menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
			menuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {

				@Override
				public boolean onMenuItemClick(final MenuItem item) {
					new AsyncTask<Void, Void, Void>() {

						@Override
						protected Void doInBackground(final Void... params) {
							new Client(getContentResolver()).resolveIssue(serverId, issueActionId, action);
							return null;
						}

						@Override
						protected void onPostExecute(final Void result) {
							// Toast.makeText(ShowIssueActivity.this, R.string.create_folder_folder_created, Toast.LENGTH_LONG).show();
						}

					}.execute();

					// TODO Auto-generated method stub
					return false;
				}
			});
		}
		return super.onCreateOptionsMenu(menu);
	}

	private void showOnView(final Bundle bundle, final String key, final int viewId) {
		final String value = bundle.getString(key);
		showOnView(viewId, value);
	}

	private void showOnView(final int viewId, final String value) {
		final TextView view = (TextView) findViewById(viewId);
		if (view != null) {
			if (value == null) {
				view.setVisibility(View.GONE);
				return;
			}
			view.setVisibility(View.VISIBLE);
			view.setText(value);
		}
	}

}
