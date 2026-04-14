package aslframework.recognition;

import aslframework.model.HandLandmark;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class providing stateless landmark transformation operations.
 * Used by GestureLibrary and MediaPipeRecognizer to normalize and rotate
 * hand landmarks before recognition.
 */
public class LandmarkUtils {

  /**
   * Normalizes hand landmarks relative to the wrist (landmark 0),
   * making recognition invariant to hand position in the frame.
   *
   * @param landmarks the list of 21 hand landmarks to normalize
   * @return a new list of landmarks with wrist at origin (0, 0, 0)
   */
  public static List<HandLandmark> normalize(List<HandLandmark> landmarks) {
    HandLandmark wrist = landmarks.get(0);
    List<HandLandmark> normalized = new ArrayList<>();
    for (HandLandmark lm : landmarks) {
      normalized.add(new HandLandmark(
          lm.getX() - wrist.getX(),
          lm.getY() - wrist.getY(),
          lm.getZ() - wrist.getZ()
      ));
    }
    return normalized;
  }

  /**
   * Rotates a list of landmarks around their centroid in the x/y plane.
   * The z coordinate is unchanged.
   *
   * @param landmarks the landmarks to rotate
   * @param angleRad  the rotation angle in radians
   * @return a new list of rotated HandLandmark objects
   */
  public static List<HandLandmark> rotateLandmarks(List<HandLandmark> landmarks, double angleRad) {
    double cx = landmarks.stream().mapToDouble(HandLandmark::getX).average().orElse(0.5);
    double cy = landmarks.stream().mapToDouble(HandLandmark::getY).average().orElse(0.5);

    double cos = Math.cos(angleRad);
    double sin = Math.sin(angleRad);

    List<HandLandmark> rotated = new ArrayList<>();
    for (HandLandmark lm : landmarks) {
      double dx = lm.getX() - cx;
      double dy = lm.getY() - cy;
      rotated.add(new HandLandmark(
          cx + (dx * cos - dy * sin),
          cy + (dx * sin + dy * cos),
          lm.getZ()
      ));
    }
    return rotated;
  }
}