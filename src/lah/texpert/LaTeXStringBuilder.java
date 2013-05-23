package lah.texpert;

import java.io.File;
import java.io.FileWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.index.Indexer;
import lah.index.latex.CSUtils;
import lah.index.latex.CommentIndexer;
import lah.index.latex.NewlineIndexer;
import lah.index.latex.NonEscapedBackslashIndexer;
import lah.index.latex.NonEscapedSpecialCharsIndexer;
import lah.index.latex.SectionIndexer;
import android.content.Context;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.UpdateAppearance;
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

	private LinkedList<EditAction> edit_actions;

	private File file;

	private Indexer<CharSequence>[] indexers;

	private boolean is_modified;

	private boolean is_outline_modified;

	private NonEscapedBackslashIndexer nonescbs_indexer;

	private OutlineAdapter outline_adapter;

	private SectionIndexer section_indexer;

	private List<Section> sections;

	private EditText view;

	private DocumentWatcher watcher;

	@SuppressWarnings("unchecked")
	public LaTeXStringBuilder(LaTeXEditingActivity activity, CharSequence text, File file) {
		super(text);
		this.watcher = activity;
		this.is_modified = false;
		this.file = file;
		edit_actions = new LinkedList<EditAction>();
		outline_adapter = new OutlineAdapter(activity);

		// Initialize the indexers
		NewlineIndexer newline_indexer = new NewlineIndexer(text);
		nonescbs_indexer = new NonEscapedBackslashIndexer(text);
		CommentIndexer comment_indexer = new CommentIndexer(text, newline_indexer, nonescbs_indexer);
		NonEscapedSpecialCharsIndexer nonescspec_indexer = new NonEscapedSpecialCharsIndexer(text, nonescbs_indexer);
		section_indexer = new SectionIndexer(text, nonescbs_indexer);
		indexers = new Indexer[] { newline_indexer, nonescbs_indexer, comment_indexer, nonescspec_indexer,
				section_indexer };
	}

	/**
	 * Find the end index of the longest subsequence start from pos and match the regular expression [A-Za-z]*
	 * 
	 * @param pos
	 *            Start position
	 * @param len
	 *            Length of this object
	 * @return Return the maximum position {@code k >= pos} where the subsequence {@code [pos..k)} are all letters
	 */
	private int endIndexLongestLettersSubsequence(int pos, int len) {
		while (pos < len) {
			char c = charAt(pos);
			if (('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z'))
				pos++;
			else
				break;
		}
		return pos;
	}

	private void generateMetaInfo() {
		Map<String, Integer> commands = new TreeMap<String, Integer>();
		List<Section> temp_sections = new LinkedList<Section>();
		int len = length();

		for (int p = 0; p < indexers[NON_ESC_BACKSLASH].size(); p++) {
			int i = indexers[NON_ESC_BACKSLASH].getValueAt(p);
			int j = endIndexLongestLettersSubsequence(i + 1, len);
			if (j - i > 1) {
				String cmd = substring(this, i, j);
				Integer freq = commands.get(cmd);
				commands.put(cmd, freq == null ? 1 : freq + 1);
				if (sectioning_command_pattern.matcher(cmd).matches()) {
					// int k = CSUtils.endIndexArgument(this, j, len);
					// // texbook does not use \subsection in LaTeX way
					// if (k > j) {
					// String title = substring(this, j + 1, k - 1);
					// temp_sections.add(new Section(title, i));
					// }
				}
			}
		}

		// Select the frequently used commands
		Iterator<String> cmditer = commands.keySet().iterator();
		Set<String> freq_used_cmds = new TreeSet<String>();
		while (cmditer.hasNext()) {
			String cmd = cmditer.next();
			if (commands.get(cmd) >= 10)
				freq_used_cmds.add(cmd);
		}
		sections = temp_sections;
		is_outline_modified = false;
		// external_files = new TreeSet<String>();
	}

	public File getFile() {
		return file;
	}

	public String[] getFrequentlyUsedCommands() {
		// TODO Implement
		return null;
	}

	public String[] getNewCommands() {
		// TODO Implement
		return null;
	}

	public List<Section> getOutLine() {
		if (sections == null || is_outline_modified)
			generateMetaInfo();
		return sections;
	}

	public OutlineAdapter getOutlineAdapter() {
		return outline_adapter;
	}

	@Override
	public int getSpanEnd(Object what) {
		if (what instanceof TeXTokenSpan) {
			TeXTokenSpan ttsp = (TeXTokenSpan) what;
			int position = ttsp.getPosition();
			int len = length();
			// If this span is for a line comment
			if (ttsp.isComment()) {
				int nearest_linefeed = indexers[NEWLINE].getValueAt(indexers[NEWLINE].findFirst(position));
				return nearest_linefeed < 0 ? len : nearest_linefeed;
			}

			// If not, then it is either a control sequence or (non-escaped) special symbol
			if (charAt(position) == '\\')
				return CSUtils.getEndOfControlSequenceAt(this, position);
			return position + 1;
		}
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
			int nlend = indexers[NEWLINE].findFirst(end);
			end = indexers[NEWLINE].getValueAt(nlend);
			if (end < 0) // no new line after original `end` ==> last line
				end = length();
			// If [start..end) is not the first line then set start to first character after the previous new line;
			// otherwise set it to the beginning of text
			start = (nlend > 0) ? indexers[NEWLINE].getValueAt(nlend - 1) + 1 : 0;

			// Percents in [start..end) are the [pcistart..pciend)^th percent of the whole text
			int pcistart = indexers[LINE_COMMENT].findFirst(start);
			int pciend = indexers[LINE_COMMENT].findFirst(end);

			// Go through each occurrences and pick up the first non-escaped one
			int pci_nonesc = pcistart;
			for (; pci_nonesc < pciend; pci_nonesc++) {
				int pcpos = indexers[LINE_COMMENT].getValueAt(pci_nonesc);
				if (nonescbs_indexer.isNonEscaped(this, pcpos))
					// Make sure that this is not escaped % to break
					break;
			}
			int comment_start = end;
			int has_comment = 0;
			if (pci_nonesc < pciend) {
				// We found some non-escaped % on this line
				comment_start = indexers[LINE_COMMENT].getValueAt(pci_nonesc);
				has_comment = 1;
			}

			// Pick up the occurrences of special symbols from [start..comment_start)
			// int bsstart = indexers[SPECIAL].findFirst(start);
			// int bsend = indexers[SPECIAL].findFirst(comment_start);
			int bsstart = indexers[NON_ESC_BACKSLASH].findFirst(start);
			int bsend = indexers[NON_ESC_BACKSLASH].findFirst(comment_start);
			int ssstart = indexers[NON_ESC_SPECIAL].findFirst(start);
			int ssend = indexers[NON_ESC_SPECIAL].findFirst(comment_start);

			// Now we are ready to produce the result
			TeXTokenSpan[] result = new TeXTokenSpan[ssend - ssstart + bsend - bsstart + has_comment];
			if (has_comment > 0)
				result[0] = new TeXTokenSpan(this, comment_start, true);
			int d = has_comment - bsstart;
			for (int bs = bsstart; bs < bsend; bs++) {
				int pos = indexers[NON_ESC_BACKSLASH].getValueAt(bs);
				result[bs + d] = new TeXTokenSpan(this, pos, false);
			}
			d = has_comment + bsend - bsstart - ssstart;
			for (int bs = ssstart; bs < ssend; bs++) {
				int pos = indexers[NON_ESC_SPECIAL].getValueAt(bs);
				result[bs + d] = new TeXTokenSpan(this, pos, false);
			}

			// Update the cache to prevent re-computation
			cached_line_spans = result;
			cached_line_start = start;
			cached_line_end = end;
			return (T[]) result;
		}
		return super.getSpans(start, end, type);
	}

	@Override
	public int getSpanStart(Object what) {
		if (what instanceof TeXTokenSpan) {
			TeXTokenSpan ttsp = (TeXTokenSpan) what;
			return ttsp.getPosition();
		}
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
		long[] affected = new long[indexers.length];

		// Obtain affected regions for each indexer; need to do this IN REVERSE DEPENDENCY ORDER
		for (int i = indexers.length - 1; i >= 0; i--)
			affected[i] = indexers[i].beforeSequenceChanged(this, start, end);

		// Invoke superclass's method to update content and notify views
		super.replace(start, end, tb, tbstart, tbend);

		// Update the indexers after the text is replaced IN DEPENDENCY ORDER
		for (int i = 0; i < indexers.length; i++)
			indexers[i].afterSequenceChanged(this, affected[i], len_diff);

		// BUG: need to do these before all notifications
		is_modified = true;
		is_outline_modified = true;
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
		file = new_file;
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
