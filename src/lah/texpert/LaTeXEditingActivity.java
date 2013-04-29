package lah.texpert;

import java.io.File;
import java.io.FileInputStream;

import lah.texpert.fragments.EditorFragment;
import lah.texpert.fragments.LogViewFragment;
import lah.texpert.fragments.QuickAccessToolkitFragment;
import lah.widgets.fileview.FileDialog;
import lah.widgets.fileview.IFileSelectListener;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Main activity to edit the LaTeX source code
 * 
 * @author L.A.H.
 * 
 */
public class LaTeXEditingActivity extends FragmentActivity {

	public class EditorLogPagerAdapter extends FragmentPagerAdapter {

		public EditorLogPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public int getCount() {
			return 2;
		}

		@Override
		public Fragment getItem(int position) {
			switch (position) {
			case 0:
				if (editor_fragment == null)
					editor_fragment = EditorFragment.newInstance();
				return editor_fragment;
			case 1:
			default:
				if (log_fragment == null)
					log_fragment = LogViewFragment.newInstance();
				return log_fragment;
			}
		}

	}

	class OpenDocumentTask extends AsyncTask<File, Void, String> {

		static final String TAG = "open_document_task";

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
				current_document = new LaTeXStringBuilder((LaTeXEditingActivity) getActivity(), result, file);
				if (DEBUG)
					Log.v(TAG, "Indexing takes " + (System.currentTimeMillis() - t) + "ms");
				inpstr.close();
				return result;
			} catch (Exception e) {
				getActivity().runOnUiThread(notify_open_document_error);
			}
			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			long t = System.currentTimeMillis();
			if (result != null) {
				editor_fragment.setDocument(current_document);
				if (DEBUG)
					Log.v(TAG, "setText takes " + (System.currentTimeMillis() - t) + "ms");
			}
			t = System.currentTimeMillis();
			// reading_state_switcher.showPrevious();
			if (DEBUG)
				Log.v(TAG, "showPrevious takes " + (System.currentTimeMillis() - t) + "ms");
		}
	}

	class OpeningFileBufferAdapter extends ArrayAdapter<File> {

		public OpeningFileBufferAdapter(Context context) {
			super(context, 0);
		}

	}

	static final boolean DEBUG = false;

	static final String PREF_AUTOSAVE_BEFORE_COMPILE = "autosave_before_compile",
			PREF_LAST_OPEN_FILE = "last_open_file";

	// "testlatex.tex"; // "lambda.tex";
	static final String TEST_FILE = "texbook.tex";

	static final ComponentName TEXPORTAL = new ComponentName("lah.texportal",
			"lah.texportal.activities.CompileDocumentActivity");

	static final String TEXPORTAL_ARGUMENT_ENGINE = "lah.texportal.argument.ENGINE";

	ActionBar action_bar;

	private final Runnable compile_document_bibtex = new Runnable() {

		@Override
		public void run() {
			try {
				startActivityForResult(
						new Intent().setComponent(TEXPORTAL).setData(Uri.fromFile(current_document.getFile()))
								.putExtra(TEXPORTAL_ARGUMENT_ENGINE, "bibtex"), 0);
			} catch (ActivityNotFoundException e) {
				Toast.makeText(LaTeXEditingActivity.this, getString(R.string.message_cannot_send_texportal),
						Toast.LENGTH_SHORT).show();
			}
		}
	};

	private final Runnable compile_document_pdflatex = new Runnable() {

		@Override
		public void run() {
			try {
				startActivityForResult(
						new Intent().setComponent(TEXPORTAL).setData(Uri.fromFile(current_document.getFile()))
								.putExtra(TEXPORTAL_ARGUMENT_ENGINE, "pdflatex"), 0);
			} catch (ActivityNotFoundException e) {
				Toast.makeText(LaTeXEditingActivity.this, getString(R.string.message_cannot_send_texportal),
						Toast.LENGTH_SHORT).show();
			}
		}
	};

	public LaTeXStringBuilder current_document;

	EditorFragment editor_fragment;

	private FileDialog file_select_dialog;

	LogViewFragment log_fragment;

	ViewPager main_pager;

	EditorLogPagerAdapter main_pager_adapter;

	QuickAccessToolkitFragment navigation_fragment;

	private final Runnable notify_open_document_error = new Runnable() {

		@Override
		public void run() {
			Toast.makeText(getActivity(), getString(R.string.message_cannot_open_document), Toast.LENGTH_SHORT).show();
		}
	};

	OpenDocumentTask open_document_task;

	private AlertDialog overwrite_confirm_dialog;

	private SharedPreferences pref;

	// ViewSwitcher reading_state_switcher;

	private void compile(boolean is_bibtex) {
		Runnable compile_document = is_bibtex ? this.compile_document_bibtex : this.compile_document_pdflatex;
		if (current_document.getFile() == null) {
			showSaveFileAs(compile_document);
			return;
		}
		if (current_document.isModified()) {
			if (pref.getBoolean(PREF_AUTOSAVE_BEFORE_COMPILE, false))
				current_document.save(current_document.getFile(), compile_document);
			else
				confirmOverwrite(compile_document);
		} else {
			compile_document.run();
		}
	}

	private void confirmOverwrite(final Runnable action_after_overwrite) {
		if (current_document == null)
			return;
		if (overwrite_confirm_dialog == null)
			overwrite_confirm_dialog = new AlertDialog.Builder(this)
					.setTitle(getString(R.string.title_confirm_overwrite))
					.setPositiveButton(getString(R.string.action_save), new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							current_document.save(current_document.getFile(), action_after_overwrite);
						}
					}).setNegativeButton(getString(R.string.action_cancel), null).create();
		overwrite_confirm_dialog.show();
	}

	private LaTeXEditingActivity getActivity() {
		return this;
	}

	public void notifyDocumentModified() {
		// Update action bar
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_latex_editing);

		main_pager_adapter = new EditorLogPagerAdapter(getSupportFragmentManager());
		main_pager = (ViewPager) findViewById(R.id.editor_log_pager);
		main_pager.setAdapter(main_pager_adapter);

		action_bar = getActionBar();
		// action_bar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		// action_bar.setListNavigationCallbacks(new OpeningFileBufferAdapter(this), new OnNavigationListener() {
		//
		// @Override
		// public boolean onNavigationItemSelected(int itemPosition, long itemId) {
		// // TODO Auto-generated method stub
		// return false;
		// }
		// });

		// Prepare switcher
		// reading_state_switcher = (ViewSwitcher) findViewById(R.id.reading_state_switcher);

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
			}
			// else {
			// document_textview.setText("");
			// current_document = (LaTeXStringBuilder) document_textview.getText();
			// }
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
			current_document = new LaTeXStringBuilder(this, "", null);
			editor_fragment.setDocument(current_document);
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
			if (current_document.getFile() == null) {
				// Ask user to select a file to save as
				showSaveFileAs(null);
			} else {
				confirmOverwrite(null);
			}
			return true;
		case R.id.action_compile_pdflatex:
			compile(false);
			return true;
		case R.id.action_compile_bibtex:
			compile(true);
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
		if (current_document.getFile() != null)
			pref.edit().putString(PREF_LAST_OPEN_FILE, current_document.getFile().getAbsolutePath()).commit();
	}

	public void openDocument(File file) {
		if (file == null || !file.exists())
			return;
		// Toast.makeText(getActivity(), "Opening " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
		// Switch to temporary view
		// reading_state_switcher.showNext();
		// Read and style the file in background thread
		(open_document_task = new OpenDocumentTask()).execute(file);
	}

	public void showSaveFileAs(final Runnable action_after_save) {
		// Ask user to select a file to save as
		final EditText file_path_field = new EditText(getActivity());
		new AlertDialog.Builder(getActivity()).setTitle(getString(R.string.title_save_as))
		// .setMessage("Please type the path to save file as")
				.setView(file_path_field).setPositiveButton(getString(R.string.action_save), new OnClickListener() {

					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						current_document.save(new File(file_path_field.getText().toString()), action_after_save);
					}
				}).setNegativeButton(getString(R.string.action_cancel), null).show();
	}

}
