package lah.texpert;

import java.io.IOException;

import lah.spectre.stream.Streams;
import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.widget.ListView;

public class LaTeXEditingActivity extends Activity {

	private ParagraphsAdapter code_adapter;

	private ListView latex_source_listview;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_latex_editing);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.latex_editing, menu);
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();
		latex_source_listview = (ListView) findViewById(R.id.latex_source_listview);
		try {
			String test_document = Streams.readTillEnd(getAssets().open("test.tex"));
			code_adapter = new ParagraphsAdapter(this, 0);
			code_adapter.bindText(test_document);
			latex_source_listview.setAdapter(code_adapter);
		} catch (IOException e) {
			e.printStackTrace(System.out);
		}
	}

}
