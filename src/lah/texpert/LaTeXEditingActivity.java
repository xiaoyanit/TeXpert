package lah.texpert;

import java.io.File;
import java.io.IOException;

import lah.spectre.stream.Streams;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.Toast;

/**
 * Main activity to edit the LaTeX source files
 * 
 * @author L.A.H.
 * 
 */
public class LaTeXEditingActivity extends Activity {

	@SuppressWarnings("unused")
	private static final long REFRESH_PERIOD = 6000;

	private static final ComponentName TEXPORTAL = new ComponentName("lah.texportal",
			"lah.texportal.activities.CompileDocumentActivity");

	private DocumentAdapter document_adapter;

	private File focusing_file;

	@SuppressWarnings("unused")
	private Handler handler;

	private ListView latex_source_listview;

	// private Runnable refresh = new Runnable() {
	//
	// @Override
	// public void run() {
	// if (document_adapter != null && !document_adapter.isEditing())
	// document_adapter.notifyDataSetChanged();
	// handler.postDelayed(this, REFRESH_PERIOD);
	// }
	// };

	@SuppressWarnings("unused")
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
		latex_source_listview = (ListView) findViewById(R.id.latex_source_listview);
		openDocument(new File(Environment.getExternalStorageDirectory(), "CV.tex"));
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
			// TODO handle opening of document
			return true;
		case R.id.action_save:
			// TODO handle saving of document
			return true;
		case R.id.action_send:
			// TODO make sure file is in accessible place (on sdcard)
			try {
				startActivityForResult(new Intent().setComponent(TEXPORTAL).setData(Uri.fromFile(focusing_file)), 0);
			} catch (ActivityNotFoundException e) {
				Toast.makeText(this, "Fail to send document for compilation: TeXPortal is not installed!",
						Toast.LENGTH_SHORT).show();
			}
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	// @Override
	// protected void onPause() {
	// super.onPause();
	// if (handler != null)
	// handler.removeCallbacks(refresh);
	// }
	//
	// @Override
	// protected void onResume() {
	// super.onResume();
	// handler = new Handler();
	// handler.postDelayed(refresh, REFRESH_PERIOD);
	// }

	private void openDocument(File file) {
		if (file == null || !file.exists())
			return;
		try {
			focusing_file = file;
			String file_content = Streams.readTextFile(file);
			document_adapter = new DocumentAdapter(this, file_content);
			latex_source_listview.setAdapter(document_adapter);
		} catch (IOException e) {
			e.printStackTrace(System.out);
		}
	}

}
