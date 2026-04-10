package aslframework.recognition;

import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.model.RecognitionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the MediaPipeRecognizer class.
 * Verifies Euclidean distance scoring, confidence normalization, and input validation.
 */
class MediaPipeRecognizerTest {

  private MediaPipeRecognizer recognizer;
  private GestureDefinition gestureA;
  private List<HandLandmark> referenceLandmarks;

  @BeforeEach
  void setUp() {
    recognizer = new MediaPipeRecognizer();
    referenceLandmarks = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      referenceLandmarks.add(new HandLandmark(i * 0.01, i * 0.02, i * 0.03));
    }
    gestureA = new GestureDefinition("A", referenceLandmarks);
  }

  // --- Happy path ---

  @Test
  void testIdenticalLandmarksReturnsPerfectConfidence() {
    RecognitionResult result = recognizer.recognize(referenceLandmarks, gestureA);
    assertEquals(1.0, result.getConfidenceScore(), 0.0001);
  }

  @Test
  void testIdenticalLandmarksReturnsMatch() {
    RecognitionResult result = recognizer.recognize(referenceLandmarks, gestureA);
    assertTrue(result.isMatch());
  }

  @Test
  void testKnownDistanceReturnsExpectedConfidence() {
    // Shift every landmark by (1.0, 0.0, 0.0) - each distance = 1.0, avg = 1.0
    // Expected confidence = 1.0 / (1.0 + 1.0) = 0.5
    List<HandLandmark> shiftedLandmarks = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      shiftedLandmarks.add(new HandLandmark(i * 0.01 + 1.0, i * 0.02, i * 0.03));
    }
    RecognitionResult result = recognizer.recognize(shiftedLandmarks, gestureA);
    assertEquals(0.5, result.getConfidenceScore(), 0.0001);
  }

  @Test
  void testLowConfidenceReturnsFalseIsMatch() {
    // Shift landmarks far away to get low confidence
    List<HandLandmark> farLandmarks = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      farLandmarks.add(new HandLandmark(i * 0.01 + 100.0, i * 0.02, i * 0.03));
    }
    RecognitionResult result = recognizer.recognize(farLandmarks, gestureA);
    assertFalse(result.isMatch());
  }

  @Test
  void testClosestMatchIsTargetGesture() {
    RecognitionResult result = recognizer.recognize(referenceLandmarks, gestureA);
    assertEquals(gestureA, result.getClosestMatch());
  }

  // --- Edge cases ---

  @Test
  void testAllZeroLandmarksReturnsPerfectConfidence() {
    List<HandLandmark> zeroLandmarks = new ArrayList<>();
    List<HandLandmark> zeroReference = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      zeroLandmarks.add(new HandLandmark(0.0, 0.0, 0.0));
      zeroReference.add(new HandLandmark(0.0, 0.0, 0.0));
    }
    GestureDefinition zeroGesture = new GestureDefinition("Z", zeroReference);
    RecognitionResult result = recognizer.recognize(zeroLandmarks, zeroGesture);
    assertEquals(1.0, result.getConfidenceScore(), 0.0001);
  }

  @Test
  void testNegativeCoordinatesHandledCorrectly() {
    // Identical negative landmarks should still return perfect confidence
    List<HandLandmark> negativeLandmarks = new ArrayList<>();
    List<HandLandmark> negativeReference = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      negativeLandmarks.add(new HandLandmark(-0.5, -0.3, -0.1));
      negativeReference.add(new HandLandmark(-0.5, -0.3, -0.1));
    }
    GestureDefinition negativeGesture = new GestureDefinition("N", negativeReference);
    RecognitionResult result = recognizer.recognize(negativeLandmarks, negativeGesture);
    assertEquals(1.0, result.getConfidenceScore(), 0.0001);
  }

  // --- Input validation ---

  @Test
  void testTooFewUserLandmarksThrowsException() {
    List<HandLandmark> shortList = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      shortList.add(new HandLandmark(0.0, 0.0, 0.0));
    }
    assertThrows(IllegalArgumentException.class, () -> {
      recognizer.recognize(shortList, gestureA);
    });
  }

  @Test
  void testTooManyUserLandmarksThrowsException() {
    List<HandLandmark> longList = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      longList.add(new HandLandmark(0.0, 0.0, 0.0));
    }
    assertThrows(IllegalArgumentException.class, () -> {
      recognizer.recognize(longList, gestureA);
    });
  }
}