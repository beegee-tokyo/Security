package tk.giesecke.security;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

@SuppressWarnings("deprecation")
public class Security extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener {

	/** Debug tag */
	private static final String DEBUG_LOG_TAG = "Security";

	/** Context of this application */
	private Context appContext;

	/** TextView for status message display */
	private static TextView tvDebug;
	/** ImageView to show status of alarm enabled */
	private ImageView ivAlarmStatus;
	/** ImageView to show status of light enabled */
	private ImageView ivLightStatus;
	/** ImageView to show active alarm */
	private ImageView ivAlarmOn;
	/** Animator to make blinking active alarm ImageView */
	private ValueAnimator animator;
	/** Menu to access Action bar and Menu items */
	private Menu abMenu;
	/** A HTTP client to access the spMonitor device */
	private static OkHttpClient client;

	/** Status flag for alarm */
	private boolean hasAlarmOn = true;

	// For Google Cloud Messaging
	/** Name of stored registration id in shared preferences */
	private static final String PREF_GCM_REG_ID = "PREF_GCM_REG_ID";
	/** Access to activities shared preferences */
	private SharedPreferences prefs;

	/** GCM project ID */
	private static String GCM_SENDER_ID;
	/** URL to ESP8266 to store the GCM registration ID */
	private static String WEB_SERVER_URL;

	/** Access to Google Cloud Messaging */
	private GoogleCloudMessaging gcm;

	/** Status values for GCM registration process */
	private static final int ACTION_PLAY_SERVICES_DIALOG = 100;
	private static final int MSG_REGISTER_WITH_GCM = 101;
	private static final int MSG_REGISTER_WEB_SERVER = 102;
	private static final int MSG_REGISTER_WEB_SERVER_SUCCESS = 103;
	private static final int MSG_REGISTER_WEB_SERVER_FAILURE = 104;
	/** Registration ID received from GCM server */
	private String gcmRegId;

	/** Array list with available alarm names */
	private ArrayList<String> notifNames = new ArrayList<>();
	/** Array list with available alarm uri's */
	private ArrayList<String> notifUri = new ArrayList<>();
	/** Selected alarm name */
	private String notifNameSel = "";
	/** Selected alarm uri */
	private String notifUriSel = "";

	/**
	 * Called when activity is created
	 *
	 * @param savedInstanceState
	 *              Bundle with data of last instance
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.security);
		/** Toolbar for Action bar activation */
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		// Get pointer to shared preferences
		prefs = getSharedPreferences("Security",0);

		// Get context of the application to be reused in Async Tasks
		appContext = this;

		/******************************************************************/
		/* Next two values must be changed to your GCM project ID         */
		/* and the URL or IP address of your ESP8266                      */
		/******************************************************************/
		// Get project ID and ESP8266 URL
		GCM_SENDER_ID = this.getResources().getString(R.string.GCM_SENDER_ID); // = "9x8x3x2x1x3x";
		WEB_SERVER_URL = this.getResources().getString(R.string.WEB_SERVER_URL); // = "http://192.168.xxx.xxx";

		// Enable access to internet
		if (Build.VERSION.SDK_INT > 9) {
			/** ThreadPolicy to get permission to access internet */
			StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
			StrictMode.setThreadPolicy(policy);
		}

		tvDebug = (TextView) findViewById(R.id.txt_debug);
		if (GCMIntentService.isHomeWiFi(this)) {
			tvDebug.setText(getResources().getString(R.string.at_home));
		} else {
			tvDebug.setText(getResources().getString(R.string.not_home));
		}
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

		initChart();
	}

	/**
	 * Called when activity is getting visible
	 */
	@Override
	protected void onResume() {
		super.onResume();
		// Register the receiver for messages from UDP & GCM listener
		// Create an intent filter to listen to the broadcast sent with the action "ACTION_STRING_ACTIVITY"
		IntentFilter intentFilter = new IntentFilter(UDPlistener.BROADCAST_RECEIVED);
		//Map the intent filter to the receiver
		registerReceiver(activityReceiver, intentFilter);

		if (!isMyServiceRunning(UDPlistener.class)) {
			// Start service to listen to UDP broadcast messages
			startService(new Intent(this, UDPlistener.class));
		}

		// Get initial status from ESP8266
		new callESP().execute("/?s");
	}

	/**
	 * Called when activity is getting invisible
	 */
	@Override
	public void onPause() {
		super.onPause();
		// Unregister the receiver for messages from UDP listener
		unregisterReceiver(activityReceiver);
	}

	/**
	 * Initialize menu during creation
	 *
	 * @param menu
	 *              Menu to be created
	 * @return <code>boolean</code>
	 *              always true, required from Android OS
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_security, menu);
		// Save id of menu for later use */
		abMenu = menu;
		// Read saved registration id from shared preferences.
		gcmRegId = prefs.getString(PREF_GCM_REG_ID, "");

		if (!TextUtils.isEmpty(gcmRegId)) { // If already registered to GCM hide action menu item
			MenuItem menuContent = menu.getItem(2);
			menuContent.setVisible(false);
		}
		return true;
	}

	/**
	 * Listener to clicks on ActionBar and menu
	 *
	 * @param item
	 *              ActionBar / Menu item that has been clicked
	 */
	@SuppressWarnings("deprecation")
	@SuppressLint("InflateParams")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
			case R.id.bt_alarm:
				if (hasAlarmOn) {
					new callESP().execute("/?a=0");
				} else {
					new callESP().execute("/?a=1");
				}
				break;
			case R.id.bt_status:
				tvDebug.setText(getResources().getString(R.string.not_connected));
				new callESP().execute("/?s");
				break;
			case R.id.bt_regId:
				// Check device for Play Services APK.
				if (isGooglePlayInstalled()) {
					gcm = GoogleCloudMessaging.getInstance(appContext);

					// Read saved registration id from shared preferences.
					gcmRegId = prefs.getString(PREF_GCM_REG_ID, "");

					if (TextUtils.isEmpty(gcmRegId)) {
						handleMessage(MSG_REGISTER_WITH_GCM);
					} else {
						String debugTxt = "Already registered with GCM - " + gcmRegId;
						tvDebug.setText(debugTxt);
					}
				}
				break;
			case R.id.bt_switchView:
				TextView debugTxt = (TextView) findViewById(R.id.txt_debug);
				com.github.mikephil.charting.charts.LineChart secChart =
						(com.github.mikephil.charting.charts.LineChart) findViewById(R.id.graph_sec);
				com.github.mikephil.charting.charts.LineChart acChart =
						(com.github.mikephil.charting.charts.LineChart) findViewById(R.id.graph_ac);
				LinearLayout setAlarmTime = (LinearLayout) findViewById(R.id.set_alarm);
				/** Menu item to show if alarm is enabled or not */
				MenuItem alarmMenu = null;
				if (abMenu != null) { // Menu might not be available yet
					/** Menu item to toggle alarm on/off */
					alarmMenu = abMenu.getItem(4);
				}
				if (setAlarmTime.getVisibility() == View.VISIBLE) {
					setAlarmTime.setVisibility(View.GONE);
					debugTxt.setVisibility(View.GONE);
					acChart.setVisibility(View.VISIBLE);
					secChart.setVisibility(View.VISIBLE);
					initChart();
					if (alarmMenu != null) { // Menu might not be available yet
						alarmMenu.setIcon(R.drawable.ic_debug);
					}
				} else if (secChart.getVisibility() == View.VISIBLE) {
					setAlarmTime.setVisibility(View.GONE);
					debugTxt.setVisibility(View.VISIBLE);
					acChart.setVisibility(View.GONE);
					secChart.setVisibility(View.GONE);
					if (alarmMenu != null) { // Menu might not be available yet
						alarmMenu.setIcon(R.drawable.ic_alarm);
					}
				} else if (debugTxt.getVisibility() == View.VISIBLE){
					setAlarmTime.setVisibility(View.VISIBLE);
					debugTxt.setVisibility(View.GONE);
					acChart.setVisibility(View.GONE);
					secChart.setVisibility(View.GONE);
					if (alarmMenu != null) { // Menu might not be available yet
						alarmMenu.setIcon(R.drawable.ic_chart);
					}
				}
				break;
			case R.id.bt_clearGraph:
				/** Instance of DataBaseHelper */
				DataBaseHelper myDBhelper = new DataBaseHelper(getApplicationContext());
				/** Instance of data base */
				SQLiteDatabase myDataBase = myDBhelper.getReadableDatabase();
				/** Delete all data from database */
				DataBaseHelper.delAllData(myDataBase);
				myDataBase.close();
				myDBhelper.close();
				initChart();
				break;
			case R.id.bt_selAlarm:
				notifNames = new ArrayList<>();
				notifUri = new ArrayList<>();
				notifNames.add(getString(R.string.no_alarm_sel));
				notifUri.add("");
				notifNames.add(getString(R.string.snd_alarm));
				notifUri.add("android.resource://"
						+ this.getPackageName() + "/"
						+ R.raw.alarm);
				notifNames.add(getString(R.string.snd_alert));
				notifUri.add("android.resource://"
						+ this.getPackageName() + "/"
						+ R.raw.alert);
				notifNames.add(getString(R.string.snd_dog));
				notifUri.add("android.resource://"
						+ this.getPackageName() + "/"
						+ R.raw.dog);
				/** Index of last user selected alarm tone */
				int uriIndex = getNotifSounds(this, notifNames, notifUri) + 2;

				// get sound_selector.xml view
				/** Layout inflater for sound selection dialog */
				LayoutInflater dialogInflater = LayoutInflater.from(this);
				/** View for sound selection dialog */
				View locationSettingsView = dialogInflater.inflate(R.layout.sound_selector, null);
				/** Alert dialog builder for device selection dialog */
				AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

				// set sound_selector.xml to alert dialog builder
				alertDialogBuilder.setView(locationSettingsView);

				// set dialog message
				alertDialogBuilder
						.setTitle(getResources().getString(R.string.sound_selector_title))
						.setCancelable(false)
						.setNegativeButton("OK",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
										if (!notifNameSel.equalsIgnoreCase("")) {
											prefs.edit().putString("alarmUri", notifUriSel).apply();
										}
										dialog.cancel();
									}
								});

				// create alert dialog
				/** Alert dialog  for device selection */
				AlertDialog alertDialog = alertDialogBuilder.create();

				// show it
				alertDialog.show();

				/** Pointer to list view with the alarms */
				ListView lvAlarmList = (ListView) locationSettingsView.findViewById(R.id.lv_AlarmList);
				/** Array adapter for the ListView */
				final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
						appContext,
						R.layout.my_list_item,
						notifNames );
				lvAlarmList.setAdapter(arrayAdapter);
				// Use long click listener to play the alarm sound
				lvAlarmList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
					public boolean onItemLongClick(AdapterView<?> arg0, View arg1,
					                               int pos, long id) {
						/** Instance of media player */
						MediaPlayer mMediaPlayer = new MediaPlayer();
						try {
							mMediaPlayer.setDataSource(appContext, Uri.parse(notifUri.get(pos)));
							/** Audio manager to play the sound */
							final AudioManager audioManager = (AudioManager) appContext
									.getSystemService(Context.AUDIO_SERVICE);
							if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) != 0) {
								mMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
								mMediaPlayer.prepare();
								mMediaPlayer.start();
							}
						} catch (IOException e) {
							if (BuildConfig.DEBUG) Log.d("spMonitor", "Cannot play alarm");
						}
						return true;
					}
				});
				lvAlarmList.setOnItemClickListener(this);
				lvAlarmList.setItemChecked(uriIndex, true);
				lvAlarmList.setSelection(uriIndex);
				break;
			case R.id.bt_updateRemoteDB:
				// TODO start async task to get all data from the spMonitor device and sync it with the remote DB
				break;
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * Listener for button clicks on main UI
	 *
	 * @param view
	 *              View of the item that has been clicked
	 */
	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.bt_MyAirCon:
				/** Intent to start MyAircon application */
				Intent airConIntent = getPackageManager().getLaunchIntentForPackage("tk.giesecke.myaircon");
				startActivity(airConIntent);
				break;
			case R.id.bt_spMonitor:
				/** Intent to start spMonitor application */
				Intent spMonitorIntent = getPackageManager().getLaunchIntentForPackage("tk.giesecke.spmonitor");
				startActivity(spMonitorIntent);
				break;
			case R.id.dot_alarm_status:
				if (hasAlarmOn) {
					ivAlarmStatus.setImageDrawable(getResources().getDrawable(R.mipmap.ic_dot));
					new callESP().execute("/?a=0");
				} else {
					ivAlarmStatus.setImageDrawable(getResources().getDrawable(R.mipmap.ic_dot_orange));
					new callESP().execute("/?a=1");
				}
				break;
			case R.id.graph_ac:
			case R.id.graph_sec:
				initChart();
				break;
		}
	}

	/**
	 * Listener for clicks in ListView for alarm sound selection
	 *
	 * @param parent
	 *              AdapterView of alert dialog
	 * @param view
	 *              View of ListView
	 * @param position
	 *              Position in ListView that has been clicked
	 * @param id
	 *              ID of item in ListView that has been clicked
	 */
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		notifNameSel = notifNames.get(position);
		notifUriSel = notifUri.get(position);
	}

	/**
	 * Check if UDP receiver service is running
	 *
	 * @param serviceClass
	 *              Service class we want to check if it is running
	 * @return <code>boolean</code>
	 *              True if service is running
	 *              False if service is not running
	 */
	private boolean isMyServiceRunning(Class<?> serviceClass) {
		/** Activity manager for services */
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (serviceClass.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Wrapper to send several parameters to onPostExecute of AsyncTask
	 *
	 * comResult = return string as JSON from the ESP device
	 */
	public class onPostExecuteWrapper {
		/** Returned result from ESP8266 */
		public String comResult; // Result of communication
		/** Command send to the ESP8266 */
		public String cmdReq; // Requested command
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

			result.cmdReq = params[0];
			/** URL to be called */
			String urlString = WEB_SERVER_URL + params[0]; // URL to call

			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "callESP = " + urlString);

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
							result.comResult = appContext.getString(R.string.err_esp);
						}
						if (result.comResult.equalsIgnoreCase("")) {
							result.comResult = appContext.getString(R.string.err_esp);
						}
						return result;
					} catch (NullPointerException en) {
						result.comResult = getResources().getString(R.string.err_no_esp);
						return result;
					}
				}
			}

			if (result.comResult.equalsIgnoreCase("")) {
				result.comResult = appContext.getString(R.string.err_esp);
			}
			return result;
		}

		protected void onPostExecute(onPostExecuteWrapper result) {
			activityUpdate(result);
		}
	}

	/**
	 * Update UI with values received from ESP device
	 *
	 * @param result
	 * 		result sent by onPostExecute
	 */
	private void activityUpdate(final onPostExecuteWrapper result) {
		runOnUiThread(new Runnable() {
			@SuppressWarnings("deprecation")
			@Override
			public void run() {
				/** String used for temporary conversions */
				String tempString;
				if (UDPlistener.isJSONValid(result.comResult)) {
					/** JSON object to hold the result received from the ESP8266 */
					JSONObject jsonResult;
					try {
						jsonResult = new JSONObject(result.comResult);
						try {
							tempString = jsonResult.getString("device");
							if (!tempString.startsWith("sf")) { // Broadcast not from security
								return;
							}
						} catch (Exception ignore) {
							if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing device in JSON "+ jsonResult);
						}

						if (result.cmdReq.equalsIgnoreCase("/?s")) { // Status request
							/** String to hold complete status in viewable form */
							String message;
							// Get device status and light status and add it to viewable status
							message = getDeviceStatus(jsonResult) + getLightStatus(jsonResult);
							try {
								tempString = jsonResult.getString("ssid");
								message +=  "SSID: " + tempString + "\n";
							} catch (JSONException e) {
								if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing ssid in JSON object" + e.getMessage());
							}
							try {
								tempString = jsonResult.getString("ip");
								message += "IP: " + tempString + "\n";
							} catch (JSONException e) {
								if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing ip in JSON object" + e.getMessage());
							}
							try {
								tempString = jsonResult.getString("mac");
								message += "MAC: " + tempString + "\n";
							} catch (JSONException e) {
								if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing mac in JSON object" + e.getMessage());
							}
							try {
								tempString = jsonResult.getString("sketch");
								message += "Sketch size: " + tempString + "\n";
							} catch (JSONException e) {
								if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing sketch in JSON object" + e.getMessage());
							}
							try {
								tempString = jsonResult.getString("freemem");
								message += "Free Memory: " + tempString + "\n";
							} catch (JSONException e) {
								if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing freemem in JSON object" + e.getMessage());
							}

							tvDebug.setText(message);
						} else { // Change of alarm status
							try {
								tempString = jsonResult.getString("result");
								tvDebug.setText(tempString);
							} catch (JSONException e) {
								if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing result in JSON object" + e.getMessage());
								tvDebug.setText(getString(R.string.err_unknown));
							}

						}
					} catch (JSONException e) {
						if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Create JSONObject from String failed " + e.getMessage());
					}
				}
				initChart();
			}
		});
	}

	/**
	 * Get list of all available notification tones
	 *
	 * @param jsonResult
	 *              JSON object with the status received from the ESP8266
	 * @return <code>String message</code>
	 *              String with the status in viewable format
	 */
	private String getDeviceStatus(JSONObject jsonResult) {
		/** Device ID */
		String deviceIDString;
		/** Menu item to show if alarm is enabled or not */
		MenuItem alarmMenu = null;
		/** String with the device related status */
		String message = "";

		if (abMenu != null) { // Menu might not be available yet
			/** Menu item to toggle alarm on/off */
			alarmMenu = abMenu.getItem(0);
		}
		try {
			deviceIDString = jsonResult.getString("device");
		} catch (JSONException e) {
			if (BuildConfig.DEBUG)
				Log.d(DEBUG_LOG_TAG, "Missing deviceID in JSON object" + e.getMessage());
			deviceIDString = "unknown";
		}
		try {
			if (jsonResult.getInt("alarm") == 1) {
				message = "Intruder! from " + deviceIDString + "\n";
				animator.start();
			} else {
				message = "No detection at " + deviceIDString + "\n";
				animator.end();
				ivAlarmOn.setAlpha(0f);
			}
		} catch (JSONException e) {
			if (BuildConfig.DEBUG)
				Log.d(DEBUG_LOG_TAG, "Missing alarm status in JSON object" + e.getMessage());
		}
		try {
			if (jsonResult.getInt("alarm_on") == 1) {
				message += "Alarm active\n";
				if (alarmMenu != null) { // Menu might not be available yet
					alarmMenu.setTitle(R.string.bt_txt_alarm_off);
					alarmMenu.setIcon(R.drawable.ic_alarm_off);
				}
				ivAlarmStatus.setImageDrawable(getResources().getDrawable(R.mipmap.ic_dot_orange));
				hasAlarmOn = true;
			} else {
				message += "Alarm not active\n";
				if (alarmMenu != null) { // Menu might not be available yet
					alarmMenu.setTitle(R.string.bt_txt_alarm_on);
					alarmMenu.setIcon(R.drawable.ic_alarm_on);
				}
				ivAlarmStatus.setImageDrawable(getResources().getDrawable(R.mipmap.ic_dot));
				hasAlarmOn = false;
			}
		} catch (JSONException e) {
			if (BuildConfig.DEBUG)
				Log.d(DEBUG_LOG_TAG, "Missing alarm setting in JSON object" + e.getMessage());
		}
		try {
			if (jsonResult.getInt("light_on") == 1) {
				message += "Light active\n";
				ivLightStatus.setImageDrawable(getResources().getDrawable(R.mipmap.ic_dot_green));
			} else {
				message += "Light not active\n";
				ivLightStatus.setImageDrawable(getResources().getDrawable(R.mipmap.ic_dot));
			}
		} catch (JSONException e) {
			if (BuildConfig.DEBUG)
				Log.d(DEBUG_LOG_TAG, "Missing light setting in JSON object" + e.getMessage());
		}
		try {
			if (jsonResult.getInt("boot") != 0) {
				message += "Device restarted!\n";
			}
		} catch (JSONException e) {
			if (BuildConfig.DEBUG)
				Log.d(DEBUG_LOG_TAG, "Missing boot info in JSON object" + e.getMessage());
		}
		try {
			message += "Signal = " + jsonResult.getInt("rssi") + " dB\n";
		} catch (JSONException e) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing rssi in JSON object" + e.getMessage());
		}
		try {
			message += "Debug: " + jsonResult.getString("reboot") + "\n";
		} catch (JSONException e) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing rssi in JSON object" + e.getMessage());
		}


		return message;
	}

	/**
	 * Get light related status from the JSON object received from the ESP8266
	 *
	 * @param jsonResult
	 *              JSON object with the status received from the ESP8266
	 * @return <code>String message</code>
	 *              String with the status in viewable format
	 */
	private String getLightStatus(JSONObject jsonResult) {
		/** Light value measured by TSL2561 connected to the ESP8266 */
		long lightValueLong;
		/** Light value measured by the LDR connected to the ESP8266 */
		int ldrValueInt;
		try {
			lightValueLong = jsonResult.getLong("light_val");
		} catch (JSONException e) {
			if (BuildConfig.DEBUG)
				Log.d(DEBUG_LOG_TAG, "Missing light value in JSON object" + e.getMessage());
			lightValueLong = 0;
		}
		try {
			ldrValueInt = jsonResult.getInt("ldr_val");
		} catch (JSONException e) {
			if (BuildConfig.DEBUG)
				Log.d(DEBUG_LOG_TAG, "Missing light value in JSON object" + e.getMessage());
			ldrValueInt = 0;
		}
		/** String with the light related status */
		String message = "";
		if (lightValueLong != 0) {
			message += "Light = " + lightValueLong + " lux\n";
		}
		if (ldrValueInt != 0) {
			message += "LDR = " + ldrValueInt + "\n";
		}
		return message;
	}

	/**
	 * Broadcast receiver for notifications received over UDP or GCM
	 */
	private final BroadcastReceiver activityReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			/** Message received over UDP or GCM */
			String message = intent.getStringExtra("message");

			/** Return values for onPostExecute */
			onPostExecuteWrapper result = new onPostExecuteWrapper();

			result.comResult = message;
			result.cmdReq = "/?s";
			activityUpdate(result);
		}
	};

	/**
	 * Check if Google Play Service is installed on the device
	 * @return <code>boolean</code>
	 *              true if Google Play Service is installed
	 *              false if not
	 */
	private boolean isGooglePlayInstalled() {
		int resultCode = GooglePlayServicesUtil
				.isGooglePlayServicesAvailable(this);
		if (resultCode != ConnectionResult.SUCCESS) {
			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
				GooglePlayServicesUtil.getErrorDialog(resultCode, this,
						ACTION_PLAY_SERVICES_DIALOG).show();
			} else {
				Log.d(DEBUG_LOG_TAG, "Google Play Service is not installed");
				finish();
			}
			return false;
		}
		return true;

	}

	/**
	 * Handle GCM registration messages
	 *
	 * @param msg
	 * 		result from GCM communication
	 */
	private void handleMessage(final int msg) {
		runOnUiThread(new Runnable() {
			@SuppressWarnings("deprecation")
			@Override
			public void run() {
				/** String for display the result of the registration process*/
				String debugTxt;
				switch (msg) {
					case MSG_REGISTER_WITH_GCM:
						new GCMRegistrationTask().execute();
						break;
					case MSG_REGISTER_WEB_SERVER:
						new WebServerRegistrationTask().execute();
						break;
					case MSG_REGISTER_WEB_SERVER_SUCCESS:
						debugTxt = "registered with web server";
						tvDebug.setText(debugTxt);
						break;
					case MSG_REGISTER_WEB_SERVER_FAILURE:
						debugTxt = "registration with web server failed";
						tvDebug.setText(debugTxt);
						break;
				}
			}
		});
	}

	/**
	 * Get GCM registration ID from GCM server
	 */
	private class GCMRegistrationTask extends AsyncTask<Void, Void, String> {

		@Override
		protected String doInBackground(Void... params) {
			if (gcm == null && isGooglePlayInstalled()) {
				gcm = GoogleCloudMessaging.getInstance(appContext);
			}
			try {
				if (gcm != null) {
					gcmRegId = gcm.register(GCM_SENDER_ID);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			return gcmRegId;
		}

		@Override
		protected void onPostExecute(String result) {
			if (result != null) {
				/** String for display the result of the registration */
				String debugTxt = "registered with GCM " + result;
				tvDebug.setText(debugTxt);
				prefs.edit().putString(PREF_GCM_REG_ID, result).apply();
				handleMessage(MSG_REGISTER_WEB_SERVER);
			}
		}
	}

	/**
	 * Contact web server on ESP8266 to save GCM registration ID
	 */
	private class WebServerRegistrationTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			/** URL to contact */
			URL url;
			// Build url to register GCM ID
			/** String holding the URL */
			String espURL = WEB_SERVER_URL + "/?regid=" + gcmRegId;
			try {
				url = new URL(espURL);
			} catch (MalformedURLException e) {
				if (BuildConfig.DEBUG) e.printStackTrace();
				handleMessage(MSG_REGISTER_WEB_SERVER_FAILURE);
				return null;
			}

			/** HttpURLConnection to connect to ESP8266 */
			HttpURLConnection conn = null;
			try {
				conn = (HttpURLConnection) url.openConnection();
				conn.setDoOutput(true);
				conn.setUseCaches(false);
				conn.setRequestMethod("GET");
				conn.setRequestProperty("Content-Type",
						"application/x-www-form-urlencoded;charset=UTF-8");

				int status = conn.getResponseCode();
				if (status == 200) {
					// Request success
					handleMessage(MSG_REGISTER_WEB_SERVER_SUCCESS);
				} else {
					throw new IOException("Request failed with error code "
							+ status);
				}
			} catch (IOException io) {
				if (BuildConfig.DEBUG) io.printStackTrace();
				handleMessage(MSG_REGISTER_WEB_SERVER_FAILURE);
			} catch (NullPointerException npe) {
				handleMessage(MSG_REGISTER_WEB_SERVER_FAILURE);
			} finally {
				if (conn != null) {
					conn.disconnect();
				}
			}

			return null;
		}
	}
	/**
	 * Get list of all available notification tones
	 *
	 * @param context
	 *              application context
	 * @param notifNames
	 *              array list to store the name of the tones
	 * @param notifUri
	 *              array list to store the paths of the tones
	 * @return <code>int uriIndex</code>
	 *              URI of user selected alarm sound
	 */
	private static int getNotifSounds(Context context, ArrayList<String> notifNames, ArrayList<String> notifUri) {
		/** Instance of the ringtone manager */
		RingtoneManager manager = new RingtoneManager(context);
		manager.setType(RingtoneManager.TYPE_NOTIFICATION);
		/** Cursor with the notification tones */
		Cursor cursor = manager.getCursor();
		/** Access to shared preferences of application*/
		SharedPreferences mPrefs = context.getSharedPreferences("Security", 0);
		/** Last user selected alarm tone */
		String lastUri = mPrefs.getString("alarmUri","");
		/** Index of lastUri in the list */
		int uriIndex = -1;

		while (cursor.moveToNext()) {
			notifNames.add(cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX));
			notifUri.add(cursor.getString(RingtoneManager.URI_COLUMN_INDEX) + "/" +
					cursor.getString(RingtoneManager.ID_COLUMN_INDEX));
			if (lastUri.equalsIgnoreCase(cursor.getString(RingtoneManager.URI_COLUMN_INDEX) + "/" +
					cursor.getString(RingtoneManager.ID_COLUMN_INDEX))) {
				uriIndex = cursor.getPosition();
			}
		}
		return uriIndex;
	}

	/**
	 * Initialize charts
	 */
	private void initChart() {
		initSecChart();
		initACChart();
	}

	/**
	 * Initialize chart to show data from security device
	 */
	private void initSecChart() {

		/** List to hold the timestamps for the chart */
		ArrayList<String> timeSeries = new ArrayList<>();
		/** List to hold the ldr values */
		ArrayList<Entry> ldrSeries = new ArrayList<>();
		/** List to hold the alarm status */
		ArrayList<Entry> alarmSeries = new ArrayList<>();
		/** List to hold the alarm on status */
		ArrayList<Entry> alarmOnSeries = new ArrayList<>();
		/** List to hold the switch light status */
		ArrayList<Entry> switchLightsSeries = new ArrayList<>();
		/** List to hold the rssi strength */
		final ArrayList<Entry> rssiSeries = new ArrayList<>();

		// Pointer to the chart in the layout
		/* MPAndroid chart view for the current chart */
		LineChart lineChart = (LineChart) findViewById(R.id.graph_sec);

		timeSeries.clear();
		ldrSeries.clear();
		alarmSeries.clear();
		alarmOnSeries.clear();
		switchLightsSeries.clear();
		rssiSeries.clear();

		/** Instance of DataBaseHelper */
		DataBaseHelper myDBhelper = new DataBaseHelper(getApplicationContext());
		/** Instance of data base */
		SQLiteDatabase myDataBase = myDBhelper.getReadableDatabase();
		/** Cursor with data from database */
		Cursor dbCursor = DataBaseHelper.getDeviceEntries(myDataBase,"sf1");
		if (dbCursor != null) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "SF1 count = " + dbCursor.getCount());
			dbCursor.moveToFirst();
			for (int cursorIndex=0; cursorIndex<dbCursor.getCount(); cursorIndex++) {
				timeSeries.add(dbCursor.getString(17));
				if (dbCursor.getInt(6) != 0) {
					ldrSeries.add(new Entry(dbCursor.getInt(6),cursorIndex));
				} else {
					if (cursorIndex != 0) {
						ldrSeries.add(ldrSeries.get(cursorIndex-1));
					} else {
						ldrSeries.add(new Entry(0,cursorIndex));
					}
				}
				if (dbCursor.getInt(1) == 1) {
					alarmSeries.add(new Entry(500, cursorIndex));
				} else if (dbCursor.getInt(1) == 0) {
					alarmSeries.add(new Entry(0, cursorIndex));
				}
				if (dbCursor.getInt(2) == 1) {
					alarmOnSeries.add(new Entry(400, cursorIndex));
				} else if (dbCursor.getInt(2) == 0) {
					alarmOnSeries.add(new Entry(0, cursorIndex));
				}
				if (dbCursor.getInt(3) == 1) {
					switchLightsSeries.add(new Entry(300, cursorIndex));
				} else if (dbCursor.getInt(3) == 0) {
					switchLightsSeries.add(new Entry(0, cursorIndex));
				}
				if (dbCursor.getInt(7) != 0) {
					rssiSeries.add(new Entry(Math.abs(dbCursor.getInt(7)*10), cursorIndex));
				} else {
					if (cursorIndex != 0) {
						rssiSeries.add(rssiSeries.get(cursorIndex-1));
					} else {
						rssiSeries.add(new Entry(0, cursorIndex));
					}
				}
				dbCursor.moveToNext();
			}
			dbCursor.close();
		}
		myDataBase.close();
		myDBhelper.close();

		/** Line data set for ldr data */
		LineDataSet ldr = new LineDataSet(ldrSeries, "LDR");
		/** Line data set for ldr data */
		LineDataSet alarm = new LineDataSet(alarmSeries, "Alarm");
		/** Line data set for alarmOn data */
		LineDataSet alarmOn = new LineDataSet(alarmOnSeries, "Alarm on/off");
		/** Line data set for switchLights data */
		LineDataSet switchLights = new LineDataSet(switchLightsSeries, "Lights on/off");
		/** Line data set for rssi data */
		LineDataSet rssi = new LineDataSet(rssiSeries, "Signal");

		// Configure LDR data
		ldr.setLineWidth(1.75f);
		ldr.setCircleSize(0f);
		ldr.setColor(0xFFFFBB33);
		ldr.setCircleColor(0xFFFFBB33);
		ldr.setHighLightColor(0xFFFFBB33);
		ldr.setFillColor(0xAAFFBB33);
		ldr.setVisible(true);
		ldr.setDrawValues(false);
		//ldr.setDrawFilled(true);

		// Configure Alarm data
		alarm.setLineWidth(1.75f);
		alarm.setCircleSize(0f);
		alarm.setColor(Color.RED);
		alarm.setCircleColor(Color.RED);
		alarm.setHighLightColor(Color.RED);
		alarm.setFillColor(0xAAFF0000);
		alarm.setVisible(true);
		alarm.setDrawValues(false);
		//alarm.setDrawFilled(true);

		// Configure alarm enabled data
		alarmOn.setLineWidth(1.75f);
		alarmOn.setCircleSize(0f);
		alarmOn.setColor(0xFFFF7F50);
		alarmOn.setCircleColor(0xFFFF7F50);
		alarmOn.setHighLightColor(0xFFFF7F50);
		alarmOn.setFillColor(0xAAFF7F50);
		alarmOn.setVisible(true);
		alarmOn.setDrawValues(false);
		//alarmOn.setDrawFilled(true);

		// Configure light enabled data
		switchLights.setLineWidth(1.75f);
		switchLights.setCircleSize(0f);
		switchLights.setColor(0xFFFFFAF0);
		switchLights.setCircleColor(0xFFFFFAF0);
		switchLights.setHighLightColor(0xFFFFFAF0);
		switchLights.setFillColor(0xAAFFFAF0);
		switchLights.setVisible(true);
		switchLights.setDrawValues(false);
		//switchLights.setDrawFilled(true);

		// Configure signal data
		rssi.setLineWidth(1.75f);
		rssi.setCircleSize(0f);
		rssi.setColor(0xFF00FF00);
		rssi.setCircleColor(0xFF00FF00);
		rssi.setHighLightColor(0xFF00FF00);
		rssi.setFillColor(0xAA00FF00);
		rssi.setVisible(true);
		rssi.setDrawValues(false);
		//rssi.setDrawFilled(true);

		/** Data set with data for the plots */
		ArrayList<ILineDataSet> dataSets = new ArrayList<>();
		dataSets.add(ldr);
		dataSets.add(alarm);
		dataSets.add(alarmOn);
		dataSets.add(switchLights);
		dataSets.add(rssi);

		/** Data object with the data set and the y values */
		/* LineData for the plot */
		LineData plotData = new LineData(timeSeries, dataSets);

		lineChart.setBackgroundColor(Color.BLACK);
		lineChart.setDrawGridBackground(false);
		lineChart.setTouchEnabled(true);
		lineChart.setDragEnabled(true);
		lineChart.setAutoScaleMinMaxEnabled(true);
		lineChart.setData(plotData);

		/** Instance of left y axis */
		YAxis lYAx = lineChart.getAxisLeft();
		lYAx.setEnabled(true);
		lYAx.setTextColor(Color.WHITE);
		lYAx.setStartAtZero(false);
		lYAx.setSpaceTop(1);
		lYAx.setSpaceBottom(1);

		/** Instance of x axis */
		XAxis xAx = lineChart.getXAxis();
		xAx.setEnabled(true);
		xAx.setTextColor(Color.WHITE);
		xAx.setPosition(XAxis.XAxisPosition.BOTTOM);

		//lineChart.getLegend().setEnabled(false);
		lineChart.getLegend().setTextColor(Color.WHITE);

		// let the chart know it's data has changed
		lineChart.notifyDataSetChanged();
		lineChart.invalidate();
	}

	/**
	 * Initialize chart to show data from aircon remote
	 */
	private void initACChart() {

		/** List to hold the timestamps for the chart */
		ArrayList<String> timeSeries = new ArrayList<>();
		/** List to hold the power status */
		ArrayList<Entry> powerSeries = new ArrayList<>();
		/** List to hold the consumption values */
		ArrayList<Entry> consSeries = new ArrayList<>();
		/** List to hold the status */
		ArrayList<Entry> statusSeries = new ArrayList<>();
		/** List to hold the auto status */
		ArrayList<Entry> autoSeries = new ArrayList<>();

		// Pointer to the chart in the layout
		/* MPAndroid chart view for the current chart */
		LineChart lineChart = (LineChart) findViewById(R.id.graph_ac);

		timeSeries.clear();
		powerSeries.clear();
		consSeries.clear();
		statusSeries.clear();
		autoSeries.clear();

		/** Instance of DataBaseHelper */
		DataBaseHelper myDBhelper = new DataBaseHelper(getApplicationContext());
		/** Instance of data base */
		SQLiteDatabase myDataBase = myDBhelper.getReadableDatabase();
		/** Cursor with data from database */
		Cursor dbCursor = DataBaseHelper.getDeviceEntries(myDataBase, "fd1");
		if (dbCursor != null) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "FD1 count = " + dbCursor.getCount());
			dbCursor.moveToFirst();
			for (int cursorIndex=0; cursorIndex<dbCursor.getCount(); cursorIndex++) {
				timeSeries.add(dbCursor.getString(17));
				if (dbCursor.getInt(9) == 1) {
					powerSeries.add(new Entry(400, cursorIndex));
				} else if (dbCursor.getInt(9) == 0) {
					powerSeries.add(new Entry(0, cursorIndex));
				}
				if (dbCursor.getInt(13) != 0) {
					consSeries.add(new Entry(dbCursor.getInt(13), cursorIndex));
				} else {
					if (cursorIndex != 0) {
						consSeries.add(consSeries.get(cursorIndex-1));
					} else {
						consSeries.add(new Entry(0, cursorIndex));
					}
				}
				if (dbCursor.getInt(14) != 0) {
					statusSeries.add(new Entry(dbCursor.getInt(14)*150, cursorIndex));
				} else {
					if (cursorIndex != 0) {
						statusSeries.add(statusSeries.get(cursorIndex-1));
					} else {
						statusSeries.add(new Entry(0, cursorIndex));
					}
				}
				if (dbCursor.getInt(15) == 1) {
					autoSeries.add(new Entry(200, cursorIndex));
				} else if (dbCursor.getInt(15) == 0) {
					autoSeries.add(new Entry(0, cursorIndex));
				}
				dbCursor.moveToNext();
			}
			dbCursor.close();
		}
		myDataBase.close();
		myDBhelper.close();

		/** Line data set for power data */
		LineDataSet power = new LineDataSet(powerSeries, "Power");
		/** Line data set for cons data */
		LineDataSet cons = new LineDataSet(consSeries, "Consumption");
		/** Line data set for status data */
		LineDataSet status = new LineDataSet(statusSeries, "Status");
		/** Line data set for auto data */
		LineDataSet auto = new LineDataSet(autoSeries, "Auto mode");

		power.setLineWidth(1.75f);
		power.setCircleSize(0f);
		power.setColor(Color.RED);
		power.setCircleColor(Color.RED);
		power.setHighLightColor(Color.RED);
		power.setFillColor(0xAAFF0000);
		power.setVisible(true);
		power.setDrawValues(false);
		//power.setDrawFilled(true);

		cons.setLineWidth(1.75f);
		cons.setCircleSize(0f);
		cons.setColor(0xFFFF7F50);
		cons.setCircleColor(0xFFFF7F50);
		cons.setHighLightColor(0xFFFF7F50);
		cons.setFillColor(0xAAFF7F50);
		cons.setVisible(true);
		cons.setDrawValues(false);
		//cons.setDrawFilled(true);

		status.setLineWidth(1.75f);
		status.setCircleSize(0f);
		status.setColor(0xFF0000CD);
		status.setCircleColor(0xFF0000CD);
		status.setHighLightColor(0xFF0000CD);
		status.setFillColor(0xAA0000CD);
		status.setVisible(true);
		status.setDrawValues(false);
		//status.setDrawFilled(true);

		auto.setLineWidth(1.75f);
		auto.setCircleSize(0f);
		auto.setColor(0xFF800080);
		auto.setCircleColor(0xFF800080);
		auto.setHighLightColor(0xFF800080);
		auto.setFillColor(0xAA800080);
		auto.setVisible(true);
		auto.setDrawValues(false);
		//auto.setDrawFilled(true);

		/** Data set with data for the plots */
		ArrayList<ILineDataSet> dataSets = new ArrayList<>();
		dataSets.add(power);
		dataSets.add(cons);
		dataSets.add(status);
		dataSets.add(auto);

		/** Data object with the data set and the y values */
		/* LineData for the plot */
		LineData plotData = new LineData(timeSeries, dataSets);

		lineChart.setBackgroundColor(Color.BLACK);
		lineChart.setDrawGridBackground(false);
		lineChart.setTouchEnabled(true);
		lineChart.setDragEnabled(true);
		lineChart.setAutoScaleMinMaxEnabled(true);
		lineChart.setData(plotData);

		/** Instance of left y axis */
		YAxis lYAx = lineChart.getAxisLeft();
		lYAx.setEnabled(true);
		lYAx.setTextColor(Color.WHITE);
		lYAx.setStartAtZero(false);
		lYAx.setSpaceTop(1);
		lYAx.setSpaceBottom(1);

		/** Instance of x axis */
		XAxis xAx = lineChart.getXAxis();
		xAx.setEnabled(true);
		xAx.setTextColor(Color.WHITE);
		xAx.setPosition(XAxis.XAxisPosition.BOTTOM);

		//lineChart.getLegend().setEnabled(false);
		lineChart.getLegend().setTextColor(Color.WHITE);

		// let the chart know it's data has changed
		lineChart.notifyDataSetChanged();
		lineChart.invalidate();
	}
}
