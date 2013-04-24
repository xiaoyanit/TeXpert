package lah.texpert;

import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;

/**
 * Extension of {@link CharacterStyle} to style a TeX token
 * 
 * @author L.A.H.
 * 
 */
public class TeXTokenSpan extends CharacterStyle implements UpdateAppearance, Comparable<TeXTokenSpan> {

	/**
	 * Colors constants to style document
	 */
	static final int COLOR_COMMAND = 0xFF0000FF, COLOR_COMMENT = 0xFFA0A0A0, COLOR_SYMBOLS = 0xFFCC8000,
			COLOR_FORMULA = 0xFF00CC00;

	static final int FLAG_COMMAND = 0x40000000, FLAG_COMMENT = 0x80000000;

	/**
	 * Mask to extract first two MSBs and the remaining 30 lower bits
	 */
	static final int MASK_TOKEN_TYPE = 0xC0000000, MASK_VALUE = 0x3FFFFFFF;

	/**
	 * The first two most-significant-bits represents the token type
	 */
	int position;

	public TeXTokenSpan(CharSequence text, int initial_position) {
		char c = text.charAt(initial_position);
		position = initial_position | (c == '%' ? FLAG_COMMENT : (c == '\\' ? FLAG_COMMAND : 0));
	}

	@Override
	public int compareTo(TeXTokenSpan span) {
		return position - span.position;
	}

	// TODO Should use newline and symbol indexer instead
	public int getSpanEnd(CharSequence text) {
		// consideration for line comment
		int position = this.position & MASK_VALUE;
		char c = text.charAt(position);
		int l = text.length();
		if (c == '%') {
			int e = position + 1;
			while (e < l)
				if (text.charAt(e) == '\n')
					break;
				else
					e++;
			return e;
		}
		if (c == '\\') {
			int e = position + 1;
			while (e < l) {
				char ce = text.charAt(e);
				if (Character.isLetter(ce))
					e++;
				else
					break;
			}
			return e;
		}
		return position + 1;
	}

	public int getSpanStart() {
		return position & MASK_VALUE;
	}

	@Override
	public void updateDrawState(TextPaint ds) {
		switch (position & MASK_TOKEN_TYPE) {
		case FLAG_COMMAND:
			ds.setColor(COLOR_COMMAND);
			break;
		case FLAG_COMMENT:
			ds.setColor(COLOR_COMMENT);
			break;
		default:
			// ds.setColor(COLOR_COMMENT);
		}
	}

	public void setPosition(int pos) {
		position = pos;
	}
}