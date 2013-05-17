package lah.texpert;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.texpert.LaTeXStringBuilder.DocumentWatcher;
import lah.texpert.LaTeXStringBuilder.Section;
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
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils.TruncateAt;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

/**
 * Main activity to edit the LaTeX source code
 * 
 * @author L.A.H.
 * 
 */
public class LaTeXEditingActivity extends FragmentActivity implements DocumentWatcher {

	static final boolean DEBUG = false;

	// "testlatex.tex"; // "texbook.tex";
	static final String DEBUG_FILE = "lambda.tex";

	static final String PREF_AUTOSAVE_BEFORE_COMPILE = "autosave_before_compile",
			PREF_LAST_OPEN_FILE = "last_open_file", PREF_AUTOSAVE_ON_SUSPEND = "autosave_on_suspend";

	/**
	 * Template for a new document, having three parameters: document class, author, and auto-generated content
	 */
	private static final String TEMPLATE = "\\documentclass{%s}\n\n\\title{}\n\\author{%s}\n\\date{\\today}\n\n\\begin{document}\n\n\\begin{abstract}\n\n\\end{abstract}\n\n\\maketitle\n\n%s\\end{document}\n";

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

	private String[] displayed_document_classes = { "Blank", "Article", "AMS Article", "Beamer" }, document_classes = {
			"", "article", "amsart", "beamer" };

	private EditorFragment editor_fragment;

	private FileDialog file_select_dialog;

	private LogViewFragment log_fragment;

	private ViewPager main_pager;

	private EditorLogPagerAdapter main_pager_adapter;

	private final Runnable notify_open_document_error = new Runnable() {

		@Override
		public void run() {
			showToast(R.string.message_cannot_open_document);
		}
	};

	private OpenDocumentTask open_document_task;

	private OutlineFragment outline_fragment;

	private AlertDialog overwrite_confirm_dialog;

	private File pdf_file;

	private SharedPreferences pref;

	private ActionMode search_action_mode;

	private ActionMode.Callback search_action_mode_callback = new SearchActionModeCallback();

	private View search_area;

	private EditText search_pattern_edittext;

	private final Runnable show_new_doc_options = new Runnable() {

		@Override
		public void run() {
			new AlertDialog.Builder(LaTeXEditingActivity.this).setTitle("New document")
					.setSingleChoiceItems(displayed_document_classes, 0, null)
					.setPositiveButton("Create", new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							int cls = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
							if (cls == 0)
								setCurrentDocument(new LaTeXStringBuilder(LaTeXEditingActivity.this, "", null));
							else {
								String doccls = document_classes[cls];
								// TODO Cache author & allow for customized templates
								String author = "";
								String autogen = "\\section{}\n\n\\section{}\n\n\\section{}\n\n";
								String newdoc = String.format(TEMPLATE, doccls, author, autogen);
								setCurrentDocument(new LaTeXStringBuilder(LaTeXEditingActivity.this, newdoc, null));
							}
						}
					}).setNegativeButton("Cancel", null).create().show();
		}
	};

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
			return showSaveAsDialog(compile_document, null);
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

	@Override
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
		case R.id.action_wordwrap:
			boolean do_word_wrap = !item.isChecked(); // toggle check state
			item.setChecked(do_word_wrap);
			editor_fragment.document_textview.setHorizontallyScrolling(!do_word_wrap);
			return true;
		case R.id.action_save:
			// Untitled document --> ask user to select the file to save as
			return (current_document.getFile() == null ? showSaveAsDialog(null, null)
					: showConfirmOverwriteDialog(null));
		case R.id.action_pdflatex:
			return compile(false);
		case R.id.action_bibtex:
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
			return showSearchFragment();
		case R.id.action_format:
			return showFormatFragment();
		case R.id.action_clean:
			return showCleanupDialog();
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

	private void setCurrentDocument(LaTeXStringBuilder document) {
		current_document = document;
		editor_fragment.setDocument(current_document);
		updateFileInfo();
	}

	private boolean showCleanupDialog() {
		File file = current_document.getFile();
		if (file != null) {
			String name = file.getName();
			final String basename = name.substring(0, name.length() - 4);
			final File dir = file.getParentFile();
			final String[] cleanable_files = dir.list(new FilenameFilter() {

				@Override
				public boolean accept(File dir, String name) {
					return name.startsWith(basename) && !name.endsWith(".tex") && !name.endsWith(".ltx");
				}
			});
			if (cleanable_files == null || cleanable_files.length == 0)
				return showToast("No file to clean!");
			boolean[] clean_suggestion = new boolean[cleanable_files.length];
			for (int i = 0; i < cleanable_files.length; i++)
				clean_suggestion[i] = !cleanable_files[i].endsWith("bbl") && !cleanable_files[i].endsWith("pdf");
			new AlertDialog.Builder(this).setTitle("Remove generated files")
					.setMultiChoiceItems(cleanable_files, clean_suggestion, null)
					.setPositiveButton("Clean", new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							SparseBooleanArray selected_files = ((AlertDialog) dialog).getListView()
									.getCheckedItemPositions();
							int count = 0;
							for (int i = 0; i < cleanable_files.length; i++) {
								if (selected_files.get(i) && new File(dir, cleanable_files[i]).delete())
									count++;
							}
							showToast(count + " files deleted!");
						}
					}).setNegativeButton("Cancel", null).create().show();
		} else
			showToast("No file to clean!");
		return true;
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

	private boolean showFormatFragment() {
		// TODO Implement
		return showToast(R.string.message_unimplemented_feature);
	}

	private boolean showNewDocumentDialog() {
		Runnable action = show_new_doc_options;
		if (current_document.isModified())
			if (current_document.getFile() == null)
				showSaveAsDialog(action, action);
			else
				showConfirmOverwriteDialog(action);
		else
			action.run();
		return true;
	}

	private boolean showSaveAsDialog(final Runnable action_after_save, final Runnable action_if_cancel) {
		// Ask user to select a file to save as
		final EditText file_path_field = new EditText(getActivity());
		new AlertDialog.Builder(getActivity()).setTitle(getString(R.string.title_save_as)).setView(file_path_field)
				.setPositiveButton(getString(R.string.action_save), new OnClickListener() {

					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						current_document.save(new File(file_path_field.getText().toString()), action_after_save);
					}
				}).setNegativeButton(getString(R.string.action_cancel), new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (action_if_cancel != null)
							action_if_cancel.run();
					}
				}).show();
		return true;
	}

	private boolean showSearchFragment() {
		// editor_fragment.showSearchFragment();
		if (search_action_mode != null)
			return true;
		search_action_mode = getActivity().startActionMode(search_action_mode_callback);
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

	/**
	 * Fragment for editing LaTeX source code
	 * 
	 * @author L.A.H.
	 * 
	 */
	public static class EditorFragment extends Fragment {

		private static final String[] special_symbols = { "\\", "$", "{", "}", "[", "]", "^", "_", "(", ")", "%", "&",
				"#" };

		public static EditorFragment newInstance() {
			EditorFragment fragment = new EditorFragment();
			return fragment;
		}

		public EditText document_textview;

		private QuickAccessFragment quick_access_fragment;

		public EditorFragment() {
			// Required empty public constructor
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			// Inflate the layout for this fragment
			View view = inflater.inflate(R.layout.fragment_editor, container, false);

			final LaTeXEditingActivity activity = (LaTeXEditingActivity) getActivity();

			// Prepare the grid of special symbols
			GridView symbols_grid = (GridView) view.findViewById(R.id.special_symbols_grid);
			symbols_grid.setNumColumns(special_symbols.length);
			symbols_grid.setAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1,
					special_symbols));
			symbols_grid.setOnItemClickListener(new OnItemClickListener() {

				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					activity.current_document.replaceSelection(special_symbols[position]);
				}
			});

			// Prepare document editing area
			document_textview = (EditText) view.findViewById(R.id.document_area);
			document_textview.setEditableFactory(new Editable.Factory() {

				@Override
				public Editable newEditable(CharSequence source) {
					return activity.current_document;
				}
			});
			document_textview.setText(activity.current_document);

			// Prepare button to access navigation/insertion
			ImageButton quick_access_button = (ImageButton) view.findViewById(R.id.quick_access_button);
			quick_access_button.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View arg0) {
					// toggleQuickAccess();
				}
			});

			return view;
		}

		@Override
		public void onResume() {
			super.onResume();
			if (document_textview == null)
				return;

			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());

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

		public void setDocument(LaTeXStringBuilder document) {
			document_textview.setText(document);
			document.setView(document_textview);
		}

		public void toggleQuickAccess() {
			FragmentTransaction trans = getChildFragmentManager().beginTransaction();
			if (quick_access_fragment == null) {
				quick_access_fragment = QuickAccessFragment.newInstance();
				trans.replace(R.id.quick_access_fragment, quick_access_fragment).commit();
				return;
			}
			if (quick_access_fragment.isVisible())
				trans.hide(quick_access_fragment).commit();
			else
				trans.show(quick_access_fragment).commit();
		}

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
				return 0.5f;
			case 2:
				return 0.6f;
			default:
				return 1.0f;
			}
		}

	}

	/**
	 * Fragment for viewing pdfLaTeX generated log file
	 * 
	 * @author L.A.H.
	 * 
	 */
	public static class LogViewFragment extends Fragment {

		private static final Pattern error_pattern = Pattern.compile("! (.*)"), line_num_pattern = Pattern
				.compile("((l\\.|line |lines )\\s*(\\d+))[^\\d].*"), warning_pattern = Pattern
				.compile("(((! )?(La|pdf)TeX)|Package) .*Warning.*:(.*)|(Over|Under)(full \\\\[hv]box .*)");

		private static final ForegroundColorSpan ERROR_STYLE = new ForegroundColorSpan(0xffff0000),
				WARNING_STYLE = new ForegroundColorSpan(0xff00cc00), LINE_NUM_STYLE = new ForegroundColorSpan(
						0xff0000cc);

		public static LogViewFragment newInstance() {
			LogViewFragment fragment = new LogViewFragment();
			return fragment;
		}

		/**
		 * Log file to bind to
		 */
		private File log_file;

		private String log_file_name;

		private TextView log_textview;

		/**
		 * Observers to watch for changes in the source directory
		 */
		private FileObserver src_dir_observer;

		public LogViewFragment() {
			// Required empty public constructor
		}

		void loadLog() {
			final byte[] buffer = new byte[(int) log_file.length()];
			try {
				InputStream str = new FileInputStream(log_file);
				str.read(buffer);
				str.close();
				String logstr = new String(buffer, 0, buffer.length);
				final SpannableStringBuilder log_content = new SpannableStringBuilder(logstr);
				Matcher error_matcher = error_pattern.matcher(logstr);
				while (error_matcher.find()) {
					log_content.setSpan(CharacterStyle.wrap(ERROR_STYLE), error_matcher.start(), error_matcher.end(),
							Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
				}
				Matcher warning_matcher = warning_pattern.matcher(logstr);
				while (warning_matcher.find()) {
					log_content.setSpan(CharacterStyle.wrap(WARNING_STYLE), warning_matcher.start(),
							warning_matcher.end(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
				}
				Matcher line_num_matcher = line_num_pattern.matcher(logstr);
				while (line_num_matcher.find()) {
					log_content.setSpan(CharacterStyle.wrap(LINE_NUM_STYLE), line_num_matcher.start(1),
							line_num_matcher.end(1), 0);
				}
				log_textview.post(new Runnable() {

					@Override
					public void run() {
						log_textview.setText(log_content);
					}
				});
			} catch (Exception e) {
				e.printStackTrace(System.out);
			}
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			// Inflate the layout for this fragment
			View view = inflater.inflate(R.layout.fragment_log_view, container, false);
			log_textview = (TextView) view.findViewById(R.id.log_textview);
			return view;
		}

		public void startTrackingLogFile(File log_file) {
			if (log_file == null)
				return;

			this.log_file = log_file;
			log_file_name = log_file.getName();

			if (log_file.exists())
				loadLog();
			else
				log_textview.post(new Runnable() {

					@Override
					public void run() {
						log_textview.setText("");
					}
				});

			// Monitor changes to the the directory
			if (src_dir_observer != null)
				src_dir_observer.stopWatching();
			src_dir_observer = new FileObserver(log_file.getParent(), FileObserver.CLOSE_WRITE) {

				@Override
				public void onEvent(int event, String path) {
					// log file is overwritten by external process ==> reload the log
					if (path != null && log_file_name != null && path.equals(log_file_name))
						loadLog();
				}
			};
			src_dir_observer.startWatching();
		}

		public void stopTracking() {
			if (src_dir_observer != null)
				src_dir_observer.stopWatching();
			log_textview.post(new Runnable() {

				@Override
				public void run() {
					log_textview.setText("");
				}
			});
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

	/**
	 * Fragment to contain the document outline, used with pager
	 * 
	 * @author L.A.H.
	 * 
	 */
	public static class OutlineFragment extends Fragment {

		public static OutlineFragment newInstance(EditorFragment editing_fragment) {
			OutlineFragment fragment = new OutlineFragment();
			return fragment;
		}

		private OutlineAdapter outline_adapter;

		private ListView outline_listview;

		public OutlineFragment() {
			// Required empty public constructor
		}

		LaTeXStringBuilder getDocument() {
			return ((LaTeXEditingActivity) getActivity()).current_document;
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			// Inflate the layout for this fragment
			View view = inflater.inflate(R.layout.fragment_outline, container, false);
			outline_listview = (ListView) view.findViewById(R.id.document_outline);
			outline_adapter = new OutlineAdapter(getActivity());
			// outline_listview.setAdapter(outline_adapter);
			outline_listview.setOnItemClickListener(new OnItemClickListener() {

				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					getDocument().setCursor(outline_adapter.getItem(position).getTextPosition());
				}
			});
			return view;
		}

		@Override
		public void setUserVisibleHint(boolean isVisibleToUser) {
			super.setUserVisibleHint(isVisibleToUser);
			if (isVisibleToUser) {
				outline_listview.setAdapter(outline_adapter);
			} else if (outline_listview != null) {
				outline_listview.setAdapter(null);
			}
		}

		public class OutlineAdapter extends ArrayAdapter<Section> {

			public OutlineAdapter(Context context) {
				super(context, android.R.layout.simple_list_item_1);
			}

			@Override
			public int getCount() {
				List<Section> sections = getDocument().getOutLine();
				return sections == null ? 0 : sections.size();
			}

			@Override
			public Section getItem(int position) {
				List<Section> sections = getDocument().getOutLine();
				return sections == null ? null : sections.get(position);
			}

		}

		/**
		 * Task to refresh the document outline in back ground
		 */
		// class ReloadOutlineTask extends AsyncTask<Void, Void, Section[]> {
		//
		// @Override
		// protected Section[] doInBackground(Void... params) {
		// return null;
		// }
		//
		// @Override
		// protected void onPostExecute(Section[] result) {
		// sections = result;
		// }
		//
		// }

	}

	/**
	 * Fragment to contain commands for faster insertion
	 * 
	 * @author L.A.H.
	 * 
	 */
	public static class QuickAccessFragment extends Fragment {

		static final String[] access_categories = { "Command", "Label" };

		public static QuickAccessFragment newInstance() {
			QuickAccessFragment fragment = new QuickAccessFragment();
			return fragment;
		}

		public QuickAccessFragment() {
			// Required empty public constructor
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			// Inflate the layout for this fragment
			View view = inflater.inflate(R.layout.fragment_quick_access, container, false);
			return view;
		}

		/**
		 * Adapter containing quickly accessible items such as
		 * 
		 * - Frequently used commands
		 * 
		 * - Defined labels
		 * 
		 * @author L.A.H.
		 * 
		 */
		class QuickAccessAdapter extends BaseExpandableListAdapter {

			static final int CATEGORY_COMMAND = 0, CATEGORY_LABELS = 1;

			static final String SYM_PILCROW = "\u00B6", SYM_CENT = "\u00A2", SYM_SECTION = "\u00A7";

			String[] commands;

			int current_expanding_group = -1;

			private final LayoutInflater inflater;

			public QuickAccessAdapter(Context context) {
				inflater = LayoutInflater.from(context);
			}

			@Override
			public String getChild(int groupPosition, int childPosition) {
				switch (groupPosition) {
				case CATEGORY_COMMAND:
					return commands == null ? null : commands[childPosition];
				default:
					// return insertion_items[groupPosition][childPosition];
					return null;
				}
			}

			@Override
			public long getChildId(int groupPosition, int childPosition) {
				return childPosition;
			}

			@Override
			public int getChildrenCount(int groupPosition) {
				switch (groupPosition) {
				case CATEGORY_COMMAND:
					return commands == null ? 0 : commands.length;
				default:
					// return insertion_items[groupPosition].length;
					return 0;
				}
			}

			@Override
			public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView,
					ViewGroup parent) {
				return getTextViewWithText(getChild(groupPosition, childPosition), convertView);
			}

			@Override
			public String getGroup(int groupPosition) {
				return access_categories[groupPosition];
			}

			@Override
			public int getGroupCount() {
				return access_categories.length;
			}

			@Override
			public long getGroupId(int groupPosition) {
				return groupPosition;
			}

			@Override
			public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
				return getTextViewWithText(getGroup(groupPosition), convertView);
			}

			private View getTextViewWithText(String text, View convertView) {
				TextView view;
				if (convertView instanceof TextView) {
					view = (TextView) convertView;
				} else {
					view = (TextView) inflater.inflate(android.R.layout.simple_expandable_list_item_1, null);
					view.setTextSize(18);
					view.setSingleLine();
					view.setEllipsize(TruncateAt.END);
				}
				view.setText(text);
				return view;
			}

			@Override
			public boolean hasStableIds() {
				return false;
			}

			@Override
			public boolean isChildSelectable(int groupPosition, int childPosition) {
				return true;
			}
		}

	}

	class SearchActionModeCallback implements ActionMode.Callback {

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
			case R.id.menu_search:
				String search_pattern = search_pattern_edittext.getText().toString();
				boolean regex = mode.getMenu().findItem(R.id.menu_search_with_regex).isChecked();
				current_document.search(search_pattern, regex);
				return true;
				// case R.id.menu_replace:
				// return true;
			case R.id.menu_search_with_regex:
				item.setChecked(!item.isChecked());
				return true;
			default:
				return false;
			}
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			mode.getMenuInflater().inflate(R.menu.search, menu);
			if (search_area == null) {
				search_area = getLayoutInflater().inflate(R.layout.cab_search, null);
				search_pattern_edittext = (EditText) search_area.findViewById(R.id.search_pattern_edittext);
			}
			mode.setCustomView(search_area);
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			search_action_mode = null;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}
	}

}
