package lah.texpert;

import java.util.List;
import java.util.regex.Pattern;

import android.text.Editable;
import android.text.TextWatcher;

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

	private final TextWatcher watcher;

	public Paragraph(String content) {
		this.content = content;
		this.watcher = new TextWatcher() {

			@Override
			public void afterTextChanged(Editable s) {
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				// commit change to the underlying paragraph
				setContent(s);
			}
		};
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

	public TextWatcher getTextWatcher() {
		return watcher;
	}

	public void setContent(CharSequence s) {
		content = s.toString();
	}

}
