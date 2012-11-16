package ch.royalarchive.androidclient;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.Toast;

public class AlbumOverviewActivity extends Activity {
	private static String TAG = AlbumOverviewActivity.class.getSimpleName();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
	    setContentView(R.layout.album_overview);
	    
	    GridView gridview = (GridView) findViewById(R.id.album_overview);
	    gridview.setAdapter(new AlbumOverviewAdapter(this));

	    /*gridview.setOnItemClickListener(new OnItemClickListener() {
	        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
	            Toast.makeText(AlbumOverviewActivity.this, "" + position, Toast.LENGTH_SHORT).show();
	        }
	    });*/
		
	}

}
