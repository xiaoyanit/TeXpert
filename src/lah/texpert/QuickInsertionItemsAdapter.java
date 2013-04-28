package lah.texpert;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

/**
 * Adapter for a expandable list containing shortcut items
 * 
 * @author L.A.H.
 * 
 */
public class QuickInsertionItemsAdapter extends BaseExpandableListAdapter {

	private static final String[] categories = { "Special", "Escaped", "Text", "Math" };

	private static final String[][] insertion_items = {
			// Special characters
			{ "\\", "$", "%", "&", "#", "_", "~", "^", "{}", "()", "[]" },
			// Escaped special characters
			{ "\\textbackslash{}", "\\$", "\\%", "\\&", "\\#", "\\_", "\textasciitilde{}", "\\textasciicircum{}",
					"\\{\\}", "\\[\\]", "\\(\\)" },
			// Common text mode commands
			{ "\\begin{}\n\\end{}", "\\textsubscript{}", "\\textsuperscript{}", "\\emph", "\\section{}", "\\subsection{}",
					"\\subsubsection{}", "\\subsubsubsection{}", "\\paragraph{}", "\\subparagraph{}", "\\part{}",
					"\\chapter{}", "\\documentclass{}", "\\author{}", "\\title{}", "\\input{}",
					"\\includegraphics[]{}", "\\include{}", "\\usepackage{}" },
			// Common math mode commands
			{ "\\frac{}{}", "\\dfrac{}{}", "\\sqrt{}", "\\sqrt[]{}", "\\rightarrow", "\\leftarrow", "\\mapsto",
					"\\varepsilon", "\\vartheta", "\\varkappa", "\\varpi", "\\varrho", "\\varsigma", "\\varphi" } };

	int current_expanding_group = -1;

	private final LayoutInflater inflater;

	public QuickInsertionItemsAdapter(Context context) {
		inflater = LayoutInflater.from(context);
	}

	@Override
	public String getChild(int groupPosition, int childPosition) {
		return insertion_items[groupPosition][childPosition];
	}

	@Override
	public long getChildId(int groupPosition, int childPosition) {
		return childPosition;
	}

	@Override
	public int getChildrenCount(int groupPosition) {
		return insertion_items[groupPosition].length;
	}

	@Override
	public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView,
			ViewGroup parent) {
		return getTextViewWithText(getChild(groupPosition, childPosition), convertView);
	}

	@Override
	public String getGroup(int groupPosition) {
		return categories[groupPosition];
	}

	@Override
	public int getGroupCount() {
		return insertion_items.length;
	}

	@Override
	public long getGroupId(int groupPosition) {
		return groupPosition;
	}

	@Override
	public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
		return getTextViewWithText(getGroup(groupPosition), convertView);
	}

	private View getTextViewWithText(String text, View convertView) {
		if (!(convertView instanceof TextView)) {
			convertView = inflater.inflate(android.R.layout.simple_expandable_list_item_1, null);
			((TextView) convertView).setTextSize(18);
		}
		((TextView) convertView).setText(text);
		return convertView;
	}

	@Override
	public boolean hasStableIds() {
		return false;
	}

	@Override
	public boolean isChildSelectable(int groupPosition, int childPosition) {
		return true;
	}

}