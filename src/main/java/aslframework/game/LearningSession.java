package aslframework.game;

import aslframework.persistence.AttemptRecord;
import aslframework.persistence.UserProgress;
import aslframework.recognition.GestureRecognizer;

/**
 * Orchestrates a single ASL learning session for one user.
 *
 * <p>{@code LearningSession} follows the <em>composition-over-inheritance</em>
 * principle: it holds a {@link GestureRecognizer} by interface reference, so
 * the recognition backend ({@link java.aslframework.recognition.TemplateMatchRecognizer}
 * or {@link java.aslframework.recognition.MediaPipeRecognizer}) can be swapped
 * at construction time without touching any session logic.
 *
 * <p>Typical usage:
 * <pre>{@code
 * GestureRecognizer recognizer = new MediaPipeRecognizer();
 * UserProgress progress = dao.load("user_001");
 * LearningSession session = new LearningSession(recognizer, progress);
 *
 * AttemptRecord result = session.attempt(cameraFrame, challenge);
 * dao.save(progress.getUserId(), result);
 * }</pre>
 */
public class LearningSession {

  /** Recognition backend used to evaluate camera frames. */
  private final GestureRecognizer recognizer;

  /** Stateless engine that converts accuracy scores to game scores. */
  private final ScoringEngine scoringEngine;

  /** In-session progress tracker; mutated after every attempt. */
  private final UserProgress userProgress;

  /**
   * Constructs a new {@code LearningSession}.
   *
   * @param recognizer   the gesture-recognition backend to use
   * @param userProgress the in-session progress tracker for the current user
   */
  public LearningSession(GestureRecognizer recognizer, UserProgress userProgress) {
    this.recognizer = recognizer;
    this.scoringEngine = new ScoringEngine();
    this.userProgress = userProgress;
  }

  /**
   * Evaluates a single camera frame against the given challenge and records
   * the result in the user's progress.
   *
   * <p>Steps performed:
   * <ol>
   *   <li>Calls the recognizer to obtain an accuracy score for the frame.</li>
   *   <li>Determines pass/fail via {@link ScoringEngine#isPassed}.</li>
   *   <li>Calculates the integer score via {@link ScoringEngine#calculateScore}.</li>
   *   <li>Creates an {@link AttemptRecord} and appends it to {@code userProgress}.</li>
   *   <li>Increments the user's level counter if the attempt passed.</li>
   * </ol>
   *
   * @param frame     raw image bytes from the camera capture loop
   * @param challenge the current gesture challenge presented to the user
   * @return the {@link AttemptRecord} created for this attempt
   */
  public AttemptRecord attempt(byte[] frame, GestureChallenge challenge) {
    double accuracy = recognizer.recognize(null, challenge.getTarget()).getConfidenceScore();
    boolean passed = scoringEngine.isPassed(accuracy);
    int score = scoringEngine.calculateScore(accuracy, challenge.getDifficultyLevel());

    AttemptRecord record = new AttemptRecord(
        challenge.getTarget().getGestureName(),
        accuracy,
        System.currentTimeMillis(),
        passed
    );

    userProgress.addAttempt(record);

    if (passed) {
      userProgress.incrementLevel();
    }

    System.out.println("Score: " + score + " | Passed: " + passed);
    return record;
  }
}
