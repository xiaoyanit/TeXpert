package lah.texpert;

import java.lang.reflect.Array;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.widgets.TextArea;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.GetChars;
import android.text.InputFilter;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.Log;

/**
 * This class is optimized to support editing LaTeX content.
 */
@SuppressWarnings("unused")
public class LaTeXStringBuilder implements Editable {

	private static final int CACHE_SIZE = 73;

	private static Object[] EMPTY = new Object[0];

	private static final int END_MASK = 0x0F;

	/**
	 * TeX and LaTeX text patterns for syntax highlighting purposes, the first group depicted in the pattern will be
	 * highlighted using the style described in {@link DocumentAdapter#LATEX_STYLES}. TODO Command option between { and
	 * } TODO Referenced resources via \input, \includegraphics, etc TODO Verbatim and listing TODO Add math
	 * environments equation, align*, ... as well
	 */
	static final Pattern[] LATEX_PATTERNS = {
			// Non-escaped TeX special characters: \ $ % & # _ ~ ^ { }
			Pattern.compile("([\\\\\\$%&#_~\\^\\{\\}])"),
			// TeX command and escaped special characters:
			Pattern.compile("(\\\\([A-Za-z]+\\*?|[\\\\\\$%&#_~\\^\\{\\}]))"),
			// Math formulas
			Pattern.compile("(\\$([^\\$]|\\\\\\$)+\\$|\\$\\$([^\\$]|\\\\\\$)+\\$\\$)"),
			// TeX line comment TODO consider block comment via comments environment
			Pattern.compile("(%.*\\n)") };

	/**
	 * Corresponding styles for the above patterns
	 */
	private static final CharacterStyle[] LATEX_STYLES = {
			// Special character style: yellow
			new ForegroundColorSpan(Color.parseColor("#cc8000")),
			// Command style: blue
			new ForegroundColorSpan(Color.parseColor("#0000ff")),
			// Formula style: green
			new ForegroundColorSpan(Color.parseColor("#00cc00")),
			// Comment style: gray
			new ForegroundColorSpan(Color.parseColor("#a0a0a0")) };

	// TODO These value are tightly related to the public SPAN_MARK/POINT values in {@link Spanned}
	private static final int MARK = 1;

	private static final int PARAGRAPH = 3;

	private static final int POINT = 2;

	private static Object[] sCache = new Object[CACHE_SIZE];

	private static Object sLock = new Object();

	private static final int SPAN_END_AT_END = 0x8000;

	private static final int SPAN_END_AT_START = 0x4000;

	private static final int SPAN_START_AT_END = 0x2000;

	// These bits are not (currently) used by SPANNED flags
	private static final int SPAN_START_AT_START = 0x1000;

	private static final int SPAN_START_END_MASK = 0xF000;

	private static final int START_MASK = 0xF0;

	private static final int START_SHIFT = 4;

	private static char[] sTemp = null;

	@SuppressWarnings("unchecked")
	public static <T> T[] emptyArray(Class<T> kind) {
		if (kind == Object.class) {
			return (T[]) EMPTY;
		}
		int bucket = ((System.identityHashCode(kind) / 8) & 0x7FFFFFFF) % CACHE_SIZE;
		Object cache = sCache[bucket];
		if (cache == null || cache.getClass().getComponentType() != kind) {
			cache = Array.newInstance(kind, 0);
			sCache[bucket] = cache;
		}
		return (T[]) cache;
	}

	public static void getChars(CharSequence s, int start, int end, char[] dest, int destoff) {
		Class<? extends CharSequence> c = s.getClass();
		if (c == String.class)
			((String) s).getChars(start, end, dest, destoff);
		else if (c == StringBuffer.class)
			((StringBuffer) s).getChars(start, end, dest, destoff);
		else if (c == StringBuilder.class)
			((StringBuilder) s).getChars(start, end, dest, destoff);
		else if (s instanceof GetChars)
			((GetChars) s).getChars(start, end, dest, destoff);
		else {
			for (int i = start; i < end; i++)
				dest[destoff++] = s.charAt(i);
		}
	}

	private static boolean hasNonExclusiveExclusiveSpanAt(CharSequence text, int offset) {
		if (text instanceof Spanned) {
			Spanned spanned = (Spanned) text;
			Object[] spans = spanned.getSpans(offset, offset, Object.class);
			final int length = spans.length;
			for (int i = 0; i < length; i++) {
				Object span = spans[i];
				int flags = spanned.getSpanFlags(span);
				if (flags != Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					return true;
			}
		}
		return false;
	}

	public static int idealByteArraySize(int need) {
		for (int i = 4; i < 32; i++)
			if (need <= (1 << i) - 12)
				return (1 << i) - 12;
		return need;
	}

	public static int idealCharArraySize(int need) {
		return idealByteArraySize(need * 2) / 2;
	}

	public static int idealIntArraySize(int need) {
		return idealByteArraySize(need * 4) / 4;
	}

	static char[] obtain(int len) {
		char[] buf;
		synchronized (sLock) {
			buf = sTemp;
			sTemp = null;
		}
		if (buf == null || buf.length < len)
			buf = new char[idealCharArraySize(len)];
		return buf;
	}

	static void recycle(char[] temp) {
		if (temp.length > 1000)
			return;
		synchronized (sLock) {
			sTemp = temp;
		}
	}

	private static String region(int start, int end) {
		return "(" + start + " ... " + end + ")";
	}

	private int mGapLength;

	private int mGapStart;

	private int mSpanCount;

	private int mSpanCountBeforeAdd;

	/**
	 * TODO Make this efficient, there is no need for generic Object as span!
	 */
	private int[] mSpanEnds, mSpanStarts, mSpanFlags;

	private Object[] mSpans;

	/**
	 * I do not want to allow this object to have generic modification of SpanWatcher/TextWatcher. The damn issue is
	 * that {@link DynamicLayout} relies on SpanWatcher/TextWatcher for notification of changes! So I will allow for a
	 * single instance of {@link SpanWatcher} and {@link TextWatcher}.
	 */
	private SpanWatcher mDynamicLayoutSpanWatcher;

	private char[] mText;

	/**
	 * {@link TextArea} to notify about text + span changes.
	 */
	private final TextArea mTextArea;

	/**
	 * A single allowable {@link TextWatcher} that this object will notify
	 */
	private TextWatcher mDynamicLayoutTextWatcher;

	public LaTeXStringBuilder(TextArea textarea, CharSequence text) {
		mTextArea = textarea;
		int start = 0;
		int end = text.length();
		int srclen = end - start;
		if (srclen < 0)
			throw new StringIndexOutOfBoundsException();
		int len = TextArea.TextUtils.idealCharArraySize(srclen + 1);
		mText = new char[len];
		mGapStart = srclen;
		mGapLength = len - srclen;
		getChars(text, start, end, mText, 0);
		mSpanCount = 0;
		int alloc = idealIntArraySize(0);
		mSpans = new Object[alloc];
		mSpanStarts = new int[alloc];
		mSpanEnds = new int[alloc];
		mSpanFlags = new int[alloc];

		// Annotate with styles TODO Make this efficient!
		int num_spans = 0;
		for (int i = 0; i < LATEX_PATTERNS.length; i++) {
			Matcher matcher = LATEX_PATTERNS[i].matcher(this);
			if (i == 0) {
				// special character: check if it is escaped? TODO This is wrong in some case!
				while (matcher.find()) {
					int pat_start = matcher.start(1);
					int pat_end = matcher.end(1);
					if (i == 0 && pat_start > 0 && charAt(pat_start - 1) == '\\')
						continue;
					else
						setSpan(CharacterStyle.wrap(LATEX_STYLES[i]), pat_start, pat_end,
								Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
				}
			} else {
				// other patterns
				while (matcher.find()) {
					int pat_start = matcher.start(1);
					int pat_end = matcher.end(1);
					setSpan(CharacterStyle.wrap(LATEX_STYLES[i]), pat_start, pat_end,
							Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
					num_spans++;
				}
			}
		}
	}

	public LaTeXStringBuilder append(char text) {
		return append(String.valueOf(text));
	}

	public LaTeXStringBuilder append(CharSequence text) {
		int length = length();
		return replace(length, length, text, 0, text.length());
	}

	public LaTeXStringBuilder append(CharSequence text, int start, int end) {
		int length = length();
		return replace(length, length, text, start, end);
	}

	private void change(int start, int end, CharSequence cs, int csStart, int csEnd) {
		// Can be negative
		final int replacedLength = end - start;
		final int replacementLength = csEnd - csStart;
		final int nbNewChars = replacementLength - replacedLength;

		for (int i = mSpanCount - 1; i >= 0; i--) {
			int spanStart = mSpanStarts[i];
			if (spanStart > mGapStart)
				spanStart -= mGapLength;

			int spanEnd = mSpanEnds[i];
			if (spanEnd > mGapStart)
				spanEnd -= mGapLength;

			// if ((mSpanFlags[i] & SPAN_PARAGRAPH) == SPAN_PARAGRAPH) {
			// int ost = spanStart;
			// int oen = spanEnd;
			// int clen = length();
			//
			// if (spanStart > start && spanStart <= end) {
			// for (spanStart = end; spanStart < clen; spanStart++)
			// if (spanStart > end && charAt(spanStart - 1) == '\n')
			// break;
			// }
			//
			// if (spanEnd > start && spanEnd <= end) {
			// for (spanEnd = end; spanEnd < clen; spanEnd++)
			// if (spanEnd > end && charAt(spanEnd - 1) == '\n')
			// break;
			// }
			//
			// if (spanStart != ost || spanEnd != oen)
			// setSpan(mSpans[i], spanStart, spanEnd, mSpanFlags[i]);
			// }

			int flags = 0;
			if (spanStart == start)
				flags |= SPAN_START_AT_START;
			else if (spanStart == end + nbNewChars)
				flags |= SPAN_START_AT_END;
			if (spanEnd == start)
				flags |= SPAN_END_AT_START;
			else if (spanEnd == end + nbNewChars)
				flags |= SPAN_END_AT_END;
			mSpanFlags[i] |= flags;
		}

		moveGapTo(end);

		if (nbNewChars >= mGapLength) {
			resizeFor(mText.length + nbNewChars - mGapLength);
		}

		final boolean textIsRemoved = replacementLength == 0;
		// The removal pass needs to be done before the gap is updated in order to broadcast the
		// correct previous positions to the correct intersecting SpanWatchers
		if (replacedLength > 0) { // no need for span fixup on pure insertion
			// A for loop will not work because the array is being modified
			// Do not iterate in reverse to keep the SpanWatchers notified in ordering
			// Also, a removed SpanWatcher should not get notified of removed spans located
			// further in the span array.
			int i = 0;
			while (i < mSpanCount) {
				if ((mSpanFlags[i] & Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) == Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
						&& mSpanStarts[i] >= start && mSpanStarts[i] < mGapStart + mGapLength && mSpanEnds[i] >= start
						&& mSpanEnds[i] < mGapStart + mGapLength &&
						// This condition indicates that the span would become empty
						(textIsRemoved || mSpanStarts[i] > start || mSpanEnds[i] < mGapStart)) {
					removeSpan(i);
					continue; // do not increment i, spans will be shifted left in the array
				}

				i++;
			}
		}

		mGapStart += nbNewChars;
		mGapLength -= nbNewChars;

		if (mGapLength < 1)
			new Exception("mGapLength < 1").printStackTrace();

		getChars(cs, csStart, csEnd, mText, start);

		if (replacedLength > 0) { // no need for span fixup on pure insertion
			final boolean atEnd = (mGapStart + mGapLength == mText.length);
			for (int i = 0; i < mSpanCount; i++) {
				final int startFlag = (mSpanFlags[i] & START_MASK) >> START_SHIFT;
				mSpanStarts[i] = updatedIntervalBound(mSpanStarts[i], start, nbNewChars, startFlag, atEnd,
						textIsRemoved);
				final int endFlag = (mSpanFlags[i] & END_MASK);
				mSpanEnds[i] = updatedIntervalBound(mSpanEnds[i], start, nbNewChars, endFlag, atEnd, textIsRemoved);
			}
		}

		mSpanCountBeforeAdd = mSpanCount;

		// if (cs instanceof Spanned) {
		// Spanned sp = (Spanned) cs;
		// Object[] spans = sp.getSpans(csStart, csEnd, Object.class);
		//
		// for (int i = 0; i < spans.length; i++) {
		// int st = sp.getSpanStart(spans[i]);
		// int en = sp.getSpanEnd(spans[i]);
		//
		// if (st < csStart)
		// st = csStart;
		// if (en > csEnd)
		// en = csEnd;
		//
		// // Add span only if this object is not yet used as a span in this string
		// if (getSpanStart(spans[i]) < 0) {
		// setSpan(spans[i], st - csStart + start, en - csStart + start, sp.getSpanFlags(spans[i]));
		// }
		// }
		// }
	}

	/**
	 * Return the char at the specified offset within the buffer.
	 */
	public char charAt(int where) {
		int len = length();
		if (where < 0) {
			throw new IndexOutOfBoundsException("charAt: " + where + " < 0");
		} else if (where >= len) {
			throw new IndexOutOfBoundsException("charAt: " + where + " >= length " + len);
		}

		if (where >= mGapStart)
			return mText[where + mGapLength];
		else
			return mText[where];
	}

	private void checkRange(final String operation, int start, int end) {
		if (end < start) {
			throw new IndexOutOfBoundsException(operation + " " + region(start, end) + " has end before start");
		}

		int len = length();

		if (start > len || end > len) {
			throw new IndexOutOfBoundsException(operation + " " + region(start, end) + " ends beyond length " + len);
		}

		if (start < 0 || end < 0) {
			throw new IndexOutOfBoundsException(operation + " " + region(start, end) + " starts before 0");
		}
	}

	public void clear() {
		replace(0, length(), "", 0, 0);
	}

	public void clearSpans() {
		for (int i = mSpanCount - 1; i >= 0; i--) {
			int ostart = mSpanStarts[i];
			int oend = mSpanEnds[i];

			if (ostart > mGapStart)
				ostart -= mGapLength;
			if (oend > mGapStart)
				oend -= mGapLength;

			mSpanCount = i;
			mSpans[i] = null;
		}
	}

	public LaTeXStringBuilder delete(int start, int end) {
		LaTeXStringBuilder ret = replace(start, end, "", 0, 0);

		if (mGapLength > 2 * length())
			resizeFor(length());

		return ret; // == this
	}

	public void drawText(Canvas c, int start, int end, float x, float y, Paint p) {
		checkRange("drawText", start, end);
		int len = end - start;
		if (end <= mGapStart) {
			c.drawText(mText, start, len, x, y, p);
		} else if (start >= mGapStart) {
			c.drawText(mText, start + mGapLength, len, x, y, p);
		} else {
			char[] buf = obtain(len);
			getChars(start, end, buf, 0);
			c.drawText(buf, 0, len, x, y, p);
			recycle(buf);
		}
	}

	/**
	 * Copy the specified range of chars from this buffer into the specified array, beginning at the specified offset.
	 */
	public void getChars(int start, int end, char[] dest, int destoff) {
		checkRange("getChars", start, end);
		if (end <= mGapStart) {
			System.arraycopy(mText, start, dest, destoff, end - start);
		} else if (start >= mGapStart) {
			System.arraycopy(mText, start + mGapLength, dest, destoff, end - start);
		} else {
			System.arraycopy(mText, start, dest, destoff, mGapStart - start);
			System.arraycopy(mText, mGapStart + mGapLength, dest, destoff + (mGapStart - start), end - mGapStart);
		}
	}

	@Override
	public InputFilter[] getFilters() {
		return null;
	}

	/**
	 * Return the buffer offset of the end of the specified markup object, or -1 if it is not attached to this buffer.
	 */
	public int getSpanEnd(Object what) {
		int count = mSpanCount;
		Object[] spans = mSpans;

		for (int i = count - 1; i >= 0; i--) {
			if (spans[i] == what) {
				int where = mSpanEnds[i];

				if (where > mGapStart)
					where -= mGapLength;

				return where;
			}
		}

		return -1;
	}

	/**
	 * Return the flags of the end of the specified markup object, or 0 if it is not attached to this buffer.
	 */
	public int getSpanFlags(Object what) {
		int count = mSpanCount;
		Object[] spans = mSpans;
		for (int i = count - 1; i >= 0; i--) {
			if (spans[i] == what) {
				return mSpanFlags[i];
			}
		}
		return 0;
	}

	/**
	 * Return an array of the spans of the specified type that overlap the specified range of the buffer. The kind may
	 * be Object.class to get a list of all the spans regardless of type.
	 */
	@SuppressWarnings("unchecked")
	public <T> T[] getSpans(int queryStart, int queryEnd, Class<T> kind) {
		if (kind == null)
			return emptyArray(kind);

		int spanCount = mSpanCount;
		Object[] spans = mSpans;
		int[] starts = mSpanStarts;
		int[] ends = mSpanEnds;
		int[] flags = mSpanFlags;
		int gapstart = mGapStart;
		int gaplen = mGapLength;

		int count = 0;
		T[] ret = null;
		T ret1 = null;

		for (int i = 0; i < spanCount; i++) {
			int spanStart = starts[i];
			if (spanStart > gapstart) {
				spanStart -= gaplen;
			}
			if (spanStart > queryEnd) {
				continue;
			}

			int spanEnd = ends[i];
			if (spanEnd > gapstart) {
				spanEnd -= gaplen;
			}
			if (spanEnd < queryStart) {
				continue;
			}

			if (spanStart != spanEnd && queryStart != queryEnd) {
				if (spanStart == queryEnd)
					continue;
				if (spanEnd == queryStart)
					continue;
			}

			// Expensive test, should be performed after the previous tests
			if (!kind.isInstance(spans[i]))
				continue;

			if (count == 0) {
				// Safe conversion thanks to the isInstance test above
				ret1 = (T) spans[i];
				count++;
			} else {
				if (count == 1) {
					// Safe conversion, but requires a suppressWarning
					ret = (T[]) Array.newInstance(kind, spanCount - i + 1);
					ret[0] = ret1;
				}

				int prio = flags[i] & SPAN_PRIORITY;
				if (prio != 0) {
					int j;

					for (j = 0; j < count; j++) {
						int p = getSpanFlags(ret[j]) & SPAN_PRIORITY;

						if (prio > p) {
							break;
						}
					}

					System.arraycopy(ret, j, ret, j + 1, count - j);
					// Safe conversion thanks to the isInstance test above
					ret[j] = (T) spans[i];
					count++;
				} else {
					// Safe conversion thanks to the isInstance test above
					ret[count++] = (T) spans[i];
				}
			}
		}

		if (count == 0) {
			return emptyArray(kind);
		}
		if (count == 1) {
			// Safe conversion, but requires a suppressWarning
			ret = (T[]) Array.newInstance(kind, 1);
			ret[0] = ret1;
			return ret;
		}
		if (count == ret.length) {
			return ret;
		}

		// Safe conversion, but requires a suppressWarning
		T[] nret = (T[]) Array.newInstance(kind, count);
		System.arraycopy(ret, 0, nret, 0, count);
		return nret;
	}

	/**
	 * Return the buffer offset of the beginning of the specified markup object, or -1 if it is not attached to this
	 * buffer.
	 */
	public int getSpanStart(Object what) {
		int count = mSpanCount;
		Object[] spans = mSpans;

		for (int i = count - 1; i >= 0; i--) {
			if (spans[i] == what) {
				int where = mSpanStarts[i];

				if (where > mGapStart)
					where -= mGapLength;

				return where;
			}
		}

		return -1;
	}

	public LaTeXStringBuilder insert(int where, CharSequence tb) {
		return replace(where, where, tb, 0, tb.length());
	}

	public LaTeXStringBuilder insert(int where, CharSequence tb, int start, int end) {
		return replace(where, where, tb, start, end);
	}

	/**
	 * Return the number of chars in the buffer.
	 */
	public int length() {
		return mText.length - mGapLength;
	}

	private void moveGapTo(int where) {
		if (where == mGapStart)
			return;

		boolean atEnd = (where == length());

		if (where < mGapStart) {
			int overlap = mGapStart - where;
			System.arraycopy(mText, where, mText, mGapStart + mGapLength - overlap, overlap);
		} else /* where > mGapStart */{
			int overlap = where - mGapStart;
			System.arraycopy(mText, where + mGapLength - overlap, mText, mGapStart, overlap);
		}

		// XXX be more clever
		for (int i = 0; i < mSpanCount; i++) {
			int start = mSpanStarts[i];
			int end = mSpanEnds[i];

			if (start > mGapStart)
				start -= mGapLength;
			if (start > where)
				start += mGapLength;
			else if (start == where) {
				int flag = (mSpanFlags[i] & START_MASK) >> START_SHIFT;

				if (flag == POINT || (atEnd && flag == PARAGRAPH))
					start += mGapLength;
			}

			if (end > mGapStart)
				end -= mGapLength;
			if (end > where)
				end += mGapLength;
			else if (end == where) {
				int flag = (mSpanFlags[i] & END_MASK);

				if (flag == POINT || (atEnd && flag == PARAGRAPH))
					end += mGapLength;
			}

			mSpanStarts[i] = start;
			mSpanEnds[i] = end;
		}

		mGapStart = where;
	}

	/**
	 * Return the next offset after <code>start</code> but less than or equal to <code>limit</code> where a span of the
	 * specified type begins or ends.
	 */
	public int nextSpanTransition(int start, int limit, @SuppressWarnings("rawtypes") Class kind) {
		int count = mSpanCount;
		Object[] spans = mSpans;
		int[] starts = mSpanStarts;
		int[] ends = mSpanEnds;
		int gapstart = mGapStart;
		int gaplen = mGapLength;

		if (kind == null) {
			kind = Object.class;
		}

		for (int i = 0; i < count; i++) {
			int st = starts[i];
			int en = ends[i];

			if (st > gapstart)
				st -= gaplen;
			if (en > gapstart)
				en -= gaplen;

			if (st > start && st < limit && kind.isInstance(spans[i]))
				limit = st;
			if (en > start && en < limit && kind.isInstance(spans[i]))
				limit = en;
		}

		return limit;
	}

	private void removeSpan(int i) {
		int start = mSpanStarts[i];
		int end = mSpanEnds[i];

		if (start > mGapStart)
			start -= mGapLength;
		if (end > mGapStart)
			end -= mGapLength;

		int count = mSpanCount - (i + 1);
		System.arraycopy(mSpans, i + 1, mSpans, i, count);
		System.arraycopy(mSpanStarts, i + 1, mSpanStarts, i, count);
		System.arraycopy(mSpanEnds, i + 1, mSpanEnds, i, count);
		System.arraycopy(mSpanFlags, i + 1, mSpanFlags, i, count);

		mSpanCount--;

		mSpans[mSpanCount] = null;
	}

	/**
	 * Remove the specified markup object from the buffer.
	 */
	public void removeSpan(Object what) {
		for (int i = mSpanCount - 1; i >= 0; i--) {
			if (mSpans[i] == what) {
				removeSpan(i);
				return;
			}
		}
	}

	public LaTeXStringBuilder replace(int start, int end, CharSequence tb) {
		return replace(start, end, tb, 0, tb.length());
	}

	public LaTeXStringBuilder replace(final int start, final int end, CharSequence tb, int tbstart, int tbend) {
		checkRange("replace", start, end);

		final int origLen = end - start;
		final int newLen = tbend - tbstart;

		if (origLen == 0 && newLen == 0 && !hasNonExclusiveExclusiveSpanAt(tb, tbstart)) {
			// This is a no-op iif there are no spans in tb that would be added (with a 0-length)
			// Early exit so that the text watchers do not get notified
			return this;
		}

		// Try to keep the cursor / selection at the same relative position during a text replacement. If replaced or
		// replacement text length is zero, this is already taken care of.
		boolean adjustSelection = origLen != 0 && newLen != 0;
		int selectionStart = 0;
		int selectionEnd = 0;
		if (adjustSelection) {
			selectionStart = Selection.getSelectionStart(this);
			selectionEnd = Selection.getSelectionEnd(this);
		}
		change(start, end, tb, tbstart, tbend);
		if (adjustSelection) {
			if (selectionStart > start && selectionStart < end) {
				final int offset = (selectionStart - start) * newLen / origLen;
				selectionStart = start + offset;
				setSpan(Selection.SELECTION_START, selectionStart, selectionStart, Spanned.SPAN_POINT_POINT);
			}
			if (selectionEnd > start && selectionEnd < end) {
				final int offset = (selectionEnd - start) * newLen / origLen;
				selectionEnd = start + offset;
				setSpan(Selection.SELECTION_END, selectionEnd, selectionEnd, Spanned.SPAN_POINT_POINT);
			}
		}
		if (mDynamicLayoutTextWatcher != null)
			mDynamicLayoutTextWatcher.onTextChanged(this, start, origLen, newLen);
		if (mTextArea != null)
			mTextArea.onTextChanged(this, start, origLen, newLen);
		sendToSpanWatchers(start, end, newLen - origLen);
		return this;
	}

	private void resizeFor(int size) {
		final int oldLength = mText.length;
		final int newLength = TextArea.TextUtils.idealCharArraySize(size + 1);
		final int delta = newLength - oldLength;
		if (delta == 0)
			return;

		char[] newText = new char[newLength];
		System.arraycopy(mText, 0, newText, 0, mGapStart);
		final int after = oldLength - (mGapStart + mGapLength);
		System.arraycopy(mText, oldLength - after, newText, newLength - after, after);
		mText = newText;

		mGapLength += delta;
		if (mGapLength < 1)
			new Exception("mGapLength < 1").printStackTrace();

		for (int i = 0; i < mSpanCount; i++) {
			if (mSpanStarts[i] > mGapStart)
				mSpanStarts[i] += delta;
			if (mSpanEnds[i] > mGapStart)
				mSpanEnds[i] += delta;
		}
	}

	private void sendToSpanWatchers(int replaceStart, int replaceEnd, int nbNewChars) {
		for (int i = 0; i < mSpanCountBeforeAdd; i++) {
			int spanStart = mSpanStarts[i];
			int spanEnd = mSpanEnds[i];
			if (spanStart > mGapStart)
				spanStart -= mGapLength;
			if (spanEnd > mGapStart)
				spanEnd -= mGapLength;
			int spanFlags = mSpanFlags[i];

			int newReplaceEnd = replaceEnd + nbNewChars;
			boolean spanChanged = false;

			int previousSpanStart = spanStart;
			if (spanStart > newReplaceEnd) {
				if (nbNewChars != 0) {
					previousSpanStart -= nbNewChars;
					spanChanged = true;
				}
			} else if (spanStart >= replaceStart) {
				// No change if span start was already at replace interval boundaries before replace
				if ((spanStart != replaceStart || ((spanFlags & SPAN_START_AT_START) != SPAN_START_AT_START))
						&& (spanStart != newReplaceEnd || ((spanFlags & SPAN_START_AT_END) != SPAN_START_AT_END))) {
					// TODO A correct previousSpanStart cannot be computed at this point.
					// It would require to save all the previous spans' positions before the replace
					// Using an invalid -1 value to convey this would break the broacast range
					spanChanged = true;
				}
			}

			int previousSpanEnd = spanEnd;
			if (spanEnd > newReplaceEnd) {
				if (nbNewChars != 0) {
					previousSpanEnd -= nbNewChars;
					spanChanged = true;
				}
			} else if (spanEnd >= replaceStart) {
				// No change if span start was already at replace interval boundaries before replace
				if ((spanEnd != replaceStart || ((spanFlags & SPAN_END_AT_START) != SPAN_END_AT_START))
						&& (spanEnd != newReplaceEnd || ((spanFlags & SPAN_END_AT_END) != SPAN_END_AT_END))) {
					// TODO same as above for previousSpanEnd
					spanChanged = true;
				}
			}

			if (spanChanged) {
				if (mDynamicLayoutSpanWatcher != null)
					mDynamicLayoutSpanWatcher.onSpanChanged(this, mSpans[i], previousSpanStart, previousSpanEnd,
							spanStart, spanEnd);
				if (mTextArea != null)
					mTextArea.onSpanChanged(this, mSpans[i], previousSpanStart, previousSpanEnd, spanStart, spanEnd);
			}
			mSpanFlags[i] &= ~SPAN_START_END_MASK;
		}

		// The spans starting at mIntermediateSpanCount were added from the replacement text
		// TODO currently not support add new spans
		// for (int i = mSpanCountBeforeAdd; i < mSpanCount; i++) {
		// int spanStart = mSpanStarts[i];
		// int spanEnd = mSpanEnds[i];
		// if (spanStart > mGapStart) spanStart -= mGapLength;
		// if (spanEnd > mGapStart) spanEnd -= mGapLength;
		// sendSpanAdded(mSpans[i], spanStart, spanEnd);
		// }
	}

	public void setFilters(InputFilter[] filters) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Mark the specified range of text with the specified object. The flags determine how the span will behave when
	 * text is inserted at the start or end of the span's range.
	 */
	public void setSpan(Object what, int start, int end, int flags) {
		// For TextWatcher and SpanWatcher
		if (what instanceof TextWatcher && what instanceof SpanWatcher) {
			mDynamicLayoutTextWatcher = (TextWatcher) what;
			mDynamicLayoutSpanWatcher = (SpanWatcher) what;
			return;
		}

		// For Selection.SELECTION_START and Selection.SELECTION_END

		// inline from setSpan(true, what, start, end, flags);
		checkRange("setSpan", start, end);
		int flagsStart = (flags & START_MASK) >> START_SHIFT;
		// if (flagsStart == PARAGRAPH) {
		// if (start != 0 && start != length()) {
		// char c = charAt(start - 1);
		// if (c != '\n')
		// throw new RuntimeException("PARAGRAPH span must start at paragraph boundary");
		// }
		// }
		int flagsEnd = flags & END_MASK;
		// if (flagsEnd == PARAGRAPH) {
		// if (end != 0 && end != length()) {
		// char c = charAt(end - 1);
		//
		// if (c != '\n')
		// throw new RuntimeException("PARAGRAPH span must end at paragraph boundary");
		// }
		// }

		// 0-length Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
		if (flagsStart == POINT && flagsEnd == MARK && start == end) {
			// if (send)
			// Log.e("SpannableStringBuilder", "SPAN_EXCLUSIVE_EXCLUSIVE spans cannot have a zero length");
			// // Silently ignore invalid spans when they are created from this class.
			// // This avoids the duplication of the above test code before all the
			// // calls to setSpan that are done in this class
			return;
		}

		// int nstart = start;
		// int nend = end;

		if (start > mGapStart) {
			start += mGapLength;
		} else if (start == mGapStart) {
			if (flagsStart == POINT || (flagsStart == PARAGRAPH && start == length()))
				start += mGapLength;
		}

		if (end > mGapStart) {
			end += mGapLength;
		} else if (end == mGapStart) {
			if (flagsEnd == POINT || (flagsEnd == PARAGRAPH && end == length()))
				end += mGapLength;
		}

		int count = mSpanCount;
		Object[] spans = mSpans;

		for (int i = 0; i < count; i++) {
			if (spans[i] == what) {
				int ostart = mSpanStarts[i];
				int oend = mSpanEnds[i];

				if (ostart > mGapStart)
					ostart -= mGapLength;
				if (oend > mGapStart)
					oend -= mGapLength;

				mSpanStarts[i] = start;
				mSpanEnds[i] = end;
				mSpanFlags[i] = flags;
				return;
			}
		}

		if (mSpanCount + 1 >= mSpans.length) {
			int newsize = idealIntArraySize(mSpanCount + 1);
			Object[] newspans = new Object[newsize];
			int[] newspanstarts = new int[newsize];
			int[] newspanends = new int[newsize];
			int[] newspanflags = new int[newsize];

			System.arraycopy(mSpans, 0, newspans, 0, mSpanCount);
			System.arraycopy(mSpanStarts, 0, newspanstarts, 0, mSpanCount);
			System.arraycopy(mSpanEnds, 0, newspanends, 0, mSpanCount);
			System.arraycopy(mSpanFlags, 0, newspanflags, 0, mSpanCount);

			mSpans = newspans;
			mSpanStarts = newspanstarts;
			mSpanEnds = newspanends;
			mSpanFlags = newspanflags;
		}

		mSpans[mSpanCount] = what;
		mSpanStarts[mSpanCount] = start;
		mSpanEnds[mSpanCount] = end;
		mSpanFlags[mSpanCount] = flags;
		mSpanCount++;
	}

	/**
	 * Return a new CharSequence containing a copy of the specified range of this buffer, including the overlapping
	 * spans.
	 */
	public CharSequence subSequence(int start, int end) {
		// return new LaTeXStringBuilder(this, start, end);
		throw new UnsupportedOperationException("LaTeXStringBuilder#subSequence");
	}

	/**
	 * Return a String containing a copy of the chars in this buffer, limited to the [start, end[ range.
	 * 
	 * @hide
	 */
	public String substring(int start, int end) {
		char[] buf = new char[end - start];
		getChars(start, end, buf, 0);
		return new String(buf);
	}

	/**
	 * Return a String containing a copy of the chars in this buffer.
	 */
	@Override
	public String toString() {
		int len = length();
		char[] buf = new char[len];

		getChars(0, len, buf, 0);
		return new String(buf);
	}

	private int updatedIntervalBound(int offset, int start, int nbNewChars, int flag, boolean atEnd,
			boolean textIsRemoved) {
		if (offset >= start && offset < mGapStart + mGapLength) {
			if (flag == POINT) {
				// A POINT located inside the replaced range should be moved to the end of the
				// replaced text.
				// The exception is when the point is at the start of the range and we are doing a
				// text replacement (as opposed to a deletion): the point stays there.
				if (textIsRemoved || offset > start) {
					return mGapStart + mGapLength;
				}
			} else {
				if (flag == PARAGRAPH) {
					if (atEnd) {
						return mGapStart + mGapLength;
					}
				} else { // MARK
					// MARKs should be moved to the start, with the exception of a mark located at
					// the end of the range (which will be < mGapStart + mGapLength since mGapLength
					// is > 0, which should stay 'unchanged' at the end of the replaced text.
					if (textIsRemoved || offset < mGapStart - nbNewChars) {
						return start;
					} else {
						// Move to the end of replaced text (needed if nbNewChars != 0)
						return mGapStart;
					}
				}
			}
		}
		return offset;
	}
}
