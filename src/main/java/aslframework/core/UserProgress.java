package aslframework.core;

import aslframework.persistence.AttemptRecord;
import java.util.Collections;
import java.util.List;

/**
 * In-session view of a user's learning progress.
 *
 * <p>This class lives in the {@code core} layer and is the object that
 * {@link aslframework.game.LearningSession} interacts with during gameplay.
 * It wraps a {@link aslframework.persistence.UserProgress} loaded from the
 * database, adding session-level convenience without exposing raw persistence
 * objects to game logic.
 *
 * <p>Separation of concerns:
 * <ul>
 *   <li>{@code core.UserProgress} (this class) – used by game/session logic</li>
 *   <li>{@link aslframework.persistence.UserProgress} – used by
 *       {@link aslframework.persistence.UserProgressDAO} for DB I/O</li>
 * </ul>
 */
public class UserProgress {

  /** The underlying persistence object that backs this session view. */
  private final aslframework.persistence.UserProgress persistenceProgress;

  /**
   * Constructs a {@code core.UserProgress} that wraps the given persistence object.
   *
   * @param persistenceProgress the DB-loaded progress record; must not be {@code null}
   */
  public UserProgress(aslframework.persistence.UserProgress persistenceProgress) {
    this.persistenceProgress = persistenceProgress;
  }

  /**
   * Convenience constructor that creates a fresh progress record for a new user.
   *
   * @param userId unique identifier for the user
   */
  public UserProgress(String userId) {
    this.persistenceProgress = new aslframework.persistence.UserProgress(userId);
  }

  /**
   * Records a new attempt in this user's history.
   *
   * @param record the attempt to add; must not be {@code null}
   */
  public void addAttempt(AttemptRecord record) {
    persistenceProgress.addAttempt(record);
  }

  /**
   * Returns an unmodifiable view of all recorded attempts.
   *
   * @return read-only list of {@link AttemptRecord}s in insertion order
   */
  public List<AttemptRecord> getAttempts() {
    return Collections.unmodifiableList(persistenceProgress.getAttempts());
  }

  /**
   * Computes the average accuracy across all recorded attempts.
   *
   * @return mean accuracy in {@code [0.0, 1.0]}, or {@code 0.0} if no attempts exist
   */
  public double getAverageAccuracy() {
    return persistenceProgress.getAverageAccuracy();
  }

  /**
   * Returns the unique identifier of this user.
   *
   * @return user ID
   */
  public String getUserId() { return persistenceProgress.getUserId(); }

  /**
   * Returns the number of levels this user has completed.
   *
   * @return levels completed count
   */
  public int getLevelsCompleted() { return persistenceProgress.getLevelsCompleted(); }

  /**
   * Increments the completed-level counter by one.
   * Called by {@link aslframework.game.LearningSession} when an attempt passes.
   */
  public void incrementLevel() { persistenceProgress.incrementLevel(); }

  /**
   * Exposes the underlying persistence object so it can be flushed to the
   * database via {@link aslframework.persistence.UserProgressDAO#save}.
   *
   * @return the wrapped {@link aslframework.persistence.UserProgress}
   */
  public aslframework.persistence.UserProgress toPersistence() {
    return persistenceProgress;
  }
}
