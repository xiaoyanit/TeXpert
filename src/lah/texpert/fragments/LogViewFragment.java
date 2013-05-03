package lah.texpert.fragments;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.texpert.R;
import android.os.Bundle;
import android.os.FileObserver;
import android.support.v4.app.Fragment;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
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

	private static final Pattern error_pattern = Pattern.compile("! (.*)"), line_num_pattern = Pattern
			.compile("((l\\.|line |lines )\\s*(\\d+))[^\\d].*"), warning_pattern = Pattern
			.compile("(((! )?(La|pdf)TeX)|Package) .*Warning.*:(.*)|(Over|Under)(full \\\\[hv]box .*)");

	private static final ForegroundColorSpan ERROR_STYLE = new ForegroundColorSpan(0xffff0000),
			WARNING_STYLE = new ForegroundColorSpan(0xff00cc00), LINE_NUM_STYLE = new ForegroundColorSpan(0xff0000cc);

	public static LogViewFragment newInstance() {
		LogViewFragment fragment = new LogViewFragment();
		return fragment;
	}

	/**
	 * Log file to bind to
	 */
	private File log_file;

	private String log_file_name;

	private TextView log_textview;

	/**
	 * Observers to watch for changes in the source directory
	 */
	private FileObserver src_dir_observer;

	public LogViewFragment() {
		// Required empty public constructor
	}

	void loadLog() {
		final byte[] buffer = new byte[(int) log_file.length()];
		try {
			InputStream str = new FileInputStream(log_file);
			str.read(buffer);
			str.close();
			String logstr = new String(buffer, 0, buffer.length);
			final SpannableStringBuilder log_content = new SpannableStringBuilder(logstr);
			Matcher error_matcher = error_pattern.matcher(logstr);
			while (error_matcher.find()) {
				log_content.setSpan(CharacterStyle.wrap(ERROR_STYLE), error_matcher.start(), error_matcher.end(),
						Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
			}
			Matcher warning_matcher = warning_pattern.matcher(logstr);
			while (warning_matcher.find()) {
				log_content.setSpan(CharacterStyle.wrap(WARNING_STYLE), warning_matcher.start(), warning_matcher.end(),
						Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
			}
			Matcher line_num_matcher = line_num_pattern.matcher(logstr);
			while (line_num_matcher.find()) {
				log_content.setSpan(CharacterStyle.wrap(LINE_NUM_STYLE), line_num_matcher.start(1),
						line_num_matcher.end(1), 0);
			}
			log_textview.post(new Runnable() {

				@Override
				public void run() {
					log_textview.setText(log_content);
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
				if (path != null && log_file_name != null && path.equals(log_file_name))
					loadLog();
			}
		};
		src_dir_observer.startWatching();
	}

	public void stopTracking() {
		if (src_dir_observer != null)
			src_dir_observer.stopWatching();
		log_textview.post(new Runnable() {

			@Override
			public void run() {
				log_textview.setText("");
			}
		});
	}

}
