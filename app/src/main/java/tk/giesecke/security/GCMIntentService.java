package tk.giesecke.security;

import android.app.IntentService;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

public class GCMIntentService extends IntentService {

	@SuppressWarnings("FieldCanBeLocal")
	/** Debug tag */
	private static final String DEBUG_LOG_TAG = "Security-GCM";

	public GCMIntentService() {
		super(GCMIntentService.class.getName());
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		/** Bundle with extras received when Intent is called */
		Bundle extras = intent.getExtras();
		if (!extras.isEmpty()) {
			// Check if screen is locked
			KeyguardManager myKM = (KeyguardManager) getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE);
			boolean phoneIsLocked = myKM.inKeyguardRestrictedInputMode();
			// Check if screen is off
			PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
			boolean screenIsOff = true;
			if (Build.VERSION.SDK_INT >= 20) {
				if (powerManager.isInteractive()) {
					screenIsOff = false;
				}
			}
			else {
				//noinspection deprecation
				if(powerManager.isScreenOn()){
					screenIsOff = false;
				}
			}
			// Check if we are connected to the home WiFi
			boolean notOnHomeWifi = !isHomeWiFi(getApplicationContext());

			// If we are not on home Wifi or screen is off or locked => process the message
			if (notOnHomeWifi || phoneIsLocked || screenIsOff) {
				if (extras.containsKey("message")) {
					// read extras as sent from server
					/** Message received as GCM push notification  */
					String message = extras.getString("message");
					// Check if response is a JSON array
					if (UDPlistener.isJSONValid(message)) {
						if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Got JSON: " + message);
						/** JSON object to hold the received message */
						JSONObject jsonResult;
						try {
							jsonResult = new JSONObject(message);
							try {
								/** Device ID to check who sent the push notification */
								String broadCastDevice = jsonResult.getString("device");
								if (broadCastDevice.startsWith("sf")) { // Broadcast from security device
									// Show notification
									UDPlistener.alarmNotif(jsonResult, getApplicationContext());
								} else {
									if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Got message "+ jsonResult);
								}
								// Broadcast message inside the Android device
								sendGCMBroadcast(message);
								// Write status to database
								UDPlistener.startAsyncDBWrite(jsonResult);
							} catch (Exception ignore) {
								if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing device in JSON "+ jsonResult);
							}
						} catch (JSONException e) {
							if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Create JSONObject from String failed " + e.getMessage());
						}
					} else { // Test message from
						Toast.makeText(getApplicationContext(), "Received GCM: " + message, Toast.LENGTH_LONG).show();
					}
				}
			}
		}
		// Release the wake lock provided by the WakefulBroadcastReceiver.
		GCMBroadcastReceiver.completeWakefulIntent(intent);
	}

	//send broadcast from activity to all receivers listening to the action "ACTION_STRING_ACTIVITY"
	private void sendGCMBroadcast(String msgReceived) {
		/** Intent for broadcast */
		Intent broadCastIntent = new Intent();
		broadCastIntent.setAction(UDPlistener.BROADCAST_RECEIVED);
		broadCastIntent.putExtra("sender", "GCM");
		broadCastIntent.putExtra("message", msgReceived);
		sendBroadcast(broadCastIntent);
	}

	/**
	 * Check WiFi connection and return SSID
	 *
	 * @param context
	 *            application context
	 * @return <code>String</code>
	 *            SSID name or empty string if not connected
	 */
	@SuppressWarnings("deprecation")
	public static Boolean isHomeWiFi(Context context) {
		/** Access to connectivity manager */
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		/** WiFi connection information  */
		android.net.NetworkInfo wifiOn = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		if (!wifiOn.isConnected()) {
			return false;
		} else {
			/** WiFi manager to check current connection */
			final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			/** Info of current connection */
			final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
			if (connectionInfo != null && !TextUtils.isEmpty(connectionInfo.getSSID())) {
				String currentSSID = connectionInfo.getSSID();
				currentSSID = currentSSID.substring(1,currentSSID.length()-1);
				String MY_LOCAL_SSID = context.getResources().getString(R.string.MY_LOCAL_SSID);
				return currentSSID.equalsIgnoreCase(MY_LOCAL_SSID);
			}
		}
		return false;
	}
}
