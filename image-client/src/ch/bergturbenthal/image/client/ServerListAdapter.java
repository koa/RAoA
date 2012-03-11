package ch.bergturbenthal.image.client;

import java.util.ArrayList;
import java.util.List;

import javax.jmdns.ServiceInfo;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class ServerListAdapter extends BaseAdapter {

  private final Context mContext;

  private final List<ServiceInfo> items = new ArrayList<ServiceInfo>();

  public ServerListAdapter(final Context mContext) {
    this.mContext = mContext;
  }

  public void addInfo(final ServiceInfo info) {
    items.add(info);
    notifyDataSetChanged();
  }

  @Override
  public int getCount() {
    return items.size();
  }

  @Override
  public Object getItem(final int arg0) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public long getItemId(final int arg0) {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getItemViewType(final int position) {
    return super.getItemViewType(position);
  }

  @Override
  public View getView(final int position, final View convertView, final ViewGroup parent) {

    TextView imageView;
    if (convertView == null) { // if it's not recycled, initialize some
                               // attributes
      imageView = new TextView(mContext);
    } else {
      imageView = (TextView) convertView;
    }
    final ServiceInfo info = items.get(position);
    imageView.setTextSize(30);
    imageView.setText(info.getName());
    return imageView;
  }

  public void removeInfo(final ServiceInfo info) {
    items.remove(info);
    notifyDataSetChanged();
  }

}
