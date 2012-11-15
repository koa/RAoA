package ch.royalarchive.androidclient;

import android.app.ListActivity;
import android.os.Bundle;

public class AlbumOverviewActivity extends ListActivity {
	
	private AlbumOverviewAdapter adapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		adapter = new AlbumOverviewAdapter(this);
		setListAdapter(adapter);
		
	}

}
