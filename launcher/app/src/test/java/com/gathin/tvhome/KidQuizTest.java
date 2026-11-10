package com.gathin.tvhome;

import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class KidQuizTest {

    @Test public void everyGradeProducesTheRightGroup() {
        for (int grade = 1; grade <= 6; grade++) {
            List<KidQuiz.Question> group = KidQuiz.generate(new Random(7), grade, 3);
            assertEquals(3, group.size());
            for (KidQuiz.Question q : group) {
                assertTrue(q.answer >= 0);
                assertTrue("grade " + grade + " answer " + q.answer + " too big",
                        q.answer <= KidQuiz.maxAnswer(grade));
                assertTrue(q.text.length() > 0);
            }
            assertNotEquals(group.get(0).text, group.get(1).text);
            assertNotEquals(group.get(1).text, group.get(2).text);
        }
    }

    @Test public void gradesStayWithinTheirCurriculum() {
        // A grade-2 group never needs anything past the multiplication table and ±100.
        List<KidQuiz.Question> g2 = KidQuiz.generate(new Random(11), 2, 5);
        for (KidQuiz.Question q : g2) assertTrue(q.answer <= 100);
        // Grade 1 stays within 20.
        List<KidQuiz.Question> g1 = KidQuiz.generate(new Random(11), 1, 5);
        for (KidQuiz.Question q : g1) assertTrue(q.answer <= 20);
    }

    @Test public void sameSeedSameGroup() {
        assertEquals(KidQuiz.generate(new Random(42), 3, 3).toString(),
                KidQuiz.generate(new Random(42), 3, 3).toString());
    }

    @Test public void gateStaysAtGradeOneOrTwoLevel() {
        for (int i = 0; i < 50; i++) {
            assertTrue(KidQuiz.gate(new Random(1000 + i), 1).answer <= 20);   // easy: grade 1, within 20
            assertTrue(KidQuiz.gate(new Random(2000 + i), 2).answer <= 100);  // default: grade 2, within 100
            assertTrue(KidQuiz.gate(new Random(3000 + i), 3).answer <= 99);   // hard: still two-digit
        }
    }

    @Test public void gateDifficultyClamps() {
        KidQuiz.Question low = KidQuiz.gate(new Random(5), 0);
        KidQuiz.Question same = KidQuiz.gate(new Random(5), 1);
        assertEquals(low.text, same.text);   // both clamped to difficulty 1
        KidQuiz.Question high = KidQuiz.gate(new Random(5), 9);
        KidQuiz.Question hard = KidQuiz.gate(new Random(5), 3);
        assertEquals(hard.text, high.text);  // both clamped to difficulty 3
    }

    @Test public void clampedGradeAndCount() {
        assertEquals(5, KidQuiz.generate(new Random(3), 0, 9).size());
        assertEquals(1, KidQuiz.generate(new Random(3), 9, 1).size());
    }
}
