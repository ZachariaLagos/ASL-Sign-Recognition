package aslframework.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the ScoringEngine class.
 * Verifies pass/fail thresholds and score calculation logic.
 */
public class ScoringEngineTest {

  private ScoringEngine scoringEngine;

  @BeforeEach
  void setUp() {
    scoringEngine = new ScoringEngine();
  }

  // Testing isPassed

  @Test
  void testIsPassed_exactThreshold_returnsTrue() {
    assertTrue(scoringEngine.isPassed(0.8));
  }

  @Test
  void testIsPassed_aboveThreshold_returnsTrue() {
    assertTrue(scoringEngine.isPassed(1.0));
  }

  @Test
  void testIsPassed_belowThreshold_returnsFalse() {
    assertFalse(scoringEngine.isPassed(0.79));
  }

  @Test
  void testIsPassed_zeroAccuracy_returnsFalse() {
    assertFalse(scoringEngine.isPassed(0.0));
  }

  // Testing calculateScore

  @Test
  void testCalculateScore_perfectAccuracyDifficulty1_returns100() {
    assertEquals(100, scoringEngine.calculateScore(1.0, 1));
  }

  @Test
  void testCalculateScore_halfAccuracyDifficulty2_returns100() {
    assertEquals(100, scoringEngine.calculateScore(0.5, 2));
  }

  @Test
  void testCalculateScore_zeroAccuracy_returnsZero() {
    assertEquals(0, scoringEngine.calculateScore(0.0, 5));
  }

  @Test
  void testCalculateScore_higherDifficultyGivesHigherScore() {
    int lowDifficulty  = scoringEngine.calculateScore(0.9, 1);
    int highDifficulty = scoringEngine.calculateScore(0.9, 3);
    assertTrue(highDifficulty > lowDifficulty);
  }
}