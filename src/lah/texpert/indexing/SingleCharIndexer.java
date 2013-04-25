package lah.texpert.indexing;

/**
 * Indexer to index a single character
 * 
 * @author L.A.H.
 * 
 */
public class SingleCharIndexer extends CharIndexer {

	/**
	 * Character that this object is indexing
	 */
	private final char indexed_char;

	public SingleCharIndexer(CharSequence text, char indexed_char) {
		this.indexed_char = indexed_char;
		initialize(text);
	}

	@Override
	public boolean isIndexedPosition(CharSequence text, int pos) {
		return text.charAt(pos) == this.indexed_char;
	}
	
}
