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
 * Unit tests for the MockGestureRecognizer class.
 * Verifies that the mock returns predictable results and rejects invalid confidence values.
 */
class MockGestureRecognizerTest {

  private List<GestureDefinition> variants;
  private List<HandLandmark> dummyLandmarks;

  @BeforeEach
  void setUp() {
    dummyLandmarks = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      dummyLandmarks.add(new HandLandmark(i * 0.01, i * 0.02, i * 0.03));
    }
    // Build a small variants list with two rotations
    variants = new ArrayList<>();
    variants.add(new StaticGestureDefinition("A", dummyLandmarks));
    variants.add(new StaticGestureDefinition("A", dummyLandmarks));
  }

  // --- Valid confidence values ---

  @Test
  void testRecognizeReturnsConfiguredConfidenceScore() {
    MockGestureRecognizer recognizer = new MockGestureRecognizer(0.95);
    RecognitionResult result = recognizer.recognize(dummyLandmarks, variants);
    assertEquals(0.95, result.getConfidenceScore());
  }

  @Test
  void testRecognizeReturnsFirstVariantAsClosestMatch() {
    MockGestureRecognizer recognizer = new MockGestureRecognizer(0.95);
    RecognitionResult result = recognizer.recognize(dummyLandmarks, variants);
    assertEquals(variants.get(0), result.getClosestMatch());
  }

  @Test
  void testRecognizeIsMatchTrueWhenHighConfidence() {
    MockGestureRecognizer recognizer = new MockGestureRecognizer(0.95);
    RecognitionResult result = recognizer.recognize(dummyLandmarks, variants);
    assertTrue(result.isMatch());
  }

  @Test
  void testRecognizeIsMatchFalseWhenLowConfidence() {
    MockGestureRecognizer recognizer = new MockGestureRecognizer(0.50);
    RecognitionResult result = recognizer.recognize(dummyLandmarks, variants);
    assertFalse(result.isMatch());
  }

  @Test
  void testRecognizeAtBoundaryConfidence() {
    MockGestureRecognizer recognizer = new MockGestureRecognizer(0.0);
    RecognitionResult result = recognizer.recognize(dummyLandmarks, variants);
    assertEquals(0.0, result.getConfidenceScore());
  }

  @Test
  void testRecognizeAtMaxConfidence() {
    MockGestureRecognizer recognizer = new MockGestureRecognizer(1.0);
    RecognitionResult result = recognizer.recognize(dummyLandmarks, variants);
    assertEquals(1.0, result.getConfidenceScore());
  }

  // --- Invalid confidence values ---

  @Test
  void testConstructorThrowsOnNegativeConfidence() {
    assertThrows(IllegalArgumentException.class, () -> {
      new MockGestureRecognizer(-0.1);
    });
  }

  @Test
  void testConstructorThrowsOnConfidenceAboveOne() {
    assertThrows(IllegalArgumentException.class, () -> {
      new MockGestureRecognizer(1.1);
    });
  }
}