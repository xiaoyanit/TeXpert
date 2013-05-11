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

import lah.texpert.indexing.CharIndexer;
import lah.texpert.indexing.CharsSetIndexer;
import lah.texpert.indexing.SingleCharIndexer;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
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

	static final int PERCENT = 0, NEWLINE = 1, SPECIAL = 2;

	static final Pattern sectioning_command_pattern = Pattern
			.compile("\\\\(part|chapter|section|subsection|subsubsection|subsubsubsection)");

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

	private CommandListener command_listener;

	private LinkedList<EditAction> edit_actions;

	private File file;

	private LaTeXEditingActivity host_activity;

	private boolean is_modified;

	private OutlineListener outline_listener;

	public LaTeXStringBuilder(LaTeXEditingActivity activity, CharSequence text, File file) {
		super(text);

		this.host_activity = activity;
		this.is_modified = false;
		this.file = file;
		edit_actions = new LinkedList<EditAction>();

		// Initialize the indexers
		if (indexers == null)
			indexers = new CharIndexer[3];
		for (int i = 0; i < indexers.length; i++) {
			if (indexers[i] == null) {
				switch (i) {
				case PERCENT:
					indexers[PERCENT] = new SingleCharIndexer(text, '%');
					break;
				case NEWLINE:
					indexers[NEWLINE] = new SingleCharIndexer(text, '\n');
					break;
				case SPECIAL:
					indexers[SPECIAL] = new CharsSetIndexer(text, '\\', '{', '}', '$', '&', '#', '_', '^', '~', '%',
							'*', '(', ')', '[', ']');
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
		int braces = 1;
		assert c == '{';
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

	public void generateMetaInfo() {
		Map<String, Integer> commands = new TreeMap<String, Integer>();
		List<String> sections = new LinkedList<String>();
		List<Integer> sections_pos = new LinkedList<Integer>();
		int len = length();

		// TODO use special char indexer to quickly jump to next special symbols
		int i = 0;
		while (i < len) {
			if (charAt(i) == '\\') {
				int j = endIndexLongestLettersSubsequence(i + 1, len);
				if (j - i > 1) {
					String cmd = substring(this, i, j);
					Integer freq = commands.get(cmd);
					commands.put(cmd, freq == null ? 1 : freq + 1);
					if (sectioning_command_pattern.matcher(cmd).matches()) {
						int k = endIndexArgument(j, len);
						sections.add(substring(this, j + 1, k - 1));
						sections_pos.add(i);
					}
				}
				i = j;
			} else
				i++;
		}

		// Select the frequently used commands
		Iterator<String> cmditer = commands.keySet().iterator();
		Set<String> freq_used_cmds = new TreeSet<String>();
		while (cmditer.hasNext()) {
			String cmd = cmditer.next();
			if (commands.get(cmd) >= 10)
				freq_used_cmds.add(cmd);
		}
		if (command_listener != null) {
			command_listener.onCommandListChanged(freq_used_cmds.toArray(new String[freq_used_cmds.size()]));
		}
		if (outline_listener != null) {
			int[] sect_pos = new int[sections_pos.size()];
			int t = 0;
			for (Integer k : sections_pos) {
				sect_pos[t] = k;
				t++;
			}
			outline_listener.onOutlineChanged(sections.toArray(new String[sections.size()]), sect_pos);
		}
		// external_files = new TreeSet<String>();
	}

	public File getFile() {
		return file;
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

	public boolean searchNext(String search_pattern) {
		int sel_start = Selection.getSelectionStart(this);
		if (0 <= sel_start && sel_start < length()) {
			Matcher m = Pattern.compile(search_pattern).matcher(this);
			if (m.find(sel_start)) {
				Selection.setSelection(this, m.start(), m.end());
				return true;
			}
		}
		return false;
	}

	public void setCommandListener(CommandListener listener) {
		command_listener = listener;
	}

	public void setOutlineListener(OutlineListener listener) {
		outline_listener = listener;
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

	public interface CommandListener {

		void onCommandListChanged(String[] commands);

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

	public interface OutlineListener {

		void onOutlineChanged(String[] sections, int[] sections_pos);

	}

}
