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

import lah.index.CharIndexer;
import lah.index.CharsSetIndexer;
import lah.index.SingleCharIndexer;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.UpdateAppearance;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

/**
 * This extension of {@link SpannableStringBuilder} is to provide better syntax highlighting performance editing LaTeX
 * content.
 * 
 * @author L.A.H.
 * 
 */
public class LaTeXStringBuilder extends SpannableStringBuilder {

	private static CharIndexer[] indexers;

	static final int PERCENT = 0, NEWLINE = 1, SPECIAL = 2, BACKSLASH = 3;

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

	private LaTeXEditingActivity host_activity;

	private boolean is_modified;

	private boolean is_outline_modified;

	private List<Section> sections;

	private EditText view;

	public LaTeXStringBuilder(LaTeXEditingActivity activity, CharSequence text, File file) {
		super(text);

		this.host_activity = activity;
		this.is_modified = false;
		this.file = file;
		edit_actions = new LinkedList<EditAction>();

		// Initialize the indexers
		if (indexers == null)
			indexers = new CharIndexer[4];
		for (int i = 0; i < indexers.length; i++) {
			if (indexers[i] == null) {
				switch (i) {
				case PERCENT:
					indexers[i] = new SingleCharIndexer(text, '%');
					break;
				case NEWLINE:
					indexers[i] = new SingleCharIndexer(text, '\n');
					break;
				case SPECIAL:
					// '~', '#', '*', '(', ')', '[', ']'
					indexers[i] = new CharsSetIndexer(text, '\\', '{', '}', '$', '&', '_', '^', '%');
					break;
				case BACKSLASH:
					indexers[i] = new SingleCharIndexer(text, '\\');
					break;
				}
			} else {
				indexers[i].initialize(text);
			}
		}
	}

	/**
	 * Find the index of a LaTeX argument enclosed in curly braces at a position
	 * 
	 * @param pos
	 * @param len
	 * @return
	 */
	private int endIndexArgument(int pos, int len) {
		char c = charAt(pos);
		if (c != '{')
			return pos - 1;
		int braces = 1;
		pos = pos + 1;
		while (pos < len && braces > 0) {
			c = charAt(pos);
			switch (c) {
			case '{':
				braces++;
				break;
			case '}':
				braces--;
				break;
			}
			pos++;
		}
		return pos;
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

		for (int p = 0; p < indexers[BACKSLASH].size(); p++) {
			int i = indexers[BACKSLASH].get(p);
			int j = endIndexLongestLettersSubsequence(i + 1, len);
			if (j - i > 1) {
				String cmd = substring(this, i, j);
				Integer freq = commands.get(cmd);
				commands.put(cmd, freq == null ? 1 : freq + 1);
				if (sectioning_command_pattern.matcher(cmd).matches()) {
					int k = endIndexArgument(j, len);
					// texbook does not use \subsection in LaTeX way
					if (k > j) {
						String title = substring(this, j + 1, k - 1);
						temp_sections.add(new Section(title, i));
					}
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

	@Override
	public int getSpanEnd(Object what) {
		if (what instanceof TeXTokenSpan) {
			TeXTokenSpan ttsp = (TeXTokenSpan) what;
			int position = ttsp.getPosition();
			int len = length();
			// If this span is for a line comment
			if (ttsp.isComment()) {
				int nearest_linefeed = indexers[NEWLINE].get(indexers[NEWLINE].findFirst(position));
				return nearest_linefeed < 0 ? len : nearest_linefeed;
			}
			// If not, then this span starts with a special character

			if (charAt(position) == '\\') {
				// TODO Make sure that this is not escaped
				return endIndexLongestLettersSubsequence(position + 1, len);
			}
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
			end = indexers[NEWLINE].get(nlend);
			if (end < 0) // no new line after original `end` ==> last line
				end = length();
			// If [start..end) is not the first line then set start to first character after the previous new line;
			// otherwise set it to the beginning of text
			start = (nlend > 0) ? indexers[NEWLINE].get(nlend - 1) + 1 : 0;

			// Percents in [start..end) are the [pcistart..pciend)^th percent of the whole text
			int pcistart = indexers[PERCENT].findFirst(start);
			int pciend = indexers[PERCENT].findFirst(end);

			// Go through each occurrences and pick up the first non-escaped one
			int pci_nonesc = pcistart;
			for (; pci_nonesc < pciend; pci_nonesc++) {
				int pcpos = indexers[PERCENT].get(pci_nonesc);
				if (isNonEscaped(pcpos))
					// Make sure that this is not escaped % to break
					// TODO Do the same for escaped command as well
					break;
			}
			int comment_start = end;
			int has_comment = 0;
			if (pci_nonesc < pciend) {
				// We found some non-escaped % on this line
				comment_start = indexers[PERCENT].get(pci_nonesc);
				has_comment = 1;
			}

			// Pick up the occurrences of special symbols from [start..comment_start)
			int bsstart = indexers[SPECIAL].findFirst(start);
			int bsend = indexers[SPECIAL].findFirst(comment_start);

			// Now we are ready to produce the result
			TeXTokenSpan[] result = new TeXTokenSpan[bsend - bsstart + has_comment];
			if (has_comment > 0)
				result[0] = new TeXTokenSpan(this, comment_start, true);
			for (int bs = bsstart; bs < bsend; bs++) {
				int pos = indexers[SPECIAL].get(bs);
				// TODO Skip consecutive backslashes & annotate if a backslash has escape effect
				result[bs - bsstart + has_comment] = new TeXTokenSpan(this, pos, false);
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

	boolean isLetter(char c) {
		return ('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z');
	}

	public boolean isModified() {
		return is_modified;
	}

	/**
	 * Determine if the character at a specific position is escaped i.e. the backslash (if any) right before it does not
	 * function as an escaped character. This can be determined by checking if the maximum number of backslashes right
	 * before the character is even.
	 * 
	 * @param position
	 *            The position to check
	 * @return {@code true} if this position is not backslash-escaped
	 */
	public boolean isNonEscaped(int position) {
		// int num_backslash_bef = 0;
		// Find the position of farthest backslash right before character at position
		int fbspos = position - 1;
		while (fbspos >= 0 && charAt(fbspos) == '\\') {
			// num_backslash_bef++;
			fbspos--;
		}
		// Note: Maximum number of backslash before `position` is `position - fbspos - 1`
		return ((position - fbspos - 1) & 1) == 0;
	}

	@Override
	public LaTeXStringBuilder replace(int start, int end, CharSequence tb, int tbstart, int tbend) {
		return replace(start, end, tb, tbstart, tbend, true);
	}

	private LaTeXStringBuilder replace(int start, int end, CharSequence tb, int tbstart, int tbend,
			boolean save_action_for_undo) {
		// Update the indexers and then invoke superclass's method
		if (save_action_for_undo) {
			// Kick out long-performed actions
			if (edit_actions.size() == 256)
				edit_actions.removeLast();
			edit_actions.push(new EditAction(start, substring(this, start, end), substring(tb, tbstart, tbend)));
		}
		for (int i = 0; i < indexers.length; i++)
			indexers[i].replace(this, start, end, tb, tbstart, tbend);
		super.replace(start, end, tb, tbstart, tbend);
		if (host_activity != null)
			host_activity.notifyDocumentStateChanged();
		is_modified = true;
		is_outline_modified = true;
		cached_line_spans = null; // Invalidate cache after replacement
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

	public void save(File new_file, Runnable action_after_saved) {
		try {
			FileWriter writer = new FileWriter(new_file, false);
			writer.write(toString());
			writer.close();
			file = new_file;
			is_modified = false;
			if (action_after_saved != null)
				action_after_saved.run();
			host_activity.notifyDocumentStateChanged();
		} catch (Exception e) {
			Toast.makeText(host_activity, host_activity.getString(R.string.message_cannot_save_document),
					Toast.LENGTH_SHORT).show();
			e.printStackTrace(System.out);
		}
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
			// Must not save this replacement; otherwise, we cannot make a next undo
			replace(pos, pos + aft.length(), bef, 0, bef.length(), false);
		}
		return true;
	}

	public interface DocumentWatcher {
		
		void notifyDocumentStateChanged();
		
	}

	static class EditAction {

		String before, after;

		int replace_pos;

		public EditAction(int pos, String bef, String aft) {
			replace_pos = pos;
			before = bef;
			after = aft;
		}

	}

	public static class Section {

		int position;

		String title;

		SectionType type;

		public Section(String title, int position) {
			this.title = title;
			this.position = position;
		}

		public int getTextPosition() {
			return position;
		}

		@Override
		public String toString() {
			return title;
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
		static final int COLOR_COMMAND = 0xFF0000FF, COLOR_COMMENT = 0xFFA0A0A0, COLOR_SYMBOLS = 0xFFCC8000,
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
				ds.setColor(COLOR_COMMAND);
				break;
			case FLAG_COMMENT:
				ds.setColor(COLOR_COMMENT);
				break;
			default:
				ds.setColor(COLOR_SYMBOLS);
			}
		}
	}

}
