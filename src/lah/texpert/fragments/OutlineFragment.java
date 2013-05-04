package lah.texpert.fragments;

import lah.texpert.LaTeXStringBuilder;
import lah.texpert.LaTeXStringBuilder.OutlineListener;
import lah.texpert.R;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * Fragment to contain the document outline, used with pager
 * 
 * @author L.A.H.
 * 
 */
public class OutlineFragment extends Fragment {

	public static OutlineFragment newInstance(EditorFragment editing_fragment) {
		OutlineFragment fragment = new OutlineFragment();
		fragment.editor_fragment = editing_fragment;
		return fragment;
	}

	private OutlineAdapter adapter;

	private EditorFragment editor_fragment;

	private ListView outline_listview;

	public OutlineFragment() {
		// Required empty public constructor
	}

	public OutlineListener getOutlineListener() {
		return adapter;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// Inflate the layout for this fragment
		View view = inflater.inflate(R.layout.fragment_outline, container, false);
		outline_listview = (ListView) view.findViewById(R.id.document_outline);
		adapter = new OutlineAdapter(getActivity());
		outline_listview.setAdapter(adapter);
		outline_listview.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				editor_fragment.bringPointIntoView(adapter.sections_pos[arg2]);
			}
		});
		return view;
	}

	class OutlineAdapter extends ArrayAdapter<String> implements LaTeXStringBuilder.OutlineListener {

		String[] sections;

		int[] sections_pos;

		public OutlineAdapter(Context context) {
			super(context, android.R.layout.simple_list_item_1);
		}

		@Override
		public int getCount() {
			return sections == null ? 0 : sections.length;
		}

		@Override
		public String getItem(int position) {
			return sections == null ? null : sections[position];
		}

		@Override
		public void onOutlineChanged(String[] sections, int[] sections_pos) {
			this.sections = sections;
			this.sections_pos = sections_pos;
			notifyDataSetChanged();
		}

	}

}
