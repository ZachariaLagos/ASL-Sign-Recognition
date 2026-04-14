package aslframework.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import aslframework.model.GestureDefinition;
import aslframework.persistence.UserProgress;
import aslframework.model.StaticGestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.persistence.AttemptRecord;
import aslframework.recognition.MockGestureRecognizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for the LearningSession class.
 * Verifies that attempts are recorded correctly and level progression works as expected.
 * Uses MockGestureRecognizer to avoid requiring a camera or MediaPipe.
 */
public class LearningSessionTest {

  private static final double PASSING_CONFIDENCE = 0.9;
  private static final double FAILING_CONFIDENCE = 0.5;

  private List<GestureDefinition> variantsA;
  private GestureChallenge easyChallenge;
  private UserProgress userProgress;

  @BeforeEach
  void setUp() {
    List<HandLandmark> landmarks = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      landmarks.add(new HandLandmark(i * 0.01, i * 0.02, i * 0.03));
    }
    StaticGestureDefinition gestureA = new StaticGestureDefinition("A", landmarks);
    variantsA = new ArrayList<>();
    variantsA.add(gestureA);
    easyChallenge = new GestureChallenge(variantsA, 1);
    userProgress = new UserProgress("user_001");
  }

  @Test
  void testAttempt_passing_returnsNonNullRecord() {
    LearningSession session = new LearningSession(
        new MockGestureRecognizer(PASSING_CONFIDENCE), userProgress);
    AttemptRecord record = session.attempt(new byte[0], easyChallenge);
    assertNotNull(record);
  }

  @Test
  void testAttempt_passing_levelIsIncremented() {
    LearningSession session = new LearningSession(
        new MockGestureRecognizer(PASSING_CONFIDENCE), userProgress);
    assertEquals(0, userProgress.getLevelsCompleted());
    session.attempt(new byte[0], easyChallenge);
    assertEquals(1, userProgress.getLevelsCompleted());
  }

  @Test
  void testAttempt_passing_attemptIsStoredInProgress() {
    LearningSession session = new LearningSession(
        new MockGestureRecognizer(PASSING_CONFIDENCE), userProgress);
    session.attempt(new byte[0], easyChallenge);
    assertEquals(1, userProgress.getAttempts().size());
  }

  @Test
  void testAttempt_failing_levelIsNotIncremented() {
    LearningSession session = new LearningSession(
        new MockGestureRecognizer(FAILING_CONFIDENCE), userProgress);
    session.attempt(new byte[0], easyChallenge);
    assertEquals(0, userProgress.getLevelsCompleted());
  }

  @Test
  void testAttempt_failing_attemptIsStillRecorded() {
    LearningSession session = new LearningSession(
        new MockGestureRecognizer(FAILING_CONFIDENCE), userProgress);
    session.attempt(new byte[0], easyChallenge);
    assertEquals(1, userProgress.getAttempts().size());
  }

  @Test
  void testAttempt_multipleAttempts_allAreRecorded() {
    LearningSession session = new LearningSession(
        new MockGestureRecognizer(PASSING_CONFIDENCE), userProgress);
    session.attempt(new byte[0], easyChallenge);
    session.attempt(new byte[0], easyChallenge);
    session.attempt(new byte[0], easyChallenge);
    assertEquals(3, userProgress.getAttempts().size());
  }

  @Test
  void testAttempt_multiplePassingAttempts_levelCountsCorrectly() {
    LearningSession session = new LearningSession(
        new MockGestureRecognizer(PASSING_CONFIDENCE), userProgress);
    session.attempt(new byte[0], easyChallenge);
    session.attempt(new byte[0], easyChallenge);
    assertEquals(2, userProgress.getLevelsCompleted());
  }
}