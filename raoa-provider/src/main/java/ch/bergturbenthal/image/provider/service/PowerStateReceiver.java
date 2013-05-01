package ch.bergturbenthal.image.provider.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.os.PowerManager;

public class PowerStateReceiver extends BroadcastReceiver {

  public static void notifyPowerState(final Context context) {
    final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    final boolean isScreenOn = pm.isScreenOn();
    final Intent intent2 = new Intent(context, SynchronisationServiceImpl.class);
    intent2.putExtra("command", (Parcelable) (isScreenOn ? ServiceCommand.SCREEN_ON : ServiceCommand.SCREEN_OFF));
    context.startService(intent2);
  }

  @Override
  public void onReceive(final Context context, final Intent intent) {
    notifyPowerState(context);
  }

}
