package lah.texpert.fragments;

import lah.texpert.LaTeXEditingActivity;
import lah.texpert.R;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupExpandListener;

/**
 * Fragment containing quick navigation (section, external inputs, etc.) and insertion of commands (list of used
 * commands sorted in usage frequencies)
 * 
 * @author L.A.H.
 * 
 */
public class QuickAccessFragment extends Fragment {

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

		// Prepare the quick access list
		final LaTeXEditingActivity activity = (LaTeXEditingActivity) getActivity();
		final ExpandableListView insertion_listview = (ExpandableListView) view
				.findViewById(R.id.quick_insertion_items_listview);
		final QuickAccessItemsAdapter adapter = new QuickAccessItemsAdapter(activity);
		insertion_listview.setAdapter(adapter);
		insertion_listview.setOnChildClickListener(new OnChildClickListener() {

			@Override
			public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
				activity.replaceCurrentSelection(adapter.getChild(groupPosition, childPosition));
				return true;
			}
		});
		insertion_listview.setOnGroupExpandListener(new OnGroupExpandListener() {

			@Override
			public void onGroupExpand(int groupPosition) {
				if (adapter.current_expanding_group >= 0 && adapter.current_expanding_group != groupPosition)
					insertion_listview.collapseGroup(adapter.current_expanding_group);
				adapter.current_expanding_group = groupPosition;
			}
		});
		
		return view;
	}

}
