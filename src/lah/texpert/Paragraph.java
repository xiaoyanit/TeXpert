package lah.texpert;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A text paragraph
 * 
 * @author L.A.H.
 * 
 */
public class Paragraph {

	/**
	 * Single line break pattern
	 */
	public static final Pattern linebreak = Pattern.compile("\\n|\\r\\n");

	/**
	 * Pattern for paragraph breaks (two or more \n characters)
	 */
	public static final Pattern parabreak = Pattern.compile("(\\n|\\r\\n){2,}");

	private String content;

	private List<String> lines;

	public Paragraph(String content) {
		this.content = content;
	}

	public CharSequence getContent() {
		return content;
	}

	public String getLine(int childPosition) {
		return lines.get(childPosition);
	}

	public int getNumLines() {
		return lines.size();
	}

}
