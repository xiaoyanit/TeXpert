package lah.texpert;

import java.io.File;
import java.io.FileWriter;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.index.DynamicTracker;
import lah.index.latex.CSUtils;
import lah.index.latex.CommentIndexer;
import lah.index.latex.ControlSequencesCounter;
import lah.index.latex.LabelsIndexer;
import lah.index.latex.NewlineIndexer;
import lah.index.latex.NonEscapedBackslashIndexer;
import lah.index.latex.NonEscapedSpecialCharsIndexer;
import lah.index.latex.RefsIndexer;
import lah.index.latex.SectionIndexer;
import android.content.Context;
import android.os.FileObserver;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.UpdateAppearance;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;

/**
 * This extension of {@link SpannableStringBuilder} is to provide better syntax highlighting performance editing LaTeX
 * content.
 * 
 * @author L.A.H.
 * 
 */
public class LaTeXStringBuilder extends SpannableStringBuilder {

	/**
	 * Ordering in term of dependency: indexing of line comments and non-escaped special symbols depend on escape
	 * backslashed and new lines.
	 */
	static final int NEWLINE = 0, NON_ESC_BACKSLASH = 1, LINE_COMMENT = 2, NON_ESC_SPECIAL = 3, SECTIONS = 4;

	static final Pattern sectioning_command_pattern = Pattern
			.compile("\\\\(part|chapter|section|subsection|subsubsection|subsubsubsection|input)");

	private static String substring(CharSequence cseq, int s, int e) {
		if (s > e)
			return null;
		char[] data = new char[e - s];
		for (int k = s; k < e; k++)
			data[k - s] = cseq.charAt(k);
		return new String(data);
	}

	/**
	 * Cached computed spans for a text line. This improves efficiency due to the fact that Android will split each text
	 * line (substring without newline in between) into several displayed lines (substrings of that line that fits the
	 * width of the containing view); each of which will then be passed to {@link #getSpans(int, int, Class)} for
	 * formatting.
	 * 
	 * TODO Should cache more for better responsiveness?
	 */
	private TeXTokenSpan[] cached_line_spans;

	private int cached_line_start, cached_line_end;

	private CommentIndexer comment_indexer;

	private ControlSequencesCounter control_sequence_counter;

	private DynamicTracker<CharSequence>[] document_trackers;

	private LinkedList<EditAction> edit_actions;

	private boolean is_modified;

	@SuppressWarnings("unused")
	private LabelsIndexer label_indexer;

	File log_file, pdf_file;

	private String log_file_name, pdf_file_name;

	private NewlineIndexer newline_indexer;

	private NonEscapedBackslashIndexer nonesc_backslash_indexer;

	private NonEscapedSpecialCharsIndexer nonesc_special_chars_indexer;

	private OutlineAdapter outline_adapter;

	@SuppressWarnings("unused")
	private RefsIndexer ref_indexer;

	private SectionIndexer section_indexer;

	/**
	 * Observers to watch for changes in the directory containing the file whose content this document holds
	 */
	private FileObserver src_dir_observer;

	private File tex_file;

	private EditText view;

	private DocumentWatcher watcher;

	@SuppressWarnings("unchecked")
	public LaTeXStringBuilder(LaTeXEditingActivity activity, CharSequence text, File file) {
		super(text);
		this.watcher = activity;
		this.is_modified = false;
		// this.tex_file = file;
		edit_actions = new LinkedList<EditAction>();
		outline_adapter = new OutlineAdapter(activity);

		// Initialize the indexers
		String TAG = "indexing";

		long t = System.currentTimeMillis();
		newline_indexer = new NewlineIndexer(text);
		if (LaTeXEditingActivity.DEBUG)
			Log.v(TAG, "Index linefeed takes " + (System.currentTimeMillis() - t));

		t = System.currentTimeMillis();
		nonesc_backslash_indexer = new NonEscapedBackslashIndexer(text);
		if (LaTeXEditingActivity.DEBUG)
			Log.v(TAG, "Index non-escaped backslashes takes " + (System.currentTimeMillis() - t));

		t = System.currentTimeMillis();
		comment_indexer = new CommentIndexer(text, newline_indexer, nonesc_backslash_indexer);
		if (LaTeXEditingActivity.DEBUG)
			Log.v(TAG, "Index comments takes " + (System.currentTimeMillis() - t));

		t = System.currentTimeMillis();
		nonesc_special_chars_indexer = new NonEscapedSpecialCharsIndexer(text, nonesc_backslash_indexer);
		if (LaTeXEditingActivity.DEBUG)
			Log.v(TAG, "Index non-escaped special chars takes " + (System.currentTimeMillis() - t));

		t = System.currentTimeMillis();
		section_indexer = new SectionIndexer(text, nonesc_backslash_indexer);
		if (LaTeXEditingActivity.DEBUG)
			Log.v(TAG, "Index sections takes " + (System.currentTimeMillis() - t));

		// ref_indexer = new RefsIndexer(text, nonesc_backslash_indexer);
		// label_indexer = new LabelsIndexer(text, nonesc_backslash_indexer);

		t = System.currentTimeMillis();
		control_sequence_counter = new ControlSequencesCounter(text, nonesc_backslash_indexer);
		if (LaTeXEditingActivity.DEBUG)
			Log.v(TAG, "Count control sequences takes " + (System.currentTimeMillis() - t));

		document_trackers = new DynamicTracker[] { newline_indexer, nonesc_backslash_indexer, comment_indexer,
				nonesc_special_chars_indexer, section_indexer, control_sequence_counter };
		// , ref_indexer, label_indexer };
		setFile(file);
	}

	public File getFile() {
		return tex_file;
	}

	public String[] getFrequentlyUsedCommands() {
		// TODO Implement
		return null;
	}

	public String[] getNewCommands() {
		// TODO Implement
		return null;
	}

	public OutlineAdapter getOutlineAdapter() {
		return outline_adapter;
	}

	public File getPdfFile() {
		return pdf_file;
	}

	@Override
	public int getSpanEnd(Object what) {
		if (what instanceof TeXTokenSpan) {
			TeXTokenSpan ttsp = (TeXTokenSpan) what;
			int position = ttsp.getPosition();
			int len = length();
			// If this span is for a line comment
			if (ttsp.isComment()) {
				int nearest_linefeed = newline_indexer.getValueAt(newline_indexer.findFirst(position));
				return nearest_linefeed < 0 ? len : nearest_linefeed;
			}

			// If not, then it is either a control sequence or (non-escaped) special symbol
			if (charAt(position) == '\\')
				return CSUtils.getEndOfControlSequenceAt(this, position);
			return position + 1;
		}
		// if (what instanceof TeXReferenceSpan) {
		// // TODO implement
		// return ((TeXReferenceSpan) what).position + 10;
		// }
		return super.getSpanEnd(what);
	}

	/**
	 * Assumption: [start..end) is a substring of a single text line; in particular, a displayed line.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T> T[] getSpans(int start, int end, Class<T> type) {
		if (type == CharacterStyle.class) {
			// If this displayed line is cached, return cached result
			if (cached_line_spans != null && cached_line_start <= start && end <= cached_line_end)
				return (T[]) cached_line_spans;

			// First, we extend the range to contain a full line of text
			int nlend = newline_indexer.findFirst(end);
			end = newline_indexer.getValueAt(nlend);
			if (end < 0) // no new line after original `end` ==> last line
				end = length();
			// If [start..end) is not the first line then set start to first character after the previous new line;
			// otherwise set it to the beginning of text
			start = (nlend > 0) ? newline_indexer.getValueAt(nlend - 1) + 1 : 0;

			// Percents in [start..end) are the [pcistart..pciend)^th percent of the whole text
			int pcistart = comment_indexer.findFirst(start);
			int pciend = comment_indexer.findFirst(end);

			// Go through each occurrences and pick up the first non-escaped one
			int pci_nonesc = pcistart;
			for (; pci_nonesc < pciend; pci_nonesc++) {
				int pcpos = comment_indexer.getValueAt(pci_nonesc);
				if (nonesc_backslash_indexer.isNonEscaped(this, pcpos))
					// Make sure that this is not escaped % to break
					break;
			}
			int comment_start = end;
			int has_comment = 0;
			if (pci_nonesc < pciend) {
				// We found some non-escaped % on this line
				comment_start = comment_indexer.getValueAt(pci_nonesc);
				has_comment = 1;
			}

			// Pick up the occurrences of special symbols from [start..comment_start)
			// int bsstart = indexers[SPECIAL].findFirst(start);
			// int bsend = indexers[SPECIAL].findFirst(comment_start);
			int bsstart = nonesc_backslash_indexer.findFirst(start);
			int bsend = nonesc_backslash_indexer.findFirst(comment_start);
			int ssstart = nonesc_special_chars_indexer.findFirst(start);
			int ssend = nonesc_special_chars_indexer.findFirst(comment_start);

			// Now we are ready to produce the result
			TeXTokenSpan[] result = new TeXTokenSpan[ssend - ssstart + bsend - bsstart + has_comment];
			if (has_comment > 0)
				result[0] = new TeXTokenSpan(this, comment_start, true);
			int d = has_comment; // has_comment - bsstart;
			for (int bs = bsstart; bs < bsend; bs++) {
				int pos = nonesc_backslash_indexer.getValueAt(bs);
				result[d] = new TeXTokenSpan(this, pos, false);
				d++;
			}
			// d = has_comment + bsend - bsstart - ssstart;
			for (int bs = ssstart; bs < ssend; bs++) {
				int pos = nonesc_special_chars_indexer.getValueAt(bs);
				result[d] = new TeXTokenSpan(this, pos, false);
				d++;
			}

			// Update the cache to prevent re-computation
			cached_line_spans = result;
			cached_line_start = start;
			cached_line_end = end;
			return (T[]) result;
		}
		// if (type == ClickableSpan.class) {
		// int rs = ref_indexer.findFirst(start);
		// int re = ref_indexer.findFirst(end);
		// if (rs > 0)
		// rs = rs - 1;
		// TeXReferenceSpan[] result = new TeXReferenceSpan[re - rs];
		// for (int i = rs; i < re; i++)
		// result[i - rs] = new TeXReferenceSpan(ref_indexer.getValueAt(i));
		// return (T[]) result;
		// }
		return super.getSpans(start, end, type);
	}

	@Override
	public int getSpanStart(Object what) {
		if (what instanceof TeXTokenSpan) {
			TeXTokenSpan ttsp = (TeXTokenSpan) what;
			return ttsp.getPosition();
		}
		// if (what instanceof TeXReferenceSpan) {
		// return ((TeXReferenceSpan) what).position;
		// }
		return super.getSpanStart(what);
	}

	public boolean isModified() {
		return is_modified;
	}

	@Override
	public LaTeXStringBuilder replace(int start, int end, CharSequence tb, int tbstart, int tbend) {
		return replace(start, end, tb, tbstart, tbend, true);
	}

	private LaTeXStringBuilder replace(int start, int end, CharSequence tb, int tbstart, int tbend,
			boolean save_action_for_undo) {
		if (save_action_for_undo) {
			// Kick out long-performed actions
			if (edit_actions.size() == 256)
				edit_actions.removeLast();
			edit_actions.push(new EditAction(start, substring(this, start, end), substring(tb, tbstart, tbend)));
		}

		int len_diff = (tbend - tbstart) - (end - start);
		long[] affected = new long[document_trackers.length];

		// Obtain affected regions for each indexer; need to do this IN REVERSE DEPENDENCY ORDER
		for (int i = document_trackers.length - 1; i >= 0; i--)
			affected[i] = document_trackers[i].beforeSequenceChanged(this, start, end);

		// Invoke superclass's method to update content and notify views
		super.replace(start, end, tb, tbstart, tbend);

		// Update the indexers after the text is replaced IN DEPENDENCY ORDER
		for (int i = 0; i < document_trackers.length; i++)
			document_trackers[i].afterSequenceChanged(this, affected[i], len_diff);

		// BUG: need to do these before all notifications
		is_modified = true;
		cached_line_spans = null; // Invalidate cache after replacement

		// Refresh the commands and document outline
		outline_adapter.notifyDataSetChanged();

		// Notify activity to update action bar, etc.
		if (watcher != null)
			watcher.notifyDocumentStateChanged();
		return this;
	}

	/**
	 * Replace the text within selected region with new content
	 * 
	 * @param content
	 *            Text string to replace current selection with
	 */
	public void replaceSelection(String content) {
		int sel_start = Selection.getSelectionStart(this);
		int sel_end = Selection.getSelectionEnd(this);
		if (0 <= sel_start && sel_start <= sel_end)
			replace(sel_start, sel_end, content);
	}

	public void save(File new_file, Runnable action_after_saved) throws Exception {
		FileWriter writer = new FileWriter(new_file, false);
		writer.write(toString());
		writer.close();
		setFile(new_file);
		is_modified = false;
		if (watcher != null)
			watcher.notifyDocumentStateChanged();
		if (action_after_saved != null)
			action_after_saved.run();
	}

	public boolean search(String search_pattern, boolean regex) {
		int search_start_loc = Selection.getSelectionEnd(this);
		if (search_start_loc < 0 || search_start_loc >= length())
			search_start_loc = 0;
		Matcher m = Pattern.compile(search_pattern, regex ? 0 : Pattern.LITERAL).matcher(this);
		if (m.find(search_start_loc)) {
			setSelection(m.start(), m.end());
			return true;
		}
		return false;
	}

	public void setCursor(int position) {
		Selection.setSelection(this, position);
	}

	void setFile(File file) {
		tex_file = file;

		if (tex_file == null) {
			if (src_dir_observer != null)
				src_dir_observer.stopWatching();
			return;
		}

		File dir = tex_file.getParentFile();
		String name = tex_file.getName();
		if (name.endsWith(".tex") || name.endsWith(".ltx")) {
			String basename = name.substring(0, name.length() - 4);
			pdf_file = new File(dir, basename + ".pdf");
			log_file = new File(dir, basename + ".log");
			log_file_name = log_file.getName();
			pdf_file_name = pdf_file.getName();
			if (LaTeXEditingActivity.DEBUG) {
				Log.v("LSB", "PDF file: " + pdf_file.getAbsolutePath());
				Log.v("LSB", "Log file: " + log_file.getAbsolutePath());
			}
		}

		if (pdf_file != null && pdf_file.exists())
			watcher.loadPdf(pdf_file);

		if (log_file != null && log_file.exists())
			watcher.loadLog(log_file);

		// Monitor changes to the the directory
		if (src_dir_observer != null)
			src_dir_observer.stopWatching();
		src_dir_observer = new FileObserver(dir.getAbsolutePath(), FileObserver.CLOSE_WRITE) {

			@Override
			public void onEvent(int event, String path) {
				if (path == null)
					return;
				if (log_file_name != null && path.equals(log_file_name))
					watcher.loadLog(log_file);
				if (pdf_file_name != null && path.equals(pdf_file_name))
					watcher.loadPdf(pdf_file);
			}
		};
		src_dir_observer.startWatching();
	}

	public void setSelection(int start, int end) {
		if (view != null)
			view.clearFocus();
		Selection.setSelection(this, start, end);
		if (view != null)
			view.requestFocus();
	}

	public void setView(EditText document_textview) {
		view = document_textview;
	}

	public boolean undoLastEdit() {
		if (!edit_actions.isEmpty()) {
			EditAction last_action = edit_actions.pop();
			int pos = last_action.replace_pos;
			String bef = last_action.before;
			String aft = last_action.after;
			// Must not save this replacement; otherwise, we cannot make a next
			// undo
			replace(pos, pos + aft.length(), bef, 0, bef.length(), false);
		}
		return true;
	}

	public interface DocumentWatcher {

		void loadLog(File log_file);

		void loadPdf(File pdf_file);

		void notifyDocumentStateChanged();

	}

	public static class EditAction {

		String before, after;

		int replace_pos;

		public EditAction(int pos, String bef, String aft) {
			replace_pos = pos;
			before = bef;
			after = aft;
		}

	}

	public class OutlineAdapter extends ArrayAdapter<Section> {

		public OutlineAdapter(Context context) {
			super(context, android.R.layout.simple_list_item_1);
		}

		@Override
		public int getCount() {
			return section_indexer.size();
		}

		@Override
		public Section getItem(int position) {
			int doc_pos = section_indexer.getValueAt(position);
			return new Section(doc_pos);
		}

	}

	public class Section {

		int position;

		SectionType type;

		public Section(int position) {
			this.position = position;
		}

		public int getTextPosition() {
			return position;
		}

		@Override
		public String toString() {
			int j = CSUtils.getEndOfControlSequenceAt(LaTeXStringBuilder.this, position);
			int k = CSUtils.getEndOfArgumentAt(LaTeXStringBuilder.this, j);
			// texbook does not use \subsection in LaTeX way
			return k > j ? CSUtils.substring(LaTeXStringBuilder.this, j + 1, k - 1) : "";
		}
	}

	public enum SectionType {
		CHAPTER, PART, SECTION, SUBSECTION, SUBSUBSECTION, SUBSUBSUBSECTION
	}

	/**
	 * Extension of {@link ClickableSpan} to hold suggestion texts
	 * 
	 * @author L.A.H.
	 * 
	 */
	static class TeXReferenceSpan extends ClickableSpan {

		int position;

		TeXReferenceSpan(int position) {
			this.position = position;
		}

		@Override
		public void onClick(View widget) {
			// TODO Implement
			System.out.println("Link at " + position + " is click");
		}

		// @Override
		// public void updateDrawState(TextPaint ds) {
		// super.updateDrawState(ds);
		// }

	}

	/**
	 * Extension of {@link ClickableSpan} to hold suggestion texts
	 * 
	 * @author L.A.H.
	 * 
	 */
	static class TeXSuggestionSpan extends ClickableSpan {

		@Override
		public void onClick(View widget) {
			// TODO Implement
		}

		// @Override
		// public void updateDrawState(TextPaint ds) {
		// super.updateDrawState(ds);
		// }

	}

	/**
	 * Extension of {@link CharacterStyle} to style a TeX token
	 * 
	 * @author L.A.H.
	 * 
	 */
	static class TeXTokenSpan extends CharacterStyle implements UpdateAppearance, Comparable<TeXTokenSpan> {

		/**
		 * Colors constants to style document
		 */
		static final int COLOR_CONTROL_SEQUENCE = 0xFF0000FF, COLOR_COMMENT = 0xFFA0A0A0, COLOR_SYMBOL = 0xFFCC8000,
				COLOR_FORMULA = 0xFF00CC00;

		static final int FLAG_COMMAND = 0x40000000, FLAG_COMMENT = 0x80000000, FLAG_SYMBOL = 0x10000000;

		/**
		 * Mask to extract first two MSBs and the remaining 30 lower bits
		 */
		static final int MASK_TOKEN_TYPE = 0xC0000000, MASK_POSITION = 0x3FFFFFFF;

		/**
		 * The first two most-significant-bits represents the token type
		 */
		private int position;

		public TeXTokenSpan(CharSequence text, int position, boolean is_comment) {
			if (is_comment) {
				this.position = position | FLAG_COMMENT;
			} else {
				this.position = position;
				char c = text.charAt(position);
				if (c == '\\' && position + 1 < text.length()) {
					char nc = text.charAt(position + 1);
					if (('A' <= nc && nc <= 'Z') || ('a' <= nc && nc <= 'z'))
						this.position |= FLAG_COMMAND;
				}
			}
		}

		@Override
		public int compareTo(TeXTokenSpan span) {
			return position - span.position;
		}

		public int getPosition() {
			return position & MASK_POSITION;
		}

		public boolean isComment() {
			return (position & FLAG_COMMENT) == FLAG_COMMENT;
		}

		@Override
		public void updateDrawState(TextPaint ds) {
			switch (position & MASK_TOKEN_TYPE) {
			case FLAG_COMMAND:
				ds.setColor(COLOR_CONTROL_SEQUENCE);
				break;
			case FLAG_COMMENT:
				ds.setColor(COLOR_COMMENT);
				break;
			default:
				ds.setColor(COLOR_SYMBOL);
			}
		}
	}

}
