package lah.texpert;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView.BufferType;

/**
 * Adapter to bind to the LaTeX document
 * 
 * @author L.A.H.
 * 
 */
public class DocumentAdapter extends ArrayAdapter<DocumentAdapter.Paragraph> {

	/**
	 * Class to encapsulate char sequence (i.e. non-formating) String editing operations. This is to support undo.
	 */
	class EditOperation {

		EditOperation inverse_op;

		public CharSequence old_content, new_content;

		public int[] positions;

		/**
		 * Basically, any editing operation can be described as a substitution.
		 * 
		 * @param positions
		 *            The positions to make substitutions
		 * @param old_content
		 *            Old character sequence
		 * @param new_content
		 *            New character sequence
		 */
		public EditOperation(int[] positions, CharSequence old_content, CharSequence new_content) {
			this.positions = positions;
			this.old_content = old_content;
			this.new_content = new_content;
		}

		public CharSequence apply(CharSequence text) {
			// TODO implement
			return text;
		}

		public EditOperation getInverseOperation() {
			if (inverse_op == null) {
				// TODO compute appropriately
				int[] inv_positions = new int[positions.length];
				inverse_op = new EditOperation(inv_positions, new_content, old_content);
			}
			return inverse_op;
		}

	}

	/**
	 * Class for a single text paragraph (no two consecutive line-breaks)
	 */
	class Paragraph implements TextWatcher {

		/**
		 * Editable to hold the information
		 */
		private Editable content;

		/**
		 * Split into lines
		 */
		private List<String> lines;

		public Paragraph(Editable content) {
			this.content = content;
			for (int i = 0; i < LATEX_PATTERNS.length; i++) {
				Matcher matcher = LATEX_PATTERNS[i].matcher(content);
				while (matcher.find())
					content.setSpan(CharacterStyle.wrap(LATEX_STYLES[i]), matcher.start(1), matcher.end(1),
							Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
			}
		}

		@Override
		public void afterTextChanged(Editable s) {
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		public CharSequence getContent() {
			return content;
		}

		public String getLine(int childPosition) {
			return lines.get(childPosition);
		}

		public int getNumLines() {
			return lines.size();
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			content.replace(start, start + before, s, 0, count);
		}

	}

	/**
	 * TeX and LaTeX text patterns for syntax highlighting purposes, the first group depicted in the pattern will be
	 * highlighted using the style described in {@link DocumentAdapter#LATEX_STYLES}.
	 */
	static final Pattern[] LATEX_PATTERNS = {
			// TODO Non-escaped TeX special characters: \ $ % & # _ ~ ^ { }
			// TeX command and escaped special characters:
			Pattern.compile("(\\\\([A-Za-z]+|[\\\\\\$%&#_~\\^\\{\\}]))"),
			// TODO Command option between { and }
			// TODO Referenced resources via \input, \includegraphics, etc
			// TODO Verbatim and listing
			// Math formulas TODO add math environments equation, align*, ... as well
			Pattern.compile("((\\$|\\$\\$)([^\\$]|\\\\\\$)*(\\$|\\$\\$))"),
			// TeX line comment TODO consider block comment via comments environment
			Pattern.compile("(%.*\\n)") };

	/**
	 * Corresponding styles for the above patterns
	 */
	static final CharacterStyle[] LATEX_STYLES = {
			// Command style: blue
			new ForegroundColorSpan(Color.parseColor("#0000ff")),
			// Formula style: green
			new ForegroundColorSpan(Color.parseColor("#00ff00")),
			// Comment style: gray
			new ForegroundColorSpan(Color.parseColor("#a0a0a0")) };

	/**
	 * Pattern for a single line of text (within a text string)
	 */
	static final Pattern LINE_PATTERN = Pattern.compile(".*(\\n|\\r\\n)|.*\\z");

	/**
	 * Pattern for a text paragraph within the entire document (strings which ends with two or more \n or \r\n or end of
	 * input)
	 */
	static final Pattern PARAGRAPH_PATTERN = Pattern.compile("(.|\\n.)*(\\n{2,}|\\n?\\z)");

	private View current_focus;

	private StringBuilder document_builder;

	private LayoutInflater layout_inflater;

	private List<Paragraph> paragraphs;

	private final OnFocusChangeListener update_focus = new OnFocusChangeListener() {

		@Override
		public void onFocusChange(View view, boolean hasFocus) {
			current_focus = hasFocus ? view : null;
		}
	};

	public DocumentAdapter(Context context, String initial_document) {
		super(context, 0);
		assert context != null;
		layout_inflater = LayoutInflater.from(context);
		if (initial_document == null)
			initial_document = "";
		document_builder = new StringBuilder(initial_document);
		paragraphs = new LinkedList<Paragraph>();
		Matcher paramatcher = PARAGRAPH_PATTERN.matcher(document_builder);
		while (paramatcher.find()) {
			Editable paraeditable = new SpannableStringBuilder(paramatcher.group());
			paragraphs.add(new Paragraph(paraeditable));
		}
	}

	public String getContent() {
		return document_builder.toString();
	}

	@Override
	public int getCount() {
		return paragraphs == null ? 0 : paragraphs.size();
	}

	@Override
	public Paragraph getItem(int position) {
		return paragraphs.get(position);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		Paragraph paragraph = getItem(position);
		EditText paragraph_view = (EditText) (convertView == null ? layout_inflater.inflate(R.layout.paragraph, null)
				: convertView);
		paragraph_view.setText(paragraph.getContent(), BufferType.SPANNABLE);
		// paragraph_view.addTextChangedListener(paragraph);
		paragraph_view.setOnFocusChangeListener(update_focus);
		return paragraph_view;
	}

	public boolean isEditing() {
		return current_focus != null;
	}

}
