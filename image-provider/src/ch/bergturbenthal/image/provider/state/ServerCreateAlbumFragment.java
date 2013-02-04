/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.provider.state;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

import android.app.Fragment;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CalendarView;
import android.widget.Spinner;
import ch.bergturbenthal.image.provider.Client;
import ch.bergturbenthal.image.provider.R;

public class ServerCreateAlbumFragment extends Fragment {

  @Override
  public void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
    final Cursor cursor = getActivity().getContentResolver().query(Client.ALBUM_URI, new String[] { Client.Album.FULL_NAME }, null, null, null);
    final LinkedHashSet<String> existingFolders = new LinkedHashSet<String>();
    while (cursor.moveToNext()) {
      final String albumDir = cursor.getString(0);
      final int lastSlash = albumDir.lastIndexOf('/');
      if (lastSlash <= 1)
        continue;
      final String folderPart = albumDir.substring(0, lastSlash);
      existingFolders.add(folderPart);
    }
    existingFolders.add("");
    final View view = inflater.inflate(R.layout.activity_server_createalbum, container, false);
    final Spinner selectFolderSpinner = (Spinner) view.findViewById(R.id.selectFolderSpinner);
    selectFolderSpinner.setAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_dropdown_item,
                                                            new ArrayList<String>(existingFolders)));
    final CalendarView datePicker = (CalendarView) view.findViewById(R.id.selectDate);
    // set startdate to now - 60 days
    datePicker.setMinDate(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60));
    // datePicker.setMaxDate(System.currentTimeMillis() +
    // TimeUnit.DAYS.toMillis(30));
    return view;
  }

}
