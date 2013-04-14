package lah.texpert;

import android.text.GetChars;
import android.text.Spanned;
import android.text.SpannedString;

public class TextUtils {

	private static Object sLock = new Object();

	private static char[] sTemp = null;

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

	public static int idealByteArraySize(int need) {
		for (int i = 4; i < 32; i++)
			if (need <= (1 << i) - 12)
				return (1 << i) - 12;

		return need;
	}

	public static int idealCharArraySize(int need) {
		return idealByteArraySize(need * 2) / 2;
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

	static void recycle(char[] temp) {
		if (temp.length > 1000)
			return;

		synchronized (sLock) {
			sTemp = temp;
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
	 * Get the end value from a range packed in a long by {@link #packRangeInLong(int, int)}
	 * 
	 * @see #unpackRangeStartFromLong(long)
	 * @see #packRangeInLong(int, int)
	 * @hide
	 */
	public static int unpackRangeEndFromLong(long range) {
		return (int) (range & 0x00000000FFFFFFFFL);
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

	private TextUtils() { /* cannot be instantiated */
	}

}
