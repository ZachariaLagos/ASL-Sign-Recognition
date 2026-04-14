package aslframework.game.session;

import aslframework.game.scoring.ScoringStrategy;
import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.model.RecognitionResult;
import aslframework.persistence.AttemptRecord;
import aslframework.recognition.GestureLibrary;
import aslframework.recognition.GestureRecognizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base for all game sessions, providing shared infrastructure.
 *
 * <p>Handles the parts every session needs:
 * <ul>
 *   <li>Holding the recognizer, library, and scoring strategy</li>
 *   <li>The {@code allAttempts} list and its accessor</li>
 *   <li>{@link #evaluate} — the single place where recognition happens</li>
 *   <li>{@link #requireNotFinished} — guard reused by all subclass attempt methods</li>
 *   <li>A {@link GameEventListener} that subclasses fire for UI/logging callbacks</li>
 * </ul>
 *
 * <p>Subclasses implement the mode-specific attempt flow, letter progression,
 * and result construction.
 */
public abstract class AbstractGameSession implements GameSession {

  protected final GestureRecognizer  recognizer;
  protected final GestureLibrary     library;
  protected final ScoringStrategy    scoringStrategy;

  /** Listener for game events. Defaults to no-op; swap via {@link #setEventListener}. */
  protected GameEventListener eventListener = new NoOpGameEventListener();

  protected boolean finished = false;
  private final List<AttemptRecord> allAttempts = new ArrayList<>();

  /**
   * Constructs the shared session infrastructure.
   *
   * @param recognizer      the gesture recognizer backend
   * @param library         the loaded gesture library
   * @param scoringStrategy the scoring/pass-threshold strategy for this mode
   */
  protected AbstractGameSession(GestureRecognizer recognizer,
                                 GestureLibrary library,
                                 ScoringStrategy scoringStrategy) {
    this.recognizer      = recognizer;
    this.library         = library;
    this.scoringStrategy = scoringStrategy;
  }

  // ── Shared infrastructure ────────────────────────────────────────────────────

  /**
   * Runs recognition for the given letter and landmarks, creates an
   * {@link AttemptRecord}, appends it to the shared attempt list, and returns it.
   *
   * <p>This is the single call-site for recognition across all session types —
   * neither subclass duplicates this logic.
   *
   * @param letter        the ASL letter being attempted
   * @param userLandmarks 21 hand landmarks from the camera
   * @return the created {@link AttemptRecord}
   */
  protected AttemptRecord evaluate(String letter, List<HandLandmark> userLandmarks) {
    List<GestureDefinition> variants = library.getGestureVariants(letter);
    RecognitionResult result   = recognizer.recognize(userLandmarks, variants);
    double            accuracy = result.getConfidenceScore();
    boolean           passed   = scoringStrategy.isPassed(accuracy);

    AttemptRecord record = new AttemptRecord(
        letter, accuracy, System.currentTimeMillis(), passed);
    allAttempts.add(record);
    return record;
  }

  /**
   * Throws {@link IllegalStateException} if the session has already ended.
   * Call at the top of every public attempt method.
   */
  protected void requireNotFinished() {
    if (finished) {
      throw new IllegalStateException("Session is already finished.");
    }
  }

  // ── GameSession default implementations ─────────────────────────────────────

  @Override
  public boolean isFinished() {
    return finished;
  }

  @Override
  public List<AttemptRecord> getAllAttempts() {
    return Collections.unmodifiableList(allAttempts);
  }

  /**
   * Default implementation returns 0. Practice mode overrides this to expose
   * the running score; battle mode scores are tracked per-round instead.
   */
  @Override
  public int getTotalScore() {
    return 0;
  }

  /**
   * Replaces the event listener used by this session.
   * Call before the first {@link #attempt} to ensure no events are missed.
   *
   * @param listener the listener to install; must not be {@code null}
   */
  public void setEventListener(GameEventListener listener) {
    if (listener == null) throw new IllegalArgumentException("listener must not be null");
    this.eventListener = listener;
  }
}
