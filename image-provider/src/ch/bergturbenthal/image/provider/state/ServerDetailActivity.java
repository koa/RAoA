package ch.bergturbenthal.image.provider.state;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import ch.bergturbenthal.image.provider.R;

/**
 * An activity representing a single Server detail screen. This activity is only
 * used on handset devices. On tablet-size devices, item details are presented
 * side-by-side with a list of items in a {@link ServerListActivity}.
 * <p>
 * This activity is mostly just a 'shell' activity containing nothing more than
 * a {@link ServerDetailFragment}.
 */
public class ServerDetailActivity extends Activity {

  private static class TabListener<T extends Fragment> implements ActionBar.TabListener {
    private Fragment fragment;
    private final Context context;
    private final String tag;
    private final Class<T> clz;

    public TabListener(final Context context, final String tag, final Class<T> clz) {
      this.context = context;
      this.tag = tag;
      this.clz = clz;
    }

    @Override
    public void onTabReselected(final Tab tab, final FragmentTransaction ft) {
      // User selected the already selected tab. Usually do nothing.
    }

    @Override
    public void onTabSelected(final Tab tab, final FragmentTransaction ft) {
      if (fragment == null) {
        fragment = Fragment.instantiate(context, clz.getName());
        ft.add(android.R.id.content, fragment, tag);
      } else
        ft.attach(fragment);
    }

    @Override
    public void onTabUnselected(final Tab tab, final FragmentTransaction ft) {
      if (fragment != null)
        ft.detach(fragment);
    }

  }

  @Override
  public boolean onOptionsItemSelected(final MenuItem item) {
    switch (item.getItemId()) {
    case android.R.id.home:
      // This ID represents the Home or Up button. In the case of this
      // activity, the Up button is shown. Use NavUtils to allow users
      // to navigate up one level in the application structure. For
      // more details, see the Navigation pattern on Android Design:
      //
      // http://developer.android.com/design/patterns/navigation.html#up-vs-back
      //
      final Intent upIntent = new Intent(this, ServerListActivity.class);
      upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startActivity(upIntent);
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final ActionBar actionBar = getActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
    actionBar.setDisplayShowTitleEnabled(false);
    final Class<Fragment> clz;
    actionBar.newTab().setText(R.string.server_progress)
             .setTabListener(new TabListener<ServerDetailFragment>(getApplication(), "progress", ServerDetailFragment.class));

    setContentView(R.layout.activity_server_detail);

    // Show the Up button in the action bar.
    getActionBar().setDisplayHomeAsUpEnabled(true);

    // savedInstanceState is non-null when there is fragment state
    // saved from previous configurations of this activity
    // (e.g. when rotating the screen from portrait to landscape).
    // In this case, the fragment will automatically be re-added
    // to its container so we don't need to manually add it.
    // For more information, see the Fragments API guide at:
    //
    // http://developer.android.com/guide/components/fragments.html
    //
    if (savedInstanceState == null) {
      // Create the detail fragment and add it to the activity
      // using a fragment transaction.
      final Bundle arguments = new Bundle();
      arguments.putString(ServerDetailFragment.ARG_ITEM_ID, getIntent().getStringExtra(ServerDetailFragment.ARG_ITEM_ID));
      final ServerDetailFragment fragment = new ServerDetailFragment();
      fragment.setArguments(arguments);
      getFragmentManager().beginTransaction().add(R.id.server_detail_container, fragment).commit();
    }
  }
}
