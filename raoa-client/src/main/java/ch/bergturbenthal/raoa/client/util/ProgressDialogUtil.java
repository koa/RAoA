package ch.bergturbenthal.raoa.client.util;

import android.app.ProgressDialog;
import android.content.Context;
import ch.bergturbenthal.raoa.R;

public class ProgressDialogUtil {
	public static ProgressDialog createProgressDialog(final Context context) {
		final ProgressDialog progressDialog = new ProgressDialog(context);
		progressDialog.setMessage(context.getString(R.string.please_wait));
		progressDialog.setCancelable(false);
		return progressDialog;
	}
}
