package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * The kids-mode session: owns the {@link KidSessionPolicy}, persists it (kids_mode prefs, 60 s
 * cadence + on every state change, so a reboot or a process death loses at most one tick), and shows
 * the quiz/lock layer — as a system overlay when the permission is granted, else as the in-Home
 * {@link ParentGateActivity} at the next entry point. Timers come from two hosts, whichever is alive:
 * HomeKeyService (the accessibility helper, always bound) ticks every 60 s; MainActivity settles the
 * remaining tail when Home resumes. Both write the same lastUptime, so nothing double-counts.
 */
public final class KidSession {

    private static final long TICK_MS = 60_000;

    private static KidSession instance;

    private final Context app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private KidSessionPolicy policy;
    private long lastUptime;
    private boolean tickArmed;
    private boolean shieldUp;
    private long lastToastAt;
    private final Runnable ticker = this::tick;

    static synchronized KidSession get(Context c) {
        if (instance == null) instance = new KidSession(c.getApplicationContext());
        return instance;
    }

    private KidSession(Context app) {
        this.app = app;
        restore();
    }

    // ---- lifecycle ---------------------------------------------------------------------------------

    /** HomeKeyService connected (after the user unlock): the full-mode host takes over. */
    public void serviceUp() {
        applyConfig();
        rollDayIfNeeded();
        persist();
        startTick();
        react();
    }

    /** HomeKeyService gone; the activity's event-driven path takes over until it is back. */
    public static void serviceDown() {
        KidSession s = instance;
        if (s != null) s.stopTick();
    }

    /** After an enable toggle or any config change (KidModeSettings.changed). */
    public void rearm() {
        if (KidModeSettings.load(app).enabled) {
            applyConfig();
            rollDayIfNeeded();
            startTick();
        } else {
            stopTick();
            policy.setWatching(false);
            KidOverlayController.dismiss();
        }
        persist();
        react();
    }

    private synchronized void startTick() {
        if (tickArmed || !KidModeSettings.load(app).enabled) return;
        tickArmed = true;
        lastUptime = SystemClock.uptimeMillis();
        handler.postDelayed(ticker, TICK_MS);
    }

    private synchronized void stopTick() {
        tickArmed = false;
        handler.removeCallbacks(ticker);
    }

    // ---- events -------------------------------------------------------------------------------------

    private void tick() {
        if (!KidModeSettings.load(app).enabled) {
            tickArmed = false;
            return;
        }
        applyConfig();
        rollDayIfNeeded();
        long now = SystemClock.uptimeMillis();
        int delta = (int) Math.min(600, (now - lastUptime) / 1000);
        lastUptime = now;
        policy.setWatching(policy.isWatching() && interactive());
        policy.tick(delta);
        persist();
        react();
        handler.postDelayed(ticker, TICK_MS);
    }

    /** Front-window tracking from HomeKeyService (activity windows only, as with app_keys "front:"). */
    public void onForegroundPackage(String pkg) {
        if (!KidModeSettings.load(app).enabled) return;
        applyConfig();
        rollDayIfNeeded();
        boolean watch = KidModeSettings.countsAsWatching(app, pkg) && interactive();
        if (!watch) lastUptime = SystemClock.uptimeMillis();
        policy.setWatching(watch);
        persist();
        react();
    }

    /** A tile, top-bar app, library entry or open-on-start pick started an app. Fallback session start. */
    public boolean onAppLaunched(String pkg) {
        if (!KidModeSettings.load(app).enabled) return false;
        applyConfig();
        rollDayIfNeeded();
        if (!KidModeSettings.countsAsWatching(app, pkg)) return false;
        lastUptime = SystemClock.uptimeMillis();
        policy.setWatching(true);
        persist();
        react();
        return true;
    }

    /** Home is back in front: pause, settle the tail, and in degraded mode put the layer back up. */
    public void onHomeResumed() {
        if (!KidModeSettings.load(app).enabled) return;
        applyConfig();
        rollDayIfNeeded();
        if (policy.isWatching()) {
            int delta = (int) Math.min(3600, (SystemClock.uptimeMillis() - lastUptime) / 1000);
            policy.tick(delta);
        }
        lastUptime = SystemClock.uptimeMillis();
        policy.setWatching(false);
        persist();
        if (!react() && (policy.lockedToday() || policy.pendingQuiz())) showShieldActivity();
    }

    /** True when the launch must not happen (locked or quiz pending); shows the layer instead. */
    public boolean interceptLaunch() {
        if (!KidModeSettings.load(app).enabled) return false;
        applyConfig();
        rollDayIfNeeded();
        if (!policy.lockedToday() && !policy.pendingQuiz()) return false;
        if (!react()) showShieldActivity();
        return true;
    }

    // ---- what the cards call back -------------------------------------------------------------------

    public void onQuizPassed() {
        policy.onQuizPassed();
        persist();
        KidOverlayController.dismiss();
        react();
    }

    public void onUnlockToday() {
        policy.onUnlockedToday();
        persist();
        KidOverlayController.dismiss();
        react();
    }

    public void onCloseMode() {
        KidModeSettings.setEnabled(app, false);
        KidOverlayController.dismiss();
    }

    public void clearGate() {
        KidModeSettings.prefs(app).edit()
                .putInt("gate_streak", 0).putInt("gate_cooldown_sec", 0).putLong("gate_cooldown_until", 0).commit();
    }

    /** What the in-Home shield should show when no explicit mode was asked for. */
    public String shieldMode() {
        if (policy.lockedToday()) return KidOverlayController.LOCK;
        if (policy.pendingQuiz()) return KidOverlayController.QUIZ;
        return KidOverlayController.GATE;
    }

    /**
     * ParentGateActivity lives and dies by this: while it shows the cards in-Home, the overlay stands
     * down (the two would otherwise stack, the overlay covering the very gate the parent is on). When
     * it leaves, the state is re-evaluated, so a pending quiz or lock comes back on its own.
     */
    public void shieldActivity(boolean up) {
        shieldUp = up;
        if (up) KidOverlayController.dismiss();
        else react();
    }

    // ---- internals -----------------------------------------------------------------------------------

    private void applyConfig() {
        KidModeSettings cfg = KidModeSettings.load(app);
        policy.config(cfg.sessionMinutes * 60, cfg.dailyCapMinutes * 60);
    }

    private void rollDayIfNeeded() {
        String today = today();
        if (today.equals(KidModeSettings.prefs(app).getString("watch_date", ""))) return;
        policy.rollDay();
        KidModeSettings.prefs(app).edit().putString("watch_date", today).commit();
    }

    private void restore() {
        KidModeSettings cfg = KidModeSettings.load(app);
        policy = new KidSessionPolicy(cfg.sessionMinutes * 60, cfg.dailyCapMinutes * 60);
        String today = today();
        if (!today.equals(KidModeSettings.prefs(app).getString("watch_date", ""))) {
            KidModeSettings.prefs(app).edit().putString("watch_date", today).commit();
            return;   // a fresh day: the zeroed policy is already the rolled-over state
        }
        policy.restore(
                KidModeSettings.prefs(app).getInt("watched_seconds", 0),
                KidModeSettings.prefs(app).getInt("quiz_level", 0),
                KidModeSettings.prefs(app).getBoolean("pending_quiz", false),
                KidModeSettings.prefs(app).getBoolean("locked_today", false),
                KidModeSettings.prefs(app).getBoolean("unlocked_today", false));
    }

    private void persist() {
        KidModeSettings.prefs(app).edit()
                .putString("watch_date", today())
                .putInt("watched_seconds", policy.watchedSeconds())
                .putInt("quiz_level", policy.quizLevel())
                .putBoolean("pending_quiz", policy.pendingQuiz())
                .putBoolean("locked_today", policy.lockedToday())
                .putBoolean("unlocked_today", policy.unlockedToday())
                .commit();
    }

    /** Shows the layer for the current state, without rebuilding one that is already up. */
    private boolean react() {
        if (!KidModeSettings.load(app).enabled) {
            KidOverlayController.dismiss();
            return false;
        }
        if (shieldUp) { KidOverlayController.dismiss(); return true; }   // the in-Home shield is the active surface
        if (!interactive()) return KidOverlayController.showing();   // screen off: the layer keeps waiting
        String up = KidOverlayController.currentMode();
        if (policy.lockedToday()) {
            if (KidOverlayController.LOCK.equals(up) || KidOverlayController.GATE.equals(up)
                    || KidOverlayController.OPTIONS.equals(up)) return true;
            return showLayer(KidOverlayController.LOCK);
        }
        if (policy.pendingQuiz()) {
            if (KidOverlayController.QUIZ.equals(up) || KidOverlayController.GATE.equals(up)
                    || KidOverlayController.OPTIONS.equals(up)) return true;
            return showLayer(KidOverlayController.QUIZ);
        }
        KidOverlayController.dismiss();
        return false;
    }

    private boolean showLayer(String mode) {
        if (KidOverlayController.show(app, mode, overlayHost())) return true;
        // Degraded: nudge once in a while; the in-Home shield appears at the next entry point.
        if (SystemClock.elapsedRealtime() - lastToastAt > 30_000) {
            lastToastAt = SystemClock.elapsedRealtime();
            Toast.makeText(app, mode.equals(KidOverlayController.LOCK)
                    ? R.string.kids_mode_locked_toast : R.string.kids_mode_quiz_toast, Toast.LENGTH_LONG).show();
        }
        return false;
    }

    private KidOverlayController.Host overlayHost() {
        return new KidOverlayController.Host() {
            @Override public void onQuizPassed() { KidSession.this.onQuizPassed(); }
            @Override public void onUnlockToday() { KidSession.this.onUnlockToday(); }
            @Override public void onCloseMode() { KidSession.this.onCloseMode(); }
            @Override public void onGateAbandon() { /* nothing gets past the overlay */ }
        };
    }

    private void showShieldActivity() {
        Intent i = new Intent(app, ParentGateActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(ParentGateActivity.EXTRA_MODE, shieldMode());
        try {
            app.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private boolean interactive() {
        PowerManager power = app.getSystemService(PowerManager.class);
        return power != null && power.isInteractive();
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime());
    }
}
