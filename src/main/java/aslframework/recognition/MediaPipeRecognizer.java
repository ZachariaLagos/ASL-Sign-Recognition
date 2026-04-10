
package aslframework.recognition;

import aslframework.model.GestureDefinition;
import aslframework.model.HandLandmark;
import aslframework.model.RecognitionResult;

import java.util.List;

/**
 * A {@link GestureRecognizer} implementation that compares hand landmark positions
 * using Euclidean distance to compute a confidence score
 *
 * <p>For each of the 21 MediaPipe hand landmarks, the 3D Euclidean distance between
 * the user's landmark and the reference landmark is computed. The average distance
 * is then normalized to a confidence score in the range [0.0, 1.0] using the formula
 * {@code 1.0 / (1.0 + averageDistance)}, where a perfect match yields 1.0.</p>
 *
 * <p>Note: This implementation assumes that landmark coordinates have already been
 * extracted from the camera frame by MediaPipe before being passed in.</p>
 */

public class MediaPipeRecognizer implements GestureRecognizer {

  private static final int LANDMARK_COUNT = 21;

  /**
   * Compares the user's hand landmarks against a target gesture definition
   * and returns a confidence score based on average Euclidean distance
   *
   * @param userLandmarks the list of 21 hand landmarks detected from the user's hand
   * @param targetGesture the reference gesture definition to compare against
   * @return a RecognitionResult containing the confidence score and match status
   * @throws IllegalArgumentException if the landmark list does not contain exactly 21 landmarks
   */
  @Override
  public RecognitionResult recognize(List<HandLandmark> userLandmarks, GestureDefinition targetGesture){
    List<HandLandmark> referenceLandmarks = targetGesture.getReferenceLandmarks();

    if (userLandmarks.size() != LANDMARK_COUNT || referenceLandmarks.size() != LANDMARK_COUNT){
      throw new IllegalArgumentException(
          "Both landmark lists must contain exactly " + LANDMARK_COUNT + " points." +
              "Got user= " + userLandmarks.size() + ", reference= " + referenceLandmarks.size()
      );
    }
    double totalDistance = 0.0;

    for (int i=0; i<LANDMARK_COUNT; i++){
      totalDistance += euclideanDistance(userLandmarks.get(i), referenceLandmarks.get(i));
    }
    double avgDistance = totalDistance / LANDMARK_COUNT;
    double confidenceScore = 1.0 / (1.0 + avgDistance);

    return new RecognitionResult(confidenceScore, targetGesture);
  }

  /**
   * Computes the Euclidean Distance between two 3D hand landmarks.
   *
   * @param a the first landmark
   * @param b the second landmark
   * @return the straight-line distance between the two points in 3D space
   */
  private double euclideanDistance(HandLandmark a, HandLandmark b){
    double dx = a.getX() - b.getX();
    double dy = a.getY() - b.getY();
    double dz = a.getZ() - b.getZ();
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }


}
