package lah.texpert.indexing;

import java.util.Arrays;

/**
 * This class is to index a character sequence i.e. marking positions of a specific character in it.
 * 
 * The aim is to make it efficiently updated in the event the target sequence is modified (replace).
 * 
 * Subclass only needs to implement {@link CharIndexer#isIndexedPosition(CharSequence, int)}.
 * 
 * @author L.A.H.
 * 
 */
public abstract class CharIndexer {

	private int first_segment_end, second_segment_start, second_segment_delta;

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

	protected CharIndexer() {
		// pre-allocated, this takes 1MB
		markers = new int[1 << 18];
		// both segments initially empty
		first_segment_end = 0;
		second_segment_start = markers.length;
		second_segment_delta = 0;
	}

	/**
	 * Find the index of first occurrence of {@link CharIndexer#idx} after a specified position
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
			return r - second_segment_start + first_segment_end;
		}
	}

	/**
	 * Get the position of the {@code n+1}-th occurrence of {@link CharIndexer#idx} in the current text
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
	 * Initialize this indexer with a piece of text
	 * 
	 * @param text
	 */
	protected final void initialize(CharSequence text) {
		if (text != null) {
			try {
				for (int i = 0; i < text.length(); i++) {
					if (isIndexedPosition(text, i)) {
						markers[first_segment_end++] = i;
					}
				}
				// after this, markers[0..first_segment_end) contains all positions of idx in text
			} catch (Exception e) {
				e.printStackTrace(System.out);
			}
		}
	}

	abstract boolean isIndexedPosition(CharSequence text, int pos);

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
		int ps = findFirst(start), pe = findFirst(end);

		// First step: Delete [start..end) ==> delete markers[ps..pe)
		// After this, we expect first_segment_end is storage location of ps and second_segment_start is storage
		// location of pe; so that later on we can easily insert content.
		if (ps < first_segment_end && pe >= first_segment_end) {
			// easiest case: ps and pe are on different segments
			// adjusting the segment sizes to get the effect of removal
			first_segment_end = ps;
			second_segment_start += pe - first_segment_end;
		} else if (ps < first_segment_end) {
			// ps, pe are both on the first segment i.e. ps <= pe < first_segment_end
			// we move [pe...first_segment_end) to the second segment
			int len = first_segment_end - pe;
			System.arraycopy(markers, pe, markers, second_segment_start - len, len);
			if (second_segment_delta != 0) {
				for (int k = second_segment_start - len; k < second_segment_start; k++)
					markers[k] -= second_segment_delta;
			}
			first_segment_end = ps; // retract
			second_segment_start -= len; // extend
		} else {
			// ps, pe are both on the second segment i.e. pe >= ps >= first_segment_end
			// move [second_segment_start..ps) to the first fragment
			int len = ps - first_segment_end;
			System.arraycopy(markers, second_segment_start, markers, first_segment_end, len);
			if (second_segment_delta != 0) {
				for (int k = second_segment_start - len; k < second_segment_start; k++)
					markers[k] += second_segment_delta;
			}
			first_segment_end += len; // extend
			second_segment_start += (pe - first_segment_end); // retract
		}

		// Second step: Adjust second_segment_delta with number of characters added
		second_segment_delta += (tbend - tbstart) - (end - start);

		// Third step: Insert tb[tbstart..tbend) in
		// Note that the current first_segment_end mark the previous location of ps
		// we just need to add `correct` indices of `idx` starting from first_segment_end
		for (int i = tbstart; i < tbend; i++) {
			if (isIndexedPosition(tb, i)) {
				markers[first_segment_end++] = i - tbstart + start;
			}
		}
	}

	/**
	 * Get total number of occurrences
	 * 
	 * @return
	 */
	public int size() {
		return markers.length - second_segment_start + first_segment_end;
	}

	/**
	 * Assign the i-th occurrence of `idx` to be at the v-th character of the text
	 * 
	 * @param i
	 * @param v
	 */
	void store(int i, int v) {
		if (i >= first_segment_end) {
			i = second_segment_start + (i - first_segment_end);
			markers[i] = v - second_segment_delta;
		} else {
			markers[i] = v;
		}
	}
}
