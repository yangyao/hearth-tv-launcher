package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;

/**
 * Offline quiz generation for kids mode: grade-appropriate arithmetic for the watch-time checkpoints
 * (the child answers these), and harder mixed-operation gates for the parent. Always offline, no
 * network, no fixed answer to memorise. Constraints everywhere: integer answers, no division, no
 * negative results — the remote keypad never needs an input method. The Random is a parameter so
 * tests are deterministic.
 */
public final class KidQuiz {

    /** One question: display text without the answer ("7×8−15") and its non-negative integer answer. */
    public static class Question {
        public final String text;
        public final int answer;

        Question(String text, int answer) { this.text = text; this.answer = answer; }

        @Override public String toString() { return text + "=" + answer; }
    }

    private KidQuiz() {}

    /** A group of distinct questions for a grade (1–6 clamped), at most 5. */
    public static List<Question> generate(Random random, int grade, int count) {
        int g = Math.max(1, Math.min(6, grade));
        count = Math.max(1, Math.min(5, count));
        Set<String> seen = new HashSet<>();
        List<Question> out = new ArrayList<>();
        int guard = 0;
        while (out.size() < count && guard++ < 200) {
            Question q = byGrade(random, g);
            if (seen.add(q.text)) out.add(q);
        }
        while (out.size() < count) out.add(byGrade(random, g));   // spaces are large; duplicates all but impossible
        return out;
    }

    /**
     * The parent gate: grade-1/2 math on purpose — a speed bump for a child, not an exam for the
     * parent. The difficulty levels only add steps inside small numbers: 1 is one step within 20,
     * 2 is the multiplication table plus one step within 100, 3 is three small steps within 100.
     */
    public static Question gate(Random random, int difficulty) {
        int d = Math.max(1, Math.min(3, difficulty));
        for (int attempt = 0; attempt < 40; attempt++) {
            Question q = gateOnce(random, d);
            if (q != null) return q;
        }
        return new Question("7×8+15", 71);   // unreachable in practice
    }

    /** Upper bound on every answer a grade can produce (unit tests). */
    public static int maxAnswer(int grade) {
        switch (Math.max(1, Math.min(6, grade))) {
            case 1: return 20;
            case 2: return 100;
            case 3: return 999;
            case 4: return 2000;
            case 5: return 6000;
            default: return 10000;
        }
    }

    // ---- grade templates: null = constraints unmet, retry --------------------------------------

    private static Question byGrade(Random r, int grade) {
        for (int attempt = 0; attempt < 20; attempt++) {
            Question q;
            switch (grade) {
                case 1: q = grade1(r); break;
                case 2: q = grade2(r); break;
                case 3: q = grade3(r); break;
                case 4: q = grade4(r); break;
                case 5: q = grade5(r); break;
                default: q = grade6(r); break;
            }
            if (q != null) return q;
        }
        return new Question("2+3", 5);
    }

    /** Grade 1: addition and subtraction within 20. */
    private static Question grade1(Random r) {
        if (r.nextBoolean()) {
            int a = ri(r, 1, 19), b = ri(r, 1, 20 - a);
            return new Question(a + "+" + b, a + b);
        }
        int a = ri(r, 2, 20), b = ri(r, 1, a - 1);
        return new Question(a + "−" + b, a - b);
    }

    /** Grade 2: the multiplication table, plus one step of ± within 100. */
    private static Question grade2(Random r) {
        int a = ri(r, 2, 9), b = ri(r, 2, 9), p = a * b, c = ri(r, 1, 10);
        switch (r.nextInt(3)) {
            case 0: return new Question(a + "×" + b, p);
            case 1: return p + c <= 100 ? new Question(a + "×" + b + "+" + c, p + c) : null;
            default: return p - c >= 0 ? new Question(a + "×" + b + "−" + c, p - c) : null;
        }
    }

    /** Grade 3: two-digit times one digit, one more step; or a bracketed sum times one digit. */
    private static Question grade3(Random r) {
        int a = ri(r, 10, 19), b = ri(r, 2, 9), p = a * b, c = ri(r, 10, 99);
        if (r.nextBoolean()) {
            if (p + c <= 999) return new Question(a + "×" + b + "+" + c, p + c);
        } else {
            if (p - c >= 0) return new Question(a + "×" + b + "−" + c, p - c);
        }
        int x = ri(r, 2, 12), y = ri(r, 2, 12), z = ri(r, 2, 9), v = (x + y) * z;
        return v <= 999 ? new Question("(" + x + "+" + y + ")×" + z, v) : null;
    }

    /** Grade 4: two products one step apart, or a product of a bracketed sum minus one term. */
    private static Question grade4(Random r) {
        int a = ri(r, 2, 9), b = ri(r, 2, 9), c = ri(r, 2, 9), d = ri(r, 2, 9);
        if (r.nextBoolean()) {
            int v = a * b + c * d;
            return v <= 2000 ? new Question(a + "×" + b + "+" + c + "×" + d, v) : null;
        }
        int e = ri(r, 1, 50), v = a * (b + c) - e;
        return v >= 0 && v <= 2000 ? new Question(a + "×(" + b + "+" + c + ")−" + e, v) : null;
    }

    /** Grade 5: a three-step mix with brackets, intermediates held under 1000. */
    private static Question grade5(Random r) {
        int a = ri(r, 3, 12), b = ri(r, 3, 12), c = ri(r, 5, 50), d = ri(r, 2, 6), e = ri(r, 10, 99);
        int inner = a * b + c;
        if (inner > 1000) return null;
        int v = inner * d - e;
        return v >= 0 && v <= 6000 ? new Question("(" + a + "×" + b + "+" + c + ")×" + d + "−" + e, v) : null;
    }

    /** Grade 6: four steps with brackets, or two products one step apart. */
    private static Question grade6(Random r) {
        if (r.nextBoolean()) {
            int a = ri(r, 6, 25), b = ri(r, 6, 25), c = ri(r, 3, 15), d = ri(r, 3, 15), e = ri(r, 10, 150);
            int v = (a + b) * (c + d) - e;
            return v >= 0 && v <= 10000 ? new Question("(" + a + "+" + b + ")×(" + c + "+" + d + ")−" + e, v) : null;
        }
        int a = ri(r, 12, 30), b = ri(r, 5, 15), c = ri(r, 5, 15), d = ri(r, 5, 15), e = ri(r, 10, 99);
        int v = a * b - c * d + e;
        return v >= 0 && v <= 10000 ? new Question(a + "×" + b + "−" + c + "×" + d + "+" + e, v) : null;
    }

    private static Question gateOnce(Random r, int d) {
        if (d == 1) return grade1(r);   // one step, within 20
        if (d == 2) return grade2(r);   // the multiplication table ± one step, within 100
        // 3: three small steps, still within 100
        int a = ri(r, 2, 9), b = ri(r, 2, 9), c = ri(r, 10, 60), e = ri(r, 1, 40);
        int v = a * b + c - e;
        return v >= 10 && v <= 99 ? new Question(a + "×" + b + "+" + c + "−" + e, v) : null;
    }

    private static int ri(Random r, int lo, int hi) { return hi <= lo ? lo : lo + r.nextInt(hi - lo + 1); }
}
