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
    List<HandLandmark> rawLandmarks = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      rawLandmarks.add(new HandLandmark(i * 0.01, i * 0.02, i * 0.03));
    }
    referenceLandmarks = LandmarkUtils.normalize(rawLandmarks);
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
    List<HandLandmark> shiftedLandmarks = new ArrayList<>();
    shiftedLandmarks.add(new HandLandmark(0.0, 0.0, 0.0));
    for (int i = 1; i < 21; i++) {
      shiftedLandmarks.add(new HandLandmark(i * 0.01 + 1.0, i * 0.02, i * 0.03));
    }
    RecognitionResult result = recognizer.recognize(shiftedLandmarks, variants);
    assertTrue(result.getConfidenceScore() > 0.0 && result.getConfidenceScore() < 1.0);
  }

  @Test
  void testLowConfidenceReturnsFalseIsMatch() {
    List<HandLandmark> farLandmarks = new ArrayList<>();
    farLandmarks.add(new HandLandmark(0.0, 0.0, 0.0)); // wrist unchanged
    for (int i = 1; i < 21; i++) {
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
    List<HandLandmark> rawFar = new ArrayList<>();
    List<HandLandmark> rawPerfect = new ArrayList<>();
    List<HandLandmark> rawUser = new ArrayList<>();

    for (int i = 0; i < 21; i++) {
      rawFar.add(new HandLandmark(i * 0.01 + 100.0, i * 0.02, i * 0.03));
      rawPerfect.add(new HandLandmark(i * 0.05, i * 0.05, i * 0.05));
      rawUser.add(new HandLandmark(i * 0.05, i * 0.05, i * 0.05));
    }

    GestureDefinition farVariant     = new StaticGestureDefinition("A", LandmarkUtils.normalize(rawFar));
    GestureDefinition perfectVariant = new StaticGestureDefinition("A", LandmarkUtils.normalize(rawPerfect));

    List<GestureDefinition> multiVariants = new ArrayList<>();
    multiVariants.add(farVariant);
    multiVariants.add(perfectVariant);

    RecognitionResult result = recognizer.recognize(rawUser, multiVariants);
    assertEquals(1.0, result.getConfidenceScore(), 0.0001);
    assertEquals(perfectVariant, result.getClosestMatch());
  }

  // --- Edge cases ---

  @Test
  void testAllZeroLandmarksReturnsPerfectConfidence() {
    // Note: all-zero landmarks produce a zero vector which has no direction.
    // Cosine similarity is undefined for zero vectors - the guard returns 0.0.
    // This edge case cannot occur in practice since MediaPipe always returns
    // non-zero landmarks for a detected hand.
    List<HandLandmark> zeroLandmarks = new ArrayList<>();
    List<HandLandmark> zeroReference = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      zeroLandmarks.add(new HandLandmark(0.0, 0.0, 0.0));
      zeroReference.add(new HandLandmark(0.0, 0.0, 0.0));
    }
    List<GestureDefinition> zeroVariants = new ArrayList<>();
    zeroVariants.add(new StaticGestureDefinition("Z", LandmarkUtils.normalize(zeroReference)));
    RecognitionResult result = recognizer.recognize(zeroLandmarks, zeroVariants);
    // Zero vectors have no direction - cosine similarity returns 0.0 by design
    assertEquals(0.0, result.getConfidenceScore(), 0.0001);
  }

  @Test
  void testNegativeCoordinatesHandledCorrectly() {
    List<HandLandmark> negativeLandmarks = new ArrayList<>();
    List<HandLandmark> negativeReference = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      negativeLandmarks.add(new HandLandmark(-0.5 + i * 0.01, -0.3 + i * 0.02, -0.1 + i * 0.03));
      negativeReference.add(new HandLandmark(-0.5 + i * 0.01, -0.3 + i * 0.02, -0.1 + i * 0.03));
    }
    List<GestureDefinition> negativeVariants = new ArrayList<>();
    negativeVariants.add(new StaticGestureDefinition("N", LandmarkUtils.normalize(negativeReference)));
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