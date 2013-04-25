package lah.texpert.indexing;

/**
 * Indexer to index positions of non-letter
 * 
 * @author L.A.H.
 * 
 */
public class NonLetterIndexer extends CharIndexer {

	public NonLetterIndexer(CharSequence text) {
		initialize(text);
	}

	@Override
	boolean isIndexedPosition(CharSequence text, int pos) {
		char ch = text.charAt(pos);
		return (ch < 'A' || ch > 'Z') && (ch < 'a' || ch > 'z');
	}

}
