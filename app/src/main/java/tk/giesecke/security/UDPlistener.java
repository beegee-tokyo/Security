package tk.giesecke.security;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StrictMode;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPlistener extends Service {

	private static final int UDP_SERVER_PORT = 5000;
	public static final String BROADCAST_RECEIVED = "BC_RECEIVED";

	private final Boolean shouldRestartSocketListen=true;
	private DatagramSocket socket;
	private Context intentContext;

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
		Log.e("UDP", "Initialize listener in onResume");

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
	private void listenAndWaitAndThrowIntent(InetAddress broadcastIP, Integer port) throws Exception {
		byte[] recvBuf = new byte[250];
		if (socket == null || socket.isClosed()) {
			socket = new DatagramSocket(port, broadcastIP);
		}
		DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
		Log.e("UDP", "Waiting for UDP broadcast");
		socket.receive(packet);

		String senderIP = packet.getAddress().getHostAddress();
		String message = new String(packet.getData()).trim();

		Log.e("UDP", "Got UDB broadcast from " + senderIP + ", message: " + message);

		String[] msgSplit = message.split(",");

		/** String for notification */
		String notifText;
		/** Icon for notification */
		int notifIcon;
		/** Background color for notification icon in SDK Lollipop and newer */
		int notifColor;
		/** Need to play alarm sound */
		boolean doAlarm = false;

		if (msgSplit[2].equalsIgnoreCase("0")) {
			notifIcon = R.drawable.no_detection;
			notifText = "No detection from " + senderIP;
			//noinspection deprecation
			notifColor = intentContext.getResources()
					.getColor(android.R.color.holo_green_light);
		} else {
			notifIcon = R.drawable.detection;
			notifText = "Intruder! from " + senderIP;
			//noinspection deprecation
			notifColor = intentContext.getResources()
					.getColor(android.R.color.holo_red_light);
			doAlarm = true;
		}
		notifText += " Light = " + msgSplit[3] + " lux";

		/* Pointer to notification builder for export/import arrow */
		NotificationCompat.Builder myNotifBuilder;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
			myNotifBuilder = new NotificationCompat.Builder(intentContext)
					.setContentTitle(intentContext.getString(R.string.app_name))
					.setContentIntent(PendingIntent.getActivity(intentContext, 0, new Intent(intentContext, Security.class), 0))
					.setAutoCancel(false)
					.setPriority(NotificationCompat.PRIORITY_DEFAULT)
					.setVisibility(Notification.VISIBILITY_PUBLIC)
					.setWhen(System.currentTimeMillis());
		} else {
			myNotifBuilder = new NotificationCompat.Builder(intentContext)
					.setContentTitle(intentContext.getString(R.string.app_name))
					.setContentIntent(PendingIntent.getActivity(intentContext, 0, new Intent(intentContext, Security.class), 0))
					.setAutoCancel(false)
					.setPriority(NotificationCompat.PRIORITY_DEFAULT)
					.setWhen(System.currentTimeMillis());
		}

		/* Pointer to notification manager for export/import arrow */
		NotificationManager notificationManager1 = (NotificationManager) intentContext.getSystemService(Context.NOTIFICATION_SERVICE);

		if (doAlarm) {
			myNotifBuilder.setSound(Uri.parse("android.resource://"
					+ this.getPackageName() + "/"
					+ R.raw.dog));
		}

		myNotifBuilder.setSmallIcon(notifIcon);
		myNotifBuilder.setContentText(notifText);
		myNotifBuilder.setTicker(notifText);
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			myNotifBuilder.setColor(notifColor);
		}

		/* Pointer to notification for export/import arrow */
		Notification notification1 = myNotifBuilder.build();
		notificationManager1.notify(1, notification1);

		sendBroadcast(senderIP, msgSplit);
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
					Log.i("UDP", "no longer listening for UDP broadcasts cause of error " + e.getMessage());
					Log.i("UDP", "restart listener for UDP broadcasts after error");
					onCreate();
				}
			}
		});

		UDPBroadcastThread.start();
	}

	//send broadcast from activity to all receivers listening to the action "ACTION_STRING_ACTIVITY"
	private void sendBroadcast(String ipAddress, String[] msgSplit) {
		Intent broadCastIntent = new Intent();
		broadCastIntent.setAction(BROADCAST_RECEIVED);
		broadCastIntent.putExtra("ipAddress", ipAddress);
		broadCastIntent.putExtra("message", msgSplit);
		sendBroadcast(broadCastIntent);
	}
}
