package lah.texpert;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;

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
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ViewSwitcher;

/**
 * Main activity to edit the LaTeX source code
 * 
 * @author L.A.H.
 * 
 */
public class LaTeXEditingActivity extends Activity {

	class OpenDocumentTask extends AsyncTask<File, Void, String> {

		private static final String TAG = "open_document_task";

		@Override
		protected String doInBackground(File... params) {
			File file = params[0];
			FileInputStream inpstr = null;
			try {
				// Reading file
				inpstr = new FileInputStream(file);
				int file_length = (int) file.length();
				byte[] buffer = new byte[file_length];
				long t = System.currentTimeMillis();
				inpstr.read(buffer, 0, file_length);
				String result = new String(buffer, 0, file_length);
				if (DEBUG)
					Log.v(TAG, "Reading takes " + (System.currentTimeMillis() - t) + "ms");

				// Indexing for syntax highlight
				t = System.currentTimeMillis();
				current_document = new LaTeXStringBuilder(result);
				if (DEBUG)
					Log.v(TAG, "Indexing takes " + (System.currentTimeMillis() - t) + "ms");

				current_file = file;
				inpstr.close();
				return result;
			} catch (Exception e) {
				runOnUiThread(notify_open_document_error);
			}
			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			long t = System.currentTimeMillis();
			if (result != null) {
				document_textview.setText(current_document);
				if (DEBUG)
					Log.v(TAG, "setText takes " + (System.currentTimeMillis() - t) + "ms");
			}
			t = System.currentTimeMillis();
			reading_state_switcher.showPrevious();
			if (DEBUG)
				Log.v(TAG, "showPrevious takes " + (System.currentTimeMillis() - t) + "ms");
		}
	}

	private static final boolean DEBUG = false;

	static final String PREF_LAST_OPEN_FILE = "last_open_file";

	// "testlatex.tex"; // "lambda.tex";
	private static final String TEST_FILE = "texbook.tex";

	private static final ComponentName TEXPORTAL = new ComponentName("lah.texportal",
			"lah.texportal.activities.CompileDocumentActivity");

	private final Runnable compile_document = new Runnable() {

		@Override
		public void run() {
			try {
				startActivityForResult(new Intent().setComponent(TEXPORTAL).setData(Uri.fromFile(current_file)), 0);
			} catch (ActivityNotFoundException e) {
				Toast.makeText(LaTeXEditingActivity.this, getString(R.string.message_cannot_send_texportal),
						Toast.LENGTH_SHORT).show();
			}
		}
	};

	private LaTeXStringBuilder current_document;

	private File current_file;

	private EditText document_textview;

	private FileDialog file_select_dialog;

	private final Runnable notify_open_document_error = new Runnable() {

		@Override
		public void run() {
			Toast.makeText(LaTeXEditingActivity.this, getString(R.string.message_cannot_open_document),
					Toast.LENGTH_SHORT).show();
		}
	};

	private OpenDocumentTask open_document_task;

	private AlertDialog overwrite_confirm_dialog;

	private SharedPreferences pref;

	private ViewSwitcher reading_state_switcher;

	private void confirmOverwrite(final Runnable action_after_overwrite) {
		if (current_document == null)
			return;
		if (overwrite_confirm_dialog == null)
			overwrite_confirm_dialog = new AlertDialog.Builder(this)
					.setTitle(getString(R.string.title_confirm_overwrite))
					.setPositiveButton(getString(R.string.action_save), new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							saveDocumentAs(current_file, action_after_overwrite);
						}
					}).setNegativeButton(getString(R.string.action_cancel), null).create();
		overwrite_confirm_dialog.show();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_latex_editing);

		// Prepare switcher
		reading_state_switcher = (ViewSwitcher) findViewById(R.id.reading_state_switcher);

		// Prepare document editing area
		current_document = new LaTeXStringBuilder("");
		document_textview = (EditText) findViewById(R.id.document_area);
		document_textview.setEditableFactory(new Editable.Factory() {

			@Override
			public Editable newEditable(CharSequence source) {
				return current_document;
			}
		});

		// Prepare quick insertion area
		// final ExpandableListView insertion_listview = (ExpandableListView)
		// findViewById(R.id.quick_insertion_items_listview);
		// final QuickInsertionItemsAdapter adapter = new QuickInsertionItemsAdapter(this);
		// insertion_listview.setAdapter(adapter);
		// insertion_listview.setOnChildClickListener(new OnChildClickListener() {
		//
		// @Override
		// public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id)
		// {
		// if (current_document != null) {
		// current_document.replaceSelection(adapter.getChild(groupPosition, childPosition));
		// }
		// return true;
		// }
		// });
		// insertion_listview.setOnGroupExpandListener(new OnGroupExpandListener() {
		//
		// @Override
		// public void onGroupExpand(int groupPosition) {
		// if (adapter.current_expanding_group > 0 && adapter.current_expanding_group != groupPosition)
		// insertion_listview.collapseGroup(adapter.current_expanding_group);
		// adapter.current_expanding_group = groupPosition;
		// }
		// });

		// Handling user intent
		if (DEBUG) {
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
		case R.id.action_new:
			current_file = null;
			current_document = new LaTeXStringBuilder("");
			document_textview.setText(current_document);
			return true;
		case R.id.action_open:
			if (file_select_dialog == null)
				file_select_dialog = new FileDialog(this, new IFileSelectListener() {

					@Override
					public void onFileSelected(File result) {
						openDocument(result);
					}
				}, pref.getString(PREF_LAST_OPEN_FILE, ""));
			file_select_dialog.show();
			return true;
		case R.id.action_save:
			if (current_file == null) {
				// Ask user to select a file to save as
				showSaveFileAs(null);
			} else {
				confirmOverwrite(null);
			}
			return true;
		case R.id.action_compile:
			if (current_file == null) {
				showSaveFileAs(compile_document);
				return true;
			}
			confirmOverwrite(compile_document);
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
	public void onPause() {
		super.onPause();
		if (open_document_task != null)
			open_document_task.cancel(true);
		if (current_file != null)
			pref.edit().putString(PREF_LAST_OPEN_FILE, current_file.getAbsolutePath()).commit();
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
		Toast.makeText(LaTeXEditingActivity.this, "Opening " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
		// Switch to temporary view
		reading_state_switcher.showNext();
		// Read and style the file in background thread
		(open_document_task = new OpenDocumentTask()).execute(file);
	}

	private void saveDocumentAs(File file, Runnable action_after_saved) {
		try {
			if (current_document != null && file != null) {
				FileWriter writer = new FileWriter(file, false);
				writer.write(current_document.toString());
				writer.close();
				current_file = file;
				if (action_after_saved != null)
					action_after_saved.run();
			}
		} catch (Exception e) {
			Toast.makeText(this, getString(R.string.message_cannot_save_document), Toast.LENGTH_SHORT).show();
			e.printStackTrace(System.out);
		}
	}

	public void showSaveFileAs(final Runnable action_after_save) {
		// Ask user to select a file to save as
		final EditText file_path_field = new EditText(this);
		new AlertDialog.Builder(this).setTitle(getString(R.string.title_save_as))
				.setMessage("Please type the path to save file as").setView(file_path_field)
				.setPositiveButton(getString(R.string.action_save), new OnClickListener() {

					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						saveDocumentAs(new File(file_path_field.getText().toString()), action_after_save);
					}
				}).setNegativeButton(getString(R.string.action_cancel), null).show();
	}

}
