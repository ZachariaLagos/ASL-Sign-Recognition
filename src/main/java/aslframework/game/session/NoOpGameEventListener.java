package aslframework.game.session;

import aslframework.game.round.BattleRound;
import aslframework.game.result.GameResult;
import aslframework.persistence.AttemptRecord;

import java.util.List;

/**
 * No-op implementation of {@link GameEventListener}.
 *
 * <p>Extend this class and override only the events you care about.
 * Avoids forcing every caller to implement all methods.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * GameEventListener logger = new NoOpGameEventListener() {
 *     {@literal @}Override
 *     public void onAttempt(String letter, AttemptRecord r, int streak) {
 *         System.out.printf("%-2s  %s  %.1f%%%n",
 *             letter, r.isPassed() ? "HIT" : "MISS", r.getAccuracy() * 100);
 *     }
 * };
 * }</pre>
 */
public class NoOpGameEventListener implements GameEventListener {
  @Override public void onAttempt(String l, AttemptRecord r, int streak) {}
  @Override public void onLetterCleared(String l, int ls, int ts) {}
  @Override public void onSessionFinished(GameResult result) {}
  @Override public void onRoundOpened(BattleRound round, List<String> active) {}
  @Override public void onRoundClosed(BattleRound round, List<String> eliminated) {}
  @Override public void onPlayerEliminated(String playerId, int roundCleared) {}
  @Override public void onWinnersDeclared(List<String> winners) {}
}
