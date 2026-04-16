package aslframework.game.scoring;

/**
 * Strategy interface for evaluating recognition accuracy and computing scores.
 *
 * <p>Implementations define whether an accuracy threshold is met and how points
 * are calculated from recognition results.
 */
public interface ScoringStrategy {

  /**
   * Determines if the given accuracy meets the pass threshold.
   *
   * @param accuracy a normalized confidence score (typically 0–1)
   * @return {@code true} if the accuracy is sufficient to pass
   */
  boolean isPassed(double accuracy);

  /**
   * Calculates the base score for a single attempt.
   *
   * <p>Formula: {@code floor(accuracy × 100) × tier}
   *
   * @param accuracy normalized confidence score
   * @param tier     difficulty tier (typically 1–5)
   * @return points earned for this attempt
   */
  int calculateScore(double accuracy, int tier);
}
