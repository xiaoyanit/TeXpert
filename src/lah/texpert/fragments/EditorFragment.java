package lah.texpert.fragments;

import lah.texpert.LaTeXEditingActivity;
import lah.texpert.LaTeXStringBuilder;
import lah.texpert.R;
import lah.texpert.SettingsActivity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;

/**
 * Fragment for editing LaTeX source code
 * 
 * @author L.A.H.
 * 
 */
public class EditorFragment extends Fragment {

	private static final String[] special_symbols = { "\\", "$", "{", "}", "[", "]", "^", "_", "(", ")", "%", "&", "#" };

	public static EditorFragment newInstance() {
		EditorFragment fragment = new EditorFragment();
		return fragment;
	}

	public EditText document_textview;

	private QuickAccessFragment quick_access_fragment;

	public EditorFragment() {
		// Required empty public constructor
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// Inflate the layout for this fragment
		View view = inflater.inflate(R.layout.fragment_editor, container, false);

		final LaTeXEditingActivity activity = (LaTeXEditingActivity) getActivity();

		// Prepare the grid of special symbols
		GridView symbols_grid = (GridView) view.findViewById(R.id.special_symbols_grid);
		symbols_grid.setNumColumns(special_symbols.length);
		symbols_grid.setAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1,
				special_symbols));
		symbols_grid.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				activity.current_document.replaceSelection(special_symbols[position]);
			}
		});

		// Prepare document editing area
		document_textview = (EditText) view.findViewById(R.id.document_area);
		document_textview.setEditableFactory(new Editable.Factory() {

			@Override
			public Editable newEditable(CharSequence source) {
				return activity.current_document;
			}
		});
		document_textview.setText(activity.current_document);

		// Prepare button to access navigation/insertion
		ImageButton quick_access_button = (ImageButton) view.findViewById(R.id.quick_access_button);
		quick_access_button.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				// toggleQuickAccess();
			}
		});

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (document_textview == null)
			return;

		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());

		// Update the font size
		String font_size = pref.getString(SettingsActivity.PREF_FONT_SIZE, "18");
		try {
			document_textview.setTextSize(Integer.parseInt(font_size));
		} catch (Exception e) {
			document_textview.setTextSize(18);
		}

		// Update the font family
		String font_family = pref.getString(SettingsActivity.PREF_FONT_FAMILY, "Sans Serif");
		if (font_family.equals("Sans Serif"))
			document_textview.setTypeface(Typeface.SANS_SERIF);
		else if (font_family.equals("Monospace"))
			document_textview.setTypeface(Typeface.MONOSPACE);
		else
			document_textview.setTypeface(Typeface.SERIF);
	}

	public void setDocument(LaTeXStringBuilder document) {
		document_textview.setText(document);
		document.setView(document_textview);
	}

	public void toggleQuickAccess() {
		FragmentTransaction trans = getChildFragmentManager().beginTransaction();
		if (quick_access_fragment == null) {
			quick_access_fragment = QuickAccessFragment.newInstance();
			trans.replace(R.id.quick_access_fragment, quick_access_fragment).commit();
			return;
		}
		if (quick_access_fragment.isVisible())
			trans.hide(quick_access_fragment).commit();
		else
			trans.show(quick_access_fragment).commit();
	}

}
