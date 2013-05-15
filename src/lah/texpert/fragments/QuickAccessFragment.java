package lah.texpert.fragments;

import lah.texpert.R;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils.TruncateAt;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

/**
 * 
 * 
 * @author L.A.H.
 * 
 */
public class QuickAccessFragment extends Fragment {

	static final String[] access_categories = { "Command", "Label" };

	public static QuickAccessFragment newInstance() {
		QuickAccessFragment fragment = new QuickAccessFragment();
		return fragment;
	}

	public QuickAccessFragment() {
		// Required empty public constructor
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// Inflate the layout for this fragment
		View view = inflater.inflate(R.layout.fragment_quick_access, container, false);
		return view;
	}

	/**
	 * Adapter containing quickly accessible items such as
	 * 
	 * - Frequently used commands
	 * 
	 * - Defined labels
	 * 
	 * @author L.A.H.
	 * 
	 */
	class QuickAccessAdapter extends BaseExpandableListAdapter {

		static final int CATEGORY_COMMAND = 0, CATEGORY_LABELS = 1;

		static final String SYM_PILCROW = "\u00B6", SYM_CENT = "\u00A2", SYM_SECTION = "\u00A7";

		String[] commands;

		int current_expanding_group = -1;

		private final LayoutInflater inflater;

		public QuickAccessAdapter(Context context) {
			inflater = LayoutInflater.from(context);
		}

		@Override
		public String getChild(int groupPosition, int childPosition) {
			switch (groupPosition) {
			case CATEGORY_COMMAND:
				return commands == null ? null : commands[childPosition];
			default:
				// return insertion_items[groupPosition][childPosition];
				return null;
			}
		}

		@Override
		public long getChildId(int groupPosition, int childPosition) {
			return childPosition;
		}

		@Override
		public int getChildrenCount(int groupPosition) {
			switch (groupPosition) {
			case CATEGORY_COMMAND:
				return commands == null ? 0 : commands.length;
			default:
				// return insertion_items[groupPosition].length;
				return 0;
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
	}

}
