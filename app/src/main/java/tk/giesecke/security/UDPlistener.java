package tk.giesecke.security;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteDatabase;
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

	/** Receiver for screen on/off broadcast msgs */
	private static BroadcastReceiver mReceiver = null;

	private static final String DEBUG_LOG_TAG = "Security-UDP";

	private static final int UDP_SERVER_PORT = 5000;
	public static final String BROADCAST_RECEIVED = "BC_RECEIVED";

	private final Boolean shouldRestartSocketListen=true;
	private DatagramSocket socket;
	private static Context intentContext;
	WifiManager.MulticastLock lock = null;

	public UDPlistener() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		// Start in foreground (to avoid being killed)
		startForeground(1, ServiceNotification(this));

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

		// Start listener to connection changes
		/** IntentFilter to receive connection change broadcast msgs */
		IntentFilter filter = new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION);
		/** Receiver for screen on/off broadcast msgs */
		UDPlistener.mReceiver = new EventReceiver();
		registerReceiver(UDPlistener.mReceiver, filter);

		if (!GCMIntentService.isHomeWiFi(getApplicationContext())) { // We have no local WiFi connection!
			return;
		}
		// Get the MulticastLock to be able to receive multicast UDP messages
		WifiManager wifi = (WifiManager)getSystemService( Context.WIFI_SERVICE );
		if(wifi != null){
			if (lock != null) { // In case we restart after receiver problem
				lock = wifi.createMulticastLock("Security");
				lock.acquire();
			}
		}

		// TODO the below code does not work! UDP broadcast messages are not received if screen is
		// off or locked. We use GCM instead!
		// Keep device awake to make sure we receive the alarm message
//		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
//		@SuppressWarnings("deprecation") PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "whatever");
//		wl.acquire();

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


		// Check if response is a JSON array
		if (isJSONValid(message)) {
			JSONObject jsonResult;
			try {
				jsonResult = new JSONObject(message);
				try {
					String broadCastDevice = jsonResult.getString("device");
					if (broadCastDevice.startsWith("sf")) { // Broadcast from security device
						if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Got UDP broadcast from " + senderIP + ", message: " + message);
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
			startAsyncDBWrite(jsonResult);
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
				} catch (Exception e) {
					if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Restart UDP listener after error " + e.getMessage());
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
		int hasAlarmActive;
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
			hasAlarmActive = jsonValues.getInt("alarm_on");
		} catch (JSONException e) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing alarm status in JSON object" + e.getMessage());
			hasAlarmActive = 0;
		}

		// Show notification only if it is an alarm
		if (hasAlarmInt == 1 && hasAlarmActive == 1) {
			/** String for notification */
			String notifText;
			/** Icon for notification */
			int notifIcon;
			/** Background color for notification icon in SDK Lollipop and newer */
			int notifColor;

			notifIcon = R.drawable.detection;
			notifText = "Intruder! from " + deviceIDString;
			//noinspection deprecation
			notifColor = notifContext.getResources().getColor(android.R.color.holo_red_light);

			if (BuildConfig.DEBUG) startAsyncFileWrite(jsonValues);
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Wrote to file: " + jsonValues.toString());

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
			NotificationManager notificationManager = (NotificationManager) notifContext.getSystemService(Context.NOTIFICATION_SERVICE);

			/** Access to shared preferences of app widget */
			String selUri = intentContext.getSharedPreferences("Security", 0).getString("alarmUri", "");/** Uri of selected alarm */
			myNotifBuilder.setSound(Uri.parse(selUri));

			myNotifBuilder.setSmallIcon(notifIcon)
					.setContentText(notifText)
					.setContentText(notifText)
					.setStyle(new NotificationCompat.BigTextStyle().bigText(notifText))
					.setTicker(notifText);
			if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				myNotifBuilder.setColor(notifColor);
			}

			/* Pointer to notification */
			Notification alarmNotification = myNotifBuilder.build();
			notificationManager.notify(2, alarmNotification);
		}
	}

	private static void startAsyncFileWrite(JSONObject jsonValues) {
		asyncFileWrite task = new asyncFileWrite(intentContext);
		task.execute(jsonValues);
	}

	private static class asyncFileWrite extends AsyncTask<JSONObject, Void, Void> {

		private final Context myContextRef;

		public asyncFileWrite(Context myContextRef) {
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

	public static void startAsyncDBWrite(JSONObject jsonValues) {
		asyncDBWrite task = new asyncDBWrite(intentContext);
		task.execute(jsonValues);
	}

	private static class asyncDBWrite extends AsyncTask<JSONObject, Void, Void> {

		private final Context myContextRef;

		public asyncDBWrite(Context myContextRef) {
			this.myContextRef = myContextRef;
		}

		@Override
		protected Void doInBackground(JSONObject... params) {

			JSONObject jsonResult = params[0];
			/** Instance of DataBaseHelper */
			DataBaseHelper myDBhelper = new DataBaseHelper(myContextRef);
			/** Instance of data base */
			SQLiteDatabase myDataBase = myDBhelper.getWritableDatabase();
			if (!DataBaseHelper.addData(myDataBase,jsonResult)) {
				if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Writing into database failed " + jsonResult.toString());
			}
			myDataBase.close();
			myDBhelper.close();
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

	/**
	 * Display notification that UDP listener is active
	 *
	 * @param context
	 *          Context of the application
	 * @return <code>Notification</code>
	 *          Instance of the notification
	 */
	private Notification ServiceNotification(Context context) {
		/** String for notification */
		String notifText;
		/** Icon for notification */
		int notifIcon;
		/** Background color for notification icon in SDK Lollipop and newer */
		int notifColor;

		// Prepare notification for foreground service
		notifIcon = R.drawable.no_detection;
		notifText = context.getResources().getString(R.string.udp_listener);
		//noinspection deprecation
		notifColor = context.getResources()
				.getColor(android.R.color.holo_green_light);

		/* Pointer to notification builder for export/import arrow */
		NotificationCompat.Builder myNotifBuilder;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
			myNotifBuilder = new NotificationCompat.Builder(context)
					.setContentTitle(context.getString(R.string.app_name))
					.setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, Security.class), 0))
					.setAutoCancel(false)
					.setPriority(NotificationCompat.PRIORITY_DEFAULT)
					.setVisibility(Notification.VISIBILITY_PUBLIC)
					.setWhen(System.currentTimeMillis());
		} else {
			myNotifBuilder = new NotificationCompat.Builder(context)
					.setContentTitle(context.getString(R.string.app_name))
					.setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, Security.class), 0))
					.setAutoCancel(false)
					.setPriority(NotificationCompat.PRIORITY_DEFAULT)
					.setWhen(System.currentTimeMillis());
		}

		myNotifBuilder.setSmallIcon(notifIcon)
				.setContentText(notifText)
				.setContentText(notifText)
				.setStyle(new NotificationCompat.BigTextStyle().bigText(notifText))
				.setTicker(notifText);
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			myNotifBuilder.setColor(notifColor);
		}

		/* Pointer to notification */
		return myNotifBuilder.build();
	}
}
