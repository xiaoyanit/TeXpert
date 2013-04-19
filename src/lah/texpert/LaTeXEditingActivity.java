package lah.texpert;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.stream.Streams;
import lah.widgets.TextArea;
import lah.widgets.fileview.FileDialog;
import lah.widgets.fileview.IFileSelectListener;
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
import android.support.v4.app.FragmentActivity;
import android.text.Spannable;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * Main activity to edit the LaTeX source code
 * 
 * @author L.A.H.
 * 
 */
public class LaTeXEditingActivity extends FragmentActivity {

	/**
	 * TeX and LaTeX text patterns for syntax highlighting purposes, the first group depicted in the pattern will be
	 * highlighted using the style described in {@link DocumentAdapter#LATEX_STYLES}. TODO Command option between { and
	 * } TODO Referenced resources via \input, \includegraphics, etc TODO Verbatim and listing TODO Add math
	 * environments equation, align*, ... as well
	 */
	static final Pattern[] LATEX_PATTERNS = {
			// Non-escaped TeX special characters: \ $ % & # _ ~ ^ { }
			Pattern.compile("([\\\\\\$%&#_~\\^\\{\\}])"),
			// TeX command and escaped special characters:
			Pattern.compile("(\\\\([A-Za-z]+\\*?|[\\\\\\$%&#_~\\^\\{\\}]))"),
			// Math formulas
			Pattern.compile("(\\$([^\\$]|\\\\\\$)+\\$|\\$\\$([^\\$]|\\\\\\$)+\\$\\$)"),
			// TeX line comment TODO consider block comment via comments environment
			Pattern.compile("(%.*\\n)") };

	/**
	 * Corresponding styles for the above patterns
	 */
	private static final CharacterStyle[] LATEX_STYLES = {
			// Special character style: yellow
			new ForegroundColorSpan(Color.parseColor("#cc8000")),
			// Command style: blue
			new ForegroundColorSpan(Color.parseColor("#0000ff")),
			// Formula style: green
			new ForegroundColorSpan(Color.parseColor("#00cc00")),
			// Comment style: gray
			new ForegroundColorSpan(Color.parseColor("#a0a0a0")) };

	private static final boolean TEST = true;

	private static final ComponentName TEXPORTAL = new ComponentName("lah.texportal",
			"lah.texportal.activities.CompileDocumentActivity");

	private TextArea document_textview;

	private FileDialog file_select_dialog;

	private File opening_file;

	private AlertDialog save_confirm_dialog;

	private SpecialSymbolsFragment special_symbols_pane;

	private LaTeXStringBuilder annotate(String text) {
		// SpannableStringBuilder builder = new SpannableStringBuilder(text);
		LaTeXStringBuilder builder = new LaTeXStringBuilder(text);
		int num_spans = 0;
		for (int i = 0; i < LATEX_PATTERNS.length; i++) {
			Matcher matcher = LATEX_PATTERNS[i].matcher(builder);
			if (i == 0) {
				// special character: check if it is escaped? TODO This is wrong in some case!
				while (matcher.find()) {
					int start = matcher.start(1);
					int end = matcher.end(1);
					if (i == 0 && start > 0 && text.charAt(start - 1) == '\\')
						continue;
					else
						builder.setSpan(CharacterStyle.wrap(LATEX_STYLES[i]), start, end,
								Spannable.SPAN_INCLUSIVE_INCLUSIVE);
				}
			} else {
				while (matcher.find()) {
					builder.setSpan(CharacterStyle.wrap(LATEX_STYLES[i]), matcher.start(1), matcher.end(1),
							Spannable.SPAN_INCLUSIVE_INCLUSIVE);
					num_spans++;
				}
			}
		}
		Log.v("annotate", "Total number of spans : " + num_spans);
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

	private void hideQuickSpecialSymbolPane() {
		if (special_symbols_pane != null)
			getSupportFragmentManager().beginTransaction().hide(special_symbols_pane).commit();
	}

	public void insertAtCursor(String string) {
		if (document_textview == null)
			document_textview = (TextArea) findViewById(R.id.document_area);
		System.out.println("Insert " + string + " at cursor.");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_latex_editing);
		if (TEST) {
			openDocument(new File(Environment.getExternalStorageDirectory(), "lambda.tex"));
		} else {
			handleIntent();
		}

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
			confirmSave();
			try {
				startActivityForResult(new Intent().setComponent(TEXPORTAL).setData(Uri.fromFile(opening_file)), 0);
			} catch (ActivityNotFoundException e) {
				Toast.makeText(this, getString(R.string.message_cannot_send_texportal), Toast.LENGTH_SHORT).show();
			}
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void openDocument(File file) {
		if (file == null || !file.exists())
			return;
		hideQuickSpecialSymbolPane();
		try {
			opening_file = file;
			String file_content = Streams.readTextFile(file);
			if (document_textview == null) {
				document_textview = (TextArea) findViewById(R.id.document_area);
			}
			LaTeXStringBuilder document = annotate(file_content);
			document.setWatcher(document_textview);
			document_textview.setText(document);
			showQuickSpecialSymbolPane();
		} catch (IOException e) {
			e.printStackTrace(System.out);
		}
	}

	private void showQuickSpecialSymbolPane() {
		if (special_symbols_pane == null) {
			special_symbols_pane = SpecialSymbolsFragment.newInstance();
			getSupportFragmentManager().beginTransaction().replace(R.id.special_symbols_fragment, special_symbols_pane)
					.commit();
		} else {
			getSupportFragmentManager().beginTransaction().show(special_symbols_pane).commit();
		}
	}

}
