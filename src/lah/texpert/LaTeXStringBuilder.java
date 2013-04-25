package lah.texpert;

import lah.texpert.indexing.CharIndexer;
import lah.texpert.indexing.CharsSetIndexer;
import lah.texpert.indexing.SingleCharIndexer;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;

/**
 * This extension of {@link SpannableStringBuilder} is to provide better performance editing LaTeX content.
 * 
 * @author L.A.H.
 * 
 */
public class LaTeXStringBuilder extends SpannableStringBuilder {

	static final int PERCENT = 0, NEWLINE = 1, SPECIAL = 2;

	private CharIndexer[] indexers;

	public LaTeXStringBuilder(CharSequence text) {
		super(text);
		// Initialize the indices
		indexers = new CharIndexer[3];
		indexers[PERCENT] = new SingleCharIndexer(text, '%');
		indexers[NEWLINE] = new SingleCharIndexer(text, '\n');
		indexers[SPECIAL] = new CharsSetIndexer(text, '\\', '{', '}', '$', '&', '#', '^', '~', '%');
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
				return e; // If the following char is not letter, this is automatically `position + 1`
			}
			return position + 1;
		}
		return super.getSpanEnd(what);
	}

	// @Override
	// public int getSpanFlags(Object what) {
	// if (what instanceof TeXTokenSpan) {
	// return SPAN_EXCLUSIVE_EXCLUSIVE;
	// }
	// return super.getSpanFlags(what);
	// }

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
			// If [start..end) is not the first line then set start to first character after the previous new line;
			// otherwise set it to the beginning of text
			start = (nlend >= 1) ? indexers[NEWLINE].get(nlend - 1) + 1 : 0;
			if (end < 0) // no more new line after end
				end = length();

			// Percents in [start..end) are the [pcistart..pciend)^th percent of the whole text
			int pcistart = indexers[PERCENT].findFirst(start);
			int pciend = indexers[PERCENT].findFirst(end);

			// Go through each occurrences and pick up the first non-escaped one
			int pci_nonesc = pcistart;
			for (; pci_nonesc < pciend; pci_nonesc++) {
				int pcpos = indexers[PERCENT].get(pci_nonesc);
				if (pcpos >= 1 && charAt(pcpos - 1) != '\\')
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
			for (int bs = bsstart; bs < bsend; bs++)
				result[bs - bsstart + has_comment] = new TeXTokenSpan(this, indexers[SPECIAL].get(bs), false);
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

	@Override
	public LaTeXStringBuilder replace(int start, int end, CharSequence tb, int tbstart, int tbend) {
		// Update the indexers and then invoke superclass's method
		for (int i = 0; i < indexers.length; i++)
			indexers[i].onReplace(start, end, tb, tbstart, tbend);
		super.replace(start, end, tb, tbstart, tbend);
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

}
