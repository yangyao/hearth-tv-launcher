package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.SystemClock;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Kids mode config and its entry-point denylist. Off by default; turning it off stops every timer
 * and restores the untouched desktop without clearing anything, exactly like the NAS photo feature's
 * switch. The denylist is what shrinks Home while the mode is on: no system settings, store,
 * installers or another launcher reachable by a child.
 */
final class KidModeSettings {

    static final String PREFS = "kids_mode";

    final boolean enabled;
    final int grade, sessionMinutes, questions, dailyCapMinutes, gateDifficulty;

    private KidModeSettings(boolean enabled, int grade, int sessionMinutes, int questions, int dailyCapMinutes, int gateDifficulty) {
        this.enabled = enabled;
        this.grade = grade;
        this.sessionMinutes = sessionMinutes;
        this.questions = questions;
        this.dailyCapMinutes = dailyCapMinutes;
        this.gateDifficulty = gateDifficulty;
    }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static KidModeSettings load(Context c) {
        SharedPreferences p = prefs(c);
        return new KidModeSettings(
                p.getBoolean("enabled", false),
                clamp(p.getInt("grade", 2), 1, 6),
                clamp(p.getInt("session_minutes", 10), 5, 30),
                clamp(p.getInt("questions", 3), 1, 5),
                capMinutes(p.getInt("daily_cap_minutes", 60)),
                clamp(p.getInt("gate_difficulty", 2), 1, 3));
    }

    static void save(Context c, boolean enabled, int grade, int sessionMinutes, int questions, int dailyCapMinutes, int gateDifficulty) {
        prefs(c).edit()
                .putBoolean("enabled", enabled)
                .putInt("grade", grade)
                .putInt("session_minutes", sessionMinutes)
                .putInt("questions", questions)
                .putInt("daily_cap_minutes", dailyCapMinutes)
                .putInt("gate_difficulty", gateDifficulty)
                .commit();
        changed(c);
    }

    static void setEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("enabled", enabled).commit();
        changed(c);
    }

    /**
     * Any change: Home rebuilds its menus on resume (settings_revision), the key helper re-reads the
     * config so window events stay subscribed, and the session re-arms or stands down.
     */
    static void changed(Context c) {
        SharedPreferences p = c.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        p.edit().putLong(Config.SETTINGS_REVISION_KEY, p.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).commit();
        c.getContentResolver().notifyChange(Uri.parse("content://com.gathin.tvhome.theme/nas-settings"), null);
        dropCache();
        HomeKeyService.applyKidsMode();
        KidSession.get(c).rearm();
    }

    // ---- the entry-point shrink ----------------------------------------------------------------

    private static volatile Set<String> cache;
    private static volatile long cacheAt;

    /**
     * Packages a child must not reach from Home while the mode is on: system and vendor settings,
     * the store, installers, files, and every other home app (switching launchers would leave the
     * mode behind). Cached for a few seconds — window events and tile reloads both ask.
     */
    static Set<String> blockedTiles(Context c) {
        Set<String> hit = cache;
        if (hit != null && SystemClock.elapsedRealtime() - cacheAt < 5000) return hit;
        Set<String> blocked = new HashSet<>(Arrays.asList(
                "com.android.settings", "com.android.tv.settings",
                "com.xiaomi.mitv.settings", "com.tcl.settings", "com.amazon.tv.launcher",
                "com.android.vending", "com.google.android.feedback",
                // TV app stores (Chinese sets ship these instead of Play; verified on the user's Sony):
                "com.dangbeimarket", "com.sony.dangbeimarket", "com.huantv.appstore",
                "com.overseas.store.appstore",
                "com.android.packageinstaller", "com.google.android.packageinstaller",
                "com.android.documentsui", "com.android.systemui", "android"));
        Intent vendor = SystemSettings.vendorIntent(c);
        if (vendor != null && vendor.getComponent() != null) blocked.add(vendor.getComponent().getPackageName());
        PackageManager pm = c.getPackageManager();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : pm.queryIntentActivities(home, 0)) {
            if (ri.activityInfo != null && !c.getPackageName().equals(ri.activityInfo.packageName))
                blocked.add(ri.activityInfo.packageName);
        }
        cache = blocked;
        cacheAt = SystemClock.elapsedRealtime();
        return blocked;
    }

    static void dropCache() { cache = null; }

    /** Watching = a third-party app in front with the screen on; our own windows and system surfaces are not. */
    static boolean countsAsWatching(Context c, String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        if (pkg.equals(c.getPackageName())) return false;
        return !blockedTiles(c).contains(pkg);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static int capMinutes(int v) { return v == 0 ? 0 : clamp(v, 30, 120); }   // 0 = unlimited
}
