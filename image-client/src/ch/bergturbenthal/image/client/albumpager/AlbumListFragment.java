package ch.bergturbenthal.image.client.albumpager;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import ch.bergturbenthal.image.client.R;

public class AlbumListFragment extends Fragment {
  public static final String ARG_OBJECT = "object";

  @Override
  public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
    // The last two arguments ensure LayoutParams are inflated
    // properly.
    final View rootView = inflater.inflate(R.layout.fragment_collection_object, container, false);
    final Bundle args = getArguments();
    final TextView textView = (TextView) rootView.findViewById(R.id.fragment_text);
    textView.setText(Integer.toString(args.getInt(ARG_OBJECT)));
    return rootView;
  }
}
