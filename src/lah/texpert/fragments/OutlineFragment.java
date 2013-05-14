package lah.texpert.fragments;

import lah.texpert.LaTeXEditingActivity;
import lah.texpert.LaTeXStringBuilder;
import lah.texpert.LaTeXStringBuilder.Section;
import lah.texpert.R;
import android.content.Context;
import android.os.AsyncTask;
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
public class OutlineFragment extends Fragment implements LaTeXStringBuilder.OutlineListener {

	public static OutlineFragment newInstance(EditorFragment editing_fragment) {
		OutlineFragment fragment = new OutlineFragment();
		return fragment;
	}

	private OutlineAdapter adapter;

	private ListView outline_listview;

	Section[] sections;

	public OutlineFragment() {
		// Required empty public constructor
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
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				((LaTeXEditingActivity) getActivity()).current_document.setCursor(adapter.getItem(position)
						.getTextPosition());
			}
		});
		return view;
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) {
		}
	}

	class OutlineAdapter extends ArrayAdapter<Section> {

		public OutlineAdapter(Context context) {
			super(context, android.R.layout.simple_list_item_1);
		}

		@Override
		public int getCount() {
			return sections == null ? 0 : sections.length;
		}

		@Override
		public Section getItem(int position) {
			return sections == null ? null : sections[position];
		}

	}

	@Override
	public void onOutlineChanged(Section[] sections) {
		this.sections = sections;
		adapter.notifyDataSetChanged();
	}

	/**
	 * Task to refresh the document outline in back ground
	 */
	class ReloadOutlineTask extends AsyncTask<Void, Void, Section[]> {

		@Override
		protected Section[] doInBackground(Void... params) {
			return null;
		}

		@Override
		protected void onPostExecute(Section[] result) {
			sections = result;
		}

	}

}
