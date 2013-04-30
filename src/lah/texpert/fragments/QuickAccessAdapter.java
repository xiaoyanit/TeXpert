package lah.texpert.fragments;

import android.content.Context;
import android.text.TextUtils.TruncateAt;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

/**
 * Adapter containing quickly accessible items such as
 * 
 * - Link to a section, subsection, etc.
 * 
 * - Link to external resources including {@code \input}, {@code \includegraphics} and {@code \bibliography}
 * 
 * - Frequently used commands
 * 
 * - Defined labels
 * 
 * @author L.A.H.
 * 
 */
public class QuickAccessAdapter extends BaseExpandableListAdapter {

	static final String[] access_categories = { "Outline", "External", "Command", "Label" };

	static final String pilcrow = "\u00B6", cent = "\u00A2", section = "\u00A7";

	private static final String[][] insertion_items = { { section, cent, pilcrow }, { "test.png" },
			{ "\\begin{}\n\\end{}\n", "\\section", "\\lambda" }, {} };

	int current_expanding_group = -1;

	private final LayoutInflater inflater;

	public QuickAccessAdapter(Context context) {
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
		return access_categories[groupPosition];
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
		TextView view;
		if (convertView instanceof TextView) {
			view = (TextView) convertView;
		} else {
			view = (TextView) inflater.inflate(android.R.layout.simple_expandable_list_item_1, null);
			view.setTextSize(18);
			view.setSingleLine();
			view.setEllipsize(TruncateAt.END);
		}
		view.setText(text);
		return view;
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