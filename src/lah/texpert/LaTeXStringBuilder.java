package lah.texpert;

import java.util.regex.Pattern;

import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.util.Log;

/**
 * This extension of {@link SpannableStringBuilder} is to provide better performance editing LaTeX content. This object
 * also forks a thread to rebuild the data structure in the background.
 * 
 * @author L.A.H.
 * 
 */
public class LaTeXStringBuilder extends SpannableStringBuilder {

	private static final String TAG = "LaTeXStringBuilder";

	CharacterIndexer[] indexers;

	static final Pattern // Special characters: \ $ % & # _ ~ ^ { }
			PATTERN_SYMBOLS = Pattern.compile("([\\\\\\$%&#_~\\^\\{\\}])"),
			// Command and escaped special characters
			PATTERN_COMMAND = Pattern.compile("(\\\\([A-Za-z]+\\*?|[\\\\\\$%&#_~\\^\\{\\}]))"),
			// Formula
			PATTERN_FORMULA = Pattern.compile("(\\$([^\\$]|\\\\\\$)+\\$|\\$\\$([^\\$]|\\\\\\$)+\\$\\$)"),
			// Line comment
			PATTERN_COMMENT = Pattern.compile("(%.*\\n)");

	/**
	 * TeX and LaTeX text patterns for syntax highlighting purposes, the first group depicted in the pattern will be
	 * highlighted using the style described in {@link DocumentAdapter#LATEX_STYLES}.
	 */
	static final Pattern[] LATEX_PATTERNS = {
			// Special characters: \ $ % & # _ ~ ^ { }
			Pattern.compile("([\\\\\\$%&#_~\\^\\{\\}])"),
			// Command and escaped special characters
			Pattern.compile("(\\\\([A-Za-z]+\\*?|[\\\\\\$%&#_~\\^\\{\\}]))"),
			// Formula
			Pattern.compile("(\\$([^\\$]|\\\\\\$)+\\$|\\$\\$([^\\$]|\\\\\\$)+\\$\\$)"),
			// Line comment
			Pattern.compile("(%.*\\n)") };

	/**
	 * Range to notify about position span changes
	 */
	int notification_start, notification_end;

	static final int BACKSLASH = 0, PERCENT = 1, NEWLINE = 2;

	public LaTeXStringBuilder(CharSequence text) {
		super(text);
		// initialize the indices
		indexers = new CharacterIndexer[3];
		indexers[BACKSLASH] = new CharacterIndexer(text, '\\');
		indexers[PERCENT] = new CharacterIndexer(text, '%');
		indexers[NEWLINE] = new CharacterIndexer(text, '\n');
	}

	@Override
	public int getSpanEnd(Object what) {
		if (what instanceof TeXTokenSpan) {
			return ((TeXTokenSpan) what).getSpanEnd(this);
		}
		return super.getSpanEnd(what);
	}

	@Override
	public int getSpanFlags(Object what) {
		if (what instanceof TeXTokenSpan) {
			return SPAN_EXCLUSIVE_EXCLUSIVE;
		}
		return super.getSpanFlags(what);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T[] getSpans(int start, int end, Class<T> type) {
		if (type == CharacterStyle.class) {
			// Let us first handle only a simplistic case here: [start..end) is just a text line
			Log.v(TAG, "getSpans start=" + start + "; end=" + end + "; content=" + subSequence(start, end).toString());

			// Extend the range to contain a full line of text
			// Assumption: [start..end) is a subsequence of a single line
			int nlend = indexers[NEWLINE].findFirst(end);
			end = indexers[NEWLINE].get(nlend);
			// if [start..end) is not the first line then set start to first character after the previous new line;
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

			// Pick up the occurrences of backslashes from [start..comment_start)
			int bsstart = indexers[BACKSLASH].findFirst(start);
			int bsend = indexers[BACKSLASH].findFirst(comment_start);

			// Now we are ready to produce the result
			Log.v(TAG, "comment_start=" + comment_start + "; bsstart=" + bsstart + "; bsend=" + bsend);
			TeXTokenSpan[] result = new TeXTokenSpan[bsend - bsstart + has_comment];
			if (has_comment > 0)
				result[0] = new TeXTokenSpan(this, comment_start);
			for (int bs = bsstart; bs < bsend; bs++)
				result[bs - bsstart + has_comment] = new TeXTokenSpan(this, indexers[BACKSLASH].get(bs));

			return (T[]) result;
		}
		return super.getSpans(start, end, type);
	}

	@Override
	public int getSpanStart(Object what) {
		if (what instanceof TeXTokenSpan) {
			return ((TeXTokenSpan) what).getSpanStart();
		}
		return super.getSpanStart(what);
	}

	@Override
	public LaTeXStringBuilder replace(int start, int end, CharSequence tb, int tbstart, int tbend) {
		// for (int i = 0; i < 3; i++)
		// indexers[i].onReplace(start, end, tb, tbstart, tbend);
		super.replace(start, end, tb, tbstart, tbend);
		return this;
	}

	/**
	 * Replace the text within selected region with new content
	 * 
	 * @param content
	 *            String to replace
	 */
	public void replaceSelection(String content) {
		int sel_start = Selection.getSelectionStart(this);
		int sel_end = Selection.getSelectionEnd(this);
		if (0 <= sel_start && sel_start <= sel_end)
			replace(sel_start, sel_end, content);
	}

	@Override
	public void setSpan(Object what, int start, int end, int flags) {
		if (what == Selection.SELECTION_START) {
			// update the region of interest (for notification)
			notification_start = Math.max(start - 1024, 0);
			notification_end = Math.min(start + 1024, length());
			Log.v(TAG, "Update region of interest " + notification_start + ".." + notification_end);
		}
		super.setSpan(what, start, end, flags);
	}
}
