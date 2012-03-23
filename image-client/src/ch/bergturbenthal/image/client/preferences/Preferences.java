package ch.bergturbenthal.image.client.preferences;

import java.util.Map.Entry;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;
import ch.bergturbenthal.image.client.R;

public class Preferences extends PreferenceActivity {

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);
    for (final Entry<String, ?> entry : getPreferenceManager().getSharedPreferences().getAll().entrySet()) {
      Log.i("Preference", entry.getKey() + ":" + entry.getValue());
    }
  }
}
