package lah.texpert.indexing;

import java.util.Arrays;

/**
 * Indexer that works with a range of characters to be indexed instead of a single one.
 * 
 * @author L.A.H.
 * 
 */
public class CharsSetIndexer extends CharIndexer {

	/**
	 * Collection of characters that this object is indexing
	 */
	private char[] indexed_chars;

	public CharsSetIndexer(CharSequence text, char... chs) {
		// sort the characters for efficient search
		indexed_chars = new char[chs.length];
		System.arraycopy(chs, 0, indexed_chars, 0, chs.length);
		Arrays.sort(indexed_chars);
		initialize(text);
	}

	@Override
	public boolean isIndexedPosition(CharSequence text, int pos) {
		return Arrays.binarySearch(indexed_chars, text.charAt(pos)) >= 0;
	}
	
}
