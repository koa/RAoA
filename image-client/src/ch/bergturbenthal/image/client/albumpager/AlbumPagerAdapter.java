package ch.bergturbenthal.image.client.albumpager;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class AlbumPagerAdapter extends FragmentStatePagerAdapter {

  public AlbumPagerAdapter(final FragmentManager fm) {
    super(fm);
  }

  @Override
  public int getCount() {
    return 3;
  }

  @Override
  public Fragment getItem(final int position) {
    final Fragment fragment = new AlbumListFragment();
    final Bundle args = new Bundle();
    // Our object is just an integer :-P
    args.putInt(AlbumListFragment.ARG_OBJECT, position + 1);
    fragment.setArguments(args);
    return fragment;
  }

}
