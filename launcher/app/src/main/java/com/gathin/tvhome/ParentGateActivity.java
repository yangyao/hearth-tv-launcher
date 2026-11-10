package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;

/**
 * The in-Home shield: the same cards as KidOverlayController, rendered as a translucent activity.
 * Used two ways: the dropdown's 儿童模式 entry opens it on the parent gate (Home is in front, no
 * overlay needed), and the degraded path — no overlay permission — puts the quiz or the daily-cap
 * lock here at the next entry point (Home resume, a blocked launch). Back abandons the gate; on the
 * quiz and the lock the card swallows it, so the state only clears by answering or by a parent.
 */
public class ParentGateActivity extends Activity implements KidOverlayController.Host {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_GATE = KidOverlayController.GATE;

    private KidOverlayController.Shield shield;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = KidSession.get(this).shieldMode();
        // Same leanback fullscreen conventions as the other settings pages, so the window spans the
        // whole TV panel and the cards size against it.
        getWindow().getDecorView().setSystemUiVisibility(5894);
        KidSession.get(this).shieldActivity(true);   // the in-Home shield owns the layer while open
        shield = KidOverlayController.buildShield(this, this);
        setContentView(shield);
        shield.swap(mode);
        shield.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        KidSession.get(this).shieldActivity(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Kids mode turned off while this sat behind something: nothing to guard any more.
        if (!KidModeSettings.load(this).enabled) finish();
    }

    // KidOverlayController.Host: the card stays up until one of these runs, then this activity leaves.

    @Override public void onQuizPassed() { KidSession.get(this).onQuizPassed(); finish(); }
    @Override public void onUnlockToday() { KidSession.get(this).onUnlockToday(); finish(); }
    @Override public void onCloseMode() { KidSession.get(this).onCloseMode(); finish(); }
    @Override public void onGateAbandon() { finish(); }   // Back on the gate: no change, mode stays on
}
