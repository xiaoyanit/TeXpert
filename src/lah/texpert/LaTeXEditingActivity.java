package lah.texpert;

import java.io.File;
import java.io.IOException;

import lah.spectre.stream.Streams;
import lah.widgets.fileview.FileDialog;
import lah.widgets.fileview.IFileSelectListener;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Main activity to edit the LaTeX source code
 * 
 * @author L.A.H.
 * 
 */
public class LaTeXEditingActivity extends FragmentActivity {

	private static final boolean TEST = false;

	private static final ComponentName TEXPORTAL = new ComponentName("lah.texportal",
			"lah.texportal.activities.CompileDocumentActivity");

	private LaTeXStringBuilder current_document;

	private File current_file;

	private EditText document_textview;

	private FileDialog file_select_dialog;

	private AlertDialog save_confirm_dialog;

	private final OnClickListener save_doc_on_click = new OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			try {
				if (current_document != null && current_file != null)
					Streams.writeStringToFile(current_document.toString(), current_file, false);
			} catch (IOException e) {
				Toast.makeText(LaTeXEditingActivity.this, getString(R.string.message_cannot_save_document),
						Toast.LENGTH_SHORT).show();
			}
		}
	};

	private QuickTeXFragment special_symbols_pane;

	private void confirmSave() {
		if (current_document == null)
			return;
		if (save_confirm_dialog == null)
			save_confirm_dialog = new AlertDialog.Builder(this).setTitle("Save document?")
					.setPositiveButton("Save", save_doc_on_click).setNegativeButton("Cancel", null).create();
		save_confirm_dialog.show();
	}

	@SuppressWarnings("unused")
	private void hideQuickSpecialSymbolPane() {
		if (special_symbols_pane != null)
			getSupportFragmentManager().beginTransaction().hide(special_symbols_pane).commit();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_latex_editing);
		document_textview = (EditText) findViewById(R.id.document_area);
		document_textview.setEditableFactory(new Editable.Factory() {

			@Override
			public Editable newEditable(CharSequence source) {
				return new LaTeXStringBuilder(source);
			}
		});
		showQuickSpecialSymbolPane();
		if (TEST) {
			openDocument(new File(Environment.getExternalStorageDirectory(), "lambda.tex"));
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
				startActivityForResult(new Intent().setComponent(TEXPORTAL).setData(Uri.fromFile(current_file)), 0);
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
		try {
			current_file = file;
			String file_content = Streams.readTextFile(file);
			document_textview.setText(file_content);
			current_document = (LaTeXStringBuilder) document_textview.getText();
			special_symbols_pane.target_document = current_document;
			// showQuickSpecialSymbolPane();
		} catch (IOException e) {
			e.printStackTrace(System.out);
		}
	}

	private void showQuickSpecialSymbolPane() {
		if (special_symbols_pane == null) {
			special_symbols_pane = QuickTeXFragment.newInstance(current_document);
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.quick_insertion_items_fragment, special_symbols_pane).commit();
		} else {
			special_symbols_pane.target_document = current_document;
			getSupportFragmentManager().beginTransaction().show(special_symbols_pane).commit();
		}
	}

}
