package aslframework.game;

import aslframework.persistence.AttemptRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks the in-game state common to all game modes for a single player.
 *
 * <p>Records every attempt and counts successful rounds cleared.
 * Mode-specific behaviour (e.g. elimination in battle mode) lives in
 * {@link BattlePlayerState}, which extends this class.
 */
public class PlayerState {

  private final String            playerId;
  protected int                   roundsCleared;
  protected final List<AttemptRecord> attempts;

  /**
   * Constructs a fresh {@code PlayerState} for the given player.
   *
   * @param playerId unique identifier for this player
   */
  public PlayerState(String playerId) {
    this.playerId      = playerId;
    this.roundsCleared = 0;
    this.attempts      = new ArrayList<>();
  }

  /**
   * Records an attempt and increments {@code roundsCleared} if it passed.
   * Subclasses may override to add mode-specific side-effects (e.g. elimination).
   *
   * @param record the attempt to record; must not be {@code null}
   */
  public void recordAttempt(AttemptRecord record) {
    attempts.add(record);
    if (record.isPassed()) {
      roundsCleared++;
    }
  }

  /** Returns this player's unique identifier. */
  public String getPlayerId() { return playerId; }

  /** Returns the number of rounds (letters) this player has successfully cleared. */
  public int getRoundsCleared() { return roundsCleared; }

  /** Returns all attempt records in insertion order. */
  public List<AttemptRecord> getAttempts() {
    return Collections.unmodifiableList(attempts);
  }

  @Override
  public String toString() {
    return String.format("PlayerState(id=%s, rounds=%d)", playerId, roundsCleared);
  }
}
