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

	static final int FLAG_COMMAND = 0x40000000, FLAG_COMMENT = 0x80000000, FLAG_SYMBOL = 0x10000000;

	/**
	 * Mask to extract first two MSBs and the remaining 30 lower bits
	 */
	static final int MASK_TOKEN_TYPE = 0xC0000000, MASK_POSITION = 0x3FFFFFFF;

	/**
	 * The first two most-significant-bits represents the token type
	 */
	private int position;

	public TeXTokenSpan(CharSequence text, int position, boolean is_comment) {
		if (is_comment) {
			this.position = position | FLAG_COMMENT;
		} else {
			this.position = position;
			char c = text.charAt(position);
			if (c == '\\' && position + 1 < text.length()) {
				char nc = text.charAt(position + 1);
				if (('A' <= nc && nc <= 'Z') || ('a' <= nc && nc <= 'z'))
					this.position |= FLAG_COMMAND;
			}
		}
	}

	@Override
	public int compareTo(TeXTokenSpan span) {
		return position - span.position;
	}

	public int getPosition() {
		return position & MASK_POSITION;
	}

	public boolean isComment() {
		return (position & FLAG_COMMENT) == FLAG_COMMENT;
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
			ds.setColor(COLOR_SYMBOLS);
		}
	}
}