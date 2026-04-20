package aslframework.game.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for StandardScoringStrategy.
 * Pass threshold is GameConfig.PASS_THRESHOLD = 0.75
 */
class StandardScoringStrategyTest {

    private StandardScoringStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new StandardScoringStrategy();
    }

    // ── isPassed ─────────────────────────────────────────────────────────────

    @Test
    void isPassed_exactThreshold_returnsTrue() {
        assertTrue(strategy.isPassed(0.75));
    }

    @Test
    void isPassed_aboveThreshold_returnsTrue() {
        assertTrue(strategy.isPassed(0.9));
    }

    @Test
    void isPassed_perfectScore_returnsTrue() {
        assertTrue(strategy.isPassed(1.0));
    }

    @Test
    void isPassed_justBelowThreshold_returnsFalse() {
        assertFalse(strategy.isPassed(0.74));
    }

    @Test
    void isPassed_zero_returnsFalse() {
        assertFalse(strategy.isPassed(0.0));
    }

    // ── calculateScore ────────────────────────────────────────────────────────

    @Test
    void calculateScore_perfectAccuracyTier1_returns100() {
        assertEquals(100, strategy.calculateScore(1.0, 1));
    }

    @Test
    void calculateScore_perfectAccuracyTier3_returns300() {
        assertEquals(300, strategy.calculateScore(1.0, 3));
    }

    @Test
    void calculateScore_halfAccuracyTier2_returns100() {
        assertEquals(100, strategy.calculateScore(0.5, 2));
    }

    @Test
    void calculateScore_zeroAccuracy_returnsZero() {
        assertEquals(0, strategy.calculateScore(0.0, 5));
    }

    @Test
    void calculateScore_truncatesDecimal() {
        // (int)(0.999 * 100) = 99, not 100
        assertEquals(99, strategy.calculateScore(0.999, 1));
    }

    @Test
    void calculateScore_tier0_returnsZero() {
        assertEquals(0, strategy.calculateScore(1.0, 0));
    }
}
