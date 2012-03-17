package ch.bergturbenthal.image.client;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import ch.bergturbenthal.image.data.model.AlbumEntry;

public class AlbumListAdapter extends ArrayAdapter<AlbumEntry> {

  private final Activity activity;
  private final LayoutInflater inflater;

  public AlbumListAdapter(final Activity activity) {
    super(activity, R.layout.album_list_item);
    this.activity = activity;
    inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
  }

  @Override
  public View getView(final int position, final View convertView, final ViewGroup parent) {
    final AlbumEntry albumEntry = getItem(position);
    final View view;
    if (convertView == null) {
      view = inflater.inflate(R.layout.album_list_item, parent, false);
    } else
      view = convertView;
    final java.text.DateFormat dateFormat = DateFormat.getDateFormat(getContext());
    ((TextView) view.findViewById(R.id.album_name)).setText(albumEntry.getName());
    final TextView albumDate = (TextView) view.findViewById(R.id.date);
    if (albumEntry.getFirstPhotoDate() == null || albumEntry.getLastPhotoDate() == null)
      albumDate.setVisibility(View.INVISIBLE);
    else {
      albumDate.setVisibility(View.VISIBLE);
      final String fromDate = dateFormat.format(albumEntry.getFirstPhotoDate());
      final String toDate = dateFormat.format(albumEntry.getLastPhotoDate());
      final Resources resources = getContext().getResources();
      if (fromDate.equals(toDate)) {
        albumDate.setText(resources.getString(R.string.timeSpanOneDate, fromDate));
      } else
        albumDate.setText(resources.getString(R.string.timeSpanTwoDates, fromDate, toDate));
    }
    return view;
  }
}
