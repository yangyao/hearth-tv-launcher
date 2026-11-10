package com.gathin.tvhome;

/**
 * The parent gate's cooldown math: no fixed password, just arithmetic a parent does instantly and a
 * young child cannot. Wrong answers back off exponentially so guessing or hammering gets nowhere;
 * the correct answer is never revealed. KidSession persists the streak and the deadline so a reboot
 * cannot skip a cooldown.
 */
public final class ParentGatePolicy {

    static final int BASE_SECONDS = 30;
    static final int MAX_SECONDS = 600;   // ten minutes, then it stays there

    private ParentGatePolicy() {}

    /** 1st wrong answer → 30 s, each further one doubles it, capped at ten minutes. */
    public static int cooldownSeconds(int wrongStreak) {
        if (wrongStreak <= 0) return 0;
        long seconds = BASE_SECONDS;
        for (int i = 1; i < wrongStreak && seconds < MAX_SECONDS; i++) seconds <<= 1;
        return (int) Math.min(MAX_SECONDS, seconds);
    }
}
