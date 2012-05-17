package ch.bergturbenthal.image.client.albumpager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import ch.bergturbenthal.image.client.R;
import ch.bergturbenthal.image.client.albumlist.AlbumListAdapter;
import ch.bergturbenthal.image.client.resolver.AlbumService;
import ch.bergturbenthal.image.client.resolver.Resolver;
import ch.bergturbenthal.image.data.model.AlbumEntry;

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

    final Context context = container.getContext();
    final Resolver resolver = new Resolver(context);
    resolver.establishLastConnection(new Resolver.ConnectionUrlListener() {

      @Override
      public void notifyConnectionEstabilshed(final String foundUrl, final String serverName) {
        final AlbumService albumService = new AlbumService(foundUrl);
        final List<AlbumEntry> albums = new ArrayList<AlbumEntry>(albumService.listAlbums().getAlbumNames());
        Collections.sort(albums, new Comparator<AlbumEntry>() {
          @Override
          public int compare(final AlbumEntry o1, final AlbumEntry o2) {
            return o1.getName().compareTo(o2.getName());
          }
        });
        container.post(new Runnable() {

          @Override
          public void run() {
            final AlbumListAdapter albumList = new AlbumListAdapter(context, clientTitle, albums);
            setListAdapter(albumList);
          }
        });
      }

      @Override
      public void notifyConnectionNotEstablished() {
      }

    });

    return rootView;
  }
}
