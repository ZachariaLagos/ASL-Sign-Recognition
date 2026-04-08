package aslframework.recognition;

import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.model.RecognitionResult;

import java.util.List;

/**
 * A test-only implementation of {@link GestureRecognizer} that returns
 * a configurable, hardcoded recognition result.
 *
 * <p>This class is intended for use in unit tests and development only.
 * It allows teammates to build and test game logic against predictable
 * recognizer output without requiring MediaPipe or a live camera feed.</p>
 */
public class MockGestureRecognizer implements GestureRecognizer {

  private final double fixedConfidence;

  /**
   * Constructs a MockGestureRecognizer that always returns the given confidence score.
   *
   * @param fixedConfidence the confidence score to return on every recognize() call,
   *                        between 0.0 and 1.0
   */
  public MockGestureRecognizer(double fixedConfidence) {
    if (fixedConfidence < 0.0 || fixedConfidence > 1.0) {
      throw new IllegalArgumentException(
          "Confidence score must be between 0.0 and 1.0, got: " + fixedConfidence
      );
    }
    this.fixedConfidence = fixedConfidence;
  }

  /**
   * Returns a hardcoded RecognitionResult using the configured confidence score.
   * The target gesture is passed through as the closest match regardless of landmarks.
   *
   * @param userLandmarks  the user's hand landmarks (ignored in this mock)
   * @param targetGesture  the target gesture, returned as the closest match
   * @return a RecognitionResult with the fixed confidence score
   */
  @Override
  public RecognitionResult recognize(List<HandLandmark> userLandmarks, GestureDefinition targetGesture) {
    return new RecognitionResult(fixedConfidence, targetGesture);
  }
}