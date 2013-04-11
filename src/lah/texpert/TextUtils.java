package lah.texpert;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Annotation;
import android.text.GetChars;
import android.text.ParcelableSpan;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.CharacterStyle;
import android.text.style.EasyEditSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LocaleSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ScaleXSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuggestionSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

@SuppressLint("NewApi")
public class TextUtils {

	public enum TruncateAt {
		START, MIDDLE, END, MARQUEE,
		/**
		 * @hide
		 */
		END_SMALL
	}

	private static void writeWhere(Parcel p, Spanned sp, Object o) {
		p.writeInt(sp.getSpanStart(o));
		p.writeInt(sp.getSpanEnd(o));
		p.writeInt(sp.getSpanFlags(o));
	}

	/**
	 * Create a new String object containing the given range of characters from the source string. This is different
	 * than simply calling {@link CharSequence#subSequence(int, int) CharSequence.subSequence} in that it does not
	 * preserve any style runs in the source sequence, allowing a more efficient implementation.
	 */
	public static String substring(CharSequence source, int start, int end) {
		if (source instanceof String)
			return ((String) source).substring(start, end);
		if (source instanceof StringBuilder)
			return ((StringBuilder) source).substring(start, end);
		if (source instanceof StringBuffer)
			return ((StringBuffer) source).substring(start, end);

		char[] temp = obtain(end - start);
		getChars(source, start, end, temp, 0);
		String ret = new String(temp, 0, end - start);
		recycle(temp);

		return ret;
	}

	/**
	 * Flatten a CharSequence and whatever styles can be copied across processes into the parcel.
	 */
	public static void writeToParcel(CharSequence cs, Parcel p, int parcelableFlags) {
		if (cs instanceof Spanned) {
			p.writeInt(0);
			p.writeString(cs.toString());

			Spanned sp = (Spanned) cs;
			Object[] os = sp.getSpans(0, cs.length(), Object.class);

			// note to people adding to this: check more specific types
			// before more generic types. also notice that it uses
			// "if" instead of "else if" where there are interfaces
			// so one object can be several.

			for (int i = 0; i < os.length; i++) {
				Object o = os[i];
				Object prop = os[i];

				if (prop instanceof CharacterStyle) {
					prop = ((CharacterStyle) prop).getUnderlying();
				}

				if (prop instanceof ParcelableSpan) {
					ParcelableSpan ps = (ParcelableSpan) prop;
					p.writeInt(ps.getSpanTypeId());
					ps.writeToParcel(p, parcelableFlags);
					writeWhere(p, sp, o);
				}
			}

			p.writeInt(0);
		} else {
			p.writeInt(1);
			if (cs != null) {
				p.writeString(cs.toString());
			} else {
				p.writeString(null);
			}
		}
	}

	public static CharSequence stringOrSpannedString(CharSequence source) {
		if (source == null)
			return null;
		if (source instanceof SpannedString)
			return source;
		if (source instanceof Spanned)
			return new SpannedString(source);

		return source.toString();
	}

	/* package */static void recycle(char[] temp) {
		if (temp.length > 1000)
			return;

		synchronized (sLock) {
			sTemp = temp;
		}
	}

	/**
	 * Returns true if the string is null or 0-length.
	 * 
	 * @param str
	 *            the string to be examined
	 * @return true if str is null or zero length
	 */
	public static boolean isEmpty(CharSequence str) {
		if (str == null || str.length() == 0)
			return true;
		else
			return false;
	}

	/**
	 * Pack 2 int values into a long, useful as a return value for a range
	 * 
	 * @see #unpackRangeStartFromLong(long)
	 * @see #unpackRangeEndFromLong(long)
	 * @hide
	 */
	public static long packRangeInLong(int start, int end) {
		return (((long) start) << 32) | end;
	}

	/**
	 * Get the start value from a range packed in a long by {@link #packRangeInLong(int, int)}
	 * 
	 * @see #unpackRangeEndFromLong(long)
	 * @see #packRangeInLong(int, int)
	 * @hide
	 */
	public static int unpackRangeStartFromLong(long range) {
		return (int) (range >>> 32);
	}

	/**
	 * Get the end value from a range packed in a long by {@link #packRangeInLong(int, int)}
	 * 
	 * @see #unpackRangeStartFromLong(long)
	 * @see #packRangeInLong(int, int)
	 * @hide
	 */
	public static int unpackRangeEndFromLong(long range) {
		return (int) (range & 0x00000000FFFFFFFFL);
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

	private TextUtils() { /* cannot be instantiated */
	}

	/** @hide */
	public static final int ALIGNMENT_SPAN = 1;
	/** @hide */
	public static final int FOREGROUND_COLOR_SPAN = 2;
	/** @hide */
	public static final int RELATIVE_SIZE_SPAN = 3;
	/** @hide */
	public static final int SCALE_X_SPAN = 4;
	/** @hide */
	public static final int STRIKETHROUGH_SPAN = 5;
	/** @hide */
	public static final int UNDERLINE_SPAN = 6;
	/** @hide */
	public static final int STYLE_SPAN = 7;
	/** @hide */
	public static final int BULLET_SPAN = 8;
	/** @hide */
	public static final int QUOTE_SPAN = 9;
	/** @hide */
	public static final int LEADING_MARGIN_SPAN = 10;
	/** @hide */
	public static final int URL_SPAN = 11;
	/** @hide */
	public static final int BACKGROUND_COLOR_SPAN = 12;
	/** @hide */
	public static final int TYPEFACE_SPAN = 13;
	/** @hide */
	public static final int SUPERSCRIPT_SPAN = 14;
	/** @hide */
	public static final int SUBSCRIPT_SPAN = 15;
	/** @hide */
	public static final int ABSOLUTE_SIZE_SPAN = 16;
	/** @hide */
	public static final int TEXT_APPEARANCE_SPAN = 17;
	/** @hide */
	public static final int ANNOTATION = 18;
	/** @hide */
	public static final int SUGGESTION_SPAN = 19;
	/** @hide */
	public static final int SPELL_CHECK_SPAN = 20;
	/** @hide */
	public static final int SUGGESTION_RANGE_SPAN = 21;
	/** @hide */
	public static final int EASY_EDIT_SPAN = 22;
	/** @hide */
	public static final int LOCALE_SPAN = 23;

	public static final Parcelable.Creator<CharSequence> CHAR_SEQUENCE_CREATOR = new Parcelable.Creator<CharSequence>() {
		/**
		 * Read and return a new CharSequence, possibly with styles, from the parcel.
		 */
		public CharSequence createFromParcel(Parcel p) {
			int kind = p.readInt();

			String string = p.readString();
			if (string == null) {
				return null;
			}

			if (kind == 1) {
				return string;
			}

			SpannableString sp = new SpannableString(string);

			while (true) {
				kind = p.readInt();

				if (kind == 0)
					break;

				switch (kind) {
				case ALIGNMENT_SPAN:
					readSpan(p, sp, new AlignmentSpan.Standard(p));
					break;

				case FOREGROUND_COLOR_SPAN:
					readSpan(p, sp, new ForegroundColorSpan(p));
					break;

				case RELATIVE_SIZE_SPAN:
					readSpan(p, sp, new RelativeSizeSpan(p));
					break;

				case SCALE_X_SPAN:
					readSpan(p, sp, new ScaleXSpan(p));
					break;

				case STRIKETHROUGH_SPAN:
					readSpan(p, sp, new StrikethroughSpan(p));
					break;

				case UNDERLINE_SPAN:
					readSpan(p, sp, new UnderlineSpan(p));
					break;

				case STYLE_SPAN:
					readSpan(p, sp, new StyleSpan(p));
					break;

				case BULLET_SPAN:
					readSpan(p, sp, new BulletSpan(p));
					break;

				case QUOTE_SPAN:
					readSpan(p, sp, new QuoteSpan(p));
					break;

				case LEADING_MARGIN_SPAN:
					readSpan(p, sp, new LeadingMarginSpan.Standard(p));
					break;

				case URL_SPAN:
					readSpan(p, sp, new URLSpan(p));
					break;

				case BACKGROUND_COLOR_SPAN:
					readSpan(p, sp, new BackgroundColorSpan(p));
					break;

				case TYPEFACE_SPAN:
					readSpan(p, sp, new TypefaceSpan(p));
					break;

				case SUPERSCRIPT_SPAN:
					readSpan(p, sp, new SuperscriptSpan(p));
					break;

				case SUBSCRIPT_SPAN:
					readSpan(p, sp, new SubscriptSpan(p));
					break;

				case ABSOLUTE_SIZE_SPAN:
					readSpan(p, sp, new AbsoluteSizeSpan(p));
					break;

				case TEXT_APPEARANCE_SPAN:
					readSpan(p, sp, new TextAppearanceSpan(p));
					break;

				case ANNOTATION:
					readSpan(p, sp, new Annotation(p));
					break;

				case SUGGESTION_SPAN:
					readSpan(p, sp, new SuggestionSpan(p));
					break;

				// case SPELL_CHECK_SPAN:
				// readSpan(p, sp, new SpellCheckSpan(p));
				// break;
				//
				// case SUGGESTION_RANGE_SPAN:
				// readSpan(p, sp, new SuggestionRangeSpan(p));
				// break;

				case EASY_EDIT_SPAN:
					readSpan(p, sp, new EasyEditSpan());
					break;

				case LOCALE_SPAN:
					readSpan(p, sp, new LocaleSpan(p));
					break;

				default:
					throw new RuntimeException("bogus span encoding " + kind);
				}
			}

			return sp;
		}

		public CharSequence[] newArray(int size) {
			return new CharSequence[size];
		}
	};

	private static void readSpan(Parcel p, Spannable sp, Object o) {
		sp.setSpan(o, p.readInt(), p.readInt(), p.readInt());
	}

	static char[] obtain(int len) {
		char[] buf;

		synchronized (sLock) {
			buf = sTemp;
			sTemp = null;
		}

		if (buf == null || buf.length < len)
			buf = new char[ArrayUtils.idealCharArraySize(len)];

		return buf;
	}

	private static Object sLock = new Object();

	private static char[] sTemp = null;

}
