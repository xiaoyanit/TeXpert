package lah.texpert;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Menu;
import android.widget.TextView;

public class TeXpert extends Activity {

	/**
	 * Path to the 'Special Elite' font
	 */
	private static final String FONT_SPECIAL_ELITE = "SpecialElite.ttf";

	/**
	 * Font for TeX source code in the editor
	 */
	private Typeface tex_editor_font;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.texpert);
		
		// Load the font
		tex_editor_font = Typeface.createFromAsset(
					getAssets(), FONT_SPECIAL_ELITE);
		
		// Testing content
		String texdoc = "\\documentclass{amsart}\n\\begin{document}\n\\end{document}";
		TextView v = (TextView) findViewById(R.id.textView1);
		v.setText(texdoc);
		
		// Customized font tutorial at
		// http://mobile.tutsplus.com/tutorials/android/customize-android-fonts/
		// and the stackoverflow reference at
		// http://stackoverflow.com/questions/3651086/android-using-custom-font
		v.setTypeface(tex_editor_font);
		v.setTextSize(20.0f);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.texpert, menu);
		return true;
	}
}
