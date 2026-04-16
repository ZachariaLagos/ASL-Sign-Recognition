package aslframework.game.session;

import aslframework.model.HandLandmark;
import aslframework.persistence.AttemptRecord;
import aslframework.game.result.GameResult;

import java.util.List;

/**
 * Unified contract for all game sessions.
 *
 * <p>The UI layer and {@code Main} depend only on this interface. Whether the
 * underlying session is a {@link PracticeSession} or {@link BattleSession} is
 * irrelevant to the caller — they drive the session through this API alone.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Obtain an instance from {@link GameSessionFactory}.</li>
 *   <li>Poll {@link #getCurrentLetter()} to know what to show.</li>
 *   <li>Call {@link #attempt(List)} with each camera frame's landmarks.</li>
 *   <li>Repeat until {@link #isFinished()} returns {@code true}.</li>
 *   <li>Call {@link #finish()} to retrieve the {@link GameResult}.</li>
 * </ol>
 */
public interface GameSession {

  /**
   * Returns the letter the session currently expects the player(s) to sign.
   *
   * @return current target letter, or {@code null} if the session is finished
   */
  String getCurrentLetter();

  /**
   * Submits a recognition attempt using the given hand landmarks.
   *
   * <p>In practice mode, this is called for the single player. In battle mode,
   * the {@code playerId} is resolved internally; for a multi-player UI that
   * drives each player separately use
   * {@link BattleSession#submitAttempt(String, List)} directly.
   *
   * @param userLandmarks 21 hand landmarks from the live camera feed
   * @return the {@link AttemptRecord} created for this attempt
   * @throws IllegalStateException if the session is already finished
   */
  AttemptRecord attempt(List<HandLandmark> userLandmarks);

  /**
   * Returns whether the session has ended (naturally or via {@link #finish()}).
   *
   * @return {@code true} if finished
   */
  boolean isFinished();

  /**
   * Returns the player's current total score.
   * In battle mode returns 0 (scoring is per-round, tracked separately).
   *
   * @return total score
   */
  int getTotalScore();

  /**
   * Ends the session and returns the final result.
   * Safe to call even if the session has already ended naturally.
   *
   * @return a {@link GameResult} summarising the session
   */
  GameResult finish();

  /**
   * Returns all attempt records submitted so far, in order.
   *
   * @return unmodifiable list of attempts
   */
  List<AttemptRecord> getAllAttempts();
}
