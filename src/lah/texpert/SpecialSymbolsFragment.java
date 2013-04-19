package lah.texpert;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;

/**
 * Fragment to allow for easy insertion of frequently used TeX symbol characters
 * 
 * @author L.A.H.
 * 
 */
public class SpecialSymbolsFragment extends Fragment {

	private static final String[] special_chars = { "\\", "$", "%", "&", "#", "_", "~", "^", "{}" };

	public static SpecialSymbolsFragment newInstance() {
		SpecialSymbolsFragment fragment = new SpecialSymbolsFragment();
		return fragment;
	}

	private LaTeXEditingActivity activity;

	public SpecialSymbolsFragment() {
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		this.activity = (LaTeXEditingActivity) activity;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		GridView view = (GridView) inflater.inflate(R.layout.fragment_special_symbols, container, false);
		view.setNumColumns(special_chars.length);
		view.setAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, special_chars));
		view.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				// TODO Auto-generated method stub
				activity.insertAtCursor(special_chars[arg2]);
			}
		});
		return view;
	}
}
