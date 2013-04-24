package lah.texpert;

import java.io.File;

import lah.spectre.stream.Streams;
import lah.widgets.fileview.FileDialog;
import lah.widgets.fileview.IFileSelectListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupExpandListener;
import android.widget.Toast;

/**
 * Main activity to edit the LaTeX source code
 * 
 * @author L.A.H.
 * 
 */
public class LaTeXEditingActivity extends Activity {

	private static final boolean TESTING = true;

	private static final String TEST_FILE = "lambda.tex"; // "testlatex.tex";

	private static final ComponentName TEXPORTAL = new ComponentName("lah.texportal",
			"lah.texportal.activities.CompileDocumentActivity");

	private LaTeXStringBuilder current_document;

	private File current_file;

	private EditText document_textview;

	private FileDialog file_select_dialog;

	private SharedPreferences pref;

	private AlertDialog save_confirm_dialog;

	private final OnClickListener save_doc_on_click = new OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			saveDocumentAs(current_file);
		}
	};

	private void confirmOverwrite() {
		if (current_document == null)
			return;
		if (save_confirm_dialog == null)
			save_confirm_dialog = new AlertDialog.Builder(this).setTitle(getString(R.string.title_confirm_overwrite))
					.setPositiveButton(getString(R.string.action_save), save_doc_on_click)
					.setNegativeButton(getString(R.string.action_cancel), null).create();
		save_confirm_dialog.show();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_latex_editing);
		
		// Prepare document editing area
		document_textview = (EditText) findViewById(R.id.document_area);
		document_textview.setEditableFactory(new Editable.Factory() {

			@Override
			public Editable newEditable(CharSequence source) {
				return new LaTeXStringBuilder(source);
			}
		});
		
		// Prepare quick insertion area
		final ExpandableListView insertion_listview = (ExpandableListView) findViewById(R.id.quick_insertion_items_listview);
		final QuickInsertionItemsAdapter adapter = new QuickInsertionItemsAdapter(this);
		insertion_listview.setAdapter(adapter);
		insertion_listview.setOnChildClickListener(new OnChildClickListener() {

			@Override
			public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
				if (current_document != null) {
					current_document.replaceSelection(adapter.getChild(groupPosition, childPosition));
				}
				return true;
			}
		});
		insertion_listview.setOnGroupExpandListener(new OnGroupExpandListener() {

			@Override
			public void onGroupExpand(int groupPosition) {
				if (adapter.current_expanding_group > 0 && adapter.current_expanding_group != groupPosition)
					insertion_listview.collapseGroup(adapter.current_expanding_group);
				adapter.current_expanding_group = groupPosition;
			}
		});
		
		// Handling user intent
		if (TESTING) {
			openDocument(new File(Environment.getExternalStorageDirectory(), TEST_FILE));
		} else {
			Intent intent = getIntent();
			Uri data;
			File file;
			if ((data = intent.getData()) != null && data.getScheme().equals("file")
					&& (file = new File(data.getPath())).exists()) {
				intent.setData(null);
				openDocument(file);
			} else {
				document_textview.setText("");
				current_document = (LaTeXStringBuilder) document_textview.getText();
			}
		}
		pref = PreferenceManager.getDefaultSharedPreferences(this);
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
			if (current_file == null) {
				// Ask user to select a file to save as
				final EditText file_path_field = new EditText(this);
				new AlertDialog.Builder(this).setTitle(getString(R.string.title_save_as))
						.setMessage("Please type the path to save file as").setView(file_path_field)
						.setPositiveButton(getString(R.string.action_save), new OnClickListener() {

							@Override
							public void onClick(DialogInterface arg0, int arg1) {
								saveDocumentAs(new File(file_path_field.getText().toString()));
							}
						}).setNegativeButton(getString(R.string.action_cancel), null).show();
			} else {
				confirmOverwrite();
			}
			return true;
		case R.id.action_send:
			confirmOverwrite();
			try {
				startActivityForResult(new Intent().setComponent(TEXPORTAL).setData(Uri.fromFile(current_file)), 0);
			} catch (ActivityNotFoundException e) {
				Toast.makeText(this, getString(R.string.message_cannot_send_texportal), Toast.LENGTH_SHORT).show();
			}
			return true;
		case R.id.action_settings:
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		case R.id.action_quit:
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (document_textview == null)
			return;

		// Update the font size
		String font_size = pref.getString(SettingsActivity.PREF_FONT_SIZE, "18");
		try {
			document_textview.setTextSize(Integer.parseInt(font_size));
		} catch (Exception e) {
			document_textview.setTextSize(18);
		}

		// Update the font family
		String font_family = pref.getString(SettingsActivity.PREF_FONT_FAMILY, "Sans Serif");
		if (font_family.equals("Sans Serif"))
			document_textview.setTypeface(Typeface.SANS_SERIF);
		else if (font_family.equals("Monospace"))
			document_textview.setTypeface(Typeface.MONOSPACE);
		else
			document_textview.setTypeface(Typeface.SERIF);
	}

	private void openDocument(File file) {
		if (file == null || !file.exists())
			return;
		try {
			current_file = file;
			String file_content = Streams.readTextFile(file);
			document_textview.setText(file_content);
			current_document = (LaTeXStringBuilder) document_textview.getText();
		} catch (Exception e) {
			Toast.makeText(this, getString(R.string.message_cannot_open_document), Toast.LENGTH_SHORT).show();
		}
	}

	private void saveDocumentAs(File file) {
		try {
			if (current_document != null && file != null)
				Streams.writeStringToFile(current_document.toString(), file, false);
		} catch (Exception e) {
			Toast.makeText(this, getString(R.string.message_cannot_save_document), Toast.LENGTH_SHORT).show();
		}
	}
}
