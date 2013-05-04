package lah.texpert;

import java.io.File;
import java.io.FileInputStream;

import lah.texpert.fragments.EditorFragment;
import lah.texpert.fragments.LogViewFragment;
import lah.texpert.fragments.OutlineFragment;
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
import android.widget.ViewSwitcher;

/**
 * Main activity to edit the LaTeX source code
 * 
 * TODO Fix crashes when swiping to other fragment right after load (but have not fully load document)
 * 
 * @author L.A.H.
 * 
 */
public class LaTeXEditingActivity extends FragmentActivity {

	static final boolean DEBUG = false;

	// "testlatex.tex"; // "texbook.tex";
	static final String DEBUG_FILE = "lambda.tex";

	static final String PREF_AUTOSAVE_BEFORE_COMPILE = "autosave_before_compile",
			PREF_LAST_OPEN_FILE = "last_open_file";

	static final ComponentName TEXPORTAL = new ComponentName("lah.texportal",
			"lah.texportal.activities.CompileDocumentActivity");

	static final String TEXPORTAL_ARGUMENT_ENGINE = "lah.texportal.argument.ENGINE";

	private ActionBar action_bar;

	private final Runnable compile_document_bibtex = new Runnable() {

		@Override
		public void run() {
			try {
				startActivityForResult(
						new Intent().setComponent(TEXPORTAL).setData(Uri.fromFile(current_document.getFile()))
								.putExtra(TEXPORTAL_ARGUMENT_ENGINE, "bibtex"), 0);
			} catch (ActivityNotFoundException e) {
				showToast(R.string.message_cannot_send_texportal);
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
				showToast(R.string.message_cannot_send_texportal);
			}
		}
	};

	public LaTeXStringBuilder current_document;

	EditorFragment editor_fragment;

	private FileDialog file_select_dialog;

	LogViewFragment log_fragment;

	ViewPager main_pager;

	EditorLogPagerAdapter main_pager_adapter;

	private final Runnable notify_open_document_error = new Runnable() {

		@Override
		public void run() {
			showToast(R.string.message_cannot_open_document);
		}
	};

	OpenDocumentTask open_document_task;

	private OutlineFragment outline_fragment;

	private AlertDialog overwrite_confirm_dialog;

	private File pdf_file;

	private SharedPreferences pref;

	/**
	 * This switcher is to be shown during reading phase so as to disable 'swiping' action in pager
	 * 
	 * Without disabling such swiping, crash might occur due to the edit text being 'invalidate' while the underlying
	 * static indexer is modified.
	 */
	private ViewSwitcher state_switcher;

	private boolean compile(boolean is_bibtex) {
		Runnable compile_document = is_bibtex ? compile_document_bibtex : compile_document_pdflatex;
		if (current_document.getFile() == null)
			return showSaveAsDialog(compile_document);
		if (current_document.isModified()) {
			if (pref.getBoolean(PREF_AUTOSAVE_BEFORE_COMPILE, false))
				current_document.save(current_document.getFile(), compile_document);
			else
				showConfirmOverwriteDialog(compile_document);
		} else
			compile_document.run();
		return true;
	}

	private LaTeXEditingActivity getActivity() {
		return this;
	}

	public void notifyDocumentStateChanged() {
		updateActionBar();
		updateFileInfo();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_latex_editing);

		// Setup the main pager
		editor_fragment = EditorFragment.newInstance();
		outline_fragment = OutlineFragment.newInstance(editor_fragment);
		log_fragment = LogViewFragment.newInstance();
		main_pager_adapter = new EditorLogPagerAdapter(getSupportFragmentManager());
		main_pager = (ViewPager) findViewById(R.id.editor_log_pager);
		main_pager.setAdapter(main_pager_adapter);
		main_pager.setCurrentItem(1);

		// Prepare action bar
		action_bar = getActionBar();
		// Set up the action bar to show a dropdown list.
		// action_bar.setDisplayShowTitleEnabled(false);
		// action_bar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		// action_bar.setListNavigationCallbacks(new ArrayAdapter<String>(action_bar.getThemedContext(),
		// android.R.layout.simple_list_item_1, android.R.id.text1, new String[] {}), new OnNavigationListener() {
		// @Override
		// public boolean onNavigationItemSelected(int itemPosition, long itemId) {
		// return false;
		// }
		// });

		// Prepare switcher
		state_switcher = (ViewSwitcher) findViewById(R.id.reading_state_switcher);

		// Handling user intent (in case open from file browsing app)
		current_document = new LaTeXStringBuilder(this, "", null);
		if (DEBUG) {
			openDocument(new File(Environment.getExternalStorageDirectory(), DEBUG_FILE));
		} else {
			Intent intent = getIntent();
			Uri data;
			File file;
			if ((data = intent.getData()) != null && data.getScheme().equals("file")
					&& (file = new File(data.getPath())).exists()) {
				intent.setData(null);
				openDocument(file);
			}
		}
		updateActionBar();

		pref = PreferenceManager.getDefaultSharedPreferences(this);
		// Debug.startMethodTracing("tracereflow.trace");
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
			return showNewDocumentDialog();
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
			// Untitled document --> ask user to select the file to save as
			return (current_document.getFile() == null ? showSaveAsDialog(null) : showConfirmOverwriteDialog(null));
		case R.id.action_compile_pdflatex:
			return compile(false);
		case R.id.action_compile_bibtex:
			return compile(true);
		case R.id.action_open_pdf:
			if (pdf_file != null && pdf_file.exists()) {
				try {
					startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(pdf_file),
							"application/pdf"));
				} catch (ActivityNotFoundException e) {
					showToast(R.string.message_no_pdf_viewer_app);
				}
			} else
				showToast(R.string.info_no_pdf_to_open);
			return true;
		case R.id.action_undo:
			return current_document.undoLastEdit();
		case R.id.action_search:
		case R.id.action_format:
		case R.id.action_clean:
			return showToast(R.string.message_unimplemented_feature);
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
	protected void onPause() {
		super.onPause();
		if (open_document_task != null)
			open_document_task.cancel(true);
		if (current_document != null && current_document.getFile() != null)
			pref.edit().putString(PREF_LAST_OPEN_FILE, current_document.getFile().getAbsolutePath()).commit();
	}

	private void openDocument(File file) {
		if (file == null || !file.exists())
			return;
		// Notify user
		showToast("Open " + file.getName());
		// Switch to progress bar view
		state_switcher.showNext();
		// Read and style the file in background thread
		(open_document_task = new OpenDocumentTask()).execute(file);
	}

	public void replaceCurrentSelection(String text) {
		if (current_document != null)
			current_document.replaceSelection(text);
	}

	private void setCurrentDocument(LaTeXStringBuilder document) {
		current_document = document;
		editor_fragment.setDocument(current_document);
		current_document.setCommandListener(editor_fragment.getCommandListListener());
		current_document.setOutlineListener(outline_fragment.getOutlineListener());
		current_document.generateMetaInfo();
		updateFileInfo();
	}

	private boolean showConfirmOverwriteDialog(final Runnable action_after_overwrite) {
		if (current_document == null)
			return true;
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
		return true;
	}

	private boolean showNewDocumentDialog() {
		// TODO Implement
		setCurrentDocument(new LaTeXStringBuilder(this, "", null));
		return true;
	}

	private boolean showSaveAsDialog(final Runnable action_after_save) {
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
		return true;
	}

	private boolean showToast(int msg_res_id) {
		Toast.makeText(this, getString(msg_res_id), Toast.LENGTH_SHORT).show();
		return true;
	}

	private boolean showToast(String msg) {
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
		return true;
	}

	private void updateActionBar() {
		if (current_document.getFile() != null)
			action_bar.setSubtitle(current_document.getFile().getName() + (current_document.isModified() ? "*" : ""));
		else
			action_bar.setSubtitle("untitled");
	}

	private void updateFileInfo() {
		File file = current_document.getFile();
		if (file != null) {
			String name = file.getName();
			if (name.endsWith(".tex") || name.endsWith(".ltx")) {
				File dir = file.getParentFile();
				String basename = name.substring(0, name.length() - 4);
				log_fragment.startTrackingLogFile(new File(dir, basename + ".log"));
				pdf_file = new File(dir, basename + ".pdf");
			}
		} else {
			log_fragment.stopTracking();
			pdf_file = null;
		}
		updateActionBar();
	}

	public class EditorLogPagerAdapter extends FragmentPagerAdapter {

		public EditorLogPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public int getCount() {
			return 3;
		}

		@Override
		public Fragment getItem(int position) {
			switch (position) {
			case 0:
				return outline_fragment;
			case 1:
				return editor_fragment;
			case 2:
			default:
				return log_fragment;
			}
		}

		@Override
		public float getPageWidth(int position) {
			switch (position) {
			case 0:
				return 0.4f;
			case 2:
				return 0.6f;
			default:
				return 1.0f;
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
				setCurrentDocument(current_document);
				if (DEBUG)
					Log.v(TAG, "setText takes " + (System.currentTimeMillis() - t) + "ms");
			}
			t = System.currentTimeMillis();
			state_switcher.showPrevious();
			if (DEBUG)
				Log.v(TAG, "showPrevious takes " + (System.currentTimeMillis() - t) + "ms");
		}
	}

	class OpeningFileBufferAdapter extends ArrayAdapter<File> {

		public OpeningFileBufferAdapter(Context context) {
			super(context, 0);
		}

	}

}
