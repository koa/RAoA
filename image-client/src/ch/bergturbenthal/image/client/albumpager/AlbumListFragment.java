package ch.bergturbenthal.image.client.albumpager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import ch.bergturbenthal.image.client.R;
import ch.bergturbenthal.image.client.albumlist.AlbumListAdapter;
import ch.bergturbenthal.image.client.resolver.AlbumService;
import ch.bergturbenthal.image.client.resolver.ConnectedHandler;
import ch.bergturbenthal.image.client.resolver.ConnectionAdapter;
import ch.bergturbenthal.image.client.resolver.Resolver;
import ch.bergturbenthal.raoa.data.model.AlbumEntry;

public class AlbumListFragment extends ListFragment {
	public static final String CLIENT_TITLE = "client_title";

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {

		final View rootView = inflater.inflate(R.layout.fragment_collection_object, container, false);
		final Bundle args = getArguments();
		final TextView textView = (TextView) rootView.findViewById(R.id.fragment_text);
		// final ListView list = getListView();
		final String clientTitle = args.getString(CLIENT_TITLE);
		textView.setText(clientTitle);

		final Resolver resolver = new Resolver(container.getContext());
		try {
			resolver.establishLastConnection(new ConnectionAdapter(getActivity(), new ConnectedHandler() {

				@Override
				public void connected(final AlbumService service, final String serverName) {
					final List<AlbumEntry> albums = new ArrayList<AlbumEntry>(service.listAlbums().getAlbumNames());
					Collections.sort(albums, new Comparator<AlbumEntry>() {
						@Override
						public int compare(final AlbumEntry o1, final AlbumEntry o2) {
							return o1.getName().compareTo(o2.getName());
						}
					});
					container.post(new Runnable() {

						@Override
						public void run() {
							final AlbumListAdapter albumList = new AlbumListAdapter(container.getContext(), clientTitle, albums);
							setListAdapter(albumList);
						}
					});
				}

			}));
		} finally {
			try {
				resolver.close();
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}

		return rootView;
	}
}
