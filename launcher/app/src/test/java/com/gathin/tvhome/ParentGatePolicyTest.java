package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ParentGatePolicyTest {

    @Test public void noWrongAnswersNoCooldown() {
        assertEquals(0, ParentGatePolicy.cooldownSeconds(0));
        assertEquals(0, ParentGatePolicy.cooldownSeconds(-3));
    }

    @Test public void doublesEachTimeThenCaps() {
        assertEquals(30, ParentGatePolicy.cooldownSeconds(1));
        assertEquals(60, ParentGatePolicy.cooldownSeconds(2));
        assertEquals(120, ParentGatePolicy.cooldownSeconds(3));
        assertEquals(240, ParentGatePolicy.cooldownSeconds(4));
        assertEquals(480, ParentGatePolicy.cooldownSeconds(5));
        assertEquals(600, ParentGatePolicy.cooldownSeconds(6));
        assertEquals(600, ParentGatePolicy.cooldownSeconds(50));   // capped, never longer
    }

    @Test public void cooldownAlwaysAboveOneMinuteAfterTheFirst() {
        for (int streak = 1; streak <= 30; streak++) {
            int seconds = ParentGatePolicy.cooldownSeconds(streak);
            assertTrue("streak " + streak, seconds >= 30 && seconds <= 600);
        }
    }
}
