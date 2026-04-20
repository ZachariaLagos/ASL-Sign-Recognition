package aslframework.game.scoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerfectBonusScoringStrategyTest {

    private PerfectBonusScoringStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new PerfectBonusScoringStrategy(); // requiredSuccesses=3, bonusPerTier=10
    }

    @Test
    void isPassed_exactThreshold_returnsTrue() {
        assertTrue(strategy.isPassed(0.75));
    }

    @Test
    void isPassed_belowThreshold_returnsFalse() {
        assertFalse(strategy.isPassed(0.74));
    }

    @Test
    void calculateScore_perfectTier2_returns200() {
        assertEquals(200, strategy.calculateScore(1.0, 2));
    }

    @Test
    void calculateScore_zeroAccuracy_returnsZero() {
        assertEquals(0, strategy.calculateScore(0.0, 3));
    }

    @Test
    void calculatePerfectBonus_noFails_returnsFullBonus() {
        // max(0, 3-0) * 10 * 1 = 30
        assertEquals(30, strategy.calculatePerfectBonus(0, 1));
    }

    @Test
    void calculatePerfectBonus_oneFail_returnsReducedBonus() {
        assertEquals(20, strategy.calculatePerfectBonus(1, 1));
    }

    @Test
    void calculatePerfectBonus_failsEqualRequired_returnsZero() {
        assertEquals(0, strategy.calculatePerfectBonus(3, 1));
    }

    @Test
    void calculatePerfectBonus_failsExceedRequired_returnsZero() {
        assertEquals(0, strategy.calculatePerfectBonus(5, 1));
    }

    @Test
    void calculatePerfectBonus_higherTier_scalesBonus() {
        // max(0, 3-0) * 10 * 3 = 90
        assertEquals(90, strategy.calculatePerfectBonus(0, 3));
    }

    @Test
    void getRequiredSuccesses_defaultConfig_returns3() {
        assertEquals(3, strategy.getRequiredSuccesses());
    }

    @Test
    void customConstructor_overridesDefaults() {
        PerfectBonusScoringStrategy custom = new PerfectBonusScoringStrategy(2, 5);
        assertEquals(2, custom.getRequiredSuccesses());
        assertEquals(10, custom.calculatePerfectBonus(0, 1)); // (2-0)*5*1
    }
}
