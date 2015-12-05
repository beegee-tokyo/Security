package tk.giesecke.security;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

public class Security extends AppCompatActivity {

	static Handler updateConversationHandler;

	public static TextView text1;
	public static TextView text2;
	public static TextView text3;
	public static TextView text4;
	public static TextView text5;

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.socket_server);

		text1 = (TextView) findViewById(R.id.text1);
		text2 = (TextView) findViewById(R.id.text2);
		text3 = (TextView) findViewById(R.id.text3);
		text4 = (TextView) findViewById(R.id.text4);
		text5 = (TextView) findViewById(R.id.text5);
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Register handler for UI update
		updateConversationHandler = new Handler();

		// Register the receiver for messages from UDPlistener
		if (activityReceiver != null) {
			//Create an intent filter to listen to the broadcast sent with the action "ACTION_STRING_ACTIVITY"
			IntentFilter intentFilter = new IntentFilter(UDPlistener.BROADCAST_RECEIVED);
			//Map the intent filter to the receiver
			registerReceiver(activityReceiver, intentFilter);
		}

		if (!isMyServiceRunning(UDPlistener.class)) {
			// Start service to listen to UDP broadcast messages
			startService(new Intent(this, UDPlistener.class));
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.d("Service", "onDestroy");
		// Unregister the receiver for messages from UDPlistener
		unregisterReceiver(activityReceiver);
	}

	private boolean isMyServiceRunning(Class<?> serviceClass) {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (serviceClass.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	class updateUITxt implements Runnable {
		private String msg;
		private TextView field;

		public updateUITxt(String str, TextView selector) {
			this.msg = str;
			this.field = selector;
		}

		@Override
		public void run() {
			field.setText(msg);
		}
	}

	private BroadcastReceiver activityReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String senderIP = intent.getStringExtra("ipAdress");
			String[] msgSplit = intent.getStringArrayExtra("message");

			Security.updateConversationHandler.post(new Security.updateUITxt(senderIP, Security.text1));
			if (msgSplit[0].equalsIgnoreCase("ESP8266")) { // new broadcast message type
				Security.updateConversationHandler.post(
						new Security.updateUITxt(msgSplit[0], Security.text5)); // Module name
				Security.updateConversationHandler.post(
						new Security.updateUITxt(msgSplit[1], Security.text2)); // MAC addr
				if (msgSplit[2].equalsIgnoreCase("0")) {
					Security.updateConversationHandler.post(
							new Security.updateUITxt("No detection", Security.text3)); // Detection result
				} else {
					Security.updateConversationHandler.post(
							new Security.updateUITxt("Intruder!", Security.text3)); // Detection result
				}
				Security.updateConversationHandler.post(
						new Security.updateUITxt("Light = " + msgSplit[3] + " lux", Security.text4)); // Light
			}
		}
	};
}
