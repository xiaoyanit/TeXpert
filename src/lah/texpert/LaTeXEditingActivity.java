package lah.texpert;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.widget.ListView;

public class LaTeXEditingActivity extends Activity {

	private ListView latex_source_listview;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_latex_editing);
		latex_source_listview = (ListView) findViewById(R.id.latex_source_listview);
		latex_source_listview.setAdapter(new ParagraphsAdapter(this, 0));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.latex_editing, menu);
		return true;
	}

}
