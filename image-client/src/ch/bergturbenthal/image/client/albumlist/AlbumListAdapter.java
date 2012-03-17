package ch.bergturbenthal.image.client.albumlist;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import ch.bergturbenthal.image.client.R;
import ch.bergturbenthal.image.client.R.id;
import ch.bergturbenthal.image.client.R.layout;
import ch.bergturbenthal.image.client.R.string;
import ch.bergturbenthal.image.data.model.AlbumEntry;

public class AlbumListAdapter extends ArrayAdapter<AlbumEntry> {

  private final LayoutInflater inflater;
  private final java.text.DateFormat dateFormat;

  public AlbumListAdapter(final Context context) {
    super(context, R.layout.album_list_item);
    inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    dateFormat = DateFormat.getDateFormat(context);
  }

  @Override
  public View getView(final int position, final View convertView, final ViewGroup parent) {
    final AlbumEntry albumEntry = getItem(position);
    final View view;
    if (convertView == null) {
      view = inflater.inflate(R.layout.album_list_item, parent, false);
    } else
      view = convertView;
    ((TextView) view.findViewById(R.id.album_name)).setText(albumEntry.getName());
    final TextView albumDateView = (TextView) view.findViewById(R.id.date);
    if (albumDateView != null)
      if (albumEntry.getFirstPhotoDate() == null || albumEntry.getLastPhotoDate() == null)
        albumDateView.setVisibility(View.INVISIBLE);
      else {
        albumDateView.setVisibility(View.VISIBLE);
        final String fromDate = dateFormat.format(albumEntry.getFirstPhotoDate());
        final String toDate = dateFormat.format(albumEntry.getLastPhotoDate());
        final Resources resources = getContext().getResources();
        if (fromDate.equals(toDate)) {
          albumDateView.setText(resources.getString(R.string.timeSpanOneDate, fromDate));
        } else
          albumDateView.setText(resources.getString(R.string.timeSpanTwoDates, fromDate, toDate));
      }
    return view;
  }
}
