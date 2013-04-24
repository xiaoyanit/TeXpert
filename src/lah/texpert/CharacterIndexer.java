package lah.texpert;

import java.util.Arrays;

/**
 * This class is to index a character sequence i.e. marking positions of a specific character in it.
 * 
 * The aim is to make it efficiently updated in the event the target sequence is modified (replace).
 * 
 * @author L.A.H.
 * 
 */
public class CharacterIndexer {

	static final String TAG = "CharacterIndexer";

	int count;

	private int first_segment_end, second_segment_start, second_segment_delta;

	/**
	 * Character that this object is indexing
	 */
	final char idx;

	/**
	 * Valid indices of markers are in the ranges [0..marker_end) U [marker_start..markers.length)
	 * 
	 * In other words, markers[i] is a position of idx if and only if either 0 <= i < marker_end or marker_start <= i <
	 * markers.length. When marker_end = marker_start, we are running out of indexing space.
	 * 
	 * For marker_start <= i < markers.length, the actual position of the character is markers[i] + delta. This is for
	 * efficient update, in particular, when a replacement occurs, we do not have the increase the whole range till the
	 * end. The valid range of markers[.] array is sorted.
	 */
	private int[] markers;

	public CharacterIndexer(CharSequence text, char c) {
		idx = c;
		// pre-allocated, this takes 1MB
		markers = new int[1 << 18];
		// both segments initially empty
		first_segment_end = 0;
		second_segment_start = markers.length;
		second_segment_delta = 0;
		if (text != null) {
			try {
				for (int i = 0; i < text.length(); i++) {
					if (text.charAt(i) == idx) {
						markers[first_segment_end++] = i;
					}
				}
				count = first_segment_end;
				// after this, markers[0..first_segment_end) contains all positions of idx in text
			} catch (Exception e) {
				e.printStackTrace(System.out);
			}
		}
	}

	/**
	 * Find the index of first occurrence of {@link CharacterIndexer#idx} after a specified position
	 * 
	 * @param pos
	 *            Position in the text which we wish to find next occurrence
	 * @return Find the minimum {@code index} such that {@code markers[index] >= pos}; expect {@code get(index)} is a
	 *         marked position
	 */
	public int findFirst(int pos) {
		// search for pos in the first region (if possible)
		if (first_segment_end > 0 && markers[first_segment_end - 1] >= pos) {
			int l = Arrays.binarySearch(markers, 0, first_segment_end, pos);
			int r = l < 0 ? -1 - l : l;
			return r == first_segment_end ? second_segment_start : r;
		} else {
			// first region is empty or markers[marker_end - 1] < pos, we need to search for pos in the second region
			int l = Arrays.binarySearch(markers, second_segment_start, markers.length, pos - second_segment_delta);
			int r = l < 0 ? -1 - l : l;
			return r - first_segment_end;
		}
	}

	/**
	 * Get the position of the {@code n+1}-th occurrence of {@link CharacterIndexer#idx} in the current text
	 * 
	 * @param n
	 * @return -1 if such value does not exists.
	 */
	public int get(int n) {
		if (n < first_segment_end) {
			return markers[n];
		} else {
			// n-th occurrence should be somewhere in the second segment
			int p = n - first_segment_end + second_segment_start;
			return p < markers.length ? markers[p] + second_segment_delta : -1;
		}
	}

	/**
	 * Get total number of occurrences
	 * 
	 * @return
	 */
	public int size() {
		return count;
	}

	/**
	 * The next valid position in marker
	 * 
	 * @param n
	 *            The position to find the next
	 * @return Next valid index after {@code marker_index}; if the result is greater than {@code markers.length} then
	 *         the input {@code marker_index} is the final one.
	 */
	int next(int n) {
		if (n == first_segment_end - 1)
			return second_segment_start;
		return n + 1;
	}

	/**
	 * Update the index when a substring is replaced.
	 * 
	 * @param start
	 *            Start index of replacement (inclusive)
	 * @param end
	 *            End index of replacement (exclusive)
	 * @param tb
	 *            String whose substring [tbstart..tbend) is going to replace the substring [start..end)
	 * @param tbstart
	 *            Start index of {@code tb} (inclusive)
	 * @param tbend
	 *            End index of {@code tb} (exclusive)
	 */
	public void onReplace(int start, int end, CharSequence tb, int tbstart, int tbend) {
		int num_chars_removed = end - start;
		int num_chars_inserted = tbend - tbstart;
		int num_chars_add = num_chars_inserted - num_chars_removed;

		// [start..end) is removed ==> markers[ps..pe) are removed
		// tb[tbstart..tbend) is inserted ==> add markers to [ps..?)
		// the remaining markers[pe..) should be adjusted (all inc/dec) due to difference in number of characters
		// two cases to consider depending on whether pe - ps is greater than number of idx in
		// tb[tbstart..tbend) or not
		int ps = findFirst(start), pe = findFirst(end);

		// The following code replaces markers[ps..pe) with positions of new `idx`
		// Logically, the loop exists when either j == tbend or i == pe (or both). In case both occurs, the numbers of
		// `idx` in [start..end) and tb[tbstart..tbend) must be identical. If j != tbend, there are more `idx` in
		// tb[tbstart..tbend) so we need to expand later on; otherwise, there are more `idx` in [start..end) so we need
		// to retract some space.
		int i = ps;
		int j = tbstart;
		for (; j < tbend; j++) {
			if (tb.charAt(j) == idx) {
				int np = j - tbstart + start;
				if (i < pe) {
					// there are still some `idx` left in [start..end)
					markers[i] = (i >= second_segment_start ? np - second_segment_delta : np);
					// update to the next position
					i = next(i);
				}
			}
		}

		if (j < tbend) {
			// this case implies i == pe
			// [ps..pe) is not sufficient to store all idx in tb[j..tbend)
		}
		if (i < pe - 1) {
			// this case implies j == tbend
			// all occurrences of idx in tb[tbstart..tbend) is copied to markers[ps..i); we need to eliminate
			// the remaining invalid indices markers[i..pe)
			if (i < first_segment_end && pe >= second_segment_start) {
				// i and pe are in different segments
				first_segment_end = i;
				second_segment_start = pe;
			} else {
				// i and pe are in the same segment
				if (i < first_segment_end) {
					// they are on the first, shift [pe..marker_start) to [i..)
					first_segment_end = i;
					int len = first_segment_end - pe;
					System.arraycopy(markers, pe, markers, second_segment_start - len, len);
					for (int k = second_segment_start - len; k < second_segment_start; k++)
						markers[k] -= second_segment_delta;
				} else {
					// they are on the second, copy [marker_start..i) to the first fragment
					first_segment_end = i;
					int len = first_segment_end - pe;
					System.arraycopy(markers, pe, markers, second_segment_start - len, len);
					for (int k = second_segment_start - len; k < second_segment_start; k++)
						markers[k] -= (second_segment_delta - num_chars_add);
				}
			}
		}

		// Adjust delta accordingly [pe..) with num_chars_add
	}

	// public TeXTokenSpan[] getSpans(int start, int end) {
	// int f = findFirst(start);
	// int l = findFirst(end);
	// Log.v(TAG, "getSpans[" + start + "," + end + "] -> " + f + "," + l);
	// TeXTokenSpan[] res = new TeXTokenSpan[l - f];
	// for (int i = f; i < l; i++) {
	// res[i - f] = new TeXTokenSpan(get(i));
	// }
	// return res;
	// }
}
