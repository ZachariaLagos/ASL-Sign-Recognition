package aslframework.game;

/**
 * Central configuration for game settings and constants.
 *
 * <p>Defines pass thresholds, required successes, and scoring bonuses used
 * throughout the game system.
 */
public final class GameConfig {

  private GameConfig() {}

  /** Accuracy threshold (0–1) required to pass an attempt. */
  public static final double PASS_THRESHOLD = 0.75;

  /** Consecutive successes needed to clear a letter in practice mode. */
  public static final int PRACTICE_REQUIRED_SUCCESSES = 3;

  /** Bonus points per saved fail-slot per difficulty tier in practice mode. */
  public static final int PRACTICE_PERFECT_BONUS_PER_TIER = 10;
}
