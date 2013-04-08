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

	private static final long REFRESH_PERIOD = 6000;

	private static final ComponentName TEXPORTAL = new ComponentName("lah.texportal",
			"lah.texportal.activities.CompileDocumentActivity");

	private ParagraphsAdapter code_adapter;

	private Runnable code_refresh = new Runnable() {

		@Override
		public void run() {
			// commit the changes
			if (code_adapter != null && !code_adapter.isEditing())
				code_adapter.notifyDataSetChanged();
			// post next execution
			handler.postDelayed(this, REFRESH_PERIOD);
		}
	};

	private File focusing_file;

	private Handler handler;

	private ListView latex_source_listview;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_latex_editing);
		latex_source_listview = (ListView) findViewById(R.id.latex_source_listview);
		Intent intent = getIntent();
		Uri data;
		if ((data = intent.getData()) != null && data.getScheme().equals("file")
				&& (focusing_file = new File(data.getPath())).exists()) {
			intent.setData(null);
			try {
				String test_document = Streams.readTextFile(focusing_file);
				code_adapter = new ParagraphsAdapter(this, 0);
				code_adapter.bindText(test_document);
				latex_source_listview.setAdapter(code_adapter);
			} catch (IOException e) {
				e.printStackTrace(System.out);
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

	@Override
	protected void onPause() {
		super.onPause();
		if (handler != null)
			handler.removeCallbacks(code_refresh);
	}

	@Override
	protected void onResume() {
		super.onResume();
		handler = new Handler();
		handler.postDelayed(code_refresh, REFRESH_PERIOD);
	}

}
