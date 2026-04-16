package aslframework.game.scoring;

import aslframework.game.GameConfig;

/**
 * Scoring strategy that awards a perfect-run bonus on top of the base score.
 *
 * <p>Used by {@link aslframework.game.session.PracticeSession}. The base score
 * per attempt is identical to {@link StandardScoringStrategy}. The bonus is
 * computed separately by the session when a letter is fully cleared:
 *
 * <pre>
 *   perfect bonus = max(0, requiredSuccesses - failCount) × bonusPerTier × tier
 * </pre>
 *
 * <p>This class exposes {@link #calculatePerfectBonus} so the session can call
 * it at the moment of letter-clearance, when it knows the final fail count.
 */
public class PerfectBonusScoringStrategy implements ScoringStrategy {

  private final int requiredSuccesses;
  private final int bonusPerTier;

  /**
   * Constructs a {@code PerfectBonusScoringStrategy}.
   *
   * @param requiredSuccesses consecutive successes needed to clear a letter
   * @param bonusPerTier      bonus points per saved fail-slot per difficulty tier
   */
  public PerfectBonusScoringStrategy(int requiredSuccesses, int bonusPerTier) {
    this.requiredSuccesses = requiredSuccesses;
    this.bonusPerTier      = bonusPerTier;
  }

  /** Convenience constructor using {@link GameConfig} defaults. */
  public PerfectBonusScoringStrategy() {
    this(GameConfig.PRACTICE_REQUIRED_SUCCESSES,
         GameConfig.PRACTICE_PERFECT_BONUS_PER_TIER);
  }

  @Override
  public boolean isPassed(double accuracy) {
    return accuracy >= GameConfig.PASS_THRESHOLD;
  }

  @Override
  public int calculateScore(double accuracy, int tier) {
    return (int) (accuracy * 100) * tier;
  }

  /**
   * Calculates the perfect-run bonus earned when a letter is cleared.
   *
   * @param failCount number of failed attempts on this letter before clearing
   * @param tier      difficulty tier of the cleared letter (1–5)
   * @return bonus points; 0 if {@code failCount >= requiredSuccesses}
   */
  public int calculatePerfectBonus(int failCount, int tier) {
    return Math.max(0, requiredSuccesses - failCount) * bonusPerTier * tier;
  }

  /** Returns the number of consecutive successes required to clear a letter. */
  public int getRequiredSuccesses() {
    return requiredSuccesses;
  }
}
