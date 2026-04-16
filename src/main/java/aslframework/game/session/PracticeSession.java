package aslframework.game.session;

import aslframework.game.LetterDifficulty;
import aslframework.game.progression.LetterProgression;
import aslframework.game.progression.SequentialProgression;
import aslframework.game.result.GameResult;
import aslframework.game.result.PracticeResult;
import aslframework.game.scoring.PerfectBonusScoringStrategy;
import aslframework.model.HandLandmark;
import aslframework.persistence.AttemptRecord;
import aslframework.recognition.GestureLibrary;
import aslframework.recognition.GestureRecognizer;

import java.util.List;

/**
 * Single-player practice session: A → Z, each letter requires
 * {@link PerfectBonusScoringStrategy#getRequiredSuccesses()} consecutive passes.
 *
 * <p>Contains only practice-specific logic. Recognition, attempt recording,
 * and guards live in {@link AbstractGameSession}. Letter ordering is delegated
 * to {@link LetterProgression}. All output is sent to the injected
 * {@link GameEventListener} — no {@code System.out} calls here.
 *
 * <h2>Pass condition</h2>
 * Must succeed consecutively {@code REQUIRED_SUCCESSES} times. Any failure
 * resets the streak to zero and forfeits pending score for that letter.
 *
 * <h2>Scoring</h2>
 * <pre>
 *   attempt score  = floor(accuracy × 100) × tier
 *   perfect bonus  = max(0, required - failCount) × bonusPerTier × tier
 *   letter total   = sum of passing attempt scores + perfect bonus
 * </pre>
 */
public class PracticeSession extends AbstractGameSession {

  private final String                      playerId;
  private final LetterProgression           progression;
  private final PerfectBonusScoringStrategy bonusStrategy;

  private int consecutiveSuccesses;
  private int failsOnCurrentLetter;
  private int pendingLetterScore;
  private int totalScore;

  // ── Construction ────────────────────────────────────────────────────────────

  /**
   * Default constructor — A→Z progression, standard bonus scoring,
   * no-op event listener (replace via {@link #setEventListener}).
   */
  public PracticeSession(GestureRecognizer recognizer,
                         GestureLibrary library,
                         String playerId) {
    this(recognizer, library, playerId,
         new SequentialProgression(library),
         new PerfectBonusScoringStrategy());
  }

  /**
   * Full constructor for custom progression or scoring (e.g. tests).
   */
  public PracticeSession(GestureRecognizer recognizer,
                         GestureLibrary library,
                         String playerId,
                         LetterProgression progression,
                         PerfectBonusScoringStrategy bonusStrategy) {
    super(recognizer, library, bonusStrategy);
    this.playerId             = playerId;
    this.progression          = progression;
    this.bonusStrategy        = bonusStrategy;
    this.consecutiveSuccesses = 0;
    this.failsOnCurrentLetter = 0;
    this.pendingLetterScore   = 0;
    this.totalScore           = 0;
  }

  // ── GameSession ──────────────────────────────────────────────────────────────

  @Override
  public String getCurrentLetter() {
    return progression.current();
  }

  @Override
  public int getTotalScore() {
    return totalScore;
  }

  /**
   * Submits a recognition attempt for the current letter.
   *
   * <p>On success: increments streak and pending score; clears letter when
   * streak reaches {@code REQUIRED_SUCCESSES}.<br>
   * On failure: resets streak and forfeits pending score.
   *
   * @param userLandmarks 21 hand landmarks from the live camera feed
   * @return the {@link AttemptRecord} for this attempt
   * @throws IllegalStateException if the session is already finished
   */
  @Override
  public AttemptRecord attempt(List<HandLandmark> userLandmarks) {
    requireNotFinished();

    String letter = getCurrentLetter();
    int    tier   = LetterDifficulty.tierOf(letter);

    AttemptRecord record = evaluate(letter, userLandmarks);

    if (record.isPassed()) {
      consecutiveSuccesses++;
      pendingLetterScore += scoringStrategy.calculateScore(record.getAccuracy(), tier);
      eventListener.onAttempt(letter, record, consecutiveSuccesses);

      if (consecutiveSuccesses >= bonusStrategy.getRequiredSuccesses()) {
        clearCurrentLetter(letter, tier);
      }
    } else {
      failsOnCurrentLetter++;
      consecutiveSuccesses = 0;
      pendingLetterScore   = 0;
      eventListener.onAttempt(letter, record, 0);
    }

    return record;
  }

  @Override
  public GameResult finish() {
    boolean completedAll = finished;
    finished = true;
    int cleared = progression.currentIndex();
    PracticeResult result = new PracticeResult(
        playerId, cleared, progression.totalLetters(),
        totalScore, completedAll, getAllAttempts());
    eventListener.onSessionFinished(result);
    return result;
  }

  // ── Practice-specific queries ────────────────────────────────────────────────

  /** Returns the current consecutive-success streak (resets on failure). */
  public int getConsecutiveSuccesses()  { return consecutiveSuccesses; }

  /** Returns the total failures on the current letter. */
  public int getFailsOnCurrentLetter()  { return failsOnCurrentLetter; }

  /** Returns accumulated score for the current (incomplete) streak. Forfeited on fail. */
  public int getPendingLetterScore()    { return pendingLetterScore; }

  /** Returns the 1-based index of the current letter. */
  public int getCurrentPosition()       { return progression.currentIndex() + 1; }

  /** Returns the total number of letters in this session. */
  public int getTotalLetters()          { return progression.totalLetters(); }

  // ── Private ──────────────────────────────────────────────────────────────────

  private void clearCurrentLetter(String letter, int tier) {
    int bonus       = bonusStrategy.calculatePerfectBonus(failsOnCurrentLetter, tier);
    int letterTotal = pendingLetterScore + bonus;
    totalScore     += letterTotal;

    eventListener.onLetterCleared(letter, letterTotal, totalScore);

    consecutiveSuccesses = 0;
    failsOnCurrentLetter = 0;
    pendingLetterScore   = 0;

    progression.advance();

    if (progression.isExhausted()) {
      finished = true;
      PracticeResult result = new PracticeResult(
          playerId, progression.totalLetters(), progression.totalLetters(),
          totalScore, true, getAllAttempts());
      eventListener.onSessionFinished(result);
    }
  }
}
