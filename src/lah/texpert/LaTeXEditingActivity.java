package lah.texpert;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.stream.Streams;
import lah.widgets.TextArea;
import lah.widgets.fileview.FileDialog;
import lah.widgets.fileview.IFileSelectListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * Main activity to edit the LaTeX source files
 * 
 * @author L.A.H.
 * 
 */
public class LaTeXEditingActivity extends Activity {

	/**
	 * TeX and LaTeX text patterns for syntax highlighting purposes, the first group depicted in the pattern will be
	 * highlighted using the style described in {@link DocumentAdapter#LATEX_STYLES}.
	 */
	static final Pattern[] LATEX_PATTERNS = {
			// TODO Non-escaped TeX special characters: \ $ % & # _ ~ ^ { }
			// TeX command and escaped special characters:
			Pattern.compile("(\\\\([A-Za-z]+\\*?|[\\\\\\$%&#_~\\^\\{\\}]))"),
			// TODO Command option between { and }
			// TODO Referenced resources via \input, \includegraphics, etc
			// TODO Verbatim and listing
			// Math formulas TODO add math environments equation, align*, ... as well
			Pattern.compile("(\\$([^\\$]|\\\\\\$)+\\$|\\$\\$([^\\$]|\\\\\\$)+\\$\\$)"),
			// TeX line comment TODO consider block comment via comments environment
			Pattern.compile("(%.*\\n)") };

	/**
	 * Corresponding styles for the above patterns
	 */
	static final CharacterStyle[] LATEX_STYLES = {
			// Command style: blue
			new ForegroundColorSpan(Color.parseColor("#0000ff")),
			// Formula style: green
			new ForegroundColorSpan(Color.parseColor("#00cc00")),
			// Comment style: gray
			new ForegroundColorSpan(Color.parseColor("#a0a0a0")) };

	private static final boolean TEST = true;

	private static final ComponentName TEXPORTAL = new ComponentName("lah.texportal",
			"lah.texportal.activities.CompileDocumentActivity");

	private FileDialog file_select_dialog;

	private File opening_file;

	private AlertDialog save_confirm_dialog;

	Editable annotate(String text) {
		SpannableStringBuilder builder = new SpannableStringBuilder(text);
		for (int i = 0; i < LATEX_PATTERNS.length; i++) {
			Matcher matcher = LATEX_PATTERNS[i].matcher(builder);
			while (matcher.find())
				builder.setSpan(CharacterStyle.wrap(LATEX_STYLES[i]), matcher.start(1), matcher.end(1),
						Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
		}
		return builder;
	}

	private void confirmSave() {
		if (save_confirm_dialog == null)
			save_confirm_dialog = new AlertDialog.Builder(this).setTitle("Save document?")
					.setPositiveButton("Save", new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							try {
								Streams.writeStringToFile(((TextArea) findViewById(R.id.document_area)).getText()
										.toString(), opening_file, false);
							} catch (IOException e) {
								Toast.makeText(LaTeXEditingActivity.this, "Fail to save document!", Toast.LENGTH_SHORT)
										.show();
							}
						}
					}).setNegativeButton("Cancel", null).create();
		save_confirm_dialog.show();
	}

	private void handleIntent() {
		Intent intent = getIntent();
		Uri data;
		File file;
		if ((data = intent.getData()) != null && data.getScheme().equals("file")
				&& (file = new File(data.getPath())).exists()) {
			intent.setData(null);
			openDocument(file);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_latex_editing);
		if (TEST)
			openDocument(new File(Environment.getExternalStorageDirectory(), "CV.tex"));
		else
			handleIntent();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.latex_editing, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_open:
			if (file_select_dialog == null)
				file_select_dialog = new FileDialog(this, new IFileSelectListener() {

					@Override
					public void onFileSelected(File result) {
						openDocument(result);
					}
				}, "");
			file_select_dialog.show();
			return true;
		case R.id.action_save:
			confirmSave();
			return true;
		case R.id.action_send:
			// TODO make sure file is in accessible place (on sdcard)
			confirmSave();
			try {
				startActivityForResult(new Intent().setComponent(TEXPORTAL).setData(Uri.fromFile(opening_file)), 0);
			} catch (ActivityNotFoundException e) {
				Toast.makeText(this, "Fail to send document for compilation: TeXPortal is not installed!",
						Toast.LENGTH_SHORT).show();
			}
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void openDocument(File file) {
		if (file == null || !file.exists())
			return;
		try {
			opening_file = file;
			String file_content = Streams.readTextFile(file);
			TextArea document_textview = (TextArea) findViewById(R.id.document_area);
			document_textview.setText(annotate(file_content));
		} catch (IOException e) {
			e.printStackTrace(System.out);
		}
	}

}
