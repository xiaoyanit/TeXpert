package lah.texpert;

import android.os.Parcel;
import android.text.GetChars;
import android.text.ParcelableSpan;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.CharacterStyle;

public class TextUtils {

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

	static void recycle(char[] temp) {
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

	public static int idealByteArraySize(int need) {
		for (int i = 4; i < 32; i++)
			if (need <= (1 << i) - 12)
				return (1 << i) - 12;

		return need;
	}

	public static int idealCharArraySize(int need) {
		return idealByteArraySize(need * 2) / 2;
	}

	private static Object sLock = new Object();

	private static char[] sTemp = null;

}