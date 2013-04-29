package lah.texpert;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

/**
 * Activity to customize TeXpert
 * 
 * @author L.A.H.
 * 
 */
public class SettingsActivity extends PreferenceActivity {

	public static final String PREF_FONT_SIZE = "font_size", PREF_FONT_FAMILY = "font_family";

	private static Preference.OnPreferenceChangeListener update_pref_listener = new Preference.OnPreferenceChangeListener() {

		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			preference.setSummary(value.toString());
			return true;
		}
	};

	private static void bindPreferenceSummaryToValue(Preference preference) {
		// Set the listener to watch for value changes.
		preference.setOnPreferenceChangeListener(update_pref_listener);
		update_pref_listener.onPreferenceChange(
				preference,
				PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(),
						""));
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.pref);
		bindPreferenceSummaryToValue(findPreference(PREF_FONT_SIZE));
		bindPreferenceSummaryToValue(findPreference(PREF_FONT_FAMILY));
	}

}
