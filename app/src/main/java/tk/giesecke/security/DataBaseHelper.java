package tk.giesecke.security;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

class DataBaseHelper extends SQLiteOpenHelper {

	/** Name of the database of current month*/
	private static final String DATABASE_NAME="HomeAutomation";
	/** Name of the table */
	private static final String TABLE_NAME = "events";
	/** Debug tag */
	private static final String DEBUG_LOG_TAG = "Security-DB";

	public DataBaseHelper(Context context) {
		super(context, DATABASE_NAME, null, 1);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {

		db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
				"device TEXT, alarm BOOLEAN, alarmOn BOOLEAN, switchLights BOOLEAN, boot BOOLEAN, " +
				"lightVal INTEGER, ldrVal INTEGER, rssi INTEGER, reboot TEXT, " +
				"power BOOLEAN, mode INTEGER, speed INTEGER, temp INTEGER, cons INTEGER, " +
				"status INTEGER, auto BOOLEAN, " +
				"id INTEGER PRIMARY KEY AUTOINCREMENT, timeStamp DATETIME DEFAULT CURRENT_TIMESTAMP);");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		db.execSQL("DROP TABLE IF EXISTS "+ TABLE_NAME);
		onCreate(db);
	}

	/**
	 * Add an entry to the database
	 *
	 * @param db
	 *            pointer to database
	 * @param newEntry
	 *            JSON object with a record
	 */
	public static boolean addData(SQLiteDatabase db, JSONObject newEntry) {

		/** ContentValues to hold the received status to be added to the database */
		ContentValues values = new ContentValues(14);
		try {
			values.put("device", newEntry.getString("device"));
		} catch (Exception ignore) {
			if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Missing device in JSON " + newEntry);
			return false;
		}
		try {
			values.put("alarm", newEntry.getString("alarm"));
		} catch (Exception ignore) {
		}
		try {
			if (newEntry.getInt("alarm_on") == 0) {
				values.put("alarmOn", false);
			} else {
				values.put("alarmOn",true);
			}
		} catch (Exception ignore) {
		}
		try {
			if (newEntry.getInt("light_on") == 0) {
				values.put("switchLights",false);
			} else {
				values.put("switchLights",true);
			}
		} catch (Exception ignore) {
		}
		try {
			if (newEntry.getInt("boot") == 0) {
				values.put("boot",false);
			} else {
				values.put("boot",true);
			}
		} catch (Exception ignore) {
		}
		try {
			values.put("lightVal", newEntry.getInt("light_val"));
		} catch (Exception ignore) {
		}
		try {
			values.put("ldrVal", newEntry.getInt("ldr_val"));
		} catch (Exception ignore) {
		}
		try {
			values.put("rssi", newEntry.getInt("rssi"));
		} catch (Exception ignore) {
		}
		try {
			values.put("reboot", newEntry.getString("reboot"));
		} catch (Exception ignore) {
		}
		try {
			if (newEntry.getInt("power") == 0) {
				values.put("power",false);
			} else {
				values.put("power",true);
			}
		} catch (Exception ignore) {
		}
		try {
			values.put("mode", newEntry.getInt("mode"));
		} catch (Exception ignore) {
		}
		try {
			values.put("speed", newEntry.getInt("speed"));
		} catch (Exception ignore) {
		}
		try {
			values.put("temp", newEntry.getInt("temp"));
		} catch (Exception ignore) {
		}
		try {
			values.put("cons", newEntry.getInt("cons"));
		} catch (Exception ignore) {
		}
		try {
			values.put("status", newEntry.getInt("status"));
		} catch (Exception ignore) {
		}
		try {
			if (newEntry.getInt("auto") == 0) {
				values.put("auto",false);
			} else {
				values.put("auto",true);
			}
		} catch (Exception ignore) {
		}
		values.put("timeStamp", getCurrentTimeStamp());
		return db.insertOrThrow(TABLE_NAME, null, values) != -1;
	}

	/**
	 * Read data of specific device
	 *
	 * @param db
	 *            pointer to database
	 * @param device
	 *            the device we want to get entries from
	 * @return <code>Cursor</code> dayStamp
	 *            Cursor with all database entries matching with dayNumber
	 *            Entry per minute is
	 *            cursor[0]  = String device
	 *            cursor[1]  = Boolean alarm
	 *            cursor[2]  = Boolean alarmOn
	 *            cursor[3]  = Boolean switchLights
	 *            cursor[4]  = Boolean boot
	 *            cursor[5]  = Integer lightVal
	 *            cursor[6]  = Integer ldrVal
	 *            cursor[7]  = Integer rssi
	 *            cursor[8]  = String reboot
	 *            cursor[9]  = Boolean power
	 *            cursor[10] = Integer mode
	 *            cursor[11] = Integer speed
	 *            cursor[12] = Integer temp
	 *            cursor[13] = Integer cons
	 *            cursor[14] = Integer status
	 *            cursor[15] = Boolean auto
	 *            cursor[16] = Integer id
	 *            cursor[17] = String timeStamp
	 */
	public static Cursor getDeviceEntries(SQLiteDatabase db, String device) {
		/** Limiter for row search */
		String queryRequest = "select * from " + TABLE_NAME + " where device = '" + device +"'";
		return db.rawQuery(queryRequest, null);
	}

	/**
	 * Read data of specific device
	 *
	 * @param db
	 *            pointer to database
	 * @return <code>Cursor</code> dayStamp
	 *            Cursor with all database entries matching with dayNumber
	 *            Entry per minute is
	 *            cursor[0]  = String device
	 *            cursor[1]  = Boolean alarm
	 *            cursor[2]  = Boolean alarmOn
	 *            cursor[3]  = Boolean switchLights
	 *            cursor[4]  = Boolean boot
	 *            cursor[5]  = Integer lightVal
	 *            cursor[6]  = Integer ldrVal
	 *            cursor[7]  = Integer rssi
	 *            cursor[8]  = String reboot
	 *            cursor[9]  = Boolean power
	 *            cursor[10] = Integer mode
	 *            cursor[11] = Integer speed
	 *            cursor[12] = Integer temp
	 *            cursor[13] = Integer cons
	 *            cursor[14] = Integer status
	 *            cursor[15] = Boolean auto
	 *            cursor[16] = Integer id
	 *            cursor[17] = String timeStamp
	 */
	public static Cursor getAllEntries(SQLiteDatabase db) {
		/** Limiter for row search */
		String queryRequest = "select * from " + TABLE_NAME;
		return db.rawQuery(queryRequest, null);
	}

	/**
	 * Clear all data from database
	 */
	public static void delAllData(SQLiteDatabase db) {
		db.execSQL("delete from "+ TABLE_NAME);
		db.execSQL("vacuum");
	}
	/**
	 * Get current time as String
	 * @return
	 *      yyyy-MM-dd HH:mm:ss format date as string
	 */
	private static String getCurrentTimeStamp(){
		try {

			@SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

			return dateFormat.format(new Date());
		} catch (Exception e) {
			e.printStackTrace();

			return null;
		}
	}
}
