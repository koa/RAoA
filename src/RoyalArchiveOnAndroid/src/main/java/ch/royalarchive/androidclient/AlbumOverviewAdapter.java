package ch.royalarchive.androidclient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public class AlbumOverviewAdapter extends BaseAdapter {
	
	private final List<String> albumList = new ArrayList<String>(Arrays.asList(
			"Album1",
			"Album2",
			"Album3"));
	private final LayoutInflater layoutInflater;
	
	public AlbumOverviewAdapter(Context context) {
		layoutInflater = LayoutInflater.from(context);
	}

	@Override
	public int getCount() {
		return albumList.size();
	}

	@Override
	public Object getItem(int position) {
		return albumList.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		// TODO Auto-generated method stub
		
		return null;
	}

}
