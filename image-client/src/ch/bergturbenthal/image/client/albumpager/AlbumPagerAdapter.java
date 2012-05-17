package ch.bergturbenthal.image.client.albumpager;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class AlbumPagerAdapter extends FragmentStatePagerAdapter {

  private final List<String> clientNames = new ArrayList<String>();

  public AlbumPagerAdapter(final FragmentManager fm) {
    super(fm);
  }

  @Override
  public int getCount() {
    return clientNames.size();
  }

  @Override
  public Fragment getItem(final int position) {
    final Fragment fragment = new AlbumListFragment();
    final Bundle args = new Bundle();
    args.putString(AlbumListFragment.CLIENT_TITLE, clientNames.get(position));
    fragment.setArguments(args);
    return fragment;
  }

  public void setClientList(final List<String> clientNames) {
    this.clientNames.clear();
    this.clientNames.addAll(clientNames);
    notifyDataSetChanged();
  }

}
