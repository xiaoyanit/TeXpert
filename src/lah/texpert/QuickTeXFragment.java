package lah.texpert;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;

/**
 * Fragment to allow for easy insertion of frequently used TeX symbol characters
 * 
 * @author L.A.H.
 * 
 */
public class QuickTeXFragment extends Fragment {

	static class SymbolsAdapter extends ArrayAdapter<String> {

		int category;

		public SymbolsAdapter(Context context, int textViewResourceId) {
			super(context, textViewResourceId);
		}

		@Override
		public int getCount() {
			return category < shortcuts.length ? shortcuts[category].length : 0;
		}

		@Override
		public String getItem(int position) {
			return category < shortcuts.length ? shortcuts[category][position] : null;
		}
	}

	private static final String[][] shortcuts = {
			// Special characters
			{ "\\", "$", "%", "&", "#", "_", "~", "^", "{", "}", "(", ")", "[", "]" },
			// Escaped special characters
			{ "\\\\", "\\$", "\\%", "\\&", "\\#", "\\_", "\\~", "\\^", "\\{", "\\}", "\\[", "\\]", "\\(", "\\)" },
			// Common text mode commands
			{ "\\emph", "\\begin{}\n\\end{}", "\\section{}", "\\subsection{}", "\\subsubsection{}",
					"\\subsubsubsection{}", "\\paragraph{}", "\\subparagraph{}", "\\part{}", "\\chapter{}",
					"\\usepackage", "\\documentclass{}", "\\author{}", "\\title" },
			// Common math mode commands
			{ "\\frac{}{}", "\\dfrac{}{}", "\\sqrt{}", "\\sqrt[]{}", "\\rightarrow", "\\leftarrow", "\\mapsto" },
			// Capitalized Greek letters
			{ "\\Alpha", "\\Beta", "\\Gamma", "\\Delta", "\\Epsilon", "\\Zeta", "\\Eta", "\\Theta", "\\Iota",
					"\\Kappa", "\\Lambda", "\\Mu", "\\Nu", "\\Xi", "\\Omicron", "\\Pi", "\\Rho", "\\Sigma", "\\Tau",
					"\\Upsilon", "\\Phi", "\\Chi", "\\Psi", "\\Omega" },
			// Lower case Greek letters
			{ "\\alpha", "\\beta", "\\gamma", "\\delta", "\\epsilon", "\\varepsilon", "\\zeta", "\\eta", "\\theta",
					"\\vartheta", "\\iota", "\\kappa", "\\varkappa", "\\lambda", "\\mu", "\\nu", "\\xi", "\\omicron",
					"\\pi", "\\varpi", "\\rho", "\\varrho", "\\sigma", "\\varsigma", "\\tau", "\\upsilon", "\\phi",
					"\\varphi", "\\chi", "\\psi", "\\omega", } };

	public static QuickTeXFragment newInstance(LaTeXStringBuilder target_document) {
		QuickTeXFragment fragment = new QuickTeXFragment();
		fragment.target_document = target_document;
		return fragment;
	}

	LaTeXStringBuilder target_document;

	public QuickTeXFragment() {
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_quick_tex, container, false);
		final SymbolsAdapter adapter = new SymbolsAdapter(getActivity(), android.R.layout.simple_list_item_1);
		ListView item_grid = (ListView) view.findViewById(R.id.items_listview);
		// new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1,
		// special_chars);
		item_grid.setAdapter(adapter);
		item_grid.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				if (target_document != null)
					target_document.replaceSelection(adapter.getItem(arg2));
			}
		});
		Spinner categories_spinner = (Spinner) view.findViewById(R.id.category_spinner);
		categories_spinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				adapter.category = arg2;
				adapter.notifyDataSetChanged();
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
		return view;
	}
}
