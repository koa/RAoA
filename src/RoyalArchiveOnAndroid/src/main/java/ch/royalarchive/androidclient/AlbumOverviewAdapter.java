package ch.royalarchive.androidclient;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class AlbumOverviewAdapter extends BaseAdapter {

	private Context context;

	// references to our images
	private Integer[] thumbnailIds = { R.drawable.bild1, R.drawable.bild2,
			R.drawable.bild3, R.drawable.bild4, R.drawable.bild5,
			R.drawable.bild1, R.drawable.bild2, R.drawable.bild3,
			R.drawable.bild4, R.drawable.bild5, R.drawable.bild1,
			R.drawable.bild2, R.drawable.bild3, R.drawable.bild4,
			R.drawable.bild5, R.drawable.bild1, R.drawable.bild2,
			R.drawable.bild3, R.drawable.bild4, R.drawable.bild5,
			R.drawable.bild1, R.drawable.bild2, R.drawable.bild3,
			R.drawable.bild4, R.drawable.bild5, };

	public AlbumOverviewAdapter(Context c) {
		context = c;
	}

	public int getCount() {
		return thumbnailIds.length;
	}

	public Object getItem(int position) {
		return null;
	}

	public long getItemId(int position) {
		return 0;
	}

	// create a new ImageView for each item referenced by the Adapter
	public View getView(int position, View convertView, ViewGroup parent) {
		View v = convertView;

		if (v == null) {
			LayoutInflater li = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = li.inflate(R.layout.album_overview_item, null);
		}
		
		TextView albumName = (TextView)v.findViewById(R.id.album_item_name);
		albumName.setText("Albumname");
		TextView albumSize = (TextView)v.findViewById(R.id.album_item_size);
		albumSize.setText("125");
		
		ImageView makeOfflineAvailable = (ImageView) v.findViewById(R.id.album_item_icon_offline);
		makeOfflineAvailable.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Toast.makeText(context, "" + "Sorry, diese Funktion ist noch nicht implementiert!", Toast.LENGTH_SHORT).show();
			}
		});

		ImageView imageView = (ImageView) v.findViewById(R.id.album_item_image);
		imageView.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Toast.makeText(context, "" + "Zur Fotoübersicht!", Toast.LENGTH_SHORT).show();
			}
		});
		imageView.setImageResource(thumbnailIds[position]);

		return v;

	}
	
}
