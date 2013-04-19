package lah.texpert;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Fragment containing useful formula-related shortcuts
 * 
 * @author L.A.H.
 * 
 */
public class FormulaEditFragment extends Fragment {

	public static FormulaEditFragment newInstance() {
		FormulaEditFragment fragment = new FormulaEditFragment();
		return fragment;
	}

	public FormulaEditFragment() {
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_formula_edit, container, false);
	}

}
