/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.provider.state;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import android.app.Fragment;
import android.content.ContentResolver;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import ch.bergturbenthal.image.provider.Client;
import ch.bergturbenthal.image.provider.R;

public class ServerCreateAlbumFragment extends Fragment {

  @Override
  public void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
    final ContentResolver contentResolver = getActivity().getContentResolver();
    final Cursor cursor = contentResolver.query(Client.ALBUM_URI, new String[] { Client.Album.FULL_NAME }, null, null, null);
    final Collection<String> existingFolders = new TreeSet<String>();
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

    final EditText albumNameInput = (EditText) view.findViewById(R.id.album_name);

    final Button createButton = (Button) view.findViewById(R.id.createButton);
    createButton.setOnClickListener(new OnClickListener() {

      @Override
      public void onClick(final View v) {
        final String parentFolder = (String) selectFolderSpinner.getSelectedItem();
        if (parentFolder == null) {
          Toast.makeText(getActivity(), R.string.create_folder_error_no_parent, Toast.LENGTH_LONG);
          return;
        }
        final String albumName = albumNameInput.getText().toString();
        if (albumName == null || albumName.trim().length() == 0) {
          Toast.makeText(getActivity(), R.string.create_folder_error_no_album_name, Toast.LENGTH_LONG);
          return;
        }
        final String serverId = getArguments() == null ? null : getArguments().getString(Client.ServerEntry.SERVER_ID);
        if (serverId == null)
          return;
        final long autoAddDate = datePicker.getDate();
        final String fullAlbumName;
        if (parentFolder.equals(""))
          fullAlbumName = albumName.trim();
        else
          fullAlbumName = parentFolder + "/" + albumName.trim();
        new AsyncTask<Void, Void, Void>() {

          @Override
          protected Void doInBackground(final Void... params) {
            new Client(contentResolver).createAlbumOnServer(serverId, fullAlbumName, new Date(autoAddDate));
            return null;
          }
        }.execute();
      }
    });
    return view;
  }

}
