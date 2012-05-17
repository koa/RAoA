package ch.bergturbenthal.image.client.albumpager;

import java.util.ArrayList;
import java.util.Collection;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import ch.bergturbenthal.image.client.R;
import ch.bergturbenthal.image.client.SelectServerListView;
import ch.bergturbenthal.image.client.preferences.Preferences;
import ch.bergturbenthal.image.client.resolver.AlbumService;
import ch.bergturbenthal.image.client.resolver.Resolver;
import ch.bergturbenthal.image.client.service.DownloadService;

public class AlbumPagerActivity extends FragmentActivity {
  private ViewPager viewPager;
  private AlbumPagerAdapter pagerAdapter;
  private TabListener tabListener;
  private ActionBar actionBar;
  private AlbumService albumService;

  private ArrayList<String> clientNames;

  public void onAlbumClicked(final View v) {
    final CheckBox checkbox = (CheckBox) v;
    final String albumId = (String) checkbox.getTag();
    if (albumId == null)
      return;
    if (clientNames == null)
      return;
    final String clientId = clientNames.get(viewPager.getCurrentItem());
    if (clientId == null)
      return;
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(final Void... params) {
        if (checkbox.isChecked())
          albumService.registerClient(albumId, clientId);
        else
          albumService.unRegisterClient(albumId, clientId);
        return null;
      }
    }.execute();
  }

  @Override
  public void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.album_switcher);

    pagerAdapter = new AlbumPagerAdapter(getSupportFragmentManager());
    viewPager = (ViewPager) findViewById(R.id.pager);
    viewPager.setAdapter(pagerAdapter);

    actionBar = getActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

    tabListener = new ActionBar.TabListener() {

      @Override
      public void onTabReselected(final Tab tab, final FragmentTransaction ft) {
      }

      @Override
      public void onTabSelected(final Tab tab, final FragmentTransaction ft) {
        final int scrollX = viewPager.getScrollX();
        viewPager.setCurrentItem(tab.getPosition());
        viewPager.setScrollX(scrollX);
      }

      @Override
      public void onTabUnselected(final Tab tab, final FragmentTransaction ft) {
      }
    };
    viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {

      @Override
      public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels) {
      }

      @Override
      public void onPageScrollStateChanged(final int state) {
      }

      @Override
      public void onPageSelected(final int position) {
        getActionBar().setSelectedNavigationItem(position);
      }
    });
  }

  @Override
  public boolean onCreateOptionsMenu(final Menu menu) {
    getMenuInflater().inflate(R.menu.menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(final MenuItem item) {
    switch (item.getItemId()) {
    case R.id.selectServerItem:
      showSelectServer();
      return true;
    case R.id.preferences:
      startActivity(new Intent(getBaseContext(), Preferences.class));
      return true;
    case R.id.start_download:
      startService(new Intent(this, DownloadService.class));
      return true;
    default:
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    final ProgressDialog progressDialog = ProgressDialog.show(this, "", getResources().getString(R.string.wait_for_server_message), true);
    final Resolver resolver = new Resolver(this);
    resolver.establishLastConnection(new Resolver.ConnectionUrlListener() {

      @Override
      public void notifyConnectionEstabilshed(final String foundUrl, final String serverName) {
        albumService = new AlbumService(foundUrl);
        final Collection<String> collectedClients = albumService.listKnownClientNames();

        runOnUiThread(new Runnable() {

          @Override
          public void run() {
            clientNames = new ArrayList<String>();
            final String clientName = PreferenceManager.getDefaultSharedPreferences(AlbumPagerActivity.this).getString("client_name", null);
            if (clientName != null) {
              collectedClients.remove(clientName);
              clientNames.add(clientName);
            }
            clientNames.addAll(collectedClients);

            progressDialog.hide();
            actionBar.removeAllTabs();
            for (final String client : clientNames) {
              actionBar.addTab(actionBar.newTab().setText(client).setTabListener(tabListener));
            }
            pagerAdapter.setClientList(clientNames);
            // final AlbumListAdapter albumList = new
            // AlbumListAdapter(AlbumListView.this, clientId, albums);
            // setListAdapter(albumList);
          }
        });
      }

      @Override
      public void notifyConnectionNotEstablished() {
        runOnUiThread(new Runnable() {

          @Override
          public void run() {
            progressDialog.hide();
          }
        });
        showSelectServer();
      }

    });

  }

  private void showSelectServer() {
    startActivity(new Intent(AlbumPagerActivity.this, SelectServerListView.class));
  }
}
