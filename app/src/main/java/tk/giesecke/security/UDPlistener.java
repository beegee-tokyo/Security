package tk.giesecke.security;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StrictMode;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class UDPlistener extends Service {

	private static final String DEBUG_LOG_TAG = "Security-UDP";

	private static final int UDP_SERVER_PORT = 5000;
	public static final String BROADCAST_RECEIVED = "BC_RECEIVED";

	private final Boolean shouldRestartSocketListen=true;
	private DatagramSocket socket;
	private static Context intentContext;

	public UDPlistener() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		//this service will run until we stop it
		return START_STICKY;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		intentContext = getApplicationContext();
		// Enable access to internet
		if (android.os.Build.VERSION.SDK_INT > 9) {
			/** ThreadPolicy to get permission to access internet */
			StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
			StrictMode.setThreadPolicy(policy);
		}

		// Get the MulticastLock to be able to receive multicast UDP messages
		WifiManager wifi = (WifiManager)getSystemService( Context.WIFI_SERVICE );
		if(wifi != null){
			WifiManager.MulticastLock lock = wifi.createMulticastLock("Log_Tag");
			lock.acquire();
		}

		// Keep device awake to make sure we receive the alarm message
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		@SuppressWarnings("deprecation") PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "whatever");
		wl.acquire();

		// Start listener for UDP broadcast messages
		startListenForUDPBroadcast();
	}

	// UDP stuff starts here
	private void listenAndWaitAndThrowIntent(InetAddress broadcastIP, Integer port) {
		byte[] recvBuf = new byte[250];
		if (socket == null || socket.isClosed()) {
			try {
				socket = new DatagramSocket(port, broadcastIP);
			} catch (SocketException e) {
				if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Cannot open socket " + e.getMessage());
			}
		}
		DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
		if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Waiting for UDP broadcast");
		try {
			socket.receive(packet);
		} catch (IOException e) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Socket receive failed " + e.getMessage());
		}

		String senderIP = packet.getAddress().getHostAddress();
		String message = new String(packet.getData()).trim();

		if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Got UDP broadcast from " + senderIP + ", message: " + message);

		// Check if response is a JSON array
		if (isJSONValid(message)) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Got JSON: " + message);
			JSONObject jsonResult;
			try {
				jsonResult = new JSONObject(message);
				try {
					String broadCastDevice = jsonResult.getString("device");
					if (broadCastDevice.startsWith("sf")) { // Broadcast from security device
						alarmNotif(jsonResult, intentContext);
					}
				} catch (JSONException ignore) {
					if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing device in JSON: " + message);
					return;
				}
			} catch (JSONException e) {
				if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Create JSONObject from String failed " + e.getMessage());
				return;
			}
			sendUDPBroadcast(senderIP, message);
		}
	}

	private void startListenForUDPBroadcast() {
		Thread UDPBroadcastThread = new Thread(new Runnable() {
			public void run() {
				try {
					InetAddress broadcastIP = InetAddress.getByName("192.168.0.255"); //172.16.238.42 //192.168.1.255
					Integer port = UDP_SERVER_PORT;
					while (shouldRestartSocketListen) {
						listenAndWaitAndThrowIntent(broadcastIP, port);
					}
					//if (!shouldListenForUDPBroadcast) throw new ThreadDeath();
				} catch (Exception e) {
					if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "no longer listening for UDP broadcasts cause of error " + e.getMessage());
					if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "restart listener for UDP broadcasts after error");
					onCreate();
				}
			}
		});

		UDPBroadcastThread.start();
	}

	//send broadcast from activity to all receivers listening to the action "ACTION_STRING_ACTIVITY"
	private void sendUDPBroadcast(String ipAddress, String msgReceived) {
		Intent broadCastIntent = new Intent();
		broadCastIntent.setAction(BROADCAST_RECEIVED);
		broadCastIntent.putExtra("sender", ipAddress);
		broadCastIntent.putExtra("message", msgReceived);
		sendBroadcast(broadCastIntent);
	}

	/**
	 * Check if JSON object is valid
	 *
	 * @param test
	 *            String with JSON object or array
	 * @return boolean
	 *            true if "test" us a JSON object or array
	 *            false if no JSON object or array
	 */
	public static boolean isJSONValid(String test) {
		try {
			new JSONObject(test);
		} catch (JSONException ex) {
			try {
				new JSONArray(test);
			} catch (JSONException ex1) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Prepare notification from local broadcast or GCM broadcast
	 *
	 * @param jsonValues
	 *            JSON object with values
	 */
	public static void alarmNotif(JSONObject jsonValues, Context notifContext) {
		int hasAlarmInt;
		int hasAlarmOnInt;
		int hasLightOnInt;
		int hasRebootedInt;
		long lightValueLong;
		String deviceIDString;
		try {
			deviceIDString = jsonValues.getString("device");
		} catch (JSONException e) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing deviceID in JSON object" + e.getMessage());
			deviceIDString = "unknown";
		}
		try {
			hasAlarmInt = jsonValues.getInt("alarm");
		} catch (JSONException e) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing alarm status in JSON object" + e.getMessage());
			hasAlarmInt = 0;
		}
		try {
			hasAlarmOnInt = jsonValues.getInt("alarm_on");
		} catch (JSONException e) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing alarm setting in JSON object" + e.getMessage());
			hasAlarmOnInt = 1;
		}
		try {
			hasLightOnInt = jsonValues.getInt("light_on");
		} catch (JSONException e) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing light setting in JSON object" + e.getMessage());
			hasLightOnInt = 0;
		}
		try {
			hasRebootedInt = jsonValues.getInt("boot");
		} catch (JSONException e) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing boot info in JSON object" + e.getMessage());
			hasRebootedInt = 0;
		}
		try {
			lightValueLong = jsonValues.getLong("light_val");
		} catch (JSONException e) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing light value in JSON object" + e.getMessage());
			lightValueLong = 0;
		}
		/** String for notification */
		String notifText;
		/** Icon for notification */
		int notifIcon;
		/** Background color for notification icon in SDK Lollipop and newer */
		int notifColor;
		/** Need to play alarm sound */
		boolean doAlarm = false;

		if (hasAlarmInt == 1) {
			notifIcon = R.drawable.detection;
			notifText = "Intruder! from " + deviceIDString;
			//noinspection deprecation
			notifColor = notifContext.getResources()
					.getColor(android.R.color.holo_red_light);
			doAlarm = true;
		} else {
			notifIcon = R.drawable.no_detection;
			notifText = "No detection at " + deviceIDString;
			//noinspection deprecation
			notifColor = notifContext.getResources()
					.getColor(android.R.color.holo_green_light);
		}
		if (hasAlarmOnInt == 1) {
			notifText += "\nAlarm active";
		} else {
			notifText += "\nAlarm not active";
		}
		if (hasLightOnInt == 1) {
			notifText += "\nLight active";
		} else {
			notifText += "\nLight not active";
		}
		if (lightValueLong != 0) {
			notifText += "\nLight = " + lightValueLong + " lux";
		}
		if (hasRebootedInt != 0) {
			notifText += "\nDevice restarted!";
		}
		try {
			String rebootReason = jsonValues.getString("reboot");
			notifText += "\nReboot reason = " + rebootReason;
		} catch (JSONException e) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing reboot reason in JSON object" + e.getMessage());
		}

		if (BuildConfig.DEBUG) useFileAsyncTask(jsonValues);

		/* Pointer to notification builder for export/import arrow */
		NotificationCompat.Builder myNotifBuilder;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
			myNotifBuilder = new NotificationCompat.Builder(notifContext)
					.setContentTitle(notifContext.getString(R.string.app_name))
					.setContentIntent(PendingIntent.getActivity(notifContext, 0, new Intent(notifContext, Security.class), 0))
					.setAutoCancel(false)
					.setPriority(NotificationCompat.PRIORITY_DEFAULT)
					.setVisibility(Notification.VISIBILITY_PUBLIC)
					.setWhen(System.currentTimeMillis());
		} else {
			myNotifBuilder = new NotificationCompat.Builder(notifContext)
					.setContentTitle(notifContext.getString(R.string.app_name))
					.setContentIntent(PendingIntent.getActivity(notifContext, 0, new Intent(notifContext, Security.class), 0))
					.setAutoCancel(false)
					.setPriority(NotificationCompat.PRIORITY_DEFAULT)
					.setWhen(System.currentTimeMillis());
		}

		/* Pointer to notification manager for export/import arrow */
		NotificationManager notificationManager1 = (NotificationManager) notifContext.getSystemService(Context.NOTIFICATION_SERVICE);

		if (doAlarm) {
			/** Access to shared preferences of app widget */
			String selUri = intentContext.getSharedPreferences("Security", 0).getString("alarmUri", "");/** Uri of selected alarm */
			myNotifBuilder.setSound(Uri.parse(selUri));
		}

		myNotifBuilder.setSmallIcon(notifIcon)
				.setContentText(notifText)
				.setContentText(notifText)
				.setStyle(new NotificationCompat.BigTextStyle().bigText(notifText))
				.setTicker(notifText);
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			myNotifBuilder.setColor(notifColor);
		}

		/* Pointer to notification for export/import arrow */
		Notification notification1 = myNotifBuilder.build();
		notificationManager1.notify(1, notification1);
	}

	private static void useFileAsyncTask(JSONObject jsonValues) {
		FileWorkerAsyncTask task = new FileWorkerAsyncTask(intentContext);
		task.execute(jsonValues);
	}

	private static class FileWorkerAsyncTask extends AsyncTask<JSONObject, Void, Void> {

		private final Context myContextRef;

		public FileWorkerAsyncTask(Context myContextRef) {
			this.myContextRef = myContextRef;
		}

		@Override
		protected Void doInBackground(JSONObject... params) {

			JSONObject resultJSON = params[0];
			File debugFile = new File(myContextRef.getFilesDir() + "/SecurityLog.txt");
			String debugMsg = getCurrentTime();
			debugMsg += " -- " + resultJSON.toString() + "\n";
			Writer writer;
			try {
				writer = new BufferedWriter(new FileWriter(debugFile, true));
				writer.write(debugMsg);
				writer.close();
			} catch (IOException e) {
				if (BuildConfig.DEBUG) e.printStackTrace();
			}
			return null;
		}
	}

	/**
	 * Get current time as string
	 *
	 * @return <code>String</code>
	 *            Time as string HH:mm
	 */
	private static String getCurrentTime() {
		/** Calendar to get current time and date */
		Calendar cal = Calendar.getInstance();
		/** Time format */
		@SuppressLint("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("dd-MM HH:mm");
		return df.format(cal.getTime());
	}
}
