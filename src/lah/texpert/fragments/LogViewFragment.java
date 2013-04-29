package lah.texpert.fragments;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import lah.texpert.R;
import android.os.Bundle;
import android.os.FileObserver;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Fragment for viewing pdfLaTeX generated log file
 * 
 * @author L.A.H.
 * 
 */
public class LogViewFragment extends Fragment {

	public static LogViewFragment newInstance() {
		LogViewFragment fragment = new LogViewFragment();
		return fragment;
	}

	/**
	 * Log file to display
	 */
	File log_file;

	private String log_file_name;

	TextView log_textview;

	/**
	 * Observers to watch for changes in the source directory
	 */
	FileObserver src_dir_observer;

	public LogViewFragment() {
		// Required empty public constructor
	}

	void loadLog() {
		final byte[] buffer = new byte[(int) log_file.length()];
		try {
			InputStream str = new FileInputStream(log_file);
			str.read(buffer);
			str.close();
			log_textview.post(new Runnable() {

				@Override
				public void run() {
					log_textview.setText(new String(buffer, 0, buffer.length));
				}
			});
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// Inflate the layout for this fragment
		View view = inflater.inflate(R.layout.fragment_log_view, container, false);
		log_textview = (TextView) view.findViewById(R.id.log_textview);
		return view;
	}

	public void startTrackingLogFile(File log_file) {
		if (log_file == null)
			return;

		this.log_file = log_file;
		log_file_name = log_file.getName();

		if (log_file.exists())
			loadLog();
		else
			log_textview.post(new Runnable() {

				@Override
				public void run() {
					log_textview.setText("");
				}
			});

		// Monitor changes to the the directory
		if (src_dir_observer != null)
			src_dir_observer.stopWatching();
		src_dir_observer = new FileObserver(log_file.getParent(), FileObserver.CLOSE_WRITE) {

			@Override
			public void onEvent(int event, String path) {
				// log file is overwritten by external process ==> reload the log
				if (path.equals(log_file_name))
					loadLog();
			}
		};
		src_dir_observer.startWatching();
	}

}
