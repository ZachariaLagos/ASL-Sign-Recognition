package aslframework.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RecognitionResult class.
 * Verifies confidence score storage, match threshold logic, and string formatting.
 */
class RecognitionResultTest {

  private GestureDefinition gestureA;

  @BeforeEach
  void setUp() {
    List<HandLandmark> landmarks = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      landmarks.add(new HandLandmark(i * 0.01, i * 0.02, i * 0.03));
    }
    gestureA = new GestureDefinition("A", landmarks);
  }

  // --- Constructor & Getters ---

  @Test
  void testConstructorSetsConfidenceScoreCorrectly() {
    RecognitionResult result = new RecognitionResult(0.95, gestureA);
    assertEquals(0.95, result.getConfidenceScore());
  }

  @Test
  void testConstructorSetsClosestMatchCorrectly() {
    RecognitionResult result = new RecognitionResult(0.95, gestureA);
    assertEquals(gestureA, result.getClosestMatch());
  }

  // --- isMatch threshold logic ---

  @Test
  void testIsMatchReturnsTrueWhenConfidenceAtThreshold() {
    RecognitionResult result = new RecognitionResult(0.80, gestureA);
    assertTrue(result.isMatch());
  }

  @Test
  void testIsMatchReturnsTrueWhenConfidenceAboveThreshold() {
    RecognitionResult result = new RecognitionResult(0.95, gestureA);
    assertTrue(result.isMatch());
  }

  @Test
  void testIsMatchReturnsFalseWhenConfidenceBelowThreshold() {
    RecognitionResult result = new RecognitionResult(0.79, gestureA);
    assertFalse(result.isMatch());
  }

  @Test
  void testIsMatchReturnsFalseForZeroConfidence() {
    RecognitionResult result = new RecognitionResult(0.0, gestureA);
    assertFalse(result.isMatch());
  }

  @Test
  void testIsMatchReturnsTrueForPerfectConfidence() {
    RecognitionResult result = new RecognitionResult(1.0, gestureA);
    assertTrue(result.isMatch());
  }

  // --- toString ---

  @Test
  void testToStringFormat() {
    RecognitionResult result = new RecognitionResult(0.95, gestureA);
    assertEquals("RecognitionResult(confidence=0.95, match=true, gesture=A)", result.toString());
  }

  @Test
  void testToStringWhenNoMatch() {
    RecognitionResult result = new RecognitionResult(0.50, gestureA);
    assertEquals("RecognitionResult(confidence=0.50, match=false, gesture=A)", result.toString());
  }
}