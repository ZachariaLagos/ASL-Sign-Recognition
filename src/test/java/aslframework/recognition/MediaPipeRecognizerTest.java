package aslframework.recognition;

import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.model.RecognitionResult;
import aslframework.model.StaticGestureDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the MediaPipeRecognizer class.
 * Verifies Euclidean distance scoring, confidence normalization,
 * variant selection, and input validation.
 */
class MediaPipeRecognizerTest {

  private MediaPipeRecognizer recognizer;
  private List<GestureDefinition> variants;
  private List<HandLandmark> referenceLandmarks;

  @BeforeEach
  void setUp() {
    recognizer = new MediaPipeRecognizer();
    referenceLandmarks = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      referenceLandmarks.add(new HandLandmark(i * 0.01, i * 0.02, i * 0.03));
    }
    // Single variant - original landmarks, no rotation
    variants = new ArrayList<>();
    variants.add(new StaticGestureDefinition("A", referenceLandmarks));
  }

  // --- Happy path ---

  @Test
  void testIdenticalLandmarksReturnsPerfectConfidence() {
    RecognitionResult result = recognizer.recognize(referenceLandmarks, variants);
    assertEquals(1.0, result.getConfidenceScore(), 0.0001);
  }

  @Test
  void testIdenticalLandmarksReturnsMatch() {
    RecognitionResult result = recognizer.recognize(referenceLandmarks, variants);
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
    RecognitionResult result = recognizer.recognize(shiftedLandmarks, variants);
    assertEquals(0.5, result.getConfidenceScore(), 0.0001);
  }

  @Test
  void testLowConfidenceReturnsFalseIsMatch() {
    List<HandLandmark> farLandmarks = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      farLandmarks.add(new HandLandmark(i * 0.01 + 100.0, i * 0.02, i * 0.03));
    }
    RecognitionResult result = recognizer.recognize(farLandmarks, variants);
    assertFalse(result.isMatch());
  }

  @Test
  void testClosestMatchIsTargetGesture() {
    RecognitionResult result = recognizer.recognize(referenceLandmarks, variants);
    assertEquals(variants.get(0), result.getClosestMatch());
  }

  // --- Variant selection ---

  @Test
  void testBestVariantIsSelected() {
    // Add a second variant with landmarks shifted far away (low confidence)
    // and a third variant identical to user (perfect confidence)
    List<HandLandmark> farLandmarks = new ArrayList<>();
    List<HandLandmark> perfectLandmarks = new ArrayList<>();
    List<HandLandmark> userLandmarks = new ArrayList<>();

    for (int i = 0; i < 21; i++) {
      farLandmarks.add(new HandLandmark(i * 0.01 + 100.0, i * 0.02, i * 0.03));
      perfectLandmarks.add(new HandLandmark(i * 0.05, i * 0.05, i * 0.05));
      userLandmarks.add(new HandLandmark(i * 0.05, i * 0.05, i * 0.05));
    }

    GestureDefinition farVariant = new StaticGestureDefinition("A", farLandmarks);
    GestureDefinition perfectVariant = new StaticGestureDefinition("A", perfectLandmarks);

    List<GestureDefinition> multiVariants = new ArrayList<>();
    multiVariants.add(farVariant);
    multiVariants.add(perfectVariant);

    RecognitionResult result = recognizer.recognize(userLandmarks, multiVariants);
    assertEquals(1.0, result.getConfidenceScore(), 0.0001);
    assertEquals(perfectVariant, result.getClosestMatch());
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
    List<GestureDefinition> zeroVariants = new ArrayList<>();
    zeroVariants.add(new StaticGestureDefinition("Z", zeroReference));
    RecognitionResult result = recognizer.recognize(zeroLandmarks, zeroVariants);
    assertEquals(1.0, result.getConfidenceScore(), 0.0001);
  }

  @Test
  void testNegativeCoordinatesHandledCorrectly() {
    List<HandLandmark> negativeLandmarks = new ArrayList<>();
    List<HandLandmark> negativeReference = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      negativeLandmarks.add(new HandLandmark(-0.5, -0.3, -0.1));
      negativeReference.add(new HandLandmark(-0.5, -0.3, -0.1));
    }
    List<GestureDefinition> negativeVariants = new ArrayList<>();
    negativeVariants.add(new StaticGestureDefinition("N", negativeReference));
    RecognitionResult result = recognizer.recognize(negativeLandmarks, negativeVariants);
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
      recognizer.recognize(shortList, variants);
    });
  }

  @Test
  void testTooManyUserLandmarksThrowsException() {
    List<HandLandmark> longList = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      longList.add(new HandLandmark(0.0, 0.0, 0.0));
    }
    assertThrows(IllegalArgumentException.class, () -> {
      recognizer.recognize(longList, variants);
    });
  }
}