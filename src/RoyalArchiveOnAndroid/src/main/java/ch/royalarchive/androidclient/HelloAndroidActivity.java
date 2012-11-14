package ch.royalarchive.androidclient;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class HelloAndroidActivity extends Activity {

	private static String TAG = "RoyalArchiveOnAndroid";
	private Button next;
	
	private boolean isNext = true;

	/**
	 * Called when the activity is first created.
	 * 
	 * @param savedInstanceState
	 *            If the activity is being re-initialized after previously being
	 *            shut down then this Bundle contains the data it most recently
	 *            supplied in onSaveInstanceState(Bundle). <b>Note: Otherwise it
	 *            is null.</b>
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG, "onCreate");
		setContentView(R.layout.main);

		next = (Button) findViewById(R.id.next);
		next.setText(R.string.next);
		isNext = false;
		
		next.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				/**if(isNext){
					next.setText(R.string.finished);
					isNext = false;
				} else {
					next.setText(R.string.next);
					isNext = true;
				}
				Log.d(TAG,getString(R.string.finished));**/
				Intent intent = new Intent(HelloAndroidActivity.this, GalleryActivity.class);
				startActivity(intent);
			}
		});
		
	}

}
