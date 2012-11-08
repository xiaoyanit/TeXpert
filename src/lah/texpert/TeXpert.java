package lah.texpert;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuItem;
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

	/**
	 * {@link TextView} to displays source code
	 */
	private TextView src_code_view;

	/**
	 * The currently selected engine
	 */
	private String engine;

	/**
	 * The document that {@link TeXpert#src_code_view} is displaying
	 */
	private File current_document;
	
	/**
	 * Editable object containing
	 */
	@SuppressWarnings("unused")
	private Editable current_document_editable;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.texpert);

		// Load the font
		tex_editor_font = Typeface.createFromAsset(getAssets(),
				FONT_SPECIAL_ELITE);
		src_code_view = (TextView) findViewById(R.id.source_code_edit_text);
		// Customized font tutorial at
		// http://mobile.tutsplus.com/tutorials/android/customize-android-fonts/
		// and the stackoverflow reference at
		// http://stackoverflow.com/questions/3651086/android-using-custom-font
		src_code_view.setTypeface(tex_editor_font);
		src_code_view.setTextSize(20.0f);
		
		// Testing content
		String texdoc = "\\documentclass{amsart}\n\\begin{document}\nHello world!\n\\end{document}";
		src_code_view.setText(texdoc);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.texpert, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_compile:
			compileDocument();
			return true;
		case R.id.menu_document_structure:
			showDocumentStructure();
			return true;
		case R.id.menu_settings:
			showSettings();
			return true;
		default:
			return true;
		}
	}

	private void showSettings() {
		// TODO Auto-generated method stub
		System.out.println("Customizing TeXpert.");
	}

	private void showDocumentStructure() {
		// TODO Auto-generated method stub
		System.out.println();
		new AlertDialog.Builder(this).setTitle("Structure")
				.setMessage("Document structure for " + current_document)
				.setPositiveButton("OK", null)
				.create().show();
	}

	private void compileDocument() {
		// TODO Auto-generated method stub
		System.out
				.println("Compiling " + current_document + " using " + engine);
		new AlertDialog.Builder(this).setTitle("Portaling")
				.setPositiveButton("OK", null)
				.setMessage("Compiling document using TeXPortal.")
				.create().show();		
	}
}
