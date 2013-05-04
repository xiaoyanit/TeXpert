package lah.texpert.fragments;

import lah.texpert.LaTeXStringBuilder.DocumentStatListener;
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
public class QuickAccessAdapter extends BaseExpandableListAdapter implements DocumentStatListener {

	static final String[] access_categories = { "Outline", "Command", "Label" };

	static final int CATEGORY_OUTLINE = 0, CATEGORY_COMMAND = 1, CATEGORY_LABELS = 2;

	private static final String[][] insertion_items = { {}, {}, {} };

	static final String SYM_PILCROW = "\u00B6", SYM_CENT = "\u00A2", SYM_SECTION = "\u00A7";

	private String[] commands;

	int current_expanding_group = -1;

	private final LayoutInflater inflater;

	private String[] sections;

	int[] sections_pos;

	public QuickAccessAdapter(Context context) {
		inflater = LayoutInflater.from(context);
	}

	@Override
	public String getChild(int groupPosition, int childPosition) {
		switch (groupPosition) {
		case CATEGORY_OUTLINE:
			return sections == null ? null : SYM_SECTION + sections[childPosition];
		case CATEGORY_COMMAND:
			return commands == null ? null : commands[childPosition];
		default:
			return insertion_items[groupPosition][childPosition];
		}
	}

	@Override
	public long getChildId(int groupPosition, int childPosition) {
		return childPosition;
	}

	@Override
	public int getChildrenCount(int groupPosition) {
		switch (groupPosition) {
		case CATEGORY_OUTLINE:
			return sections == null ? 0 : sections.length;
		case CATEGORY_COMMAND:
			return commands == null ? 0 : commands.length;
		default:
			return insertion_items[groupPosition].length;
		}
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
		return access_categories.length;
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

	@Override
	public void updateCommandList(String[] cmds) {
		commands = cmds;
		notifyDataSetChanged();
	}

	@Override
	public void updateExternalResourceList(String[] externals) {
		// TODO Auto-generated method stub

	}

	@Override
	public void updateLabelList(String[] labels) {
		// TODO Auto-generated method stub

	}

	@Override
	public void updateSectionList(String[] sects, int[] sects_pos) {
		// TODO Auto-generated method stub
		sections = sects;
		sections_pos = sects_pos;
		notifyDataSetChanged();
	}

}