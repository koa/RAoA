package ch.bergturbenthal.image.client.albumpager;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import ch.bergturbenthal.image.client.R;

public class AlbumPagerActivity extends FragmentActivity {
  private ViewPager viewPager;
  private AlbumPagerAdapter pagerAdapter;

  @Override
  public void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.album_switcher);
    pagerAdapter = new AlbumPagerAdapter(getSupportFragmentManager());
    viewPager = (ViewPager) findViewById(R.id.pager);
    viewPager.setAdapter(pagerAdapter);

    final ActionBar actionBar = getActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

    final TabListener tabListener = new ActionBar.TabListener() {

      @Override
      public void onTabReselected(final Tab tab, final FragmentTransaction ft) {
      }

      @Override
      public void onTabSelected(final Tab tab, final FragmentTransaction ft) {
        viewPager.setCurrentItem(tab.getPosition());
      }

      @Override
      public void onTabUnselected(final Tab tab, final FragmentTransaction ft) {
      }
    };
    for (int i = 0; i < 3; i++) {
      actionBar.addTab(actionBar.newTab().setText("Tab " + (i + 1)).setTabListener(tabListener));
    }
    viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {

      @Override
      public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels) {
        // TODO Auto-generated method stub

      }

      @Override
      public void onPageScrollStateChanged(final int state) {
        // TODO Auto-generated method stub

      }

      @Override
      public void onPageSelected(final int position) {
        getActionBar().setSelectedNavigationItem(position);
      }
    });
  }
}
