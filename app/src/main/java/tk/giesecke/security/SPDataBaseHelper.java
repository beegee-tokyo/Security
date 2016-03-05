package tk.giesecke.security;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

/** spMonitor - DataBaseHelper
 *
 * Access to the spMonitor database
 *
 * @author Bernd Giesecke
 * @version 0.2 beta August 19, 2015.
 */
class SPDataBaseHelper extends SQLiteOpenHelper {

	/** Name of the database of current month*/
	public static final String DATABASE_NAME="spMonitor";
	/** Name of the database of */
	public static final String DATABASE_NAME_LAST="spLast";
	/** Name of the table */
	private static final String TABLE_NAME = "s";

	public SPDataBaseHelper(Context context, String dbName) {
		super(context, dbName, null, 1);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {

		db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
				"year INTEGER, month INTEGER, day INTEGER, hour INTEGER, minute INTEGER, " +
				"solar DOUBLE, cons DOUBLE, light LONG, " +
				"id INTEGER PRIMARY KEY AUTOINCREMENT);");
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
	 * @param recordLine
	 *            String with a record
	 *            format: yy,mm,dd,hh:mm,light,solar,consumption
	 *            e.g.: "15,08,13,13:54,35000,613.456,-120.22"
	 */
	public static void addDay(SQLiteDatabase db, String recordLine) {

		/* Parse the string into its single values */
		String[] valuesPerLine = recordLine.split(",");

		if (valuesPerLine.length == 1) {
			return;
		}
		/** String list with hour & minute values */
		String[] hourSplit = valuesPerLine[3].split(":");

		/** ContentValues to hold the measured and calculated values to be added to the database */
		ContentValues values = new ContentValues(14);
		values.put("year", Integer.parseInt(valuesPerLine[0]));
		values.put("month", Integer.parseInt(valuesPerLine[1]));
		values.put("day", Integer.parseInt(valuesPerLine[2]));
		values.put("hour", Integer.parseInt(hourSplit[0]));
		values.put("minute", Integer.parseInt(hourSplit[1]));
		values.put("solar", Double.parseDouble(valuesPerLine[5]));
		values.put("cons", Double.parseDouble(valuesPerLine[6]));
		values.put("light", Long.parseLong(valuesPerLine[4]));

		db.insert(TABLE_NAME, null, values);
	}

	/**
	 * Read data of day "dayNumber" and returns the data as a cursor
	 *
	 * @param db
	 *            pointer to database
	 * @param dayNumber
	 *            the day we want to read (1-31)
	 * @param monthSelected
	 *            the month we want to read from
	 * @param yearSelected
	 *            the year we want to read from
	 * @return <code>Cursor</code> dayStamp
	 *            Cursor with all database entries matching with dayNumber
	 *            Entry per minute is
	 *            cursor[0] = year stamp
	 *            cursor[1] = month stamp
	 *            cursor[2] = day stamp
	 *            cursor[3] = hour stamp
	 *            cursor[4] = minute stamp
	 *            cursor[5] = sensor power
	 *            cursor[6] = consumed power
	 *            cursor[7] = light value
	 */
	public static Cursor getDay(SQLiteDatabase db, int dayNumber, int monthSelected, int yearSelected) {
		return db.rawQuery("select * from " + TABLE_NAME + " where day= " + String.valueOf(dayNumber) + " and month= "
				+ String.valueOf(monthSelected) + " and year= " + String.valueOf(yearSelected), null);
	}

	/**
	 * Get specific row values from the database,
	 * e.g. all years stored in the database or
	 *      all months of a year stored in the database or
	 *      all days in a month of a year stored in the database
	 *
	 * @param db
	 *            pointer to database
	 * @param requestField
	 *            requested row from db
	 *            "year" returns all year values found
	 *            "month" returns all month values found in year <code>requestLimiterYear</code>
	 *            "day" returns all day values found in month <code>requestLimiterMonth</code>
	 *                                              and year <code>requestLimiterYear</code>
	 * @param requestLimiterMonth
	 *            limiter for request
	 *            unused if requestField is "year"
	 *            unused if requestField is "month"
	 *            month if requestField is "day"
	 * @param requestLimiterYear
	 *            limiter for request
	 *            unused if requestField is "year"
	 *            year if requestField is "month"
	 *            year if requestField is "day"
	 *
	 * @return <code>ArrayList<Integer></code>
	 *            array list with all entries found
	 */
	public static ArrayList<Integer> getEntries(SQLiteDatabase db,
	                                            String requestField, int requestLimiterMonth, int requestLimiterYear) {

		/** Array list holding the found values */
		ArrayList<Integer> returnArray = new ArrayList<>();

		/** Limiter for row search */
		String queryRequest = "select distinct " + requestField + " from " + TABLE_NAME;
		if (requestField.equalsIgnoreCase("day")) {
			queryRequest += " where month = " + String.valueOf(requestLimiterMonth) +
					" and year = " + String.valueOf(requestLimiterYear);
		} else if (requestField.equalsIgnoreCase("month")) {
			queryRequest += " where year = " + String.valueOf(requestLimiterYear);
		}

		/** Cursor holding the records of a day */
		Cursor allRows = db.rawQuery(queryRequest, null);

		allRows.moveToFirst();
		for (int i=0; i<allRows.getCount(); i++) {
			returnArray.add(allRows.getInt(0));
			allRows.moveToNext();
		}
		allRows.close();
		return returnArray;
	}

	/**
	 * Read data of the last database entry and returns the data as a cursor
	 *
	 * @param db
	 *            pointer to database
	 * @return <code>Cursor</code> dayStamp
	 *            Cursor with the data of the last row
	 *            Entry is
	 *            cursor[0] = year stamp
	 *            cursor[1] = month stamp
	 *            cursor[2] = day stamp
	 *            cursor[3] = hour stamp
	 *            cursor[4] = minute stamp
	 *            cursor[5] = sensor power
	 *            cursor[6] = consumed power
	 *            cursor[7] = light value
	 */
	public static Cursor getLastRow(SQLiteDatabase db) {
		return db.rawQuery("select * from " + TABLE_NAME + " order by id desc LIMIT 1", null);
	}
}
