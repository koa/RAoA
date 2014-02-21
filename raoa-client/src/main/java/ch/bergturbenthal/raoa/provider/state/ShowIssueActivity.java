package ch.bergturbenthal.raoa.provider.state;

import java.util.Date;

import android.app.Activity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextView;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.provider.Client;

public class ShowIssueActivity extends Activity {

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
