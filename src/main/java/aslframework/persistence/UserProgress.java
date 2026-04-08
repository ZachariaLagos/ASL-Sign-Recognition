package aslframework.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistence-layer representation of a user's learning progress.
 *
 * <p>This class is the data carrier loaded from and saved to the SQLite
 * database by {@link UserProgressDAO}. It holds the flat list of
 * {@link AttemptRecord}s exactly as stored in the {@code user_progress} table.
 *
 * <p>For in-session logic (scoring, level gating) use
 * {@link java.aslframework.core.UserProgress} instead, which wraps this object.
 *
 * <p>The list of attempts is exposed as an unmodifiable view to prevent
 * accidental external mutation.
 */
public class UserProgress {

  private final String userId;
  private final List<AttemptRecord> attempts = new ArrayList<>();
  private int levelsCompleted;

  /**
   * Constructs an empty {@code UserProgress} for the given user.
   *
   * @param userId unique identifier for the user
   */
  public UserProgress(String userId) {
    this.userId = userId;
  }

  /**
   * Appends an attempt record loaded from (or destined for) the database.
   *
   * @param record the attempt to add; must not be {@code null}
   */
  public void addAttempt(AttemptRecord record) {
    attempts.add(record);
  }

  /**
   * Returns an unmodifiable view of all recorded attempts in insertion order.
   *
   * @return read-only list of {@link AttemptRecord}s
   */
  public List<AttemptRecord> getAttempts() {
    return Collections.unmodifiableList(attempts);
  }

  /**
   * Computes the average accuracy across all recorded attempts.
   *
   * @return mean accuracy in {@code [0.0, 1.0]}, or {@code 0.0} if no attempts exist
   */
  public double getAverageAccuracy() {
    return attempts.stream()
        .mapToDouble(AttemptRecord::getAccuracy)
        .average()
        .orElse(0.0);
  }

  /**
   * Returns the unique identifier of this user.
   *
   * @return user ID
   */
  public String getUserId() { return userId; }

  /**
   * Returns the number of levels this user has completed.
   *
   * @return levels completed count
   */
  public int getLevelsCompleted() { return levelsCompleted; }

  /**
   * Increments the completed-level counter by one.
   * Called by {@link java.aslframework.game.LearningSession} when an attempt passes.
   */
  public void incrementLevel() { levelsCompleted++; }
}
