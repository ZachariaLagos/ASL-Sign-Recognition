package aslframework.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for the GestureChallenge class.
 * Verifies correct construction and getter behavior.
 */
public class GestureChallengeTest {

  private GestureDefinition gestureB;
  private GestureChallenge  challenge;

  @BeforeEach
  void setUp() {
    List<HandLandmark> landmarks = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      landmarks.add(new HandLandmark(i * 0.01, i * 0.02, i * 0.03));
    }
    gestureB  = new GestureDefinition("B", landmarks);
    challenge = new GestureChallenge(gestureB, 2);
  }

  // Testing Constructors and Getters

  @Test
  void testConstructorSetsTargetGestureCorrectly() {
    assertEquals("B", challenge.getTarget().getGestureName());
  }

  @Test
  void testConstructorSetsDifficultyLevelCorrectly() {
    assertEquals(2, challenge.getDifficultyLevel());
  }

  @Test
  void testGetTarget_returnsExactGestureInstance() {
    assertEquals(gestureB, challenge.getTarget());
  }

  @Test
  void testDifficultyLevel_minimumValue_returnsOne() {
    GestureChallenge minChallenge = new GestureChallenge(gestureB, 1);
    assertEquals(1, minChallenge.getDifficultyLevel());
  }
}