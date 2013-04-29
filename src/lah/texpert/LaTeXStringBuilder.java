package lah.texpert;

import java.io.File;
import java.io.FileWriter;

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

	private File file;

	private LaTeXEditingActivity host_activity;

	private boolean is_modified;

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
				// Pick up the consecutive letters following backslash to get the command (if this backslash is really
				// the start of a command)
				int e = position + 1;
				while (e < len) {
					char ce = charAt(e);
					if (('A' <= ce && ce <= 'Z') || ('a' <= ce && ce <= 'z'))
						e++;
					else
						break;
				}
				// If the following char is not letter, this is automatically `position + 1`
				return e;
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
				// FIX BUG: This might be wrong if we determine if this % is escaped simply by looking if the previous
				// character is a backslash! For illustration, % in " \\\\%" is also a non-escaped %!
				// TODO Fix the same mistake for escaped command or other special symbol case
				/*
				 * int pos = pcpos - 1; int num_backslash_bef = 0; while (pos >= start) { if (charAt(pos) == '\\') {
				 * num_backslash_bef++; pos--; } else break; } // An even number of backslash (0, 2, 4, etc.) before
				 * this % indicates that this is not an escaped one. if ((num_backslash_bef & 1) == 0) break;
				 */
				if (isNonEscaped(pcpos))
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
		// Position of farthest backslash before character at position
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
			host_activity.notifyDocumentModified();
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
		} catch (Exception e) {
			Toast.makeText(host_activity, host_activity.getString(R.string.message_cannot_save_document),
					Toast.LENGTH_SHORT).show();
			e.printStackTrace(System.out);
		}
	}

}
