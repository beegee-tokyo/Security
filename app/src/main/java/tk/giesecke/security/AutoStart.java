package tk.giesecke.security;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AutoStart extends BroadcastReceiver {
	public AutoStart() {
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		// Start service to listen to UDP broadcast messages
		context.startService(new Intent(context, UDPlistener.class));
	}
}
