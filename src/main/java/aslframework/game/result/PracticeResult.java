package aslframework.game.result;

import aslframework.game.GameMode;
import aslframework.persistence.AttemptRecord;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result for a completed practice session.
 *
 * <p>Carries practice-specific data: the total score (including perfect-run
 * bonuses), how many letters were cleared, and the full attempt history.
 */
public class PracticeResult implements GameResult {

  private final String            playerId;
  private final int               lettersCleared;
  private final int               totalLetters;
  private final int               totalScore;
  private final boolean           completed;
  private final List<AttemptRecord> attempts;

  /**
   * Constructs a {@code PracticeResult}.
   *
   * @param playerId       the player's unique ID
   * @param lettersCleared number of letters successfully cleared
   * @param totalLetters   total letters available in the session
   * @param totalScore     final score including perfect-run bonuses
   * @param completed      {@code true} if all letters were cleared
   * @param attempts       full attempt history (copied defensively)
   */
  public PracticeResult(String playerId,
                        int lettersCleared,
                        int totalLetters,
                        int totalScore,
                        boolean completed,
                        List<AttemptRecord> attempts) {
    this.playerId       = playerId;
    this.lettersCleared = lettersCleared;
    this.totalLetters   = totalLetters;
    this.totalScore     = totalScore;
    this.completed      = completed;
    this.attempts       = Collections.unmodifiableList(List.copyOf(attempts));
  }

  @Override public GameMode getMode()       { return GameMode.PRACTICE; }
  @Override public boolean  isCompleted()   { return completed; }
  @Override public int      getTotalRounds(){ return totalLetters; }

  /** Returns the player's unique ID. */
  public String getPlayerId()       { return playerId; }

  /** Returns the number of letters the player successfully cleared. */
  public int getLettersCleared()    { return lettersCleared; }

  /** Returns the total letters available in the session. */
  public int getTotalLetters()      { return totalLetters; }

  /** Returns the final committed score, including all perfect-run bonuses. */
  public int getTotalScore()        { return totalScore; }

  /** Returns the full attempt history in submission order. */
  public List<AttemptRecord> getAttempts() { return attempts; }

  @Override
  public String toString() {
    return String.format("PracticeResult[player=%s cleared=%d/%d score=%d completed=%b]",
        playerId, lettersCleared, totalLetters, totalScore, completed);
  }
}
