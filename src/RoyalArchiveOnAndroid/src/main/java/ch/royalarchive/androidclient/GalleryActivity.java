package ch.royalarchive.androidclient;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Gallery;

public class GalleryActivity extends Activity {
	
	Gallery gallery;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simplegallery);
		gallery = (Gallery) findViewById(R.id.gallery);
		// String array holding the values
		String[] text = new String[] { "Hello", "Hi", "Alloha", "Bonjour",
				"Hallo", "¡Hola" };
		// Array adapter to display our values in the gallery control
		ArrayAdapter arr = new ArrayAdapter(this,
				android.R.layout.simple_gallery_item, text);
		gallery.setAdapter(arr);
	}

}
