package lah.texpert;

import java.util.regex.Pattern;

import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;

/**
 * This class is optimized to support editing LaTeX content.
 * 
 * Unlike {@link SpannableStringBuilder}, this class makes use of certain assumptions such as non-overlapping spans.
 * 
 * TODO Command option between { and }
 * 
 * TODO Referenced resources via \input, \includegraphics, etc
 * 
 * TODO Verbatim and listing
 * 
 * TODO Add math environments equation, align*, ... as well
 * 
 * TODO consider block comment via comments environment
 * 
 * @author L.A.H.
 * 
 */
@SuppressWarnings("unused")
public class LaTeXStringBuilder extends SpannableStringBuilder {

	static class CommandSpan extends PositionSpan {

		static final Pattern PATTERN = Pattern.compile("(\\\\([A-Za-z]+\\*?|[\\\\\\$%&#_~\\^\\{\\}]))");

		public CommandSpan(int position) {
			super(position);
		}

		@Override
		public void updateDrawState(TextPaint ds) {
			ds.setColor(0xFF0000FF); // blue
		}
	}

	static class CommentSpan extends PositionSpan {

		static final Pattern PATTERN = Pattern.compile("(%.*\\n)");

		public CommentSpan(int position) {
			super(position);
		}

		@Override
		public void updateDrawState(TextPaint ds) {
			ds.setColor(0xFFA0A0A0); // gray
		}
	}

	static class FormulaSpan extends PositionSpan {

		static final Pattern PATTERN = Pattern.compile("(\\$([^\\$]|\\\\\\$)+\\$|\\$\\$([^\\$]|\\\\\\$)+\\$\\$)");

		public FormulaSpan(int position) {
			super(position);
		}

		@Override
		public void updateDrawState(TextPaint ds) {
			ds.setColor(0xFF00CC00); // green
		}
	}

	private static abstract class PositionSpan extends CharacterStyle implements UpdateAppearance,
			Comparable<PositionSpan> {

		protected int position;

		public PositionSpan(int position) {
			this.position = position;
		}

		@Override
		public int compareTo(PositionSpan span) {
			return position - span.position;
		}

		public int getSpanEnd(CharSequence text) {
			return position + 1;
		}

		public int getSpanStart() {
			return position;
		}

		public void updatePosition(int position) {
			this.position = position;
		}
	}

	static class SpecialCharacterSpan extends PositionSpan {

		// Special characters: \ $ % & # _ ~ ^ { }
		static final Pattern PATTERN = Pattern.compile("([\\\\\\$%&#_~\\^\\{\\}])");

		public SpecialCharacterSpan(int position) {
			super(position);
		}

		@Override
		public int getSpanEnd(CharSequence text) {
			return position + 1;
		}

		@Override
		public void updateDrawState(TextPaint ds) {
			ds.setColor(0xFFCC8000); // yellow
		}
	}

	/**
	 * TeX and LaTeX text patterns for syntax highlighting purposes, the first group depicted in the pattern will be
	 * highlighted using the style described in {@link DocumentAdapter#LATEX_STYLES}.
	 * 
	 */
	private static final Pattern[] LATEX_PATTERNS = {
			// Special characters: \ $ % & # _ ~ ^ { }
			Pattern.compile("([\\\\\\$%&#_~\\^\\{\\}])"),
			// Command and escaped special characters
			Pattern.compile("(\\\\([A-Za-z]+\\*?|[\\\\\\$%&#_~\\^\\{\\}]))"),
			// Formula
			Pattern.compile("(\\$([^\\$]|\\\\\\$)+\\$|\\$\\$([^\\$]|\\\\\\$)+\\$\\$)"),
			// Line comment
			Pattern.compile("(%.*\\n)") };

	private PositionSpan[] mPositionSpan;

	public LaTeXStringBuilder(CharSequence text) {
		super(text);
		// Annotate with styles TODO Make this more efficient!
		// for (int i = 0; i < LATEX_PATTERNS.length; i++) {
		// Matcher matcher = LATEX_PATTERNS[i].matcher(this);
		// // special character: check if it is escaped? TODO This is wrong in some case!
		// while (matcher.find()) {
		// int s = matcher.start(1);
		// int e = matcher.end(1);
		// switch (i) {
		// case 0:
		// if (s > 0 && text.charAt(s - 1) == '\\')
		// continue;
		// else
		// setSpan(new SpecialCharacterSpan(s), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		// break;
		// case 1:
		// setSpan(new CommandSpan(s), s, e, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
		// break;
		// case 2:
		// setSpan(new FormulaSpan(s), s, e, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
		// break;
		// case 3:
		// default:
		// setSpan(new CommentSpan(s), s, e, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
		// break;
		// }
		// }
		// }
	}

	public void replaceSelection(String content) {
		int sel_start = Selection.getSelectionStart(this);
		int sel_end = Selection.getSelectionEnd(this);
		if (0 <= sel_start && sel_start <= sel_end)
			replace(sel_start, sel_end, content);
	}

	// @Override
	// public void setSpan(Object what, int start, int end, int flags) {
	// if (what instanceof PositionSpan) {
	// return;
	// }
	// super.setSpan(what, start, end, flags);
	// }
	//
	// @SuppressWarnings("unchecked")
	// @Override
	// public <T> T[] getSpans(int start, int end, Class<T> type) {
	// if (type == CharacterStyle.class) {
	// return (T[]) mPositionSpan;
	// }
	// return super.getSpans(start, end, type);
	// }
	//
	// @Override
	// public int getSpanStart(Object tag) {
	// if (tag instanceof PositionSpan) {
	//
	// }
	// return 0;
	// }
	//
	// public int getSpanEnd(Object tag) {
	// if (tag instanceof PositionSpan) {
	//
	// }
	// return 0;
	// }
	//
	// @Override
	// public int getSpanFlags(Object tag) {
	// if (tag instanceof PositionSpan) {
	// }
	// return 0;
	// }
}
