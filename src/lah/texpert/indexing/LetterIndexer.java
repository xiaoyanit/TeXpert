package lah.texpert.indexing;

public class LetterIndexer extends CharIndexer {

	public LetterIndexer(CharSequence text) {
		initialize(text);
	}

	@Override
	boolean isIndexedPosition(CharSequence text, int pos) {
		return Character.isLetter(text.charAt(pos));
	}

}
