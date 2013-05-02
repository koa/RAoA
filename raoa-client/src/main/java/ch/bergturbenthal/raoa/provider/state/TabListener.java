package ch.bergturbenthal.raoa.provider.state;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import ch.bergturbenthal.raoa.R;

public class TabListener<T extends Fragment> implements ActionBar.TabListener {

	private final Bundle arguments;

	private final Class<T> clz;
	private final Activity context;
	private Fragment fragment;

	private final String tag;

	public static void initTabs(final Activity activity, final Bundle arguments) {
		final ActionBar actionBar = activity.getActionBar();
		final Tab oldSelectedTab = actionBar.getSelectedTab();
		final int lastIndex = oldSelectedTab == null ? -1 : oldSelectedTab.getPosition();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.removeAllTabs();
		actionBar.addTab(actionBar.newTab()
															.setText(R.string.server_progress)
															.setTabListener(new TabListener<ServerStateFragment>(activity, "progress", ServerStateFragment.class, arguments)));
		actionBar.addTab(actionBar.newTab()
															.setText(R.string.server_error)
															.setTabListener(new TabListener<ServerIssueFragment>(activity, "issues", ServerIssueFragment.class, arguments)));
		actionBar.addTab(actionBar.newTab()
															.setText(R.string.server_create_folder)
															.setTabListener(new TabListener<ServerCreateAlbumFragment>(activity, "create_folder", ServerCreateAlbumFragment.class, arguments)));
		if (lastIndex >= 0) {
			actionBar.setSelectedNavigationItem(lastIndex);
		}

	}

	public TabListener(final Activity context, final String tag, final Class<T> clz, final Bundle arguments) {
		this.context = context;
		this.tag = tag;
		this.clz = clz;
		this.arguments = arguments;
	}

	@Override
	public void onTabReselected(final Tab tab, final FragmentTransaction ft) {
		// User selected the already selected tab. Usually do nothing.
	}

	@Override
	public void onTabSelected(final Tab tab, final FragmentTransaction ft) {
		if (fragment == null) {
			fragment = Fragment.instantiate(context, clz.getName());
			ft.add(R.id.server_detail_fragment, fragment, tag);
			fragment.setArguments(arguments);
		} else {
			ft.attach(fragment);
		}
	}

	@Override
	public void onTabUnselected(final Tab tab, final FragmentTransaction ft) {
		if (fragment != null) {
			ft.detach(fragment);
		}
	}

}