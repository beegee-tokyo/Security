package tk.giesecke.security;

import android.os.AsyncTask;

public class UpdateRemoteDB extends AsyncTask{
	/**
	 * Override this method to perform a computation on a background thread. The
	 * specified parameters are the parameters passed to {@link #execute}
	 * by the caller of this task.
	 * <p/>
	 * This method can call {@link #publishProgress} to publish updates
	 * on the UI thread.
	 *
	 * @param params
	 * 		The parameters of the task.
	 * @return A result, defined by the subclass of this task.
	 * @see #onPreExecute()
	 * @see #onPostExecute
	 * @see #publishProgress
	 */
	@Override
	protected Object doInBackground(Object[] params) {
		// First get all data (day by day) from the spMonitor device
		// Second write data into local database (day by day)
		// Third send data to external database (day by day)
		// Save local database as backup in the cloud (tbd where)
		return null;
	}
}
