package lah.texpert;

import java.util.List;

import android.content.Context;
import android.view.View;
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

	public ParagraphsAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
	}

	private List<Paragraph> paragraphs;
	
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
		EditText edit_text = new EditText(getContext());		
		edit_text.setText(getItem(position).getText());
		return null;
	}

}
