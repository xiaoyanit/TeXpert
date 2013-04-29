package lah.texpert.fragments;

import lah.texpert.R;
import android.os.Bundle;
import android.os.FileObserver;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class LogViewFragment extends Fragment {

	public static LogViewFragment newInstance() {
		LogViewFragment fragment = new LogViewFragment();
		return fragment;
	}

	/**
	 * Observers to watch for changes in *.log and *.bbl
	 */
	FileObserver log_observer;

	public LogViewFragment() {
		// Required empty public constructor
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// Inflate the layout for this fragment
		return inflater.inflate(R.layout.fragment_log_view, container, false);
	}

}
