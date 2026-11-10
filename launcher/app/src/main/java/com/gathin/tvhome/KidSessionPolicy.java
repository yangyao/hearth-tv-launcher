package com.gathin.tvhome;

/**
 * The kids-mode rule engine, free of Android so it unit-tests directly. Daily cumulative model:
 * watched seconds accumulate only while a third-party app is in front and the screen is on; each
 * full session triggers one quiz checkpoint; the daily cap locks the TV for the rest of the day.
 * All state survives the process via KidSession's persistence — nothing here is remembered that
 * KidSession does not save.
 */
public final class KidSessionPolicy {

    public enum Phase { IDLE, WATCHING, QUIZ_DUE, LOCKED }

    private int sessionSeconds, capSeconds;
    private int watchedSeconds, quizLevel;
    private boolean watching, pendingQuiz, lockedToday;

    public KidSessionPolicy(int sessionSeconds, int capSeconds) {
        this.sessionSeconds = clamp(sessionSeconds, 300, 1800);
        this.capSeconds = capSeconds < 0 ? 0 : capSeconds;   // 0 = unlimited
    }

    /** The parent changed the session length or the cap; takes effect immediately. */
    public void config(int sessionSeconds, int capSeconds) {
        this.sessionSeconds = clamp(sessionSeconds, 300, 1800);
        this.capSeconds = capSeconds < 0 ? 0 : capSeconds;
        recheck();
    }

    /** A third-party app is (no longer) the front window with the screen on. */
    public void setWatching(boolean watching) {
        if (lockedToday || pendingQuiz) { this.watching = false; return; }   // the layer is up: paused
        this.watching = watching;
        recheck();
    }

    /** Called every 60 s while a host lives; only WATCHING accumulates. */
    public void tick(int deltaSeconds) {
        if (deltaSeconds <= 0) return;
        if (lockedToday || pendingQuiz) { watching = false; return; }
        if (!watching) return;
        watchedSeconds += deltaSeconds;
        recheck();
    }

    /** The child answered the whole group: the level catches up to the boundary just crossed. */
    public void onQuizPassed() {
        if (sessionSeconds > 0) quizLevel = Math.max(quizLevel, watchedSeconds / sessionSeconds);
        pendingQuiz = false;
        recheck();
    }

    /** The local date changed: a fresh day. */
    public void rollDay() {
        watchedSeconds = 0;
        quizLevel = 0;
        pendingQuiz = false;
        lockedToday = false;
    }

    /** Restores persisted state (after reboot or process death). */
    public void restore(int watchedSeconds, int quizLevel, boolean pendingQuiz, boolean lockedToday) {
        this.watchedSeconds = Math.max(0, watchedSeconds);
        this.quizLevel = Math.max(0, quizLevel);
        this.pendingQuiz = pendingQuiz;
        this.lockedToday = lockedToday;
        recheck();
    }

    public Phase phase() {
        if (lockedToday) return Phase.LOCKED;
        if (pendingQuiz) return Phase.QUIZ_DUE;
        return watching ? Phase.WATCHING : Phase.IDLE;
    }

    public boolean isWatching() { return watching; }
    public int watchedSeconds() { return watchedSeconds; }
    public int quizLevel() { return quizLevel; }
    public boolean pendingQuiz() { return pendingQuiz; }
    public boolean lockedToday() { return lockedToday; }

    /** Seconds until the daily cap, -1 when the cap is unlimited; frozen while a layer is up (not watching). */
    public int secondsLeftToday() {
        return capSeconds > 0 ? Math.max(0, capSeconds - watchedSeconds) : -1;
    }

    private void recheck() {
        if (capSeconds > 0 && watchedSeconds >= capSeconds) {
            lockedToday = true;
            pendingQuiz = false;
            watching = false;
            return;
        }
        if (sessionSeconds > 0 && watchedSeconds >= (quizLevel + 1) * sessionSeconds) pendingQuiz = true;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
