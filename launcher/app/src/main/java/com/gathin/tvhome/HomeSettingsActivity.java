package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Lets the user change HOME through the system UI, without shell or privileged permissions. */
public class HomeSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Kids mode: changing the default home would be a way out of the shrink.
        if (KidModeSettings.load(this).enabled) {
            Toast.makeText(this, R.string.kids_mode_on_hint, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }
}
