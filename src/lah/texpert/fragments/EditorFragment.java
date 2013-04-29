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
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridView;

public class EditorFragment extends Fragment {

	static final String[] special_symbols = { "\\", "$", "{}", "[]", "^", "_", "()", "%", "&", "#" };

	public static EditorFragment newInstance() {
		EditorFragment fragment = new EditorFragment();
		return fragment;
	}

	private EditText document_textview;

	public EditorFragment() {
		// Required empty public constructor
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// Inflate the layout for this fragment
		View view = inflater.inflate(R.layout.fragment_editor, container, false);

		final LaTeXEditingActivity activity = (LaTeXEditingActivity) getActivity();

		// Prepare the grid of special symbols
		GridView ssg = (GridView) view.findViewById(R.id.special_symbols_grid);
		ssg.setNumColumns(special_symbols.length);
		ssg.setAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, special_symbols));

		// Prepare document editing area
		activity.current_document = new LaTeXStringBuilder(activity, "", null);
		document_textview = (EditText) view.findViewById(R.id.document_area);
		document_textview.setEditableFactory(new Editable.Factory() {

			@Override
			public Editable newEditable(CharSequence source) {
				return activity.current_document;
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
	}

}
