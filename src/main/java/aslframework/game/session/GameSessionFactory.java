package aslframework.game.session;

import aslframework.recognition.GestureLibrary;
import aslframework.recognition.GestureRecognizer;

import java.util.List;

/**
 * Factory for creating game sessions.
 *
 * <p>Returns the {@link GameSession} interface type so callers are fully
 * decoupled from the concrete implementations. Use {@link #startBattle}
 * when you need battle-specific methods (e.g. {@link BattleSession#submitAttempt}).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // UI only needs the interface
 * GameSession session = GameSessionFactory.startPractice(recognizer, library, "alice");
 *
 * // Battle controller needs the concrete type
 * BattleSession battle = GameSessionFactory.startBattle(recognizer, library,
 *     List.of("alice", "bob", "charlie"));
 * }</pre>
 */
public final class GameSessionFactory {

  private GameSessionFactory() {}

  /**
   * Creates a single-player practice session.
   *
   * @param recognizer the gesture recognizer backend
   * @param library    the loaded gesture library
   * @param playerId   unique identifier for the player
   * @return a ready-to-use {@link GameSession} (concrete type: {@link PracticeSession})
   */
  public static GameSession startPractice(GestureRecognizer recognizer,
                                           GestureLibrary library,
                                           String playerId) {
    System.out.println("[Factory] PRACTICE for: " + playerId);
    return new PracticeSession(recognizer, library, playerId);
  }

  /**
   * Creates a multi-player battle session.
   *
   * @param recognizer the gesture recognizer backend
   * @param library    the loaded gesture library
   * @param playerIds  unique IDs for all participants (minimum 2)
   * @return a ready-to-use {@link BattleSession}
   */
  public static BattleSession startBattle(GestureRecognizer recognizer,
                                           GestureLibrary library,
                                           List<String> playerIds) {
    System.out.println("[Factory] BATTLE for: " + playerIds);
    return new BattleSession(recognizer, library, playerIds);
  }
}
