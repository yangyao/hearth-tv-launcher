package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KidSessionPolicyTest {

    @Test public void onlyWatchingAccumulates() {
        KidSessionPolicy p = new KidSessionPolicy(600, 3600);
        p.tick(600);
        assertEquals(0, p.watchedSeconds());
        p.setWatching(true);
        p.tick(120);
        assertEquals(120, p.watchedSeconds());
        p.setWatching(false);          // back on Home: paused
        p.tick(600);
        assertEquals(120, p.watchedSeconds());
    }

    @Test public void quizDueExactlyAtTheBoundary() {
        KidSessionPolicy p = new KidSessionPolicy(600, 3600);
        p.setWatching(true);
        p.tick(599);
        assertFalse(p.pendingQuiz());
        assertEquals(KidSessionPolicy.Phase.WATCHING, p.phase());
        p.tick(1);
        assertTrue(p.pendingQuiz());
        assertEquals(KidSessionPolicy.Phase.QUIZ_DUE, p.phase());
    }

    @Test public void quizPausesAndPassingResumes() {
        KidSessionPolicy p = new KidSessionPolicy(600, 0);
        p.setWatching(true);
        p.tick(600);
        p.setWatching(true);           // a front-window event while the layer is up: ignored
        p.tick(60);
        assertEquals(600, p.watchedSeconds());   // frozen while the quiz is pending
        p.onQuizPassed();
        assertFalse(p.pendingQuiz());
        p.setWatching(true);
        p.tick(599);
        assertFalse(p.pendingQuiz());  // next boundary at 1200
        p.tick(1);
        assertTrue(p.pendingQuiz());
    }

    @Test public void bigSettleJumpsStraightToTheCurrentLevel() {
        KidSessionPolicy p = new KidSessionPolicy(600, 0);
        p.setWatching(true);
        p.tick(3600);                  // e.g. the fallback path settling a long session at once
        p.onQuizPassed();
        assertEquals(6, p.quizLevel());
        assertFalse(p.pendingQuiz());
        p.setWatching(true);
        p.tick(600);
        p.onQuizPassed();
        assertEquals(7, p.quizLevel());
    }

    @Test public void dailyCapLocksAndSurvivesEverything() {
        KidSessionPolicy p = new KidSessionPolicy(600, 3600);
        p.setWatching(true);
        // Five full sessions, a quiz answered at each boundary...
        for (int i = 0; i < 5; i++) {
            p.tick(600);
            p.onQuizPassed();
            p.setWatching(true);
        }
        assertFalse(p.lockedToday());
        p.tick(600);                   // ...the sixth hour is the cap: locked before its quiz matters
        assertTrue(p.lockedToday());
        assertEquals(KidSessionPolicy.Phase.LOCKED, p.phase());
        assertFalse(p.pendingQuiz());
        p.tick(600);
        assertEquals(3600, p.watchedSeconds());  // locked: nothing accrues
        p.onQuizPassed();                        // even a stale quiz pass does not unlock
        assertTrue(p.lockedToday());
    }

    @Test public void capZeroMeansUnlimited() {
        KidSessionPolicy p = new KidSessionPolicy(600, 0);
        p.setWatching(true);
        p.tick(100000);
        assertFalse(p.lockedToday());
    }

    @Test public void rollDayResetsEverything() {
        KidSessionPolicy p = new KidSessionPolicy(600, 1800);
        p.setWatching(true);
        p.tick(1800);
        assertTrue(p.lockedToday());
        p.rollDay();
        assertEquals(KidSessionPolicy.Phase.IDLE, p.phase());
        assertEquals(0, p.watchedSeconds());
        assertEquals(0, p.quizLevel());
    }

    @Test public void secondsLeftTodayCountsDownAndUnlimitedIsMinusOne() {
        KidSessionPolicy unlimited = new KidSessionPolicy(600, 0);
        assertEquals(-1, unlimited.secondsLeftToday());   // 0 = no cap, nothing to count down to
        KidSessionPolicy p = new KidSessionPolicy(600, 1200);
        assertEquals(1200, p.secondsLeftToday());
        p.setWatching(true);
        p.tick(500);
        assertEquals(700, p.secondsLeftToday());
        p.tick(700);                               // past the cap: locked, nothing left
        assertEquals(0, p.secondsLeftToday());
        assertEquals(KidSessionPolicy.Phase.LOCKED, p.phase());
    }

    @Test public void persistedStateRestores() {
        KidSessionPolicy p = new KidSessionPolicy(600, 3600);
        p.restore(700, 1, false, false);    // reboot mid-way through the second session
        p.setWatching(true);
        p.tick(499);
        assertFalse(p.pendingQuiz());
        p.tick(1);                                 // 1200 total: the second boundary
        assertTrue(p.pendingQuiz());
    }

    @Test public void restoredLockStaysLocked() {
        KidSessionPolicy p = new KidSessionPolicy(600, 3600);
        p.restore(3600, 6, false, true);
        assertEquals(KidSessionPolicy.Phase.LOCKED, p.phase());
        p.setWatching(true);
        assertFalse(p.isWatching());
    }

    @Test public void configChangesApplyLive() {
        KidSessionPolicy p = new KidSessionPolicy(600, 0);
        p.setWatching(true);
        p.tick(599);
        p.config(300, 0);              // the parent shortened the session: 599 s already passes a boundary
        assertTrue(p.pendingQuiz());   // so the quiz comes at once
        p.onQuizPassed();
        assertEquals(1, p.quizLevel());  // the level catches up (599 / 300)
        p.setWatching(true);
        p.tick(1);                     // 600: the next 300 s boundary
        assertTrue(p.pendingQuiz());
    }
}
