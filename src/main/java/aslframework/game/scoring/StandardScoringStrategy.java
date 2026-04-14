package aslframework.game.scoring;

import aslframework.game.GameConfig;

/**
 * Flat scoring strategy: score = floor(accuracy × 100) × tier.
 *
 * <p>Used by {@link aslframework.game.session.BattleSession}. There is no
 * streak bonus — every attempt is scored independently.
 */
public class StandardScoringStrategy implements ScoringStrategy {

  @Override
  public boolean isPassed(double accuracy) {
    return accuracy >= GameConfig.PASS_THRESHOLD;
  }

  @Override
  public int calculateScore(double accuracy, int tier) {
    return (int) (accuracy * 100) * tier;
  }
}
