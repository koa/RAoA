package ch.bergturbenthal.image.client.albumlist;

import java.util.Collection;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import ch.bergturbenthal.image.client.R;
import ch.bergturbenthal.image.data.model.AlbumEntry;

public class AlbumListAdapter extends ArrayAdapter<AlbumEntry> {

	private final String clientId;
	private final LayoutInflater inflater;

	public AlbumListAdapter(final Context context, final String clientId, final List<AlbumEntry> albums) {
		super(context, R.layout.album_list_item, albums);
		this.clientId = clientId;
		inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	@Override
	public View getView(final int position, final View convertView, final ViewGroup parent) {
		final AlbumEntry albumEntry = getItem(position);
		final View view;
		if (convertView == null) {
			view = inflater.inflate(R.layout.album_list_item, parent, false);
		} else {
			view = convertView;
		}
		final CheckBox checkbox = (CheckBox) view.findViewById(R.id.album_name);
		checkbox.setText(albumEntry.getName());
		checkbox.setTag(albumEntry.getId());
		final Collection<String> clients = albumEntry.getClients();
		checkbox.setChecked(clients != null && clients.contains(clientId));
		return view;
	}
}
