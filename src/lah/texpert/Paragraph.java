package lah.texpert;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 
 * 
 * @author L.A.H.
 * 
 */
public class Paragraph {

	/**
	 * Pattern for paragraph breaks (two or more \n characters)
	 */
	Pattern parabreak = Pattern.compile("(\\n|\\r\\n){2,}");

	/**
	 * Single line break pattern
	 */
	Pattern linebreak = Pattern.compile("\\n|\\r\\n");

	private List<String> lines;

	public int getNumLines() {
		return lines.size();
	}

	public String getLine(int childPosition) {
		return lines.get(childPosition);
	}

	public CharSequence getText() {
		return null;
	}

}
