package lah.texpert;

import java.util.LinkedList;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;

/**
 * Adapter to bind to the list of paragraphs
 * 
 * @author L.A.H.
 * 
 */
public class ParagraphsAdapter extends ArrayAdapter<Paragraph> {

	private View current_focus;

	private LayoutInflater layout_inflater;

	private List<Paragraph> paragraphs;

	final OnFocusChangeListener update_focus = new OnFocusChangeListener() {

		@Override
		public void onFocusChange(View view, boolean hasFocus) {
			current_focus = hasFocus ? view : null;
		}
	};

	public ParagraphsAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
		layout_inflater = LayoutInflater.from(context);
	}

	public void bindText(String test_document) {
		if (test_document == null)
			return;
		if (paragraphs == null)
			paragraphs = new LinkedList<Paragraph>();
		else
			paragraphs.clear();
		String[] para = Paragraph.parabreak.split(test_document);
		for (String p : para) {
			paragraphs.add(new Paragraph(p));
		}
	}

	@Override
	public int getCount() {
		if (paragraphs != null)
			return paragraphs.size();
		else
			return 0;
	}

	@Override
	public Paragraph getItem(int position) {
		return paragraphs.get(position);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		assert getContext() != null;
		View paraview = convertView == null ? layout_inflater.inflate(R.layout.paragraph, null) : convertView;
		Paragraph paragraph = getItem(position);
		EditText edit_text = (EditText) paraview.findViewById(R.id.paragraph_edittext);
		edit_text.setText(paragraph.getContent());
		edit_text.addTextChangedListener(paragraph.getTextWatcher());
		edit_text.setOnFocusChangeListener(update_focus);
		return paraview;
	}

	public boolean isEditing() {
		return current_focus != null;
	}

}
