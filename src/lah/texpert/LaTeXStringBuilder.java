package lah.texpert;

import java.io.File;
import java.io.FileWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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

	public interface DocumentStatListener {

		void updateCommandList(String[] commands);

		void updateExternalResourceList(String[] externals);

		void updateLabelList(String[] labels);

		void updateSectionList(String[] sections);

	}

	private static CharIndexer[] indexers;

	static final int PERCENT = 0, NEWLINE = 1, SPECIAL = 2;

	Map<String, Integer> commands;

	Set<String> external_files;

	private File file;

	private LaTeXEditingActivity host_activity;

	private boolean is_modified;

	Map<String, Integer> sections;

	private DocumentStatListener stat_listener;

	public LaTeXStringBuilder(LaTeXEditingActivity activity, CharSequence text, File file) {
		super(text);

		this.host_activity = activity;
		this.is_modified = false;
		this.file = file;

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
	
	public void cacheStats() {
		commands = new TreeMap<String, Integer>();
		int len = length();
		
		// TODO use special char indexer to quickly jump to next special symbols
		int i = 0;
		while (i < len) {
			if (charAt(i) == '\\') {
				int j = endIndexLongestLettersSubsequence(i + 1, len);
				if (j - i > 1) {
					String cmd = subString(i, j);
					Integer freq = commands.get(cmd);
					commands.put(cmd, freq == null ? 1 : freq + 1);
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
		if (stat_listener != null)
			stat_listener.updateCommandList(freq_used_cmds.toArray(new String[freq_used_cmds.size()]));

		// sections = new TreeMap<String, Integer>();
		// external_files = new TreeSet<String>();
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
	int endIndexLongestLettersSubsequence(int pos, int len) {
		while (pos < len) {
			char c = charAt(pos);
			if (('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z'))
				pos++;
			else
				break;
		}
		return pos;
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
	 * Assumption: [start..end) is a subsequence of a single text line!
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T> T[] getSpans(int start, int end, Class<T> type) {
		if (type == CharacterStyle.class) {
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
			// TODO Cache result for efficiency
			TeXTokenSpan[] result = new TeXTokenSpan[bsend - bsstart + has_comment];
			if (has_comment > 0)
				result[0] = new TeXTokenSpan(this, comment_start, true);
			for (int bs = bsstart; bs < bsend; bs++) {
				int pos = indexers[SPECIAL].get(bs);
				// TODO Skip consecutive backslashes & annotate if a backslash has escape effect
				result[bs - bsstart + has_comment] = new TeXTokenSpan(this, pos, false);
			}
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
		// Update the indexers and then invoke superclass's method
		for (int i = 0; i < indexers.length; i++)
			indexers[i].replace(this, start, end, tb, tbstart, tbend);
		super.replace(start, end, tb, tbstart, tbend);
		if (host_activity != null)
			host_activity.notifyDocumentStateChanged();
		is_modified = true;
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

	public void setDocumentStatListener(DocumentStatListener listener) {
		stat_listener = listener;
	}

	private String subString(int s, int j) {
		char[] data = new char[j - s];
		for (int k = s; k < j; k++)
			data[k - s] = charAt(k);
		return new String(data);
	}

}
