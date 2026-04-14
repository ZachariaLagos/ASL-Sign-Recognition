package aslframework.game;

import aslframework.persistence.AttemptRecord;

/**
 * Extends {@link PlayerState} with battle-mode elimination tracking.
 *
 * <p>A {@code BattlePlayerState} starts active. The first failed attempt
 * permanently sets {@code eliminated = true}; subsequent attempts are rejected
 * by {@link aslframework.game.session.BattleSession} before they reach here.
 */
public class BattlePlayerState extends PlayerState {

  private boolean eliminated;

  /**
   * Constructs a new active {@code BattlePlayerState}.
   *
   * @param playerId unique identifier for this player
   */
  public BattlePlayerState(String playerId) {
    super(playerId);
    this.eliminated = false;
  }

  /**
   * Records an attempt and eliminates the player if it failed.
   *
   * @param record the attempt; a failed record triggers elimination
   */
  @Override
  public void recordAttempt(AttemptRecord record) {
    super.recordAttempt(record);
    if (!record.isPassed()) {
      eliminated = true;
    }
  }

  /**
   * Returns whether this player has been eliminated.
   *
   * @return {@code true} if eliminated
   */
  public boolean isEliminated() { return eliminated; }

  /**
   * Returns whether this player is still active.
   *
   * @return {@code true} if not yet eliminated
   */
  public boolean isActive() { return !eliminated; }

  @Override
  public String toString() {
    return String.format("BattlePlayerState(id=%s, rounds=%d, eliminated=%b)",
        getPlayerId(), getRoundsCleared(), eliminated);
  }
}
