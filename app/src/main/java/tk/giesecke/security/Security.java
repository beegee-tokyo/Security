package tk.giesecke.security;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

public class Security extends AppCompatActivity implements View.OnClickListener {

	private static Handler updateConversationHandler;

	private static TextView tvDebug;
	private ImageView ivAlarmStatus;
	private ImageView ivLightStatus;
	private ImageView ivAlarmOn;
	private ValueAnimator animator;
	private Menu abMenu;
	/** A HTTP client to access the spMonitor device */
	private static OkHttpClient client;

	private boolean hasAlarmOn = true;

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.security);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		// Enable access to internet
		if (Build.VERSION.SDK_INT > 9) {
			/** ThreadPolicy to get permission to access internet */
			StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
			StrictMode.setThreadPolicy(policy);
		}

		tvDebug = (TextView) findViewById(R.id.txt_debug);
		ivAlarmStatus = (ImageView) findViewById(R.id.dot_alarm_status);
		ivLightStatus = (ImageView) findViewById(R.id.dot_light);
		ivAlarmOn = (ImageView) findViewById(R.id.dot_alarm_on);

		animator = ValueAnimator.ofFloat(0f, 1f);
		animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				ivAlarmOn.setAlpha((Float) animation.getAnimatedValue());
			}
		});

		animator.setDuration(1500);
		animator.setRepeatMode(ValueAnimator.REVERSE);
		animator.setRepeatCount(-1);

		client = new OkHttpClient();
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Register handler for UI update
		updateConversationHandler = new Handler();

		// Register the receiver for messages from UDP listener
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

		new callESP().execute("http://192.168.0.141/?s");
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.d("Service", "onDestroy");
		// Unregister the receiver for messages from UDP listener
		unregisterReceiver(activityReceiver);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_security, menu);
		// Save id of menu for later use */
		abMenu = menu;
		return true;
	}

	@SuppressWarnings("deprecation")
	@SuppressLint("InflateParams")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
			case R.id.bt_alarm:
				if (hasAlarmOn) {
					new callESP().execute("http://192.168.0.141/?a=0");
				} else {
					new callESP().execute("http://192.168.0.141/?a=1");
				}
				new callESP().execute("http://192.168.0.141/?s");
				break;
			case R.id.bt_status:
				new callESP().execute("http://192.168.0.141/?s");
				break;
		}

		return super.onOptionsItemSelected(item);
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

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.bt_MyAirCon:
				Intent airConIntent = getPackageManager().getLaunchIntentForPackage("tk.giesecke.myaircon");
				startActivity(airConIntent);
				break;
			case R.id.bt_spMonitor:
				Intent spMonitorIntent = getPackageManager().getLaunchIntentForPackage("tk.giesecke.spmonitor");
				startActivity(spMonitorIntent);
				break;
		}
	}

	class updateUITxt implements Runnable {
		private final String msg;
		private final TextView field;
		private final boolean detection;

		public updateUITxt(String str, TextView selector, boolean detection) {
			this.msg = str;
			this.field = selector;
			this.detection = detection;
		}

		@Override
		public void run() {
			field.setText(msg);
			if (detection) {
				animator.start();
			} else {
				animator.end();
				ivAlarmOn.setAlpha(0f);
			}
		}
	}

	/**
	 * Wrapper to send several parameters to onPostExecute of AsyncTask
	 *
	 * comResult = return string as JSON from the ESP device
	 */
	public class onPostExecuteWrapper {
		public String comResult; // Result of communication
	}

	/**
	 * Async task class to contact ESP device
	 * params[0] = url including the command
	 */
	private class callESP extends AsyncTask<String, String, onPostExecuteWrapper> {

		@Override
		protected onPostExecuteWrapper doInBackground(String... params) {

			/** Return values for onPostExecute */
			onPostExecuteWrapper result = new onPostExecuteWrapper();

			/** URL to be called */
			String urlString = params[0]; // URL to call

			if (BuildConfig.DEBUG) Log.d("security", "callESP = " + urlString);

			/** Request to ESP device */
			Request request = new Request.Builder()
					.url(urlString)
					.build();

			if (request != null) {
				try {
					/** Response from ESP device */
					Response response = client.newCall(request).execute();
					if (response != null) {
						result.comResult = response.body().string();
					}
				} catch (IOException e) {
					result.comResult = e.getMessage();
					try {
						if (result.comResult.contains("EHOSTUNREACH")) {
							result.comResult = getApplicationContext().getString(R.string.err_esp);
						}
						if (result.comResult.equalsIgnoreCase("")) {
							result.comResult = getApplicationContext().getString(R.string.err_esp);
						}
						return result;
					} catch (NullPointerException en) {
						result.comResult = getResources().getString(R.string.err_no_esp);
						return result;
					}
				}
			}

			if (result.comResult.equalsIgnoreCase("")) {
				result.comResult = getApplicationContext().getString(R.string.err_esp);
			}
			return result;
		}

		protected void onPostExecute(onPostExecuteWrapper result) {
			if (BuildConfig.DEBUG) Log.d("myAirCon", "Result of callESP = " + result);
			activityUpdate(result);
		}
	}

	/**
	 * Update UI with values received from ESP device
	 *
	 * @param result
	 *        result sent by onPostExecute
	 */
	private void activityUpdate(final onPostExecuteWrapper result) {
		runOnUiThread(new Runnable() {
			@SuppressWarnings("deprecation")
			@Override
			public void run() {
				/** Result string split into comma separated parts */
				String[] splitMsg = result.comResult.split(",");
				/** Menu item to toggle alarm on/off */
				MenuItem alarmMenu = abMenu.getItem(0);
				if (splitMsg[1].equalsIgnoreCase("timeout")) {
					tvDebug.setText(R.string.err_esp);
				} else if (splitMsg[1].equalsIgnoreCase("Alarm switched off")) {
					alarmMenu.setTitle(R.string.bt_txt_alarm_on);
					alarmMenu.setIcon(R.drawable.ic_alarm_on);
					ivAlarmStatus.setImageDrawable(getResources().getDrawable(R.mipmap.ic_dot));
					hasAlarmOn = false;
					tvDebug.setText(splitMsg[1]);
				} else if (splitMsg[1].equalsIgnoreCase("Alarm switched on")) {
					alarmMenu.setTitle(R.string.bt_txt_alarm_off);
					alarmMenu.setIcon(R.drawable.ic_alarm_off);
					ivAlarmStatus.setImageDrawable(getResources().getDrawable(R.mipmap.ic_dot_orange));
					hasAlarmOn = true;
					tvDebug.setText(splitMsg[1]);
				} else {
					if (splitMsg[6].equalsIgnoreCase("TRUE")) {
						alarmMenu.setTitle(R.string.bt_txt_alarm_off);
						alarmMenu.setIcon(R.drawable.ic_alarm_off);
						ivAlarmStatus.setImageDrawable(getResources().getDrawable(R.mipmap.ic_dot_orange));
						hasAlarmOn = true;
					} else {
						alarmMenu.setTitle(R.string.bt_txt_alarm_on);
						alarmMenu.setIcon(R.drawable.ic_alarm_on);
						ivAlarmStatus.setImageDrawable(getResources().getDrawable(R.mipmap.ic_dot));
						hasAlarmOn = false;
					}
					if (splitMsg[7].equalsIgnoreCase("TRUE")) {
						ivLightStatus.setImageDrawable(getResources().getDrawable(R.mipmap.ic_dot_green));
					} else {
						ivLightStatus.setImageDrawable(getResources().getDrawable(R.mipmap.ic_dot));
					}
					if (splitMsg[8].equalsIgnoreCase("FALSE")) {
						animator.end();
						ivAlarmOn.setAlpha(0f);
					}
					result.comResult = "SSID " + splitMsg[1]
							+ "-  IP " + splitMsg[2]
							+ " - MAC " + splitMsg[3]
							+ "\nApp size " + splitMsg[4]
							+ " - Free mem " + splitMsg[5]
							+ "\nAlarm status " + splitMsg[6]
							+ " - Light status " + splitMsg[7]
							+ " - Detection " + splitMsg[8];
					tvDebug.setText(result.comResult);
				}
			}
		});
	}

	private final BroadcastReceiver activityReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String debugMsg = intent.getStringExtra("ipAddress");
			String[] message = intent.getStringArrayExtra("message");
			debugMsg += " - " + message[0] +
					" - " + message[1] +
					" - " + message[2] +
					" - " + message[3];
			boolean hasAlarm = message[2].equalsIgnoreCase("1");
			Security.updateConversationHandler.post(new Security.updateUITxt(debugMsg, Security.tvDebug, hasAlarm));
		}
	};
}
