package aslframework.game.result;

import aslframework.game.GameMode;

/**
 * Common contract for the summary produced at the end of any game session.
 *
 * <p>Callers (e.g. the UI layer) that only need to know whether a session
 * finished and which mode it was depend on this interface. Mode-specific data
 * (score, winner, rounds) is accessed by casting to {@link PracticeResult} or
 * {@link BattleResult}, or via the visitor / pattern-match idiom.
 */
public interface GameResult {

  /**
   * Returns the game mode this result belongs to.
   *
   * @return {@link GameMode#PRACTICE} or {@link GameMode#BATTLE}
   */
  GameMode getMode();

  /**
   * Returns whether the game ended naturally (all letters cleared, or last
   * player standing), as opposed to being abandoned via an early {@code finish()} call.
   *
   * @return {@code true} if completed normally
   */
  boolean isCompleted();

  /**
   * Returns the total number of rounds (letters attempted) in this session.
   *
   * @return total rounds played
   */
  int getTotalRounds();
}
