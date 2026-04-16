package aslframework.game.session;

import aslframework.game.round.BattleRound;
import aslframework.game.result.GameResult;
import aslframework.persistence.AttemptRecord;

import java.util.List;

/**
 * Observer interface for game session events.
 *
 * <p>Sessions call these methods at key moments. By injecting a listener,
 * callers (UI, logging, analytics) receive structured events without the
 * session needing to know anything about how they are handled.
 *
 * <p>This removes all {@code System.out.printf} calls from session logic —
 * output is entirely the listener's responsibility.
 *
 * <p>A no-op default implementation {@link NoOpGameEventListener} is provided
 * so callers only override the events they care about.
 *
 * <h2>Example — console logger</h2>
 * <pre>{@code
 * GameSession session = GameSessionFactory.startPractice(
 *     recognizer, library, "alice", new ConsoleGameEventListener());
 * }</pre>
 */
public interface GameEventListener {

  // ── Common events ────────────────────────────────────────────────────────────

  /**
   * Fired after any attempt is evaluated.
   *
   * @param letter   the letter that was attempted
   * @param record   the resulting attempt record
   * @param streak   consecutive successes on this letter (practice) or 0 (battle)
   */
  void onAttempt(String letter, AttemptRecord record, int streak);

  /**
   * Fired when a letter is fully cleared (practice: streak complete; battle: n/a).
   *
   * @param letter       the cleared letter
   * @param letterScore  points earned for this letter (base + bonus)
   * @param totalScore   cumulative score after this letter
   */
  void onLetterCleared(String letter, int letterScore, int totalScore);

  /**
   * Fired when the session ends (naturally or abandoned).
   *
   * @param result the final game result
   */
  void onSessionFinished(GameResult result);

  // ── Battle-specific events ───────────────────────────────────────────────────

  /**
   * Fired when a new battle round opens.
   *
   * @param round          the newly opened round
   * @param activePlayers  IDs of players still in the game
   */
  void onRoundOpened(BattleRound round, List<String> activePlayers);

  /**
   * Fired when a battle round closes (all active players have submitted).
   *
   * @param round            the round that just closed
   * @param eliminatedThisRound player IDs eliminated in this round
   */
  void onRoundClosed(BattleRound round, List<String> eliminatedThisRound);

  /**
   * Fired when a player is eliminated.
   *
   * @param playerId     the eliminated player's ID
   * @param roundCleared number of rounds they survived
   */
  void onPlayerEliminated(String playerId, int roundCleared);

  /**
   * Fired when winner(s) are declared.
   *
   * @param winners list of winning player IDs (multiple = joint win)
   */
  void onWinnersDeclared(List<String> winners);
}
