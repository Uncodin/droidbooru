package in.uncod.android.droidbooru;

import android.os.Bundle;

import com.actionbarsherlock.app.SherlockPreferenceActivity;

/**
 * This activity is for changing settings required prior to login, such as server selection
 */
public class LoginSettingsActivity extends SherlockPreferenceActivity {
    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.prefs_login_settings); // Using deprecated method because currently there is no compat implementation of PreferenceFragment
    }
}
