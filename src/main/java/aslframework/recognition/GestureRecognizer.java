package aslframework.recognition;

import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.model.RecognitionResult;

import java.util.List;

/**
 * Defines the contract for gesture recognition in the ASL learning platform.
 * Implementations compare a user's detected hand landmarks against a target
 * gesture definition and return a scored recognition result.
 *
 * <p>Known implementations include {@link MediaPipeRecognizer} for live camera
 * input and {@code MockGestureRecognizer} for testing purposes.</p>
 */
public interface GestureRecognizer {

  /**
   * Compares the user's hand landmarks against a target gesture definition
   * and returns a recognition result with a confidence score.
   *
   * @param userLandmarks  the list of 21 hand landmarks detected from the user's hand
   * @param targetGesture  the reference gesture definition to compare against
   * @return a RecognitionResult containing the confidence score and match status
   */
  RecognitionResult recognize(List<HandLandmark> userLandmarks, List<GestureDefinition> targetGesture);
}